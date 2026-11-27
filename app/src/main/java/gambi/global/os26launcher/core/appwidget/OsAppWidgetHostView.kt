package gambi.global.os26launcher.core.appwidget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.TextView
import gambi.global.os26launcher.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * G3c Task 4 — host view với chữ lỗi của ta (tiếng Việt, qua `strings.xml`) thay cho "Problem loading widget" của hệ thống.
 *
 * B13 — bỏ padding mặc định (8dp mỗi cạnh) mà [AppWidgetHostView.setAppWidget] tự đặt: thẻ kính của ô đã là khung, padding làm nền widget lùi vào trong thẻ
 * (hai lớp khung) và ăn 16dp chiều cao của hàng lưới vốn thấp hơn Launcher3 (đo được, Live 4 API 30: widget Pulsar 4×1 còn 61dp, hàng nút bị cắt).
 * Hệ thống vẫn trừ padding mặc định khi báo cỡ, nên `HostedWidgetViews.onSized` cộng bù.
 *
 * B13 Task 2 — thu nhỏ cho vừa ô ([onLayout]): hàng lưới kiểu iOS thấp hơn hàng Launcher3, nhiều provider khai `minHeight` thấp hơn layout thật nên nội dung
 * tràn khỏi ô và bị cắt. Cách chung cho mọi widget: bố trí nội dung trong "hộp ảo" lớn hơn ô (ô / tỉ lệ), đo phần nội dung tràn ra ngoài hộp, lặp tới khi hết
 * tràn rồi thu đều cả hai trục (giữ tỉ lệ) về đúng ô. Widget vừa ô giữ tỉ lệ 1, bố trí như `FrameLayout` thường. Provider vẫn nhận cỡ ô thật.
 */
class OsAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private val box = Rect()
    private val extent = Rect()
    private val placed = Rect()

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
    }

    override fun getErrorView(): View = TextView(context).apply {
        setText(R.string.aw_error_load)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 12f
    }

    /**
     * Gọi `super` trước: từ API 31 `AppWidgetHostView.onLayout` tính cỡ hiện tại để chọn `RemoteViews` theo cỡ (responsive) và tự thay view lỗi khi layout
     * ném lỗi (đọc mã nguồn AOSP); bỏ qua nó thì mất cả hai. Sau đó đặt lại từng con trong hộp ảo `ô / s` theo đúng luật `FrameLayout` (lề, `layout_gravity`),
     * rồi thu `s` quanh góc trên trái của ô. Bắt đầu từ `s = 1` mỗi lần layout (nội dung đổi có thể hết tràn); tối đa [MAX_PASSES] lần thu, không dưới
     * [MIN_SCALE] (dưới mức này chấp nhận cắt phần còn lại thay vì chữ quá nhỏ). Lỗi khi đặt lại: trả con về tỉ lệ 1 và layout thường.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left - paddingLeft - paddingRight
        val h = bottom - top - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            try {
                fit(child, w, h)
            } catch (e: RuntimeException) {
                Log.w(TAG, "fit failed ${e.javaClass.name}")
                child.scaleX = 1f
                child.scaleY = 1f
                super.onLayout(true, left, top, right, bottom)
                return
            }
        }
    }

    private fun fit(child: View, w: Int, h: Int) {
        val slop = resources.displayMetrics.density // tràn dưới 1dp (làm tròn) không tính
        var s = 1f
        var pass = 0
        while (true) {
            val bw = (w / s).roundToInt()
            val bh = (h / s).roundToInt()
            layoutInBox(child, bw, bh)
            val ratio = fitRatio(child, slop)
            if (ratio >= 1f || pass == MAX_PASSES || s <= MIN_SCALE) break
            s = max(MIN_SCALE, s * ratio)
            pass++
        }
        // Thu quanh góc trên trái của Ô (không phải của con: con có thể lệch trong hộp vì lề / gravity).
        child.pivotX = (paddingLeft - child.left).toFloat()
        child.pivotY = (paddingTop - child.top).toFloat()
        child.scaleX = s
        child.scaleY = s
    }

    /** Đo và đặt [child] trong hộp `bw × bh` ở góc trên trái của vùng nội dung, theo luật `FrameLayout` (lề, `layout_gravity`). */
    private fun layoutInBox(child: View, bw: Int, bh: Int) {
        box.set(paddingLeft, paddingTop, paddingLeft + bw, paddingTop + bh)
        val spec = { size: Int -> View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY) }
        // Padding của ta là 0 (setAppWidget), nên đo theo hộp chính là đo theo hộp + padding.
        measureChildWithMargins(child, spec(bw + paddingLeft + paddingRight), 0, spec(bh + paddingTop + paddingBottom), 0)
        val lp = child.layoutParams as? FrameLayout.LayoutParams
        // -1 = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY (hằng có từ API 24); FrameLayout coi như TOP | START.
        val gravity = if (lp == null || lp.gravity == -1) Gravity.TOP or Gravity.START else lp.gravity
        val container = Rect(
            box.left + (lp?.leftMargin ?: 0),
            box.top + (lp?.topMargin ?: 0),
            box.right - (lp?.rightMargin ?: 0),
            box.bottom - (lp?.bottomMargin ?: 0)
        )
        Gravity.apply(gravity, child.measuredWidth, child.measuredHeight, container, placed, layoutDirection)
        child.layout(placed.left, placed.top, placed.right, placed.bottom)
    }

    /**
     * Tỉ lệ cần thu để mọi view đang hiện nằm gọn trong hộp: `min(hộp / vùng phủ)` theo hai trục, 1 nếu không tràn. Vùng phủ = hợp khung của con và mọi
     * view con cháu đang VISIBLE (toạ độ trong host). Không đi vào `AdapterView` (ListView / GridView / StackView...): con của nó tràn là do cuộn, không phải
     * do thiếu chỗ.
     */
    private fun fitRatio(child: View, slop: Float): Float {
        extent.set(box)
        accumulate(child, 0, 0)
        val overX = extent.width() - box.width()
        val overY = extent.height() - box.height()
        if (overX <= slop && overY <= slop) return 1f
        return min(box.width().toFloat() / extent.width(), box.height().toFloat() / extent.height())
    }

    private fun accumulate(v: View, dx: Int, dy: Int) {
        if (v.visibility != View.VISIBLE) return
        val l = dx + v.left
        val t = dy + v.top
        if (v.width > 0 && v.height > 0) extent.union(l, t, l + v.width, t + v.height)
        if (v is ViewGroup && v !is AdapterView<*>) {
            for (i in 0 until v.childCount) accumulate(v.getChildAt(i), l - v.scrollX, t - v.scrollY)
        }
    }

    private companion object {
        const val TAG = "OsAppWidgetHostView"

        /** Không thu nhỏ hơn mức này: chữ 14sp còn ~8,4sp. Đổi sau khi đo trên máy. */
        const val MIN_SCALE = 0.6f

        /** Số lần thu tối đa mỗi lần layout (hộp lớn hơn có thể làm view `match_parent` lớn theo và tràn tiếp). */
        const val MAX_PASSES = 3
    }
}
