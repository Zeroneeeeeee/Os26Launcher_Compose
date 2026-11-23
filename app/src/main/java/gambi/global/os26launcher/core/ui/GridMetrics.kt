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
            val DEFAULT = IconSizing(iconScale = 1f, labelTextSp = 12.5f)

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
        val labelTextSp: Float = IconSizing.BASE_LABEL_SP,
        // 2026-11-23: khe cách đều [space] của lưới Home ([capToDock], xem công thức ở measure()) — khe trái màn → icon đầu = khe giữa hai
        // icon = khe icon cuối → phải màn. 0 ở lưới không [capToDock] (thư mục, Today…), nơi không áp cách đều này. HomeDock dùng lại giá trị
        // này (không tự tính gap riêng) để icon dock thẳng cột với icon lưới phía trên.
        val iconSpacingX: Int = 0
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
     *
     * [topPaddingOverridePx] (2026-11-23, chỉ lưới Home — [capToDock]): CHỐT lề trên (so với gốc [heightPx] này, không phải mép màn — gọi
     * nơi khác tự trừ phần container đã bị đẩy xuống) thay cho công thức mặc định; phần còn lại xuống tới hàng cuối được CHIA ĐỀU làm khe
     * dọc giữa các hàng ([gutterY]), luôn kẹp để không vượt [heightPx]. `null` (lưới khác: thư mục, Today…) giữ nguyên cách tính cũ.
     */
    fun measure(widthPx: Int, heightPx: Int, density: Float, topPaddingOverridePx: Int? = null): Cells? {
        if (widthPx <= 0 || heightPx <= 0 || columns <= 0 || rows <= 0) return null

        val roundUnitPx = dpToPx(ROUND_UNIT_DP, density)
        val paddingLeft = dpToPx(PADDING_DP, density)
        val paddingTop = paddingLeft
        val gutterX = dpToPx(GUTTER_DP, density)
        // 2026-11-23: khe DỌC dùng để TÍNH NGÂN SÁCH chiều cao ô (cellHeightRaw -> iconSize) giữ nguyên = gutterX, KHÔNG nhân ROW_GUTTER_SCALE
        // ở đây — nhân vào đây rồi cộng ngược phần "tiết kiệm" vào cellHeightRaw làm pitch hàng-tới-hàng chỉ đổi Δgutter/rows (gần như vô hình,
        // xem ghi chú ở renderGutterY bên dưới, cỡ icon theo đó cũng không còn phụ thuộc ROW_GUTTER_SCALE (giữ nguyên hành vi cũ).
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

        fun iconFor(labelHeightPx: Int, innerGapPx: Int): Int {
            val availableForIcon = cellHeightRaw - labelHeightPx - innerGapPx
            // 2026-11-23 (chuẩn iOS, công thức ĐẠI SỐ thay cho các trần rời rạc hay chặn chồng nhau): lưới Home (capToDock) đặt
            // screenWidth = padding×(n+1) + icon×n với padding = ICON_PADDING_RATIO × icon (CHỈNH TAY — so trực tiếp màn hình thật, không
            // chốt cứng 0.5 nữa) ⇒ icon = widthPx / (n + ICON_PADDING_RATIO×(n+1)). Khác cellWidthRaw (chia đều KHÔNG kể padding riêng) và
            // IOS_ICON_WIDTH_FRACTION cũ (chặn cứng theo % màn, hay kẹp cùng cellWidthRaw làm đổi iconScale không thấy hiệu quả) — công thức
            // này tự vừa khít theo chiều rộng, không cần chặn thêm; `space` (padding thật, Cells.iconSpacingX) vẫn được tính lại CHÍNH XÁC từ
            // iconSize cuối cùng ở dưới (sau khi đã kẹp theo chiều cao/scale), nên luôn đúng đẳng thức trên dù icon có bị kẹp nhỏ hơn.
            val widthTargetPx = if (capToDock)
                (widthPx / (columns + ICON_PADDING_RATIO * (columns + 1))).toInt()
            else
                maxIconPx.coerceAtMost(cellWidthRaw)
            val iconSizeRaw = (widthTargetPx
                .coerceAtMost(availableForIcon) * sizing.iconScale).toInt()
                .coerceAtMost(maxIconPx)
                .coerceAtMost(dockCapPx)
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
        var finalLabelHeightPx = 0
        val iconSize: Int
        if (!showLabels) {
            iconSize = iconFor(0, 0)
        } else {
            val full = iconFor(fullLabelHeightPx, fullInnerGapPx)
            val fullIconPx = dpToPx(LABEL_FULL_ICON_DP, density)
            if (full >= fullIconPx) {
                iconSize = full
                innerGapPx = fullInnerGapPx
                finalLabelHeightPx = fullLabelHeightPx
            } else {
                val scale = full.toFloat() / fullIconPx
                if (scale < LABEL_MIN_SCALE) {
                    effectiveShowLabels = false
                    iconSize = iconFor(0, 0)
                } else {
                    labelScale = scale
                    innerGapPx = (fullInnerGapPx * scale).toInt()
                    finalLabelHeightPx = (fullLabelHeightPx * scale).toInt()
                    iconSize = iconFor(finalLabelHeightPx, innerGapPx)
                }
            }
        }

        // 2026-11-23 (chủ dự án): lưới Home cách đều theo ICON: khe trái màn → icon đầu = khe giữa hai icon = khe icon cuối → phải màn = [space].
        // Tâm icon cột i = space + icon/2 + i × (icon + space). Giữ khe giữa hai ô là gutter (widget cạnh nhau vẫn có khe), nên
        // ô rộng icon + space − gutter và lề trái (space + gutter) / 2. Lưới khác (thư mục, Today riêng) giữ cách cũ.
        var cellWidth = roundDownToMultiple(cellWidthRaw, roundUnitPx)
        var cellGutterX = gutterX
        var cellPaddingLeft = paddingLeft
        var iconSpacingX = 0
        if (capToDock) {
            val space = (widthPx - columns * iconSize) / (columns + 1)
            if (space > 0) {
                cellGutterX = minOf(gutterX, space)
                cellWidth = iconSize + space - cellGutterX
                cellPaddingLeft = (space + cellGutterX) / 2
                iconSpacingX = space
            }
        }

        // 2026-11-24 (chủ dự án): cellHeightRaw (ngân sách CŨ, dựa lề/khe thô 8dp) thường DƯ ra nhiều hơn icon+nhãn thật — nhất là từ khi icon bị
        // CHIỀU RỘNG (công thức iOS) kẹp nhỏ hơn chiều cao cho phép. Ô (Constraints.fixed trong HomeGridPage) vẫn đặt icon+nhãn ở ĐỈNH ô nên
        // phần dư đó lộ ra như khoảng trắng THÊM giữa nhãn hàng trên và icon hàng dưới — khe dọc [renderGutterY] dù tính đúng vẫn bị "nuốt"
        // bởi phần dư này. Ô lưới Home (capToDock) giờ KHÍT SÁT icon+khe trong+nhãn thật (không còn khoảng trống thừa); lưới khác giữ cellHeightRaw cũ.
        val cellHeightFinal = if (capToDock) iconSize + innerGapPx + finalLabelHeightPx else roundDownToMultiple(cellHeightRaw, roundUnitPx)
        val renderPaddingTop: Int
        val renderGutterY: Int
        if (capToDock && topPaddingOverridePx != null) {
            // 2026-11-23 (chủ dự án, so trực tiếp với iOS): lề trên CHỐT theo [topPaddingOverridePx] (HomeScreen truyền = 1.7×statusBar đã trừ
            // phần container); phần còn lại chia đều cho (rows-1) khoảng, từ cuối lề trên tới ĐÁY hàng cuối, là mức TRẦN (gridMaxGutterY) —
            // cách tính giữ nguyên như cũ, chỉ đổi vai trò từ "khe thật" thành "khe tối đa lưới còn cho phép". Kẹp ≥ 0 để chắc không vượt
            // [heightPx] dù lề trên + tổng hàng có sát/vượt heightPx (icon/nhãn quá cao).
            val safeTop = topPaddingOverridePx.coerceIn(0, heightPx)
            val totalRowsHeight = rows * cellHeightFinal
            val availableForGutters = heightPx - safeTop - totalRowsHeight
            val gridMaxGutterY = if (rows > 1) (availableForGutters / (rows - 1)).coerceAtLeast(0) else 0
            // 2026-11-24 (chủ dự án): khe DỌC thật = 0.7 × khe NGANG (iconSpacingX, khe giữa 2 icon theo hàng ngang) — không vượt trần
            // [gridMaxGutterY] ở trên.
            val desiredGutterY = (0.7f * iconSpacingX).toInt()
            renderGutterY = minOf(desiredGutterY, gridMaxGutterY).coerceAtLeast(0)
            renderPaddingTop = minOf(safeTop, maxOf(0, heightPx - totalRowsHeight))
        } else {
            // 2026-11-23: lề TRÊN cố định bằng lề 2 BÊN đã tính cuối (cellPaddingLeft — ở lưới capToDock là (space+gutter)/2 theo X, không còn
            // là PADDING_DP thô như trước), không liên quan gì tới cellHeightRaw/iconSize (đã tính xong ở trên, dùng paddingTop thô).
            renderPaddingTop = cellPaddingLeft
            // 2026-11-23 (chủ dự án "giảm khoảng cách giữa các hàng icon", tính lại không pha loãng): khe DỌC THỰC VẼ RA nhỏ hơn khe dùng tính
            // ngân sách ở trên — không cộng ngược phần "tiết kiệm" vào đâu cả (không dồn lên/dưới, không co giãn cellHeight) — nên pitch (khoảng
            // hàng-tới-hàng) co lại ĐÚNG (gutterY − renderGutterY), không bị chia lại cho [rows] nữa.
            renderGutterY = (gutterY * ROW_GUTTER_SCALE).toInt()
        }

        return Cells(
            cellWidth = cellWidth,
            cellHeight = cellHeightFinal,
            iconSize = iconSize,
            gutterX = cellGutterX,
            gutterY = renderGutterY,
            paddingLeft = cellPaddingLeft,
            paddingTop = renderPaddingTop,
            columns = columns,
            rows = rows,
            showLabels = effectiveShowLabels,
            labelScale = labelScale,
            innerGapPx = innerGapPx,
            labelTextSp = sizing.labelTextSp,
            iconSpacingX = iconSpacingX
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

        /**
         * CHỈNH TAY (2026-11-23, chủ dự án so trực tiếp với iOS thật: "<0.5"): padding / icon của lưới Home ([capToDock]) — padding ở đây
         * vừa là khe giữa 2 icon vừa là lề 2 bên màn (model đồng nhất). 0.5 (suy từ icon 60pt/màn 393pt) ra to hơn ngoài thực tế; 0.4 là điểm
         * khởi đầu, chỉnh lại theo mắt cho khớp ảnh chụp iOS thật.
         */
        internal const val ICON_PADDING_RATIO = 0.43f

        /** CHỈNH TAY (2026-11-23, chủ dự án "giảm khoảng cách giữa các hàng icon"): khe dọc (gutterY) / GUTTER_DP. */
        internal const val ROW_GUTTER_SCALE = 0.5f

        // Polish 2026-11-19: hằng ICON_SCALE (0.9, TL2) chuyển thành IconSizing.iconScale.

        /** CHỈNH TAY (polish 2026-11-19): trần cạnh icon / chiều cao dock. */
        internal const val DOCK_ICON_MAX_FRACTION = 0.8f // 2026-11-19: 0.9 -> 0.8 (chủ dự án)

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
