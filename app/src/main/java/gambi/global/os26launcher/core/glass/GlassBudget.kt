package gambi.global.os26launcher.core.glass

/**
 * Số kính THẬT ([GlassMode.REAL]) và số bề mặt kính tại chỗ ([GlassMode.STATIC_BLUR]) tối đa.
 *
 * Kính thật (GS): KHÔNG có trần số kính thật. Số kính thật cùng hiện và cùng được vẽ bị chặn bởi cấu trúc: tối đa 4 khi bật hết
 * (dock, pill, thanh Library, panel nhóm; panel/lớp phủ đang ẩn là GONE), và lựa chọn của người dùng (mức Tùy chỉnh) thắng mọi trần
 * (rơi về mờ im lặng sẽ làm Cài đặt nói một đằng, màn hình vẽ một nẻo). Số LENS=6 và CLASSIC=1 cũ lấy từ demo nền animate liên tục
 * (AGENTS.md "Hiệu năng đã đo"), chưa bao giờ được code nào gọi, đã xóa. Kết quả đo Oppo bật hết (GS Task 6): Oppo A3x, 2026-11-05, build 6f0edd1, 2 lượt mỗi ô, ALL (4 công tắc bật) so B0 (Tự động) cùng phiên: G2 vuốt Janky 1.33% so 1.38%, p95 15.08 so 15.02 ms, khung > 33 ms 2.0 so 2.0; G4 Library + panel nhóm (4 kính cùng hiện) Janky 4.42% so 4.55%, p95 24.95 so 25.12 ms, > 33 ms 33.0 so 32.5; G7 rung Janky 0.22% so 0.18%; G1 đứng yên 0 khung ở mọi cấu hình.
 *
 * Kính tại chỗ (GL Task 6, số đo Live 4 ngày 2026-11-03, tier CLASSIC_CPU, mức kính Tự động; xem plans/GL-glass-pass.md Task 5-6a):
 * - ô thư mục: 6 ô kính mỗi trang (12 ô hiển thị cùng lúc khi vuốt giữa hai trang) đạt D10 ở S2 (vuốt) và, sau Task 6a
 *   (mỗi ô rung chỉ còn một lệnh vẽ), ở S3i/S3s (rung); chưa đo số ô lớn hơn 6 mỗi trang;
 * - ô nhóm Library: 8 ô đạt D10 ở S4 (Janky +1.45 điểm, Δp95 +1.7 ms);
 * - LENS (Oppo) chưa có số đo nên chưa giới hạn; Task 5 phần Oppo sẽ chốt.
 * CLASSIC_GPU (API 31-32) dùng cùng số với CLASSIC_CPU (D5): không có máy đo riêng.
 */
object GlassBudget {
    private const val LITE_CELLS_PER_PAGE_CLASSIC = 6 // đã đo (Live 4)
    private const val LITE_CELLS_PER_PAGE_LENS = 24 // chưa đo trên Oppo: chưa giới hạn (cả trang)
    private const val LITE_TILES = 8 // đã đo (Live 4, S4)

    /** Số ô thư mục có kính tại chỗ tối đa trên MỘT trang Home (ô vượt rơi về FLAT theo thứ tự đọc). */
    fun maxLiteGlassCellsPerPage(tier: GlassTier, realGlassCapable: Boolean): Int = when {
        // 24 chỉ có nghĩa trên máy có đường Lens thật (nền đo là Oppo); `FORCE_HIGH` trên máy API < 33 dùng ngân sách CLASSIC.
        tier == GlassTier.LENS && realGlassCapable -> LITE_CELLS_PER_PAGE_LENS
        else -> LITE_CELLS_PER_PAGE_CLASSIC
    }

    /**
     * GI Task 4: số icon app có tấm kính mờ phía sau tối đa trên MỘT trang Home (và dock). Chưa đo: Task 5 đo trên Oppo.
     * LENS (máy có đường Lens thật) chưa giới hạn; CLASSIC là 0 vì ngân sách Live 4 chỉ đủ 6 ô kính tại chỗ mỗi trang
     * mà một trang có tới 24 icon (chỉ vài icon có tấm thì trang trông không đồng đều, nên icon chỉ dùng tấm nướng sẵn).
     * Ngân sách này tách riêng khỏi ô thư mục/widget ([maxLiteGlassCellsPerPage]).
     */
    fun maxIconPlatesPerPage(tier: GlassTier, realGlassCapable: Boolean): Int =
        if (tier == GlassTier.LENS && realGlassCapable) Int.MAX_VALUE else 0

    /** Số ô nhóm App Library có kính tại chỗ tối đa. */
    @Suppress("UNUSED_PARAMETER")
    fun maxLiteGlassTiles(tier: GlassTier): Int = LITE_TILES
}
