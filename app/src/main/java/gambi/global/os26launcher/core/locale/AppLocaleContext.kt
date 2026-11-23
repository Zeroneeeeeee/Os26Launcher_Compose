package gambi.global.os26launcher.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * LG Task 4 — ngôn ngữ app cho context KHÔNG phải Activity (service, thông báo, context ứng dụng của ViewModel/repository).
 *
 * API 33+: hệ thống áp ngôn ngữ theo app cho cả process, [wrap] trả nguyên context. API < 33: `AppCompatDelegate.setApplicationLocales`
 * chỉ đổi Activity, nên ngôn ngữ đã chọn được ghi thêm vào kho phụ (SharedPreferences [PREFS], ghi đồng bộ) và [wrap] dựng context theo
 * tag đó. Kho phụ đọc được cả khi process do service/báo thức dựng lại mà chưa mở Activity nào. Xem `docs/config-schema.md`.
 *
 * Ở `core` (không biết feature nào) để `data/...` dùng được mà không import `ui`.
 */
object AppLocaleContext {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"

    /** Ghi tag đã chọn (null = theo hệ thống). Gọi trước `setApplicationLocales`. */
    fun store(context: Context, tag: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
        }.commit() // đồng bộ: Activity tạo lại ngay sau đó, service có thể đọc liền
    }

    /** Bản bọc gần nhất của context ứng dụng (repository, ViewModel gọi [wrap] mỗi lần dựng chuỗi): tag -> context. */
    @Volatile
    private var cached: Pair<String, Context>? = null

    /** Context có chuỗi theo ngôn ngữ app. Không có lựa chọn (hoặc API 33+) thì trả nguyên [context]. */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
            ?: return context
        val locale = Locale.forLanguageTag(tag)
        val current = context.resources.configuration.locales[0]
        if (current != null && current.toLanguageTag() == locale.toLanguageTag()) return context
        val isApp = context === context.applicationContext
        if (isApp) cached?.let { (t, c) -> if (t == tag) return c }
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config).also { if (isApp) cached = tag to it }
    }

    /** Khóa ngôn ngữ chuỗi của [context] sau khi bọc: đổi thì thứ đã dựng chữ sẵn (panel, danh mục) phải dựng lại. */
    fun localeKey(context: Context): String = wrap(context).resources.configuration.locales.toLanguageTags()
}
