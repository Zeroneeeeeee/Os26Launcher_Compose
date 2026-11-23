package gambi.global.os26launcher.data.apps

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.SweepGradient
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import gambi.global.os26launcher.core.bitmap.IconShape
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.domain.model.IconTint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biến một [Drawable] icon thành bitmap vuông [IconShape.DEFAULT] đã cắt, hai giai đoạn tách bạch:
 *  1. [flatten]: vẽ icon thành ảnh vuông đầy kín (adaptive: tự vẽ hai lớp; legacy: tấm nền + co cho vừa);
 *  2. [mask]: cắt ảnh đó bằng hình bo góc, góc ngoài hình trong suốt thật (alpha = 0).
 * G2b (tô màu, 3D) sẽ cắm giữa hai giai đoạn: kiểu icon xử lý ảnh đầy kín rồi mới cắt.
 *
 * Gọi song song từ nhiều coroutine IO: không giữ [Paint]/[android.graphics.Path]/bitmap dùng chung.
 */
@Singleton
class IconRenderer @Inject constructor() {

    fun render(drawable: Drawable, sizePx: Int, style: IconStyleKey): Bitmap {
        val shape = IconShape.forCorner(style.corner) // 2026-11-23: thanh "Bo góc" của kính
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        try {
            // G2b Tô màu: adaptive có hình tách được (lớp foreground không phủ kín) vẽ thẳng "ô tối + hình một màu";
            // còn lại (legacy, foreground phủ kín/ảnh) flatten như thường rồi hai tông theo độ sáng (xem [tint]).
            // GI Kính: ô kính trong kiểu Bubble Kiss + hình giữ màu gốc, không đi qua flatten/tint (xem [glassContent]).
            val glyphTile = style.style == IconStyle.TINTED && drawable is AdaptiveIconDrawable &&
                tintGlyphTile(drawable, Canvas(content), sizePx, style.tint)
            // CF Trong suốt: adaptive có hình tách được thì chỉ vẽ foreground (không ô nền); còn lại như Mặc định.
            val bareGlyph = !glyphTile && style.style == IconStyle.TRANSPARENT && drawable is AdaptiveIconDrawable &&
                bareGlyph(drawable, Canvas(content), sizePx)
            if (style.style == IconStyle.GLASS) glassContent(drawable, content, sizePx, shape)
            else if (!glyphTile && !bareGlyph) flatten(drawable, Canvas(content), sizePx)
            // 3D vẽ lớp phủ SAU khi cắt (bevel)
            val styled = if (style.style == IconStyle.TINTED && !glyphTile) tint(
                content,
                sizePx,
                style.tint
            ) else content
            try {
                val out = mask(styled, sizePx, shape)
                if (style.style == IconStyle.THREE_D || style.bevel) bevel(out, sizePx, shape) // CF Step 7: nổi chồng lên kiểu khác
                return out
            } finally {
                if (styled !== content) styled.recycle()
            }
        } finally {
            content.recycle() // bitmap tạm: không vào cache, thu hồi ngay trên mọi đường (kể cả ném lỗi)
        }
    }

    /**
     * P3-4 (CI): icon riêng của người dùng (ảnh hoặc icon mẫu đã vẽ sẵn). KHÔNG áp kiểu icon (Q6), chỉ phủ kín ô vuông kiểu centerCrop rồi cắt
     * theo hình TL2 như mọi icon khác.
     */
    fun renderCustom(src: Bitmap, sizePx: Int, shape: IconShape = IconShape.DEFAULT): Bitmap {
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        try {
            val scale = maxOf(sizePx.toFloat() / src.width, sizePx.toFloat() / src.height)
            val w = src.width * scale
            val h = src.height * scale
            val left = (sizePx - w) / 2f
            val top = (sizePx - h) / 2f
            Canvas(content).drawBitmap(src, null, android.graphics.RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            return mask(content, sizePx, shape)
        } finally {
            content.recycle()
        }
    }

    /**
     * P3-4 (CI, Q5): icon mẫu "hình của app trên nền màu" cho lưới 10 ô của sheet đổi icon. Trả ô VUÔNG CHƯA CẮT (lưu làm icon riêng;
     * [renderCustom] cắt hình lúc hiển thị). Nền: [color] chuyển nhẹ sáng ở trên. Hình:
     * - adaptive có hình tách được ([foregroundIsGlyph]): lớp foreground tô trắng (kiểu glyph của mẫu Figma 5.2);
     * - còn lại (legacy, foreground phủ kín): cả icon gốc thu nhỏ [ON_COLOR_ART_RATIO] đặt giữa.
     */
    fun renderOnColor(drawable: Drawable, sizePx: Int, color: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val size = sizePx.toFloat()
        val top = blend(color, Color.WHITE, ON_COLOR_TOP_LIGHTEN)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, size, top, color, Shader.TileMode.CLAMP) })
        val foreground = (drawable as? AdaptiveIconDrawable)?.foreground?.mutate()
        if (foreground != null && foregroundIsGlyph(foreground)) {
            val q = sizePx / 4
            foreground.setBounds(-q, -q, sizePx + q, sizePx + q)
            // Tô trắng giữ alpha (rìa mịn): mọi kênh màu = 255, alpha giữ nguyên.
            foreground.colorFilter = android.graphics.PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            foreground.draw(canvas)
            foreground.colorFilter = null
        } else {
            val art = (size * ON_COLOR_ART_RATIO).toInt()
            val off = (sizePx - art) / 2
            val icon = drawable.constantState?.newDrawable()?.mutate() ?: drawable
            icon.setBounds(off, off, off + art, off + art)
            icon.draw(canvas)
        }
        return out
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /**
     * IP: icon lấy thẳng từ mục của gói. Gói đã định nghĩa diện mạo nên KHÔNG áp Tô màu/3D; vẫn cắt bằng hình TL2 để lưới đồng
     * nhất và góc đục sót của gói không lộ ra.
     */
    fun renderPackIcon(drawable: Drawable, sizePx: Int, shape: IconShape = IconShape.DEFAULT): Bitmap {
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(content))
            return mask(content, sizePx, shape)
        } finally {
            content.recycle()
        }
    }

    /**
     * IP: icon dự phòng cho app gói không có mục, theo cách ghép chuẩn của launcher hỗ trợ icon pack (nguồn: gist
     * MajeurAndroid/a51869e826b9a283a173b65e923857f8): nền (iconback) vẽ kín hộp; icon gốc co theo `scale` về giữa; nếu có
     * iconmask thì vẽ mặt nạ bằng DST_OUT lên icon (điểm đục của mặt nạ XÓA icon); cuối cùng iconupon phủ lên. Rồi cắt hình TL2.
     */
    fun renderPackFallback(
        original: Drawable,
        sizePx: Int,
        pack: IconPackManager.PackData,
        seed: Int,
        shape: IconShape = IconShape.DEFAULT
    ): Bitmap {
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(content)
            pack.back(seed)?.let {
                it.setBounds(0, 0, sizePx, sizePx)
                it.draw(canvas)
            }
            val target = (sizePx * pack.scale).toInt().coerceIn(1, sizePx * 2)
            val offset = (sizePx - target) / 2
            val icon = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            try {
                val iconCanvas = Canvas(icon)
                iconCanvas.save()
                iconCanvas.translate(offset.toFloat(), offset.toFloat())
                if (original is AdaptiveIconDrawable) flattenAdaptive(
                    original,
                    iconCanvas,
                    target
                ) else {
                    original.setBounds(0, 0, target, target)
                    original.draw(iconCanvas)
                }
                iconCanvas.restore()
                pack.mask()?.let {
                    it.setBounds(0, 0, sizePx, sizePx)
                    val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG)
                    val maskBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    try {
                        it.draw(Canvas(maskBitmap))
                        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                        iconCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)
                    } finally {
                        maskBitmap.recycle()
                    }
                }
                canvas.drawBitmap(icon, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            } finally {
                icon.recycle()
            }
            pack.upon()?.let {
                it.setBounds(0, 0, sizePx, sizePx)
                it.draw(canvas)
            }
            return mask(content, sizePx, shape)
        } finally {
            content.recycle()
        }
    }

    /**
     * Ảnh vuông đầy kín. Adaptive: tự vẽ hai lớp (bỏ mặt nạ của OEM, để hình đồng nhất bất kể ROM).
     * Còn lại (legacy): tấm nền + co cho vừa (xem [flattenLegacy]).
     */
    private fun flatten(drawable: Drawable, canvas: Canvas, sizePx: Int) {
        if (drawable is AdaptiveIconDrawable) {
            flattenAdaptive(drawable, canvas, sizePx)
        } else {
            flattenLegacy(drawable, canvas, sizePx)
        }
    }

    /**
     * Icon legacy (PNG trong suốt, vector, layer...). Đo trước trên ảnh mẫu [SAMPLE_PX]x[SAMPLE_PX]:
     *  - rỗng hoàn toàn: chỉ tấm nền;
     *  - kín viền (>= [FULL_BLEED_RATIO] điểm ảnh viền đục): icon tự mang nền, vẽ kín hộp, không tấm nền;
     *  - còn lại: tấm nền (trắng, xám đậm nếu hình quá sáng) rồi vẽ icon co/phóng sao cho cạnh dài của hộp bao
     *    điểm ảnh đục bằng [LEGACY_ART_RATIO] x sizePx, đặt giữa hộp.
     */
    private fun flattenLegacy(drawable: Drawable, canvas: Canvas, sizePx: Int) {
        val sample = Bitmap.createBitmap(SAMPLE_PX, SAMPLE_PX, Bitmap.Config.ARGB_8888)
        var minX = SAMPLE_PX;
        var minY = SAMPLE_PX;
        var maxX = -1;
        var maxY = -1
        var borderOpaque = 0;
        var borderTotal = 0
        var lumaSum = 0.0;
        var lumaCount = 0
        try {
            drawable.setBounds(0, 0, SAMPLE_PX, SAMPLE_PX)
            drawable.draw(Canvas(sample))
            val px = IntArray(SAMPLE_PX * SAMPLE_PX)
            sample.getPixels(px, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
            for (y in 0 until SAMPLE_PX) for (x in 0 until SAMPLE_PX) {
                val c = px[y * SAMPLE_PX + x]
                val a = c ushr 24
                if (x == 0 || y == 0 || x == SAMPLE_PX - 1 || y == SAMPLE_PX - 1) {
                    borderTotal++
                    if (a >= BORDER_OPAQUE_ALPHA) borderOpaque++
                }
                if (a >= ALPHA_MIN) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                if (a >= LUMA_ALPHA_MIN) {
                    val r = (c shr 16) and 0xFF;
                    val g = (c shr 8) and 0xFF;
                    val b = c and 0xFF
                    lumaSum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    lumaCount++
                }
            }
        } finally {
            sample.recycle()
        }

        if (maxX < 0) { // rỗng hoàn toàn
            canvas.drawColor(PLATE_WHITE)
            return
        }
        drawable.setBounds(0, 0, sizePx, sizePx)
        if (borderOpaque >= FULL_BLEED_RATIO * borderTotal) { // tự mang nền
            drawable.draw(canvas)
            return
        }
        val luma = if (lumaCount > 0) lumaSum / lumaCount else 0.0
        canvas.drawColor(if (luma > BRIGHT_LUMA) PLATE_DARK else PLATE_WHITE)
        val w = (maxX - minX + 1).toFloat() / SAMPLE_PX
        val h = (maxY - minY + 1).toFloat() / SAMPLE_PX
        val scale = (LEGACY_ART_RATIO / maxOf(w, h)).coerceAtMost(LEGACY_MAX_SCALE)
        val cx = (minX + maxX + 1) / 2f / SAMPLE_PX // tâm hộp bao theo tỉ lệ cạnh
        val cy = (minY + maxY + 1) / 2f / SAMPLE_PX
        canvas.save()
        canvas.translate(sizePx / 2f, sizePx / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-cx * sizePx, -cy * sizePx)
        drawable.draw(canvas)
        canvas.restore()
    }

    /**
     * Mỗi lớp của adaptive icon rộng 108dp, vùng nhìn thấy là 72dp ở giữa (tài liệu adaptive icon). Phóng lớp ra
     * mỗi phía thêm sizePx/4 (= (108 - 72) / 2 / 72 cạnh) thì vùng nhìn thấy lấp đầy hộp: cùng cỡ nhìn thấy như khi
     * vẽ qua AdaptiveIconDrawable.draw, chỉ khác là không bị mặt nạ OEM cắt. Bounds đặt thẳng lên drawable con;
     * drawable adaptive này chỉ dùng cho một lần vẽ rồi bỏ.
     */
    private fun flattenAdaptive(drawable: AdaptiveIconDrawable, canvas: Canvas, sizePx: Int) {
        val q = sizePx / 4
        val background = drawable.background
        val foreground = drawable.foreground
        if (background == null) {
            // Không có lớp nền: tấm nền trắng để icon không trong suốt (Task 3 dùng chung hàm tấm nền)
            canvas.drawColor(PLATE_WHITE)
        } else {
            background.setBounds(-q, -q, sizePx + q, sizePx + q)
            background.draw(canvas)
        }
        if (foreground != null) {
            foreground.setBounds(-q, -q, sizePx + q, sizePx + q)
            foreground.draw(canvas)
        }
    }

    /**
     * CF — kiểu Trong suốt: vẽ riêng lớp foreground (màu gốc), bỏ lớp background, cùng tỉ lệ như [flattenAdaptive]. Trả false
     * (chưa vẽ gì) khi foreground không tách được hình ([foregroundIsGlyph]): caller vẽ như Mặc định.
     * mutate() trước khi vẽ thử: foreground chung ConstantState với mọi bản của cùng app (B8).
     */
    private fun bareGlyph(drawable: AdaptiveIconDrawable, canvas: Canvas, sizePx: Int): Boolean {
        val foreground = drawable.foreground?.mutate() ?: return false
        if (!foregroundIsGlyph(foreground)) return false
        val q = sizePx / 4
        foreground.setBounds(-q, -q, sizePx + q, sizePx + q)
        foreground.draw(canvas)
        return true
    }

    /**
     * G2b — Tô màu cho adaptive icon (cách làm của Tinted iOS và của Android 16 "auto-themed": ô nền đồng nhất + hình một
     * màu; plan G2b mục Task 5). BỎ lớp background của app, vẽ MỌI icon trên cùng một ô tối (chuyển nhẹ từ trên xuống), rồi
     * vẽ riêng lớp foreground bằng một [ColorMatrixColorFilter]: độ sáng của foreground ánh xạ vào khoảng [glyphLo, glyphHi]
     * của cùng màu tô (không bao giờ tối hơn glyphLo nên luôn đọc được trên ô tối), alpha giữ nguyên nên rìa hình vẫn mịn.
     * Một lượt vẽ, không đọc điểm ảnh của ảnh lớn. Trả false (chưa vẽ gì) khi foreground không tách được hình: xem [foregroundIsGlyph].
     * Lưu ý: gắn bộ lọc màu lên foreground; drawable này chỉ dùng cho một lần vẽ rồi bỏ (như [flattenAdaptive]).
     */
    private fun tintGlyphTile(
        drawable: AdaptiveIconDrawable,
        canvas: Canvas,
        sizePx: Int,
        tint: IconTint
    ): Boolean {
        // B8: foreground chung ConstantState (và Paint của BitmapState) với mọi bản newDrawable của cùng app trong process; phải
        // mutate() TRƯỚC khi vẽ thử ([foregroundIsGlyph]) lẫn khi gắn bộ lọc màu, nếu không bộ lọc rò sang lần vẽ Mặc định/3D.
        val foreground = drawable.foreground?.mutate() ?: return false
        if (!foregroundIsGlyph(foreground)) return false
        val size = sizePx.toFloat()
        fun hsv(h: Float, sat: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(h, sat.coerceIn(0f, 1f), v))

        val tilePaint = Paint()
        tilePaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            size,
            hsv(tint.hue, tint.saturation, TINT_TILE_TOP_VALUE),
            hsv(tint.hue, tint.saturation, TINT_TILE_BOTTOM_VALUE),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size, size, tilePaint)

        val lo = hsv(tint.hue, tint.saturation * TINT_GLYPH_LO_SATURATION, TINT_GLYPH_LO_VALUE)
        val hi = hsv(tint.hue, tint.saturation * TINT_LIGHT_SATURATION, TINT_LIGHT_VALUE)
        fun row(shift: Int): FloatArray {
            val l = ((lo shr shift) and 0xFF).toFloat()
            val d = (((hi shr shift) and 0xFF) - l) / 255f
            return floatArrayOf(d * LUMA_R, d * LUMA_G, d * LUMA_B, 0f, l)
        }

        val r = row(16);
        val g = row(8);
        val b = row(0)
        foreground.setColorFilter(
            ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        r[0], r[1], r[2], 0f, r[4],
                        g[0], g[1], g[2], 0f, g[4],
                        b[0], b[1], b[2], 0f, b[4],
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        )
        val q =
            sizePx / 4 // như flattenAdaptive: phóng lớp 108dp để vùng nhìn thấy 72dp lấp đầy hộp
        canvas.save()
        canvas.scale(
            TINT_GLYPH_SCALE,
            TINT_GLYPH_SCALE,
            size / 2f,
            size / 2f
        ) // hình nhỏ lại một chút để ô còn đọc là ô
        foreground.setBounds(-q, -q, sizePx + q, sizePx + q)
        // GI review: gỡ bộ lọc sau khi vẽ (ConstantState dùng chung) để kiểu DEFAULT/THREE_D không thừa hưởng màu tô.
        try {
            foreground.draw(canvas)
        } finally {
            foreground.setColorFilter(null)
        }
        canvas.restore()
        return true
    }

    /**
     * GI — kiểu Kính, theo chế độ icon "Clear" của iOS 26 (ảnh chủ dự án gửi 2026-11-10). Ô kính MỜ màu sữa: ảnh nền mờ do
     * `IconCellView` vẽ phía sau (kính tại chỗ, chỉ ở lưới + dock trên máy đủ khả năng); bitmap này mang phần còn lại:
     *  1. lòng ô trắng nhạt [GLASS_FILL_COLOR] (máy / nơi không có kính phía sau vẫn đọc được là một ô kính);
     *  2. bóng đổ rất nhẹ của hình;
     *  3. hình đơn sắc trắng-xám theo độ sáng (giữ chi tiết bên trong), hơi trong ([GLASS_GLYPH_ALPHA]), phủ gradient sáng trên / tối
     *     dưới cho cảm giác nổi;
     *  4. viền sáng mảnh SweepGradient: sáng mạnh ở góc trên-trái và dưới-phải, mờ ở hai góc còn lại.
     * Adaptive có hình tách được: dùng foreground. Còn lại (legacy, phủ kín): [flatten], đơn sắc, cắt thành ô bo góc nhỏ ở giữa.
     * [mask] cắt sau cùng nên nửa ngoài của nét viền bị cắt: vẽ nét dày gấp đôi.
     */
    private fun glassContent(drawable: Drawable, content: Bitmap, sizePx: Int, shape: IconShape) {
        val canvas = Canvas(content)
        val size = sizePx.toFloat()
        canvas.drawColor(GLASS_FILL_COLOR)

        val glyph = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        try {
            drawGlassGlyph(drawable, glyph, sizePx)
            // Gradient nổi: chỉ phủ lên điểm ảnh của hình (SRC_ATOP).
            val shade = Paint(Paint.ANTI_ALIAS_FLAG)
            shade.shader = LinearGradient(
                0f, size * 0.15f, 0f, size * 0.85f,
                intArrayOf(argbWhite(GLASS_GLYPH_LIGHT_ALPHA), 0, 0, argbBlack(GLASS_GLYPH_SHADE_ALPHA)),
                floatArrayOf(0f, 0.45f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            shade.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            Canvas(glyph).drawRect(0f, 0f, size, size, shade)

            // Bóng đổ: alpha của hình, làm mờ, dời xuống dưới.
            val blur = Paint().apply { maskFilter = BlurMaskFilter(size * GLASS_SHADOW_BLUR, BlurMaskFilter.Blur.NORMAL) }
            val offset = IntArray(2)
            val shadow = glyph.extractAlpha(blur, offset)
            try {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argbBlack(GLASS_SHADOW_ALPHA) }
                canvas.drawBitmap(shadow, offset[0].toFloat(), offset[1] + size * GLASS_SHADOW_DY, shadowPaint)
            } finally {
                shadow.recycle()
            }
            val glyphPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (GLASS_GLYPH_ALPHA * 255).toInt() }
            canvas.drawBitmap(glyph, 0f, 0f, glyphPaint)
        } finally {
            glyph.recycle()
        }
        drawGlassRim(canvas, sizePx, shape)
    }

    /** Hình của icon vào [out] (trong suốt quanh hình), đơn sắc trắng-xám: độ sáng gốc ánh xạ vào [GLASS_GLYPH_LO]..[GLASS_GLYPH_HI]. */
    private fun drawGlassGlyph(drawable: Drawable, out: Bitmap, sizePx: Int) {
        val canvas = Canvas(out)
        val size = sizePx.toFloat()
        val mono = duotoneFilter(GLASS_GLYPH_LO, GLASS_GLYPH_HI)
        val foreground = (drawable as? AdaptiveIconDrawable)?.foreground
        if (foreground != null && foregroundIsGlyph(foreground, requireMainArt = true)) {
            foreground.colorFilter = mono
            val q = sizePx / 4 // như flattenAdaptive: phóng lớp 108dp để vùng nhìn thấy 72dp lấp đầy hộp
            canvas.save()
            canvas.scale(GLASS_GLYPH_SCALE, GLASS_GLYPH_SCALE, size / 2f, size / 2f)
            foreground.setBounds(-q, -q, sizePx + q, sizePx + q)
            // GI review: bộ lọc có thể nằm trong ConstantState dùng chung -> luôn gỡ sau khi vẽ để kiểu khác không bị dính màu.
            try {
                foreground.draw(canvas)
            } finally {
                foreground.colorFilter = null
            }
            canvas.restore()
            return
        }
        // Legacy / phủ kín: ảnh đầy kín đơn sắc, cắt thành ô bo góc nhỏ ở giữa để vẫn đọc như một "hình" trên kính.
        val flat = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        try {
            flatten(drawable, Canvas(flat), sizePx)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.colorFilter = mono
            paint.shader = BitmapShader(flat, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.save()
            canvas.scale(GLASS_FALLBACK_SCALE, GLASS_FALLBACK_SCALE, size / 2f, size / 2f)
            canvas.drawPath(IconShape.DEFAULT.path(sizePx), paint)
            canvas.restore()
        } finally {
            flat.recycle()
        }
    }

    /**
     * Viền sáng kiểu iOS 26: nét theo hình icon, màu SweepGradient quanh tâm (Android bắt đầu ở hướng 3 giờ, chạy theo chiều kim
     * đồng hồ): đỉnh sáng ở 45° (dưới-phải) và 225° (trên-trái), nền mờ ở 135° và 315°.
     */
    private fun drawGlassRim(canvas: Canvas, sizePx: Int, shape: IconShape) {
        val s = sizePx.toFloat()
        val base = argbWhite(GLASS_RIM_BASE_ALPHA)
        val peak = argbWhite(GLASS_RIM_PEAK_ALPHA)
        val minor = argbWhite(GLASS_RIM_MINOR_ALPHA)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * GLASS_RIM_WIDTH * 2f // nửa ngoài bị [mask] cắt
            shader = SweepGradient(
                s / 2f, s / 2f,
                intArrayOf(base, minor, base, base, peak, base, base),
                floatArrayOf(0f, 0.125f, 0.25f, 0.5f, 0.625f, 0.75f, 1f)
            )
        }
        canvas.drawPath(shape.path(sizePx), rim)
    }

    /** ColorMatrix đưa độ sáng (luma) của mỗi điểm vào khoảng màu [lo]..[hi], giữ alpha. */
    private fun duotoneFilter(lo: Int, hi: Int): ColorMatrixColorFilter {
        fun row(shift: Int): FloatArray {
            val l = ((lo shr shift) and 0xFF).toFloat()
            val d = (((hi shr shift) and 0xFF) - l) / 255f
            return floatArrayOf(d * LUMA_R, d * LUMA_G, d * LUMA_B, 0f, l)
        }
        val r = row(16); val g = row(8); val b = row(0)
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r[0], r[1], r[2], 0f, r[4],
                    g[0], g[1], g[2], 0f, g[4],
                    b[0], b[1], b[2], 0f, b[4],
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    private fun argbWhite(alpha: Float): Int = Color.argb((alpha * 255).toInt(), 255, 255, 255)
    private fun argbBlack(alpha: Float): Int = Color.argb((alpha * 255).toInt(), 0, 0, 0)

    /**
     * Foreground có tách được "hình" khỏi nền không? Vẽ thử ở [GLYPH_PROBE_PX] vuông (vùng nhìn thấy, như [flattenAdaptive]):
     * hình khi có điểm ảnh trong suốt và viền không bị phủ kín (< [GLYPH_FULL_BLEED_RATIO] điểm viền đục). Foreground phủ kín
     * (ảnh, icon game, gradient tràn viền) hay rỗng thì không tách được: caller dùng đường hai tông theo độ sáng.
     */
    private fun foregroundIsGlyph(foreground: Drawable, requireMainArt: Boolean = false): Boolean {
        val s = GLYPH_PROBE_PX
        val probe = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        try {
            val q = s / 4
            foreground.setBounds(-q, -q, s + q, s + q)
            foreground.draw(Canvas(probe))
            val px = IntArray(s * s)
            probe.getPixels(px, 0, s, 0, 0, s, s)
            var visible = 0
            var borderOpaque = 0
            var borderTotal = 0
            var sumX = 0f
            var sumY = 0f
            for (y in 0 until s) for (x in 0 until s) {
                val a = px[y * s + x] ushr 24
                if (a >= ALPHA_MIN) {
                    visible++
                    sumX += x + 0.5f
                    sumY += y + 0.5f
                }
                if (x == 0 || y == 0 || x == s - 1 || y == s - 1) {
                    borderTotal++
                    if (a >= BORDER_OPAQUE_ALPHA) borderOpaque++
                }
            }
            if (visible == 0 || borderOpaque >= GLYPH_FULL_BLEED_RATIO * borderTotal) return false
            if (!requireMainArt) return true
            // GI: foreground chỉ là một logo nhỏ / lệch góc (vd AI Video Enhancer: tranh nằm ở lớp nền) thì KHÔNG phải hình chính
            // của icon -> caller vẽ cả icon. Trọng tâm tính theo tỉ lệ cạnh, tâm hộp = 0,5.
            val dx = sumX / visible / s - 0.5f
            val dy = sumY / visible / s - 0.5f
            return visible >= GLYPH_MIN_COVERAGE * s * s && dx * dx + dy * dy <= GLYPH_MAX_CENTER_OFFSET * GLYPH_MAX_CENTER_OFFSET
        } finally {
            probe.recycle()
        }
    }

    /**
     * G2b — Tô màu, đường dự phòng (legacy, foreground phủ kín): xám hóa theo độ sáng rồi ánh xạ lên hai tông của cùng một màu (tối ở
     * độ sáng thấp, sáng ở độ sáng cao). Chạy giống nhau trên mọi API: không dùng `getMonochrome()` (chỉ API 33+, xem
     * plan G2b D2). Một lượt vẽ với [ColorMatrixColorFilter], không `getPixels` toàn ảnh; độ sáng trung bình đo trên
     * ảnh thu nhỏ [TINT_PROBE_PX]. Icon sáng (nền trắng) đảo độ sáng để nền luôn tối. Trả bitmap MỚI; người gọi thu hồi.
     */
    private fun tint(content: Bitmap, sizePx: Int, tint: IconTint): Bitmap {
        val invert = borderLuma(content) > TINT_INVERT_LUMA
        val dark = Color.HSVToColor(floatArrayOf(tint.hue, tint.saturation, TINT_DARK_VALUE))
        val light = Color.HSVToColor(
            floatArrayOf(
                tint.hue,
                tint.saturation * TINT_LIGHT_SATURATION,
                TINT_LIGHT_VALUE
            )
        )
        // out_k = lo_k + (hi_k - lo_k) * L', với L' = độ sáng đã căng tương phản quanh 0,5 (và đảo nếu icon sáng)
        val lo = if (invert) light else dark
        val hi = if (invert) dark else light
        val sign = TINT_CONTRAST
        fun row(shift: Int): FloatArray {
            val l = ((lo shr shift) and 0xFF).toFloat()
            val h = ((hi shr shift) and 0xFF).toFloat()
            val d = (h - l) * sign / 255f // hệ số trên kênh 0..255; độ sáng chuẩn hóa = (w·P) / 255
            val offset = l + (h - l) * (0.5f - 0.5f * sign)
            return floatArrayOf(d * LUMA_R, d * LUMA_G, d * LUMA_B, 0f, offset)
        }

        val r = row(16);
        val g = row(8);
        val b = row(0)
        val matrix = ColorMatrix(
            floatArrayOf(
                r[0], r[1], r[2], 0f, r[4],
                g[0], g[1], g[2], 0f, g[4],
                b[0], b[1], b[2], 0f, b[4],
                0f, 0f, 0f, 1f, 0f
            )
        )
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        Canvas(out).drawBitmap(content, 0f, 0f, paint)
        return out
    }

    /**
     * G2b — kiểu 3D: ba lượt vẽ lên bitmap ĐÃ cắt, lượt nào cũng nằm trong hình icon nên chống răng cưa và góc ngoài vẫn
     * alpha 0. KHÔNG dùng `clipPath` (canvas phần mềm không chống răng cưa khi clip) hay `DST_IN` (bài học TL2). Không bóng
     * đổ ra ngoài: bitmap đã bằng đúng hộp icon.
     *  1. khối: MỘT gradient dọc nhiều điểm dừng trên cả hình: sáng ở đỉnh, trong suốt ở giữa, tối ở đáy (độ sâu);
     *  2. bóng loáng: một hình elip ở nửa trên, trắng đậm ở đỉnh nhạt dần xuống (cạnh dưới elip tạo vệt bóng như kính);
     *  3. viền trong: nét mảnh thụt vào trong hình, sáng ở trên, tối ở dưới (vát cạnh mềm).
     * Biến đổi [out] tại chỗ (bitmap mới tinh của [mask], chưa vào cache).
     */
    private fun bevel(out: Bitmap, sizePx: Int, iconShape: IconShape = IconShape.DEFAULT) {
        val canvas = Canvas(out)
        val size = sizePx.toFloat()
        val shape = iconShape.path(sizePx)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, size,
            intArrayOf(
                argb(BEVEL_TOP_LIGHT_ALPHA, 255),
                argb(0f, 255),
                argb(0f, 0),
                argb(BEVEL_BOTTOM_SHADE_ALPHA, 0)
            ),
            floatArrayOf(0f, BEVEL_LIGHT_END, BEVEL_SHADE_START, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawPath(shape, paint)

        val gTop = size * GLOSS_TOP
        val gBottom = size * GLOSS_BOTTOM
        paint.shader = LinearGradient(
            0f,
            gTop,
            0f,
            gBottom,
            argb(GLOSS_ALPHA_TOP, 255),
            argb(GLOSS_ALPHA_BOTTOM, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(size * GLOSS_SIDE, gTop, size * (1f - GLOSS_SIDE), gBottom, paint)

        // Viền: đường hình thu vào một nửa bề rộng nét để nét nằm gọn bên trong hình.
        val stroke = size / BEVEL_RIM_DIVISOR
        val rim = iconShape.path(sizePx)
        val scale = (size - stroke) / size
        rim.transform(Matrix().apply { setScale(scale, scale, size / 2f, size / 2f) })
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rimPaint.style = Paint.Style.STROKE
        rimPaint.strokeWidth = stroke
        rimPaint.shader = LinearGradient(
            0f, 0f, 0f, size,
            intArrayOf(
                argb(BEVEL_RIM_LIGHT_ALPHA, 255),
                argb(0f, 255),
                argb(BEVEL_RIM_DARK_ALPHA, 0)
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawPath(rim, rimPaint)
    }

    private fun argb(alpha: Float, gray: Int): Int =
        Color.argb((alpha * 255).toInt(), gray, gray, gray)

    /**
     * Độ sáng trung bình 0..1 của VIỀN ảnh đầy kín (nền của icon), đo trên ảnh thu nhỏ rất nhỏ. Đo viền thay vì cả ảnh
     * để ô nền luôn tối như Tinted của iOS: icon nền xanh lá/xanh nhạt (độ sáng trung bình) cũng bị đảo thành nền tối,
     * hình sáng (đo trên Live 4: đo cả ảnh làm điện thoại/tin nhắn thành ô xanh nhạt).
     */
    private fun borderLuma(content: Bitmap): Double {
        val probe = Bitmap.createScaledBitmap(content, TINT_PROBE_PX, TINT_PROBE_PX, true)
        try {
            val px = IntArray(TINT_PROBE_PX * TINT_PROBE_PX)
            probe.getPixels(px, 0, TINT_PROBE_PX, 0, 0, TINT_PROBE_PX, TINT_PROBE_PX)
            var sum = 0.0
            var n = 0
            val last = TINT_PROBE_PX - 1
            for (y in 0..last) for (x in 0..last) {
                if (x != 0 && y != 0 && x != last && y != last) continue
                val c = px[y * TINT_PROBE_PX + x]
                sum += LUMA_R * ((c shr 16) and 0xFF) + LUMA_G * ((c shr 8) and 0xFF) + LUMA_B * (c and 0xFF)
                n++
            }
            return sum / n / 255.0
        } finally {
            if (probe !== content) probe.recycle()
        }
    }

    /**
     * `drawPath` + `DST_IN` KHÔNG dùng được: chỉ chạm điểm ảnh nằm trong path, góc ngoài vẫn đục. Ở đây bitmap kết
     * quả mới tinh (alpha 0) và chỉ vùng trong hình được tô bằng shader của [content], nên góc giữ alpha 0.
     */
    private fun mask(content: Bitmap, sizePx: Int, shape: IconShape = IconShape.DEFAULT): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        Canvas(out).drawPath(shape.path(sizePx), paint)
        return out
    }

    private companion object {
        const val PLATE_WHITE = 0xFFFFFFFF.toInt()
        const val PLATE_DARK = 0xFF2C2C2E.toInt() // xám đậm kiểu iOS, cho logo quá sáng

        // CHỈNH TAY (icon mẫu trên nền màu, P3-4):
        const val ON_COLOR_ART_RATIO = 0.62f // icon legacy thu nhỏ đặt giữa ô màu
        const val ON_COLOR_TOP_LIGHTEN = 0.18f // đỉnh ô sáng hơn chân bấy nhiêu (pha trắng)

        // CHỈNH TAY (icon legacy):
        const val LEGACY_ART_RATIO = 0.70f   // cạnh dài của hình / cạnh hộp
        const val LEGACY_MAX_SCALE = 1.6f    // không phóng logo bé quá mức
        const val FULL_BLEED_RATIO = 0.9f    // tỉ lệ điểm ảnh viền đục để coi là "tự mang nền"
        const val BRIGHT_LUMA =
            0.85         // độ sáng trung bình của hình, trên mức này dùng tấm nền tối

        // CHỈNH TAY (kiểu Tô màu, G2b):
        const val TINT_INVERT_LUMA =
            0.35    // độ sáng VIỀN trên mức này thì đảo (nền tô thành tối, hình thành sáng)
        const val TINT_DARK_VALUE = 0.20f    // HSV V của tông tối
        const val TINT_LIGHT_VALUE = 1.0f    // HSV V của tông sáng
        const val TINT_LIGHT_SATURATION =
            0.35f // tông sáng nhạt hơn tông tối (nhân với độ bão hòa của màu)
        const val TINT_CONTRAST = 1.2f       // căng tương phản quanh độ sáng 0,5
        const val TINT_PROBE_PX = 8          // cỡ ảnh thu nhỏ để đo độ sáng trung bình

        // CHỈNH TAY (kiểu 3D, G2b):
        const val BEVEL_TOP_LIGHT_ALPHA = 0.20f  // độ sáng ở đỉnh của lớp "khối"
        const val BEVEL_BOTTOM_SHADE_ALPHA = 0.32f // độ tối ở đáy của lớp "khối"
        const val BEVEL_LIGHT_END = 0.45f        // vùng sáng kết thúc ở tỉ lệ chiều cao này
        const val BEVEL_SHADE_START = 0.55f      // vùng tối bắt đầu từ đây
        const val GLOSS_TOP = 0.035f             // elip bóng loáng: mép trên (tỉ lệ chiều cao)
        const val GLOSS_BOTTOM = 0.50f           // mép dưới
        const val GLOSS_SIDE = 0.07f             // chừa hai bên (tỉ lệ chiều rộng)
        const val GLOSS_ALPHA_TOP = 0.34f
        const val GLOSS_ALPHA_BOTTOM = 0.04f
        const val BEVEL_RIM_DIVISOR = 96f        // bề rộng viền = cạnh / giá trị này
        const val BEVEL_RIM_LIGHT_ALPHA = 0.60f
        const val BEVEL_RIM_DARK_ALPHA = 0.30f

        // CHỈNH TAY (Tô màu, đường "ô tối + hình một màu", G2b Task 5):
        const val TINT_TILE_TOP_VALUE = 0.27f    // HSV V của ô nền ở đỉnh
        const val TINT_TILE_BOTTOM_VALUE = 0.15f // ... ở đáy
        const val TINT_GLYPH_LO_VALUE = 0.62f    // hình tối nhất (luôn đọc được trên ô tối)
        const val TINT_GLYPH_LO_SATURATION = 0.55f
        const val TINT_GLYPH_SCALE = 0.86f       // thu hình để ô nền còn đọc là ô
        const val GLYPH_PROBE_PX =
            16            // cỡ ảnh thử để biết foreground có tách được hình không
        const val GLYPH_FULL_BLEED_RATIO = 0.85f // tỉ lệ điểm viền đục để coi foreground là phủ kín
        const val GLYPH_MIN_COVERAGE = 0.08f     // GI: foreground che ít hơn tỉ lệ này của vùng nhìn thấy -> chỉ là logo phụ
        const val GLYPH_MAX_CENTER_OFFSET = 0.15f // GI: trọng tâm foreground lệch tâm quá mức này (tỉ lệ cạnh) -> logo góc

        // CHỈNH TAY (kiểu Kính, GI, theo icon "Clear" của iOS 26; độ dày là tỉ lệ cạnh icon):
        const val GLASS_FILL_COLOR = 0x14FFFFFF        // lòng ô: trắng ~8%; tông chính đến từ kính mờ phía sau (tự đổi theo nền)
        const val GLASS_GLYPH_HI = 0xFFFFFFFF.toInt()  // phần sáng của hình: trắng
        const val GLASS_GLYPH_LO = 0xFF8A9099.toInt()  // phần tối của hình: xám (giữ nét chi tiết)
        const val GLASS_GLYPH_ALPHA = 0.92f            // hình hơi trong
        const val GLASS_GLYPH_SCALE = 0.86f
        const val GLASS_FALLBACK_SCALE = 0.62f         // ô hình của icon legacy / phủ kín
        const val GLASS_GLYPH_LIGHT_ALPHA = 0.10f      // gradient nổi: sáng ở trên
        const val GLASS_GLYPH_SHADE_ALPHA = 0.14f      // ... tối ở dưới
        const val GLASS_SHADOW_ALPHA = 0.35f         // GI chống chìm: bóng đậm hơn để hình tách khỏi kính
        const val GLASS_SHADOW_BLUR = 0.035f
        const val GLASS_SHADOW_DY = 0.012f
        const val GLASS_RIM_WIDTH = 0.014f
        const val GLASS_RIM_BASE_ALPHA = 0.22f         // viền ở hai góc mờ
        const val GLASS_RIM_PEAK_ALPHA = 0.95f         // đỉnh sáng góc trên-trái
        const val GLASS_RIM_MINOR_ALPHA = 0.65f        // đỉnh phụ góc dưới-phải

        const val LUMA_R = 0.299f
        const val LUMA_G = 0.587f
        const val LUMA_B = 0.114f

        const val SAMPLE_PX = 64
        const val ALPHA_MIN = 16
        const val BORDER_OPAQUE_ALPHA = 240
        const val LUMA_ALPHA_MIN = 128
    }
}
