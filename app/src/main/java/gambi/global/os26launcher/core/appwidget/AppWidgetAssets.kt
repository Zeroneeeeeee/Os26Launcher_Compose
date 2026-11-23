package gambi.global.os26launcher.core.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.AppWidgetRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * G3c Task 6 — truy cập `AppWidgetProviderInfo` của hồ sơ chính cho bảng "Tiện ích": liệt kê provider (bộ nhớ đệm theo [ComponentName]) và tải LƯỜI nhãn,
 * ảnh xem trước, icon app (nền IO, thu ảnh đúng kích thước thẻ trước khi trả: không giữ drawable gốc lớn). Ngoại lệ phân lớp có chủ ý như phần còn lại của
 * `core/appwidget` (dùng chung cho `data` lẫn `ui`). Log chỉ có số đếm/tên lớp ngoại lệ, không package/lớp provider (plan G3c, Global Constraints).
 * Widget hồ sơ công việc: chưa làm (plan Task 8, "để sau"), chỉ hồ sơ chính.
 */
@Singleton
class AppWidgetAssets @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val cache = HashMap<ComponentName, AppWidgetProviderInfo>()

    /** Provider hợp lệ cho bảng chọn (HOME_SCREEN, không HIDE_FROM_PICKER). Làm mới bộ nhớ đệm. Gọi trên IO. */
    fun pickerProviders(): List<AppWidgetProviderInfo> {
        val all = try {
            manager.getInstalledProvidersForProfile(Process.myUserHandle())
        } catch (e: RuntimeException) {
            Log.w(TAG, "getInstalledProviders failed ${e.javaClass.name}")
            emptyList()
        }
        val result = all.filter { isPickable(it) }
        synchronized(cache) {
            cache.clear()
            for (info in all) cache[info.provider] = info
        }
        return result
    }

    private fun isPickable(info: AppWidgetProviderInfo): Boolean {
        if (info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN == 0) return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER != 0
        ) return false
        return true
    }

    private fun infoOf(ref: AppWidgetRef): AppWidgetProviderInfo? {
        val key = ComponentName(ref.packageName, ref.className)
        synchronized(cache) { cache[key]?.let { return it } }
        return null
    }

    /** Nhãn của widget (rơi về null nếu provider đã biến mất khỏi bộ nhớ đệm). */
    suspend fun widgetLabel(ref: AppWidgetRef): String? = withContext(Dispatchers.IO) {
        try {
            infoOf(ref)?.loadLabel(context.packageManager)
        } catch (e: RuntimeException) {
            Log.w(TAG, "loadLabel failed ${e.javaClass.name}")
            null
        }
    }

    /**
     * Ảnh xem trước thu vừa [maxWidthPx] x [maxHeightPx] (giữ tỉ lệ), rơi về icon của provider; null nếu không có gì. Ảnh gốc được giải mã rồi
     * thu ngay trên IO, nên view chỉ giữ bitmap nhỏ.
     */
    suspend fun preview(ref: AppWidgetRef, maxWidthPx: Int, maxHeightPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val info = infoOf(ref) ?: return@withContext null
            val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0) ?: return@withContext null
            val srcW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxWidthPx
            val srcH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxHeightPx
            val scale = min(1f, min(maxWidthPx.toFloat() / srcW, maxHeightPx.toFloat() / srcH))
            val w = (srcW * scale).toInt().coerceAtLeast(1)
            val h = (srcH * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bitmap))
            bitmap
        } catch (e: RuntimeException) {
            Log.w(TAG, "preview failed ${e.javaClass.name}")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "preview OOM")
            null
        }
    }

    /** Icon app (hồ sơ chính) thu về [sizePx] vuông; null nếu không đọc được. */
    suspend fun appIcon(packageName: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "appIcon failed ${e.javaClass.name}")
            null
        }
    }

    private companion object {
        const val TAG = "AppWidgetAssets"
    }
}
