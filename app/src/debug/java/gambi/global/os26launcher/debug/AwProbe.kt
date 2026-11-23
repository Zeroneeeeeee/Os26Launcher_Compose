package gambi.global.os26launcher.debug

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.appwidget.OsAppWidgetHost

/**
 * G3c Task 1 — CHỈ BẢN DEBUG. Công cụ đo giả định về AppWidgetHost. Log tag [TAG], CHỈ ghi số đếm, id số, true/false, mã kết quả và tên lớp
 * ngoại lệ (không tên package/lớp của app khác, không nội dung widget).
 */
internal object AwProbe {
    const val TAG = "AWPROBE"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun host(): OsAppWidgetHost
        fun layout(): gambi.global.os26launcher.domain.repository.LayoutRepository
        fun reconciler(): gambi.global.os26launcher.data.layout.LayoutReconciler
        fun dao(): gambi.global.os26launcher.data.layout.db.LauncherItemDao
        fun appWidgets(): gambi.global.os26launcher.domain.repository.AppWidgetRepository
        fun apps(): gambi.global.os26launcher.domain.repository.AppRepository
        fun flowState(): gambi.global.os26launcher.ui.widget.host.AppWidgetFlowState
    }

    fun access(context: Context): Access =
        EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java)

    fun host(context: Context): OsAppWidgetHost =
        EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).host()

    fun providerOf(tag: String): ComponentName? = when (tag.uppercase()) {
        "A" -> ProbeProviderA::class.java
        "B" -> ProbeProviderB::class.java
        "C" -> ProbeProviderC::class.java
        "D" -> ProbeProviderD::class.java
        "E" -> ProbeProviderE::class.java
        else -> null
    }?.let { ComponentName("gambi.global.os26launcher", it.name) }

    fun options(): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 160)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
    }

    /** Provider thử có trong [AppWidgetManager.getInstalledProviders] không (app luôn thấy chính mình) và info của nó. */
    fun infoOf(context: Context, provider: ComponentName): AppWidgetProviderInfo? =
        AppWidgetManager.getInstance(context).installedProviders.firstOrNull { it.provider == provider }

    fun counts(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val own = context.packageName
        val all = safe("getInstalledProviders") { manager.installedProviders }
        val foreign = all?.count { it.provider.packageName != own }
        Log.i(
            TAG, "counts sdk=${Build.VERSION.SDK_INT} featureAppWidgets=${pm.hasSystemFeature("android.software.app_widgets")} " +
                    "installedProviders=${all?.size} own=${all?.count { it.provider.packageName == own }} foreign=$foreign"
        )
        val um = context.getSystemService(UserManager::class.java)
        um.userProfiles.forEachIndexed { i, profile ->
            val n = safe("getInstalledProvidersForProfile") { manager.getInstalledProvidersForProfile(profile) }?.size
            Log.i(TAG, "counts profileIndex=$i providers=$n")
        }
        val ids = safe("getAppWidgetIds") { host(context).appWidgetIds }
        Log.i(TAG, "counts hostIds=${ids?.size} ids=${ids?.joinToString(",")}")
    }

    fun <T> safe(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "$what threw ${e.javaClass.name}")
        null
    }
}
