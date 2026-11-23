package gambi.global.os26launcher.ui.home.compose

import gambi.global.os26launcher.core.ui.SpringProgress

/**
 * AP — MỌI hằng chỉnh tay của hai chuyển động App Library nằm ở đây (chủ dự án chỉnh sau khi xem trên máy). Tất cả là điểm xuất phát
 * chưa đo cảm giác. Tiến độ luôn là một số 0..1: `p` của panel (0 = đóng, 1 = mở) hoặc `p` của vuốt Library (0 = Home, 1 = Library).
 *
 * "Dải mờ" (start, end): alpha đi tuyến tính từ 0 tới 1 khi `p` đi từ start tới end (hàm [ramp]); mờ dần thì đảo lại bằng `1 - ramp`.
 */
internal object AppLibraryMotion {
    // ---- 1. Mở/đóng panel nhóm từ App Library (vòng 2, video iOS 27: panel phình ra TỪ Ô NHÓM được bấm và co về ô đó) ----

    /** P2-3 (2026-11-19): màn nhóm mở/đóng tại GIỮA màn hình (không còn mọc từ ô nhóm), phóng từ cỡ này (phần của cỡ thật). Hằng chỉnh tay. */
    const val GROUP_PANEL_CENTER_START_SCALE = 0.6f

    /** Panel nhóm hiện dần (alpha 0 -> 1) trong dải `p` này: nhanh ở đầu, vì lúc đầu panel còn rất nhỏ nằm trên ô (video #42-#44). */
    const val GROUP_PANEL_FADE_END = 0.3f

    /** App Library phía sau thu về cỡ này khi panel mở hẳn (video: ~0.93 ở #42, ~0.81 ở #44 khi đã mờ; không giữ nguyên cỡ). Pivot = tâm màn hình. */
    const val LIBRARY_BEHIND_END_SCALE = 0.85f

    /** Dải `p` của panel mà App Library phía sau mờ dần (alpha 1 -> 0); hết hẳn ở #46-#47 (~0.7). */
    const val LIBRARY_BEHIND_FADE_START = 0f
    const val LIBRARY_BEHIND_FADE_END = 0.7f

    /**
     * Nhoè Library phía sau (chỉ API 31+, `RenderEffect`): bán kính (dp) khi panel mở hẳn; 0 = tắt. GF: tắt (0) vì nền màn nhóm đã là ảnh nền mờ (xem
     * dưới) phủ kín khi mở hẳn, nhoè Library chỉ tốn thêm một lớp offscreen mỗi khung trên API 31+ mà không thấy được; API < 31 vốn không có. Bật lại: 16f.
     */
    const val LIBRARY_BEHIND_BLUR_DP = 0f

    /**
     * GF: nền màn nhóm = ảnh nền đã làm mờ (cùng bản mờ của kính), mọi máy mọi API, hiện dần theo `p` của panel; phủ thêm lớp tối nhẹ (alpha 0..1,
     * màu đen) để chữ trắng đọc được. Chế độ sáng nhẹ hơn chế độ tối (iOS: nền nhóm sáng hơn khi giao diện sáng).
     */
    const val GROUP_BG_TINT_ALPHA_DARK = 0.40f
    const val GROUP_BG_TINT_ALPHA_LIGHT = 0.25f

    /** Màu nền phẳng khi chưa có ảnh nền (ARGB): xám đậm gần iOS, đủ cho chữ trắng. */
    const val GROUP_BG_FLAT_DARK = 0xFF1C1C1E.toInt()
    const val GROUP_BG_FLAT_LIGHT = 0xFF3A3A3C.toInt()

    /** Bước lượng tử hoá bán kính nhoè (px) để không tạo `RenderEffect` mới mỗi khung khi bán kính chỉ đổi cực nhỏ. */
    const val BLUR_STEP_PX = 1f

    /**
     * Spring của panel nhóm: video mở ~0.27 s có lố nhẹ rồi yên (#49). Với khối lượng 1, thời gian yên ~ 4/(giảm chấn * sqrt(độ cứng)) = ~0.23 s,
     * lố ~1%. Tắt hoạt ảnh hệ thống thì SpringProgress tự snap.
     */
    val GROUP_PANEL_SPRING = SpringProgress.Spec(stiffness = 450f, dampingRatio = 0.82f)

    // ---- 2. Vuốt giữa trang Home cuối và App Library (theo ngón tay, `p` = LibraryOverlayController.progress) ----

    /** Trang Home trượt sang trái bao nhiêu bề rộng màn hình khi `p` = 1 (1 = như một trang pager). Cả lớp Home (trang, pill, dock) trượt cùng nhau. */
    const val HOME_SLIDE_FRACTION = 1f

    /**
     * Tech lead (video, 2026-11-10): vuốt trang cuối ↔ Library là trượt cả hai như lật trang, KHÔNG mờ dần. false = Home và Library giữ
     * alpha 1 khi vuốt (chỉ nền tối `library_scrim` vẫn hiện dần). true = dùng hai dải mờ bên dưới (bản AP đầu).
     */
    const val SWIPE_FADES = false

    /**
     * D2: spring kết thúc cử chỉ vuốt (thả tay) của `LibraryOverlayController`; nhận vận tốc thả tay nên chuyển động liền với ngón tay. Tới hạn (không nảy)
     * vì `p` kẹp 0..1; độ cứng 600 ≈ yên sau ~0,25 s. Điểm xuất phát chưa đo cảm giác (cùng loại hằng AP).
     */
    val SWIPE_SETTLE_SPRING = SpringProgress.Spec(stiffness = 600f, dampingRatio = 1f)

    /** Dải `p` mà Home (trang, chấm trang, dock) mờ dần. */
    const val HOME_FADE_START = 0f
    const val HOME_FADE_END = 0.85f

    /** Dải `p` mà App Library hiện dần khi trượt vào. */
    const val LIBRARY_FADE_START = 0.15f
    const val LIBRARY_FADE_END = 1f

    // ---- 3. Library chạy dưới thanh điều hướng (Task 4) ----

    /** Dải mờ dần ở đáy cao thêm chừng này (dp) NGOÀI inset thanh điều hướng, để chữ ô cuối tối đi từ từ chứ không cắt cụt. 0 = chỉ đúng chiều cao thanh. */
    const val NAV_FADE_EXTRA_DP = 24f

    /** Độ đậm (alpha, 0..1) của màu đen ở mép đáy màn hình; 0 = tắt dải mờ. Màu đen cùng họ với `library_scrim`. */
    const val NAV_FADE_MAX_ALPHA = 0.6f

    /** 0 khi [p] <= [start], 1 khi [p] >= [end], tuyến tính ở giữa. */
    fun ramp(p: Float, start: Float, end: Float): Float =
        if (end <= start) (if (p >= end) 1f else 0f) else ((p - start) / (end - start)).coerceIn(
            0f,
            1f
        )
}
