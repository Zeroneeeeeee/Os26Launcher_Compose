package gambi.global.os26launcher.domain.search

import gambi.global.os26launcher.domain.model.AppItem
import java.text.Normalizer
import java.util.Locale

/**
 * M8b — lọc và xếp hạng app theo truy vấn. Hàm thuần, không đụng android.*.
 *
 * Khớp không phân biệt hoa thường và dấu tiếng Việt (đ/Đ -> d). Hạng (nhỏ = lên trước):
 * 0 nhãn bắt đầu bằng truy vấn; 1 một từ của nhãn bắt đầu bằng truy vấn; 2 nhãn chứa truy vấn.
 * Cùng hạng giữ nguyên thứ tự đầu vào (sortedBy ổn định).
 */
object AppSearchMatcher {
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    private const val RANK_LABEL_PREFIX = 0
    private const val RANK_WORD_PREFIX = 1
    private const val RANK_CONTAINS = 2

    /**
     * Chuẩn hóa để so khớp: NFD, bỏ dấu tổ hợp, đ -> d, chữ thường, gộp khoảng trắng, trim.
     * B11: đường nhanh một lượt (không regex, không Normalizer); ký tự nào bảng không chắc thì cả chuỗi chạy bản gốc
     * [normalizeSlow], nên kết quả luôn giống hệt bản cũ.
     */
    fun normalize(text: String): String = normalizeFast(text) ?: normalizeSlow(text)

    /** Bản gốc (trước B11). Vừa là đường dự phòng vừa là chuẩn dựng bảng tra. */
    private fun normalizeSlow(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            // 'đ' không phân rã thành d + dấu nên NFD không xử lý được; thay tay SAU lowercase (Đ -> đ).
            .replace('đ', 'd')
            .replace(WHITESPACE, " ")
            .trim()

    // Bảng tra cho Latin-1 Supplement, Latin Extended-A/B (0x00C0..0x024F) và Latin Extended Additional (0x1E00..0x1EFF,
    // chứa chữ tiếng Việt). Dựng bằng chính bản gốc trên từng ký tự; chỉ nhận khi kết quả đúng một ký tự ASCII in được.
    private val TABLE = CharArray(0x1F00).also { t ->
        for (code in 0xC0 until t.size) {
            if (code > 0x24F && code < 0x1E00) continue
            val r = normalizeSlow(code.toChar().toString())
            if (r.length == 1 && r[0] in '\u0021'..'\u007E') t[code] = r[0]
        }
    }

    /** null = gặp ký tự ngoài vùng chắc chắn (điều khiển, nbsp, ký tự lạ, ô bảng không nhận): dùng bản gốc cho cả chuỗi. */
    private fun normalizeFast(text: String): String? {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c == ' ' -> if (sb.isNotEmpty() && sb[sb.length - 1] != ' ') sb.append(' ')
                c in 'A'..'Z' -> sb.append(c + 32)
                c in '\u0021'..'\u007E' -> sb.append(c)
                c in '\u0300'..'\u036F' -> Unit // dấu tổ hợp (chuỗi NFD): bỏ
                c.code < TABLE.size && TABLE[c.code] != '\u0000' -> sb.append(TABLE[c.code])
                else -> return null
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    fun search(apps: List<AppItem>, query: String): List<AppItem> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return apps.mapNotNull { app ->
            val rank = rankOf(normalize(app.label), q)
            if (rank == null) null else rank to app
        }.sortedBy { it.first }.map { it.second }
    }

    private fun rankOf(label: String, q: String): Int? = when {
        label.startsWith(q) -> RANK_LABEL_PREFIX
        label.contains(" $q") -> RANK_WORD_PREFIX // sau khoảng trắng = đầu một từ
        label.contains(q) -> RANK_CONTAINS
        else -> null
    }
}
