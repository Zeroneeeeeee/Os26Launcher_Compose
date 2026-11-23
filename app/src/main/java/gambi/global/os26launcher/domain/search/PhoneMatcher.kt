package gambi.global.os26launcher.domain.search

/**
 * CP — chuẩn hóa và so khớp số điện thoại cho tìm danh bạ. Hàm thuần, không `android.*`.
 *
 * Số được quy về chuỗi CHỈ CHỮ SỐ ("dạng chuẩn"): bỏ khoảng trắng, `-`, `.`, ngoặc và mọi ký tự khác; tiền tố `+84` hoặc `0084`
 * (Việt Nam) thành `0`, và số 0 trung gian ngay sau mã nước (`+84 (0) 912…`) bỏ một lần vì số quốc gia VN không bắt đầu bằng 0.
 * Số nước ngoài (`+1…`) giữ nguyên chữ số, không thêm `0`.
 */
object PhoneMatcher {
    /** Kim tìm ngắn hơn số ký tự này (sau khi quy đổi `+84` -> `0`) thì không khớp số (quyết định D7 của plan CP). */
    const val MIN_NEEDLE_LENGTH = 3

    /** Số điện thoại thô (như provider trả) -> dạng chuẩn chỉ-chữ-số. Một lượt duyệt, không regex. */
    fun canonical(raw: String): String {
        var plus = false
        var seenNonSpace = false
        val digits = StringBuilder(raw.length)
        for (c in raw) {
            if (c in '0'..'9') {
                digits.append(c)
                seenNonSpace = true
            } else if (c == '+' && !seenNonSpace) {
                plus = true
                seenNonSpace = true
            } else if (c != ' ' && c != '\t') {
                seenNonSpace = true // ký tự khác (-, ., ngoặc, ...) bỏ; dấu + ở giữa cũng bỏ
            }
        }
        return toDomestic(digits, plus)
    }

    private fun toDomestic(d: CharSequence, plus: Boolean): String {
        val s = d.toString()
        val rest = when {
            plus && s.startsWith("84") -> s.substring(2)
            s.startsWith("0084") -> s.substring(4)
            else -> return s
        }
        return "0" + if (rest.startsWith("0")) rest.substring(1) else rest
    }

    /**
     * Kim tìm từ truy vấn thô. null = không phải truy vấn số (có chữ cái hoặc ký tự lạ) hoặc quá ngắn. Hai kim khi truy vấn bắt
     * đầu bằng `84` không có `+`/`0084`: có thể là một đoạn của số, hoặc gõ tắt `+84`.
     */
    fun needles(rawQuery: String): List<String>? {
        var hasDigit = false
        for (c in rawQuery) {
            when (c) {
                in '0'..'9' -> hasDigit = true
                ' ', '+', '-', '.', '(', ')' -> Unit
                else -> return null
            }
        }
        if (!hasDigit) return null
        val primary = canonical(rawQuery)
        val out = ArrayList<String>(2)
        if (primary.length >= MIN_NEEDLE_LENGTH) out.add(primary)
        if (primary.startsWith("84") && !rawQuery.trimStart().startsWith("+")) {
            val rest = primary.substring(2)
            val alt = "0" + if (rest.startsWith("0")) rest.substring(1) else rest
            if (alt.length >= MIN_NEEDLE_LENGTH) out.add(alt)
        }
        return out.ifEmpty { null }
    }

    /** Như [rank] nhưng trả -1 thay cho null (không bọc Int, dùng ở đường nóng của SearchIndex). */
    fun rankOrNone(canonicalNumber: String, needles: List<String>): Int {
        var best = -1
        for (i in needles.indices) {
            val n = needles[i]
            if (canonicalNumber.startsWith(n)) return 1
            if (canonicalNumber.contains(n)) best = 2
        }
        return best
    }

    /** 1 = số bắt đầu bằng một kim, 2 = số chứa kim, null = không khớp. */
    fun rank(canonicalNumber: String, needles: List<String>): Int? {
        var best: Int? = null
        for (n in needles) {
            if (canonicalNumber.startsWith(n)) return 1
            if (canonicalNumber.contains(n)) best = 2
        }
        return best
    }
}
