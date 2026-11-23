package gambi.global.os26launcher.ui.language

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.locale.AppLocaleContext
import gambi.global.os26launcher.ui.controlcenter.ControlCenterService
import java.util.Locale

/**
 * LG: một ngôn ngữ của app. [tag] là tag BCP 47 gửi cho `setApplicationLocales` (cũng là tag trong `locales_config.xml`);
 * [nativeName] là tên bản ngữ (không dịch, Figma 1.3 dòng chính); [localizedName] là tên viết bằng ngôn ngữ đang dùng (dòng phụ).
 */
data class AppLanguage(val tag: String, val nativeName: String, @StringRes val localizedName: Int)

/**
 * LG — danh mục ngôn ngữ và cách đọc/áp ngôn ngữ app. Thứ tự = thứ tự hiển thị của Figma 1.3 (`302:1856`). Một ngôn ngữ chỉ vào [all]
 * (và `res/xml/locales_config.xml`) khi `values-xx/strings.xml` của nó đã dịch đủ (plan LG, L1).
 *
 * Áp bằng `AppCompatDelegate.setApplicationLocales`: API 33+ hệ thống giữ ngôn ngữ theo app cho cả process; API < 33 AppCompat tự lưu
 * (`AppLocalesMetadataHolderService` `autoStoreLocales` trong manifest) nhưng chỉ áp cho Activity, nên tag còn được ghi vào kho phụ của
 * [AppLocaleContext] cho context không phải Activity (plan LG Task 4).
 */
object AppLanguages {
    val all: List<AppLanguage> = listOf(
        AppLanguage("vi", "Tiếng Việt", R.string.lang_name_vi),
        AppLanguage("en", "English", R.string.lang_name_en),
        AppLanguage("es", "Español", R.string.lang_name_es),
        AppLanguage("pt", "Português", R.string.lang_name_pt),
        AppLanguage("id", "Bahasa Indonesia", R.string.lang_name_id),
        AppLanguage("th", "ภาษาไทย", R.string.lang_name_th),
        AppLanguage("ja", "日本語", R.string.lang_name_ja),
        AppLanguage("ko", "한국어", R.string.lang_name_ko),
        AppLanguage("hi", "हिन्दी", R.string.lang_name_hi),
    )

    /** Ngôn ngữ người dùng đã chọn trong app; null = theo hệ thống (chưa chọn, hoặc ngôn ngữ đã lưu không có trong [all]). */
    fun current(): AppLanguage? {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return null
        return match(locales[0] ?: return null)
    }

    /** Ngôn ngữ app đang hiện: ngôn ngữ đã chọn, hoặc ngôn ngữ của máy nếu app có, không thì tiếng Anh (ngôn ngữ của `values/`). */
    fun effective(): AppLanguage =
        current() ?: match(Locale.getDefault()) ?: all.first { it.tag == FALLBACK_TAG }

    /** Áp [tag]; null = theo hệ thống. Mọi AppCompatActivity đang mở tạo lại. */
    fun apply(context: Context, tag: String?) {
        AppLocaleContext.store(context, tag) // trước: Activity tạo lại có thể đọc kho phụ ngay
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
        // Trung tâm điều khiển là overlay của service: dựng lại panel theo ngôn ngữ mới (service đang không chạy thì bỏ qua).
        ControlCenterService.refresh(context)
    }

    /**
     * Khớp tag đầy đủ trước, rồi theo mã ngôn ngữ (máy `pt-BR` khớp hàng `pt`). `Locale.getLanguage()` có thể trả mã cũ `in` cho tiếng
     * Indonesia (cả với `Locale.forLanguageTag("id")`), nên hai phía đều quy về mã mới trước khi so.
     */
    private fun match(locale: Locale): AppLanguage? {
        val tag = locale.toLanguageTag()
        all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it }
        val language = modern(locale.language)
        return all.firstOrNull { modern(Locale.forLanguageTag(it.tag).language) == language }
    }

    private fun modern(language: String): String = when (language) {
        "in" -> "id"
        "iw" -> "he"
        "ji" -> "yi"
        else -> language
    }

    private const val FALLBACK_TAG = "en"
}
