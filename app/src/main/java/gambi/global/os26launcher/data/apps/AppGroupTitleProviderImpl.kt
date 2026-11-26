package gambi.global.os26launcher.data.apps

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.locale.AppLocaleContext
import gambi.global.os26launcher.domain.category.AppGroupTitleProvider
import gambi.global.os26launcher.domain.model.AppGroupKind
import javax.inject.Inject

/**
 * Bản thật của [AppGroupTitleProvider]: chuỗi đọc qua [AppLocaleContext.wrap] (đúng ngôn ngữ app cả khi gọi ngoài Activity). "Ứng dụng
 * của hãng" đặt tên theo `Build.MANUFACTURER` (vd "OPPO", "Samsung"; toàn chữ thường thì viết hoa chữ đầu), rỗng thì dùng chữ chung.
 */
class AppGroupTitleProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppGroupTitleProvider {

    override fun title(kind: AppGroupKind): String {
        val c = AppLocaleContext.wrap(context)
        return when (kind) {
            AppGroupKind.UTILITIES -> c.getString(R.string.app_group_utilities)
            AppGroupKind.GOOGLE -> c.getString(R.string.app_group_google)
            AppGroupKind.SOCIAL -> c.getString(R.string.app_group_social)
            AppGroupKind.GAMES -> c.getString(R.string.app_group_games)
            AppGroupKind.ENTERTAINMENT -> c.getString(R.string.app_group_entertainment)
            AppGroupKind.MANUFACTURER -> {
                val raw = Build.MANUFACTURER.orEmpty().trim()
                when {
                    raw.isEmpty() -> c.getString(R.string.app_group_manufacturer_fallback)
                    raw == raw.lowercase() -> raw.replaceFirstChar { it.uppercase() }
                    else -> raw
                }
            }
        }
    }
}
