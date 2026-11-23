package gambi.global.os26launcher.ui.settings

import gambi.global.os26launcher.R
import gambi.global.os26launcher.ui.language.AppLanguages
import java.text.Normalizer
import java.util.Locale

/**
 * LG — màn "Ngôn ngữ" (Figma 1.3 `302:1856`): ô tìm, rồi một card radio "Theo hệ thống" + [AppLanguages.all]. Dòng chính là tên bản ngữ,
 * dòng phụ là tên viết bằng ngôn ngữ đang dùng.
 *
 * Chạm một ngôn ngữ chưa chọn thì áp ngay (Figma không có nút xác nhận): mọi Activity tạo lại, khung Cài đặt dựng lại đúng màn này (extra
 * của intent còn nguyên), chữ đang gõ trong ô tìm mất (chấp nhận). Mở được cả từ Onboarding 0.1 (chip ngôn ngữ).
 */
class LanguageSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private var query = ""

    override val titleRes: Int = R.string.settings_hub_language

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val selected = AppLanguages.current()?.tag
        val options = buildList {
            add(Option(null, activity.getString(R.string.lang_system), activity.getString(R.string.lang_system_desc)))
            AppLanguages.all.forEach { add(Option(it.tag, it.nativeName, activity.getString(it.localizedName))) }
        }.filter { it.matches(query) }

        val rows = ArrayList<SettingsRow>(options.size + 2)
        rows.add(SettingsRow.Search(ID_SEARCH, R.string.lang_search_hint))
        if (options.isEmpty()) {
            rows.add(SettingsRow.Footer(ID_EMPTY, activity.getString(R.string.lang_no_results)))
        } else {
            options.forEach { o ->
                rows.add(
                    SettingsRow.Radio(
                        id = ID_PREFIX + (o.tag ?: SYSTEM),
                        title = 0,
                        checked = o.tag == selected,
                        titleText = o.title,
                        descriptionText = o.subtitle
                    )
                )
            }
        }
        return rows
    }

    override fun onSearchChanged(row: SettingsRow.Search, query: String) {
        if (query == this.query) return
        this.query = query
        ctx.rerender()
    }

    override fun onRadioClicked(row: SettingsRow.Radio) {
        val tag = row.id.removePrefix(ID_PREFIX).takeUnless { it == SYSTEM }
        if (tag == AppLanguages.current()?.tag) return
        AppLanguages.apply(activity, tag) // Activity tạo lại; không cần rerender
    }

    override fun onValueClicked(row: SettingsRow.Value) = Unit

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    /** [tag] null = Theo hệ thống. */
    private data class Option(val tag: String?, val title: String, val subtitle: String) {
        /** Plan LG, L8: khớp tên bản ngữ, tên đã dịch hoặc tag; không phân biệt hoa thường và dấu. */
        fun matches(query: String): Boolean {
            val q = fold(query.trim())
            if (q.isEmpty()) return true
            return fold(title).contains(q) || fold(subtitle).contains(q) || (tag != null && tag.lowercase(Locale.ROOT).startsWith(q))
        }
    }

    private companion object {
        const val ID_SEARCH = "lang_search"
        const val ID_EMPTY = "lang_empty"
        const val ID_PREFIX = "lang:"
        const val SYSTEM = "system"

        private val MARKS = Regex("\\p{Mn}+")

        /** Bỏ dấu (NFD + bỏ dấu kết hợp, đ → d) và chữ thường, để "tieng viet" khớp "Tiếng Việt". */
        fun fold(s: String): String =
            MARKS.replace(Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD), "").replace('đ', 'd')
    }
}
