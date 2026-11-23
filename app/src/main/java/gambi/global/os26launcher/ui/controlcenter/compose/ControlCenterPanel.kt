package gambi.global.os26launcher.ui.controlcenter.compose

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.OverScroller
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.controlcenter.MediaState
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterState
import gambi.global.os26launcher.domain.model.controlcenter.ControlTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTile
import gambi.global.os26launcher.ui.controlcenter.MediaActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * JS7b: panel Trung tâm điều khiển vẽ bằng Compose, thay `ControlCenterView` (RE mục 5). Lớp này KHÔNG phải composable: giữ đúng API
 * mà presenter, overlay và [gambi.global.os26launcher.ui.controlcenter.ControlCenterMotion] đã dùng với View cũ, còn trạng thái thì
 * nằm trong `mutableStateOf` để [ControlCenterScreen] đọc. [view] là View mà overlay thêm vào WindowManager.
 *
 * Bố cục, cử chỉ và thứ tự Back giữ như View cũ:
 * - ba trang dọc (chính / nhạc / mạng), kéo dọc đổi trang, cao su ở hai đầu, chỉ báo trang ở mép phải;
 * - nhấn giữ khối kết nối/nhạc sang trang Mạng/Nhạc, nhấn giữ thanh độ sáng/âm lượng mở bảng lớn, chạm ô thời gian chờ mở bảng chọn;
 * - chạm vùng trống thì đóng; trang chính kéo xuống thì báo [dismissDrag] (CG);
 * - P2-8 (REF a679197): đầu trang cố định ngoài các trang; trang chính cuộn dọc khi lưới dài hơn chỗ — kéo dọc cuộn lưới trước, hết
 *   đường cuộn mới đổi trang / kéo đóng; trang cuối kéo lên quá ngưỡng (hoặc hất lên) thì đóng panel;
 * - Back: bảng chi tiết → trang chính → đóng.
 *
 * Vòng đời Compose: cửa sổ overlay của service không có Activity nên panel tự làm chủ ([PanelOwner]): CREATED lúc dựng, RESUMED khi
 * cửa sổ panel được thêm, về CREATED khi gỡ, DESTROYED ở [destroy] (service huỷ / dựng lại panel vì đổi ngôn ngữ). Composition cha là
 * một [Recomposer] riêng, không dùng recomposer của cửa sổ: recomposer đó bị huỷ ngay lần gỡ cửa sổ đầu tiên, mà panel thì được thêm/gỡ
 * mỗi lần kéo mở.
 */
class ControlCenterPanel(context: Context) {

    interface Listener : MediaActions {
        fun onTileClick(tile: ControlTile)
        fun onCustomTileClick(tile: CustomTile)
        fun onBrightness(value: Float, done: Boolean)
        fun onVolume(value: Float, done: Boolean)
        fun onAutoBrightness()
        fun onScreenTimeout(millis: Int)
        fun onOpenSettings()

        /** Chạm vùng trống hoặc Back. */
        fun onDismiss()
    }

    /** CG: kéo xuống trên trang chính để đóng panel (overlay giữ hoạt ảnh và quyết định đóng/giữ). */
    interface DismissDrag {
        /** [dy] ≥ 0 px tính từ lúc bắt đầu kéo. */
        fun onDismissDrag(dy: Float)

        /** Nhả tay; [velocityY] px/s, dương là xuống. */
        fun onDismissRelease(dy: Float, velocityY: Float)
    }

    var listener: Listener? = null
    var dismissDrag: DismissDrag? = null

    // ---- Vòng đời + composition riêng (JS7b) ----

    private val owner = PanelOwner()
    private val frameClock = PausableMonotonicFrameClock(AndroidUiDispatcher.Main[MonotonicFrameClock]!!)
    private val composeContext = AndroidUiDispatcher.Main + frameClock
    private val recomposer = Recomposer(composeContext)
    private val recomposeScope = CoroutineScope(composeContext + Job())

    /** Đang nằm trong cửa sổ (ticker tiến độ nhạc chỉ chạy lúc này). */
    internal var attached by mutableStateOf(false)
        private set

    // targetSdk 36+: hệ thống không còn gửi KEYCODE_BACK khi bật predictive back mặc định, nên từ API 33 đăng ký
    // OnBackInvokedCallback trên cửa sổ của panel; dưới 33 vẫn đi đường phím (PanelComposeView.dispatchKeyEvent).
    private val backCallback: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.window.OnBackInvokedCallback { handleBack() }
    } else null

    val view: View = PanelComposeView(context, this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setParentCompositionContext(recomposer)
        // Composition sống qua các lần gỡ/thêm cửa sổ, chỉ bỏ khi owner DESTROYED.
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle))
        isFocusable = true
        isFocusableInTouchMode = true // nhận phím Back khi cửa sổ có focus
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = onAttached(v)
            override fun onViewDetachedFromWindow(v: View) = onDetached(v)
        })
    }

    init {
        recomposeScope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    private fun onAttached(v: View) {
        frameClock.resume()
        owner.moveTo(Lifecycle.State.RESUMED)
        attached = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backCallback as android.window.OnBackInvokedCallback
            )
        }
    }

    private fun onDetached(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback as android.window.OnBackInvokedCallback)
        }
        attached = false
        pageAnimator?.cancel()
        detailAnimator?.cancel()
        stopFling()
        if (!destroyed) owner.moveTo(Lifecycle.State.CREATED)
        frameClock.pause() // panel đã gỡ: hoạt ảnh Compose (chạy chữ) không chạy nền
    }

    private var destroyed = false

    /** JS7b: service huỷ hoặc dựng lại panel: bỏ composition, dừng recomposer. Không dùng lại được sau lệnh này. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        pageAnimator?.cancel()
        detailAnimator?.cancel()
        animatedBlocks.forEach { it.cancel() }
        owner.moveTo(Lifecycle.State.DESTROYED)
        recomposer.cancel()
        recomposeScope.cancel()
    }

    // ---- API cho overlay / motion ----

    val height: Int get() = view.height
    val resources: Resources get() = view.resources

    fun requestFocus(): Boolean = view.requestFocus()

    fun performHapticFeedback(feedbackConstant: Int): Boolean = view.performHapticFeedback(feedbackConstant)

    /** Khoảng cách từ đỉnh màn hình tới đáy thanh trạng thái (px); overlay đặt trước khi hiện. */
    var topInset by mutableIntStateOf(0)
    var bottomInset by mutableIntStateOf(0)

    // ---- Trạng thái vẽ ----

    internal var state by mutableStateOf(ControlCenterState())
        private set
    internal var media by mutableStateOf<MediaState?>(null)
        private set
    internal var nightShiftOn by mutableStateOf(false)
        private set
    internal var customSlots by mutableStateOf<List<CcCustomSlot>>(emptyList())
        private set

    internal val brightness = CcSliderModel()
    internal val volume = CcSliderModel()
    internal val playerVolume = CcSliderModel()

    /** Thanh của bảng chi tiết (độ sáng hoặc âm lượng); View cũ dựng thanh mới mỗi lần mở, ở đây đặt lại giá trị. */
    internal val detailSlider = CcSliderModel()

    // Mỗi khối có hoạt ảnh riêng (trang chính), theo thứ tự đọc; chỉ số quyết định `speed` (RE mục 2.5).
    internal val stripAnim = CcBlockAnim()
    internal val settingsAnim = CcBlockAnim()
    internal val connectivityAnim = CcBlockAnim()
    internal val mediaAnim = CcBlockAnim()
    internal val rotationAnim = CcBlockAnim()
    internal val dndAnim = CcBlockAnim()
    internal val brightnessAnim = CcBlockAnim()
    internal val volumeAnim = CcBlockAnim()
    internal val timeoutAnim = CcBlockAnim()
    private val fixedBlocks = listOf(
        stripAnim, settingsAnim, connectivityAnim, mediaAnim, rotationAnim, dndAnim, brightnessAnim, volumeAnim, timeoutAnim
    )

    /** Các khối có hoạt ảnh riêng; [gambi.global.os26launcher.ui.controlcenter.ControlCenterMotion] đặt alpha/scale/dịch lên đây. */
    var animatedBlocks: List<CcBlockAnim> = fixedBlocks
        private set

    /** Độ đậm nền (ảnh nền, lớp tối, chỉ báo trang); đọc trong `graphicsLayer` nên kéo mở không recompose. */
    internal var bgAlpha by mutableFloatStateOf(0f)
        private set

    internal var backdrop by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var backdropBlur by mutableStateOf(false)
        private set
    internal var scrimColorRes by mutableIntStateOf(R.color.cc_scrim_strong)
        private set

    fun setNightShift(on: Boolean) {
        nightShiftOn = on
    }

    fun render(s: ControlCenterState) {
        state = s
        brightness.setValueFromSystem(s.brightness)
        volume.setValueFromSystem(s.volume)
        playerVolume.setValueFromSystem(s.volume)
        if (detail == CcDetail.BRIGHTNESS && !detailHiding) detailSlider.setValueFromSystem(s.brightness)
    }

    fun renderMedia(s: MediaState?) {
        media = s
    }

    /** Dựng lại hàng ô tùy chỉnh (RE mục 5.3). Trả các ô app để presenter nạp icon và nhãn TalkBack vào [CcCustomSlot]. */
    fun setCustomTiles(tiles: List<CustomTile>): List<Pair<CustomTile.App, CcCustomSlot>> {
        val slots = tiles.map { CcCustomSlot(it) }
        customSlots = slots
        animatedBlocks = fixedBlocks + slots.map { it.anim }
        return slots.mapNotNull { slot -> (slot.tile as? CustomTile.App)?.let { it to slot } }
    }

    // ---- Nền (CG) ----

    /**
     * CG: chế độ "Không" + Làm mờ: panel muốn hệ thống làm mờ màn phía sau (blur xuyên cửa sổ, API 31+). Overlay đọc cờ này
     * trong [onBackgroundAlpha] để đặt `blurBehindRadius` của cửa sổ.
     */
    var wantsBlurBehind = false
        private set

    /** CG: overlay báo máy có đang cho blur xuyên cửa sổ không (ROM tắt được, tắt khi tiết kiệm pin). */
    var blurBehindAvailable = false
        set(value) {
            if (field == value) return
            field = value
            updateScrim()
        }

    /** CG: gọi mỗi khi độ đậm nền đổi (0 = đóng, 1 = mở hẳn), để overlay làm mờ màn phía sau theo cùng nhịp. */
    var onBackgroundAlpha: ((Float) -> Unit)? = null

    private var hasBackdropImage = false

    /** Ảnh nền đã làm mờ sẵn (nhỏ); API 31+ làm mờ thêm lúc vẽ (`Modifier.blur`, 24 dp) cho mịn khi phóng to. */
    fun setBackdrop(bitmap: Bitmap?, blur: Boolean) {
        backdrop = bitmap?.asImageBitmap()
        backdropBlur = blur
        hasBackdropImage = bitmap != null
        wantsBlurBehind = bitmap == null && blur
        updateScrim()
        onBackgroundAlpha?.invoke(bgAlpha)
    }

    /**
     * CG: có ảnh nền thì lớp tối nhẹ để kính khối sáng, rõ màu. Không ảnh mà màn phía sau đã được làm mờ: lớp tối rất nhẹ (như
     * iOS). Không ảnh và không mờ được: lớp tối đậm để chữ trắng vẫn đọc được trên app phía sau.
     */
    private fun updateScrim() {
        scrimColorRes = when {
            hasBackdropImage -> R.color.cc_scrim
            wantsBlurBehind && blurBehindAvailable -> R.color.cc_scrim_blur
            else -> R.color.cc_scrim_strong
        }
    }

    fun setBackgroundAlpha(alpha: Float) {
        bgAlpha = alpha
        onBackgroundAlpha?.invoke(alpha)
    }

    /** Mỗi lần bắt đầu kéo mở: về trang chính, đóng bảng chi tiết. */
    fun resetForOpen() {
        hideDetail(animate = false)
        setPage(0, animate = false)
        resetScroll() // P2-8
    }

    /** Back: đóng bảng chi tiết → về trang chính → đóng panel. */
    internal fun handleBack() {
        when {
            detail != null -> hideDetail()
            page != 0 -> showPage(0)
            else -> listener?.onDismiss()
        }
    }

    // ---- Bảng chi tiết (RE mục 5.2) ----

    internal var detail by mutableStateOf<CcDetail?>(null)
        private set
    internal var detailAlpha by mutableFloatStateOf(0f)
        private set
    private var detailHiding = false
    private var detailAnimator: ValueAnimator? = null
    private val detailInterpolator: TimeInterpolator = AccelerateDecelerateInterpolator() // mặc định của View.animate()

    internal fun showBrightnessDetail() {
        detailSlider.reset(state.brightness)
        showDetail(CcDetail.BRIGHTNESS)
    }

    internal fun showVolumeDetail() {
        detailSlider.reset(state.volume)
        showDetail(CcDetail.VOLUME)
    }

    internal fun showTimeoutPicker() = showDetail(CcDetail.TIMEOUT)

    /** Chiều cao thanh của bảng lớn: `min(min(W, H)·0,8, H·0,5)` px. */
    internal fun bigSliderHeight(): Int =
        (kotlin.math.min(view.width, view.height) * 0.8f).toInt().coerceAtMost((view.height * 0.5f).toInt())

    private fun showDetail(d: CcDetail) {
        detailAnimator?.cancel()
        detailHiding = false
        detail = d
        animateDetail(0f, 1f, null)
    }

    internal fun hideDetail(animate: Boolean = true) {
        if (!animate) {
            detailAnimator?.cancel()
            detailHiding = false
            detail = null
            detailAlpha = 0f
            return
        }
        if (detail == null || detailHiding) return
        detailHiding = true
        animateDetail(detailAlpha, 0f) {
            detailHiding = false
            detail = null
        }
    }

    private fun animateDetail(from: Float, to: Float, onEnd: (() -> Unit)?) {
        detailAnimator?.cancel()
        val anim = ValueAnimator.ofFloat(from, to).setDuration(DETAIL_FADE_MS)
        anim.interpolator = detailInterpolator
        anim.addUpdateListener { detailAlpha = it.animatedValue as Float }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd?.invoke()
            }
        })
        detailAnimator = anim
        anim.start()
    }

    internal fun onDetailSlider(v: Float, done: Boolean) {
        when (detail) {
            CcDetail.BRIGHTNESS -> {
                brightness.setValueFromSystem(v)
                listener?.onBrightness(v, done)
            }

            CcDetail.VOLUME -> {
                volume.setValueFromSystem(v)
                playerVolume.setValueFromSystem(v)
                listener?.onVolume(v, done)
            }

            else -> Unit
        }
    }

    internal fun onMainVolume(v: Float, done: Boolean) {
        playerVolume.setValueFromSystem(v)
        listener?.onVolume(v, done)
    }

    internal fun onPlayerVolume(v: Float, done: Boolean) {
        volume.setValueFromSystem(v)
        listener?.onVolume(v, done)
    }

    internal fun onPickTimeout(ms: Int) {
        hideDetail()
        listener?.onScreenTimeout(ms)
    }

    // ---- Phân trang dọc (RE mục 5.1) ----

    internal var page by mutableIntStateOf(0)
        private set

    /** Độ lệch trang theo tay / hoạt ảnh (px); đọc trong `graphicsLayer`. */
    internal var dragOffset by mutableFloatStateOf(0f)
        private set
    private var pageAnimator: ValueAnimator? = null

    fun showPage(index: Int) = setPage(index.coerceIn(0, PAGE_COUNT - 1), animate = true)

    private fun setPage(index: Int, animate: Boolean) {
        pageAnimator?.cancel()
        val from = dragOffset
        page = index
        if (!animate || view.height == 0) {
            dragOffset = 0f
            return
        }
        // Chuyển trang: dời từ vị trí hiện tại (đang kéo hoặc trang cũ) về trang mới.
        pageAnimator = ValueAnimator.ofFloat(from, 0f).apply {
            duration = PAGE_MS
            interpolator = PathInterpolator(0.16f, 0.545f, 0.455f, 1f)
            addUpdateListener { dragOffset = it.animatedValue as Float }
            start()
        }
    }

    // ---- Cử chỉ của gốc panel (thay onInterceptTouchEvent/onTouchEvent của View cũ) ----

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 2f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dismissing = false
    private var offsetAtDown = 0f

    /**
     * JS7b: một phần tử con (ô, thẻ, chỉ báo trang...) đã nhận cú chạm này, nên nhả tay tại chỗ không đóng panel. Gốc xoá ở chạm
     * xuống (pass Initial của gốc chạy trước con), con đặt lại ngay sau đó.
     */
    internal var childTouched = false

    /** JS7b: thanh trượt/thanh tiến độ đang giữ cú chạm: gốc không giành để đổi trang (như requestDisallowInterceptTouchEvent). */
    internal var disallowIntercept = false

    /** Gốc chỉ xử lý khi không có bảng chi tiết (bảng tự nhận chạm, chạm ngoài thì đóng bảng). */
    internal val gesturesEnabled get() = detail == null

    internal fun onRootDown(x: Float, y: Float) {
        childTouched = false
        disallowIntercept = false
        downX = x
        downY = y
        dragging = false
        pageAnimator?.cancel()
        stopFling() // P2-8: chạm dừng cú cuộn quán tính
        offsetAtDown = dragOffset // chạm giữa lúc đổi trang: giữ vị trí đang dở, không nhảy
        rawOffset = offsetAtDown
    }

    /** Trả true khi gốc đang kéo trang: nơi gọi tiêu thụ sự kiện để ô con huỷ nhấn (như ô con nhận CANCEL). */
    internal fun onRootMove(x: Float, y: Float): Boolean {
        if (!dragging) {
            if (disallowIntercept || !isVerticalDrag(x, y)) return false
            dragging = true
            downY = y // bắt đầu tính quãng từ đây, trang không giật một đoạn bằng slop
            lastDragY = y
            return true
        }
        val dy = y - lastDragY
        lastDragY = y
        applyDrag(dy)
        return true
    }

    // P2-8: quãng đổi trang chưa qua cao su (dragOffset = cao su của nó ở hai đầu) và mốc ngón tay của lần di chuyển trước.
    private var rawOffset = 0f
    private var lastDragY = 0f

    /**
     * P2-8: chia [dy] (px, dương = kéo xuống) giữa cuộn trang chính và đổi trang. Trang chính: đang lệch trang thì kéo ngược trả trang về
     * trước; trang đứng thẳng thì cuộn lưới trước, hết đường cuộn (đỉnh/đáy) phần dư mới thành kéo đổi trang (hoặc kéo đóng, CG).
     */
    private fun applyDrag(dy: Float) {
        var rem = dy
        if (page == 0) {
            if (rawOffset < 0f && rem > 0f) {
                val t = min(rem, -rawOffset); rawOffset += t; rem -= t
            } else if (rawOffset > 0f && rem < 0f) {
                val t = maxOf(rem, -rawOffset); rawOffset += t; rem -= t
            }
            if (rawOffset == 0f && rem != 0f) {
                // Nội dung đi lên khi kéo lên; chạm đỉnh/đáy thì phần còn lại thành kéo trang.
                rem += scrollMainBy(-rem)
            }
        }
        rawOffset += rem
        // CG: trang chính (đã cuộn về đỉnh) kéo xuống = đóng panel, mờ dần theo tay (chủ dự án 2026-11-20); trang không dịch.
        if (page == 0 && dismissDrag != null && (rawOffset > 0f || dismissing)) {
            dismissing = true
            dismissDrag?.onDismissDrag(rawOffset.coerceAtLeast(0f))
            dragOffset = rawOffset.coerceAtMost(0f)
            return
        }
        var off = rawOffset
        // Cao su ở hai đầu (RE mục 5.2: trang 1 kéo xuống, trang 3 kéo lên chỉ giãn rồi về).
        if ((page == 0 && off > 0) || (page == PAGE_COUNT - 1 && off < 0)) off *= RUBBER
        dragOffset = off
    }

    internal fun onRootUp(x: Float, y: Float, velocityY: Float) {
        when {
            dismissing -> {
                dismissing = false
                dragging = false
                dismissDrag?.onDismissRelease(rawOffset.coerceAtLeast(0f), velocityY)
                if (dragOffset != 0f) setPage(page, animate = true)
            }

            dragging -> {
                dragging = false
                if (page == 0 && rawOffset == 0f) {
                    // P2-8: cả cú kéo nằm trong phần cuộn của trang chính -> cuộn quán tính, không đổi trang.
                    flingMain(-velocityY)
                    return
                }
                val h = pagesHeight.takeIf { it > 0 } ?: view.height
                val threshold = h / PAGE_FRACTION
                // P2-8: tới tận cuối (trang cuối) mà vẫn kéo lên quá ngưỡng / hất lên -> đóng Trung tâm điều khiển.
                if (page == PAGE_COUNT - 1 && rawOffset < 0f && (dragOffset < -threshold * RUBBER || velocityY < -minFling)) {
                    listener?.onDismiss()
                    return
                }
                val target = when {
                    (dragOffset < -threshold || velocityY < -minFling) && page < PAGE_COUNT - 1 -> page + 1
                    (dragOffset > threshold || velocityY > minFling) && page > 0 -> page - 1
                    else -> page
                }
                dragOffset += (target - page) * h.toFloat()
                setPage(target, animate = true)
            }

            !childTouched && abs(x - downX) < touchSlop && abs(y - downY) < touchSlop -> listener?.onDismiss()
        }
        settleIfStuck()
    }

    internal fun onRootCancel() {
        if (dismissing) {
            dismissing = false
            dismissDrag?.onDismissRelease(0f, 0f) // huỷ: về trạng thái mở
        }
        if (dragging) setPage(page, animate = true)
        dragging = false
        settleIfStuck()
    }

    // ---- P2-8: cuộn dọc của trang chính (lưới dài hơn vùng trang: nhiều ô tùy chỉnh, màn ngang) ----

    /** Chiều cao vùng trang dưới đầu trang cố định (px); [ControlCenterScreen] đặt lúc đo. */
    internal var pagesHeight = 0

    /** Độ cuộn của lưới trang chính (px, 0 = đỉnh); đọc trong `graphicsLayer` nên cuộn không recompose. */
    internal var mainScroll by mutableFloatStateOf(0f)
        private set

    /** Quãng cuộn tối đa; lưới đặt lúc đo (xoay màn / bớt ô thì kẹp lại độ cuộn). */
    private var mainMaxScroll = 0f

    private val scroller = OverScroller(context)
    private var flingAnimator: TimeAnimator? = null

    internal fun updateMainMaxScroll(max: Float) {
        mainMaxScroll = max.coerceAtLeast(0f)
        if (mainScroll > mainMaxScroll) mainScroll = mainMaxScroll
    }

    /** Cuộn [dy] px (dương = nội dung đi lên), kẹp trong [0, max]. Trả quãng thực sự đã cuộn. */
    private fun scrollMainBy(dy: Float): Float {
        val target = (mainScroll + dy).coerceIn(0f, mainMaxScroll)
        val done = target - mainScroll
        if (done != 0f) mainScroll = target
        return done
    }

    private fun flingMain(velocityY: Float) {
        if (mainMaxScroll <= 0f) return
        stopFling()
        scroller.fling(0, mainScroll.toInt(), 0, velocityY.toInt(), 0, 0, 0, mainMaxScroll.toInt())
        flingAnimator = TimeAnimator().apply {
            setTimeListener { a, _, _ ->
                if (scroller.computeScrollOffset()) {
                    mainScroll = scroller.currY.toFloat().coerceIn(0f, mainMaxScroll)
                } else {
                    a.cancel()
                }
            }
            start()
        }
    }

    private fun stopFling() {
        scroller.forceFinished(true)
        flingAnimator?.cancel()
        flingAnimator = null
    }

    private fun resetScroll() {
        stopFling()
        mainScroll = 0f
    }

    /** Ô con nhận chạm giữa lúc đổi trang (animation đã bị huỷ ở chạm xuống): đưa trang về đúng chỗ khi nhả tay. */
    private fun settleIfStuck() {
        if (!dragging && dragOffset != 0f && pageAnimator?.isRunning != true) setPage(page, animate = true)
    }

    private fun isVerticalDrag(x: Float, y: Float): Boolean {
        val dx = x - downX
        val dy = y - downY
        return abs(dy) > touchSlop && abs(dy) > abs(dx)
    }

    companion object {
        const val PAGE_MAIN = 0
        const val PAGE_MEDIA = 1
        const val PAGE_NETWORK = 2
        internal const val PAGE_COUNT = 3
        private const val RUBBER = 0.35f
        private const val PAGE_FRACTION = 5f
        private const val PAGE_MS = 450L
        private const val DETAIL_FADE_MS = 300L
    }
}

/** JS7b: bảng chi tiết đang mở (RE mục 5.2). */
internal enum class CcDetail { BRIGHTNESS, VOLUME, TIMEOUT }

/**
 * JS7b: alpha / scale / dịch dọc của một khối trên trang chính, thay `View.alpha/scaleX/translationY` + `view.animate()` của View cũ.
 * Composable đọc trong `graphicsLayer {}` nên kéo mở chỉ vẽ lại lớp, không recompose. Tâm co giãn là giữa khối (như pivot cũ).
 */
class CcBlockAnim {
    var alpha by mutableFloatStateOf(1f)
    var scale by mutableFloatStateOf(1f)
    var translationY by mutableFloatStateOf(0f)

    private var animator: ValueAnimator? = null

    // ViewPropertyAnimator giữ interpolator đã đặt lần trước cho các lần sau; lần đầu là AccelerateDecelerate. Giữ y như vậy.
    private var interpolator: TimeInterpolator = DEFAULT_INTERPOLATOR

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    /** Đang chạy một hoạt ảnh [animateTo] (motion không ghi đè alpha/scale lúc này). */
    val isAnimating: Boolean get() = animator?.isRunning == true

    /** Chạy từ giá trị hiện tại tới đích; [translationY] null = không đổi. [interpolator] null = dùng cái đặt lần trước. */
    fun animateTo(alpha: Float, scale: Float, translationY: Float?, durationMs: Long, interpolator: TimeInterpolator? = null) {
        cancel()
        if (interpolator != null) this.interpolator = interpolator
        val a0 = this.alpha
        val s0 = this.scale
        val t0 = this.translationY
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            this.interpolator = this@CcBlockAnim.interpolator
            addUpdateListener {
                val f = it.animatedValue as Float
                this@CcBlockAnim.alpha = a0 + (alpha - a0) * f
                this@CcBlockAnim.scale = s0 + (scale - s0) * f
                if (translationY != null) this@CcBlockAnim.translationY = t0 + (translationY - t0) * f
            }
            start()
        }
    }

    private companion object {
        val DEFAULT_INTERPOLATOR: TimeInterpolator = AccelerateDecelerateInterpolator()
    }
}

/** JS7b: giá trị của một thanh trượt (thay `ControlSliderView.value`). Đặt từ hệ thống bị bỏ qua khi người dùng đang kéo. */
internal class CcSliderModel {
    var value by mutableFloatStateOf(0.5f)
    var dragging = false

    fun setValueFromSystem(v: Float) {
        if (dragging) return
        value = v.coerceIn(0f, 1f)
    }

    fun reset(v: Float) {
        dragging = false
        value = v.coerceIn(0f, 1f)
    }
}

/** JS7b: một ô tùy chỉnh; presenter nạp [appIcon] và [contentDescription] cho ô app (thay `ControlTileView.appIcon`). */
class CcCustomSlot internal constructor(val tile: CustomTile) {
    internal val anim = CcBlockAnim()

    var appIcon by mutableStateOf<Bitmap?>(null)
    var contentDescription by mutableStateOf<String?>(null)
}

/**
 * JS7b: chủ vòng đời cho `ComposeView` trong cửa sổ overlay của service (không có Activity làm chủ). SavedState khôi phục rỗng
 * trước khi lên CREATED (Compose đòi có registry, panel không lưu gì).
 */
private class PanelOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun moveTo(state: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = state
        if (state == Lifecycle.State.DESTROYED) viewModelStore.clear()
    }
}

/** JS7b: View gốc của panel; giữ đường phím Back cho API < 33 như `ControlCenterView.dispatchKeyEvent`. */
@SuppressLint("ViewConstructor")
private class PanelComposeView(context: Context, private val panel: ControlCenterPanel) : AbstractComposeView(context) {

    @Composable
    override fun Content() {
        ControlCenterScreen(panel)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) panel.handleBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
