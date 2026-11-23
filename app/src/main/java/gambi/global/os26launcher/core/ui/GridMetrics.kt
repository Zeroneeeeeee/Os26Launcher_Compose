package gambi.global.os26launcher.core.ui

import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.GridPreset

/**
 * Tính kích thước ô lưới từ kích thước view THẬT (đã layout), không từ displayMetrics, để không lệ
 * thuộc padding hệ thống hay thay đổi của layout cha. Hằng số thuật toán của lớp này (padding, gutter,
 * icon min/max, chiều cao nhãn) được MIỄN TRỪ khỏi dimens.xml theo quy ước dự án (project_structure.md
 * mục 7: "Mọi số đo vào dimens.xml... trừ các hằng của GridMetrics"). `internal` để IconCellView dùng
 * lại đúng INNER_GAP_DP khi vẽ nhãn, tránh một con số 4dp lặp lại ở hai nơi.
 */
data class GridMetrics(
    val columns: Int,
    val rows: Int,
    val showLabels: Boolean = true,
    /** Polish 2026-11-19: cỡ icon + cỡ chữ nhãn gom một chỗ, để sau này đưa vào Cài đặt (truyền qua [from]). */
    val sizing: IconSizing = IconSizing.DEFAULT,
    /**
     * FS (2026-11-20): chỉ lưới của pager Home bật (qua [from]). Trần dock suy chiều cao dock từ [measure] heightPx = chiều cao pager;
     * lưới khác (panel thư mục, recents, Today) có heightPx của riêng nó nên trần tính sai và co icon (thư mục ~36dp trên màn 360dp).
     */
    val capToDock: Boolean = false
) {

    /**
     * Polish 2026-11-19 (chủ dự án: icon to ra, chữ nhãn nhỏ lại, chỉnh được ở MỘT biến và sau này ở Cài đặt).
     * [iconScale]: cạnh icon / bề ngang ô (trước là hằng `ICON_SCALE` 0,9). [labelTextSp]: cỡ chữ nhãn ở cỡ đầy đủ (trước là dimen
     * `icon_label_text_size` 10sp); chiều cao chừa cho nhãn co theo cùng tỉ lệ nên chữ nhỏ thì icon được thêm chỗ theo chiều cao.
     */
    data class IconSizing(val iconScale: Float, val labelTextSp: Float) {
        companion object {
            /** CHỈNH MẮT Ở ĐÂY (mặc định toàn app cho tới khi có cài đặt). */
            // 2026-11-21 (chủ dự án "tăng cỡ chữ"): nhãn 9 -> 11.5 sp, ~12 pt của iOS so với bề ngang màn.
            val DEFAULT = IconSizing(iconScale = 0.96f, labelTextSp = 11.5f)

            /** Cỡ chữ nhãn mà các hằng chiều cao nhãn ([LABEL_HEIGHT_DP]) được đo theo. */
            const val BASE_LABEL_SP = 10f
        }
    }

    data class Cells(
        val cellWidth: Int,
        val cellHeight: Int,
        val iconSize: Int,
        val gutterX: Int,
        val gutterY: Int,
        val paddingLeft: Int,
        val paddingTop: Int,
        // M7 Task 2: thêm để cellAt() tự quy đổi ngược mà không cần GridMetrics gốc. Luôn bằng
        // columns/rows của GridMetrics đã tạo ra Cells này (xem measure()).
        val columns: Int,
        val rows: Int,
        // M8a Task 2: nằm trong Cells để so sánh theo giá trị (data class) làm IconGridView.bindAllCells / DockView.setItems
        // bind lại khi bật/tắt nhãn (bài học M5 Task 2: view con giữ dữ liệu suy ra từ kích thước phải bind lại khi Cells đổi).
        // GL Task 11: [showLabels] là giá trị HIỆU LỰC (người dùng bật nhãn VÀ icon đủ lớn để còn nhãn).
        val showLabels: Boolean,
        // GL Task 11: hệ số co của nhãn (1 = cỡ đầy đủ như trước); chữ, bóng chữ và [innerGapPx] co theo.
        val labelScale: Float = 1f,
        // GL Task 11: khoảng cách icon -> nhãn đã nhân [labelScale] (0 khi không nhãn); cùng công thức cũ khi labelScale = 1.
        val innerGapPx: Int = 0,
        // Polish 2026-11-19: cỡ chữ nhãn đầy đủ (sp) theo [IconSizing.labelTextSp]; IconCellView nhân thêm [labelScale].
        val labelTextSp: Float = IconSizing.BASE_LABEL_SP
    ) {
        /**
         * M7 Task 2 — hit-test ngược: toạ độ pixel ([px], [py]) trong hệ toạ độ của chính view đã
         * `measure()` ra `Cells` này (ví dụ `IconGridView`/`DockView`, KHÔNG phải toạ độ màn hình) →
         * ô lưới đang ở dưới điểm đó, hoặc `null` nếu điểm nằm ngoài vùng lưới (padding hoặc quá biên
         * cột/hàng cuối). Toàn bộ "pitch" (ô + gutter theo sau) được gộp vào ô đó — không có vùng chết
         * ở gutter — vì hàm này chạy liên tục trong lúc kéo tay (Task 5+), một vùng chết sẽ làm preview
         * đẩy-dồn chớp tắt mỗi khi ngón tay đi ngang khe hở giữa 2 icon.
         */
        fun cellAt(px: Float, py: Float): GridPosition? {
            val pitchX = cellWidth + gutterX
            val pitchY = cellHeight + gutterY
            if (pitchX <= 0 || pitchY <= 0) return null

            val relX = px - paddingLeft
            val relY = py - paddingTop
            if (relX < 0f || relY < 0f) return null

            val gridX = (relX / pitchX).toInt()
            val gridY = (relY / pitchY).toInt()
            if (gridX >= columns || gridY >= rows) return null

            return GridPosition(gridX, gridY)
        }
    }

    /**
     * [widthPx]/[heightPx] là kích thước view thật, [density] là `resources.displayMetrics.density`.
     * Trả null khi view chưa layout (<=0), cấu hình lưới vô nghĩa (`columns`/`rows` <= 0), hoặc ô tính
     * ra nhỏ hơn 48dp — không bao giờ chia cho 0, không crash khi view quá nhỏ (Review Focus 4 của M3).
     */
    fun measure(widthPx: Int, heightPx: Int, density: Float): Cells? {
        if (widthPx <= 0 || heightPx <= 0 || columns <= 0 || rows <= 0) return null

        val roundUnitPx = dpToPx(ROUND_UNIT_DP, density)
        val paddingLeft = dpToPx(PADDING_DP, density)
        val paddingTop = paddingLeft
        val gutterX = dpToPx(GUTTER_DP, density)
        val gutterY = gutterX

        val cellWidthRaw = (widthPx - 2 * paddingLeft - (columns - 1) * gutterX) / columns
        val cellHeightRaw = (heightPx - 2 * paddingTop - (rows - 1) * gutterY) / rows

        val minIconPx = dpToPx(ICON_MIN_DP, density)
        if (cellWidthRaw < minIconPx) return null

        val maxIconPx = dpToPx(ICON_MAX_DP, density)
        // M8a Task 2: tắt nhãn thì không chừa chỗ cho nhãn và khoảng cách trong (ô giữ nguyên kích thước, icon vẫn
        // bị chặn trên bởi ICON_MAX_DP).
        val labelTextRatio = sizing.labelTextSp / IconSizing.BASE_LABEL_SP
        val fullLabelHeightPx = (LABEL_HEIGHT_DP * labelTextRatio * density).toInt()
        val fullInnerGapPx = dpToPx(INNER_GAP_DP, density)

        // M5 (addendum sau Task 2, 2026-10-27): BỎ sàn ICON_MIN_DP (48dp, "quyết định đã chốt" cũ ở
        // M3) khỏi kích thước icon theo yêu cầu người dùng - split-screen/gập màn hình co chiều cao
        // cell xuống dưới 48dp+label thì icon phải co tỉ lệ theo, không được giữ cứng 48dp (gây icon
        // tràn/che hàng dưới, xem 2 ảnh test split-screen 2026-10-27). minIconPx ở guard phía trên
        // (cellWidthRaw) KHÔNG đổi - đó là ngưỡng "quá hẹp thì ẩn cả lưới" theo chiều rộng, tách biệt
        // với kích thước icon theo chiều cao ở đây. ICON_SAFETY_MIN_DP chỉ là sàn kỹ thuật chống
        // icon=0px (crash Bitmap.createBitmap/createScaledBitmap), không mang ý nghĩa accessibility.
        val safetyMinIconPx = dpToPx(ICON_SAFETY_MIN_DP, density)

        // Icon to bằng chiều rộng ô (= chiều rộng nhãn), bị chặn bởi chiều cao còn lại (sau khi trừ nhãn và khe); ICON_MAX_DP
        // chỉ còn là trần an toàn. Gom thành hàm để Task 11 tính lại với nhãn đã co hoặc không nhãn.
        // Polish 2026-11-19 (chủ dự án): icon không cao quá DOCK_ICON_MAX_FRACTION chiều cao dock. Dock không có trong phép đo này nhưng
        // chiều cao của nó suy được từ chính [heightPx] của pager: activity_home.xml chia phần còn lại (sau pill 48dp và lề dưới của dock,
        // đều không có weight) theo pager : dock = PAGER_WEIGHT : 1, nên dock = heightPx / PAGER_WEIGHT. Đổi weight trong layout thì đổi hằng theo.
        val dockHeightPx = heightPx / PAGER_WEIGHT
        val dockCapPx = if (capToDock && dockHeightPx > 0) (dockHeightPx * DOCK_ICON_MAX_FRACTION).toInt() else Int.MAX_VALUE
        // Như iOS (icon 60 pt trên màn rộng 393 pt ≈ 15 %): lưới Home không cho icon vượt [IOS_ICON_WIDTH_FRACTION] bề ngang lưới.
        // Chỉ lưới pager Home ([capToDock]); panel thư mục/Today có bề ngang riêng.
        val iosCapPx = if (capToDock) (widthPx * IOS_ICON_WIDTH_FRACTION).toInt() else Int.MAX_VALUE

        fun iconFor(labelHeightPx: Int, innerGapPx: Int): Int {
            val availableForIcon = cellHeightRaw - labelHeightPx - innerGapPx
            val iconSizeRaw = (maxIconPx.coerceAtMost(cellWidthRaw)
                .coerceAtMost(availableForIcon) * sizing.iconScale).toInt()
                .coerceAtMost(dockCapPx)
                .coerceAtMost(iosCapPx)
                .coerceAtLeast(safetyMinIconPx)
            return roundDownToMultiple(iconSizeRaw, roundUnitPx)
        }

        // GL Task 11: nhãn co theo icon, ẩn khi quá nhỏ (chủ dự án, bài kiểm TL1 2026-11-03).
        // - icon (tính với nhãn đầy đủ) >= LABEL_FULL_ICON_DP: y hệt trước đây, labelScale = 1;
        // - nhỏ hơn: labelScale = icon / LABEL_FULL_ICON_DP; nếu < LABEL_MIN_SCALE (chữ dưới ~8sp) thì ẩn nhãn và để icon
        //   lấy chỗ nhãn, ngược lại tính lại icon với nhãn và khe đã co (một lượt, không lặp).
        var effectiveShowLabels = showLabels
        var labelScale = 1f
        var innerGapPx = 0
        val iconSize: Int
        if (!showLabels) {
            iconSize = iconFor(0, 0)
        } else {
            val full = iconFor(fullLabelHeightPx, fullInnerGapPx)
            val fullIconPx = dpToPx(LABEL_FULL_ICON_DP, density)
            if (full >= fullIconPx) {
                iconSize = full
                innerGapPx = fullInnerGapPx
            } else {
                val scale = full.toFloat() / fullIconPx
                if (scale < LABEL_MIN_SCALE) {
                    effectiveShowLabels = false
                    iconSize = iconFor(0, 0)
                } else {
                    labelScale = scale
                    innerGapPx = (fullInnerGapPx * scale).toInt()
                    iconSize = iconFor((fullLabelHeightPx * scale).toInt(), innerGapPx)
                }
            }
        }

        // 2026-11-23 (chủ dự án): lưới Home cách đều theo ICON: khe trái màn → icon đầu = khe giữa hai icon = khe icon cuối → phải màn = [space].
        // Tâm icon cột i = space + icon/2 + i × (icon + space). Giữ khe giữa hai ô là gutter (widget cạnh nhau vẫn có khe), nên
        // ô rộng icon + space − gutter và lề trái (space + gutter) / 2. Lưới khác (thư mục, Today riêng) giữ cách cũ.
        var cellWidth = roundDownToMultiple(cellWidthRaw, roundUnitPx)
        var cellGutterX = gutterX
        var cellPaddingLeft = paddingLeft
        if (capToDock) {
            val space = (widthPx - columns * iconSize) / (columns + 1)
            if (space > 0) {
                cellGutterX = minOf(gutterX, space)
                cellWidth = iconSize + space - cellGutterX
                cellPaddingLeft = (space + cellGutterX) / 2
            }
        }

        return Cells(
            cellWidth = cellWidth,
            cellHeight = roundDownToMultiple(cellHeightRaw, roundUnitPx),
            iconSize = iconSize,
            gutterX = cellGutterX,
            gutterY = gutterY,
            paddingLeft = cellPaddingLeft,
            paddingTop = paddingTop,
            columns = columns,
            rows = rows,
            showLabels = effectiveShowLabels,
            labelScale = labelScale,
            innerGapPx = innerGapPx,
            labelTextSp = sizing.labelTextSp
        )
    }

    private fun dpToPx(dp: Int, density: Float): Int = (dp * density).toInt()

    private fun roundDownToMultiple(value: Int, unitPx: Int): Int {
        if (unitPx <= 0) return value
        return (value / unitPx) * unitPx
    }

    companion object {
        const val DOCK_SLOTS = 4

        /** M8a Task 2: cỡ lưới đến từ cài đặt (GridPreset), không còn hằng 4×6 cứng. */
        fun from(preset: GridPreset, showLabels: Boolean = true, sizing: IconSizing = IconSizing.DEFAULT) =
            GridMetrics(preset.columns, preset.rows, showLabels, sizing, capToDock = true)

        // Hằng số thuật toán, miễn trừ khỏi dimens.xml (project_structure.md mục 7).
        internal const val PADDING_DP = 8
        internal const val GUTTER_DP = 8
        internal const val ICON_MIN_DP = 48

        // Polish 2026-11-19: hằng ICON_SCALE (0.9, TL2) chuyển thành IconSizing.iconScale.

        /** CHỈNH TAY (polish 2026-11-19): trần cạnh icon / chiều cao dock. */
        internal const val DOCK_ICON_MAX_FRACTION = 0.8f // 2026-11-19: 0.9 -> 0.8 (chủ dự án)

        /** CHỈNH TAY (2026-11-21, chủ dự án "icon to quá"): trần cạnh icon / bề ngang lưới Home, theo tỉ lệ iOS (60/393). */
        internal const val IOS_ICON_WIDTH_FRACTION = 0.153f

        /** = layout_weight của `home_pager` trong activity_home.xml (dock weight 1). */
        internal const val PAGER_WEIGHT = 6
        internal const val ICON_MAX_DP = 96

        // M5 addendum: sàn kỹ thuật (không phải accessibility) để icon không bao giờ co về 0px sau
        // roundDownToMultiple(ROUND_UNIT_DP=4). Xem ghi chú tại nơi dùng trong measure().
        internal const val ICON_SAFETY_MIN_DP = 8
        internal const val LABEL_HEIGHT_DP = 20 // nhãn 1 dòng (12sp + đệm cho font scale lớn)
        internal const val INNER_GAP_DP = 4

        /** GL Task 11 (điểm xuất phát, chỉnh theo mắt): icon từ cỡ này trở lên giữ nhãn đầy đủ (Live 4 bình thường: icon 48dp). */
        internal const val LABEL_FULL_ICON_DP = 44

        /** GL Task 11: nhãn co dưới hệ số này (chữ 10sp * 0.8 = 8sp) thì ẩn hẳn. */
        internal const val LABEL_MIN_SCALE = 0.8f
        private const val ROUND_UNIT_DP = 4
    }
}
