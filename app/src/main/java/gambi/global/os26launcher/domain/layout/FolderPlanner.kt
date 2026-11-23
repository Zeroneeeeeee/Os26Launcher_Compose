package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.model.ContainerId

/**
 * G2a A1a — quy tắc dựng/giải tán thư mục, hàm THUẦN (không `android.*`, không DB). Dùng chung cho đường ghi thật
 * (`LayoutReconciler`) và cập nhật lạc quan ở UI (A1b), cùng nguyên tắc với [GridInsertPlanner].
 */
object FolderPlanner {

    /** Số app trên một trang của panel thư mục (lưới 3x3, D5). Icon thư mục ở lưới/dock cũng chỉ vẽ chừng này app đầu. */
    const val PAGE_SIZE = 9

    /** Số trang tối đa của một thư mục (FM Q1, như iOS 7+: 15 trang). */
    const val MAX_PAGES = 15

    /** Số app tối đa trong một thư mục: [MAX_PAGES] x [PAGE_SIZE] = 135 (FM Q1). */
    const val MAX_ITEMS = MAX_PAGES * PAGE_SIZE

    /** Số trang của thư mục có [appCount] app (tối thiểu 1). */
    fun pageCount(appCount: Int): Int = ((appCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceIn(1, MAX_PAGES)

    /** Số ký tự tối đa của tên thư mục (D7). */
    const val MAX_TITLE_LENGTH = 30

    /** Chuẩn hóa tên người dùng nhập: bỏ khoảng trắng hai đầu, cắt tối đa [MAX_TITLE_LENGTH]. Rỗng = chưa đặt tên. */
    fun normalizeTitle(raw: String): String = raw.trim().take(MAX_TITLE_LENGTH)

    /**
     * AN Task 9b (chủ dự án đổi D4/D5 của G2a, 2026-11-04) — "thấy gì thì được nấy": ngón tay (điểm kéo) ở VÙNG GIỮA của icon đích thì
     * hiện kính/phóng thư mục và thả = tạo thư mục / thêm vào thư mục; ở ngoài vùng giữa thì không kính, không phóng, thả = chèn
     * đẩy-dồn như M7. Không còn đường "thả nhanh ở mép vẫn gộp" và không còn giữ 500 ms ở mép (DWELL_MS đã bỏ).
     *
     * Vùng giữa tính theo ICON (hình vuông cạnh iconSize, tâm là tâm icon), không theo cả ô có nhãn. Cũ (G2a): rộng 0.5 bề rộng ô x cao
     * 0.5 cạnh icon, tức 45 x 26 dp trên Live 4 (ô ~90 dp, icon 52 dp) = 0.5 x 0.29 ô theo bề rộng/chiều cao ô. Mới: vào khi nằm trong
     * hình vuông cạnh [CENTER_ENTER_FRACTION] x iconSize (0.9 icon ~ 47 x 47 dp = 0.52 bề rộng ô x 0.6 chiều cao ô), ra khi vượt hình vuông cạnh
     * [CENTER_EXIT_FRACTION] x iconSize (1.1 icon ~ 57 dp): khoảng trễ để kính không nhấp nháy ở biên. Cả hai là HẰNG CHỈNH TAY.
     */
    const val CENTER_ENTER_FRACTION = 0.9f
    const val CENTER_EXIT_FRACTION = 1.1f

    /**
     * ([dx], [dy]) (px, gốc ở TÂM icon đích) có nằm trong vùng giữa không. [wasInside] = lần trước đã ở trong vùng giữa của CHÍNH đích này
     * (kính đang hiện hoặc đang chờ hiện): dùng ngưỡng rộng [CENTER_EXIT_FRACTION] để ra, ngược lại ngưỡng hẹp [CENTER_ENTER_FRACTION] để vào.
     */
    fun isInCenterZone(dx: Float, dy: Float, iconSize: Int, wasInside: Boolean): Boolean {
        val half = iconSize * (if (wasInside) CENTER_EXIT_FRACTION else CENTER_ENTER_FRACTION) / 2f
        return kotlin.math.abs(dx) <= half && kotlin.math.abs(dy) <= half
    }

    /** Thông tin tối thiểu về một item để quyết định có gộp được không. [container] cùng nghĩa với `ContainerId`. */
    data class Participant(
        val container: Long,
        val isApp: Boolean,
        val spanX: Int = 1,
        val spanY: Int = 1
    ) {
        val isSingleCell: Boolean get() = spanX == 1 && spanY == 1
    }

    /**
     * Có tạo được thư mục mới từ [dragged] (app đang kéo) và [target] (app bị đè lên) không.
     * Đích phải là app 1x1 trên lưới chính hoặc trong dock (P2-6: thư mục tạo tại chỗ trong dock). Nguồn là app 1x1 ở lưới chính, dock
     * (dock căn giữa lại) hoặc là con của một thư mục (`container > 0`, G2a A3b: thư mục cũ được giải tán theo [dissolve]).
     */
    fun canCreate(dragged: Participant, target: Participant): Boolean =
        dragged.isApp && target.isApp &&
                dragged.isSingleCell && target.isSingleCell &&
                (dragged.container == ContainerId.HOME || dragged.container == ContainerId.DOCK || dragged.container > 0) &&
                (target.container == ContainerId.HOME || target.container == ContainerId.DOCK)

    /**
     * Có thêm được [dragged] vào thư mục đang có [childCount] app không (D5): app 1x1 từ lưới chính, dock hoặc từ một thư mục
     * khác (`container > 0`, nơi gọi tự loại trường hợp cùng thư mục), thư mục chưa đầy ([MAX_ITEMS]). Thư mục không thêm được vào thư mục.
     */
    fun canAdd(dragged: Participant, childCount: Int): Boolean =
        dragged.isApp && dragged.isSingleCell &&
                (dragged.container == ContainerId.HOME || dragged.container == ContainerId.DOCK || dragged.container > 0) &&
                childCount < MAX_ITEMS

    enum class Dissolve {
        /** Còn từ 2 app trở lên: giữ thư mục. */
        KEEP,

        /** Còn đúng 1 app: app đó về đúng ô của thư mục, thư mục bị xóa (D9). */
        REPLACE_WITH_SINGLE,

        /** Hết app: xóa thư mục (D9). */
        DELETE
    }

    /** Quyết định số phận thư mục sau khi một thao tác làm giảm số con. Dùng từ A3. */
    fun dissolve(remainingChildren: Int): Dissolve = when {
        remainingChildren <= 0 -> Dissolve.DELETE
        remainingChildren == 1 -> Dissolve.REPLACE_WITH_SINGLE
        else -> Dissolve.KEEP
    }
}
