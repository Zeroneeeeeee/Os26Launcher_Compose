package gambi.global.os26launcher.domain.search

/**
 * M8d — chỉ mục tìm kiếm: nhãn đã chuẩn hóa MỘT LẦN lúc dựng, nên mỗi phím gõ chỉ chuẩn hóa truy vấn rồi quét chuỗi
 * (M8b chuẩn hóa lại cả 344 nhãn ở mỗi phím). Hàm thuần, không `android.*`; dựng và tìm đều chạy được ở luồng nền.
 *
 * Hạng (nhỏ = lên trước): 0 nhãn bắt đầu bằng truy vấn; 1 một từ của nhãn bắt đầu bằng truy vấn; 2 nhãn chứa truy vấn;
 * 3 một từ khóa bắt đầu bằng truy vấn; 4 từ khóa chứa truy vấn (từ khóa luôn sau khớp nhãn). Cùng hạng: ưu tiên loại
 * ([SearchKind.priority]), rồi giữ thứ tự đầu vào (sắp ổn định).
 */
class SearchIndex(private val sources: List<Source>) {

    /** [label] và [keywords] là chữ gốc; chỉ mục tự chuẩn hóa. */
    class Candidate(
        val result: SearchResult.Searchable,
        val label: String,
        val keywords: List<String> = emptyList(),
        /** CP: số điện thoại gốc (chỉ danh bạ); chỉ mục tự quy về dạng chuẩn một lần. */
        val phones: List<String> = emptyList()
    )

    internal class Entry(
        val result: SearchResult.Searchable,
        val label: String,
        val keywords: List<String>,
        val phones: Array<String>,
        val phonesCanon: Array<String>
    )

    /**
     * B11 — một nguồn (app, Cài đặt, danh bạ, shortcut) đã chuẩn hóa sẵn, bất biến. Nguồn nào đổi mới dựng lại nguồn đó;
     * [SearchIndex] chỉ duyệt lần lượt các nguồn theo thứ tự truyền vào (thứ tự này quyết định thứ tự cùng hạng).
     */
    class Source private constructor(internal val entries: Array<Entry>) {
        companion object {
            val EMPTY = Source(emptyArray())
            private val NO_PHONES = emptyArray<String>()

            fun of(candidates: List<Candidate>): Source {
                if (candidates.isEmpty()) return EMPTY
                return Source(Array(candidates.size) { i ->
                    val c = candidates[i]
                    Entry(
                        c.result,
                        AppSearchMatcher.normalize(c.label),
                        c.keywords.map(AppSearchMatcher::normalize),
                        if (c.phones.isEmpty()) NO_PHONES else c.phones.toTypedArray(),
                        if (c.phones.isEmpty()) NO_PHONES else Array(c.phones.size) { PhoneMatcher.canonical(c.phones[it]) }
                    )
                })
            }
        }
    }


    /**
     * CP: một lượt quét, không sắp xếp toàn bộ. Kết quả theo (hạng, loại) rồi thứ tự đầu vào = đúng thứ tự `sortedWith` ổn định
     * trước đây; mỗi thùng (hạng, loại) chỉ giữ tối đa `maxResults` của loại đó vì không loại nào lấy quá số đó, nên 1000 hit danh
     * bạ không tạo 1000 đối tượng Hit. Hạng ở dạng Int (-1 = không khớp), không bọc Int?.
     */
    fun search(rawQuery: String): List<SearchResult> {
        val q = AppSearchMatcher.normalize(rawQuery)
        if (q.isEmpty()) return emptyList()
        val needles = PhoneMatcher.needles(rawQuery) // null nếu không phải truy vấn số (hoặc dưới 3 ký tự số)
        val spaced = " $q" // một lần cho cả lượt quét (trước đây dựng lại cho mỗi nhãn)
        val kinds = KIND_COUNT
        val buckets = arrayOfNulls<ArrayList<Hit>>(RANKS * kinds)
        for (source in sources) for (e in source.entries) {
            val kind = e.result.kind
            var rank = if (q.length < kind.minQueryLength) NONE else rankOf(e, q, spaced)
            var phone: String? = null
            if (needles != null && e.phonesCanon.isNotEmpty()) {
                // Khớp theo số chỉ thắng khi tốt hơn hạng tên (cùng hạng: giữ khớp tên, hiện số mặc định). Trong các số của một
                // người: số bắt đầu bằng kim trước số chỉ chứa kim, rồi theo thứ tự lưu.
                var bestIdx = -1
                var bestRank = 3
                for (i in e.phonesCanon.indices) {
                    val r = PhoneMatcher.rankOrNone(e.phonesCanon[i], needles)
                    if (r != NONE && r < bestRank) {
                        bestRank = r
                        bestIdx = i
                    }
                }
                if (bestIdx >= 0 && (rank == NONE || bestRank < rank)) {
                    rank = bestRank
                    phone = e.phones[bestIdx]
                }
            }
            if (rank == NONE) continue
            val key = rank * kinds + kind.priority
            val bucket = buckets[key] ?: ArrayList<Hit>().also { buckets[key] = it }
            if (bucket.size < kind.maxResults) bucket.add(Hit(e.result, phone))
        }
        val taken = IntArray(kinds)
        val out = ArrayList<SearchResult>()
        for (key in buckets.indices) {
            val bucket = buckets[key] ?: continue
            val kind = key % kinds
            for (h in bucket) {
                val k = h.result.kind
                if (taken[kind] >= k.maxResults) break
                taken[kind]++
                val r = h.result
                out.add(if (h.phone != null && r is SearchResult.Contact) r.copy(phone = h.phone) else r)
            }
        }
        return out
    }

    /** [phone] != null: khớp theo số này (số gốc), hàng hiện và gọi/nhắn dùng số đó. */
    private class Hit(val result: SearchResult.Searchable, val phone: String?)

    private fun rankOf(e: Entry, q: String, spaced: String): Int {
        val label = e.label
        when {
            label.startsWith(q) -> return 0
            label.contains(spaced) -> return 1
            label.contains(q) -> return 2
        }
        var best = NONE
        for (k in e.keywords) {
            when {
                k.startsWith(q) || k.contains(spaced) -> return 3
                k.contains(q) -> best = 4
            }
        }
        return best
    }

    private companion object {
        const val NONE = -1
        const val RANKS = 5
        val KIND_COUNT = (SearchKind.entries.maxOf { it.priority }) + 1
    }
}
