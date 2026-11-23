package gambi.global.os26launcher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Xml
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.IconPackInfo
import gambi.global.os26launcher.domain.repository.IconPackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IP: tìm gói biểu tượng (chuẩn ADW/Nova) đã cài, đọc `appfilter.xml` của gói và trả icon cho từng component.
 *
 * Không đóng gói artwork nào: mọi ảnh lấy lúc chạy từ gói do người dùng tự cài. Bản đã phân tích (chỉ map tên, không giữ
 * bitmap) cache theo `package@versionCode`; phân tích một lần dù nhiều coroutine nạp icon cùng gọi (khóa [lock]).
 * Lỗi nào của gói (XML hỏng, drawable thiếu, gói vừa bị gỡ) cũng chỉ ghi log và trả null/bỏ mục đó.
 */
@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : IconPackRepository {

    private val pm: PackageManager = context.packageManager
    private val lock = Any()
    private val parsed =
        HashMap<String, PackData?>() // null = đã thử mà không dùng được: không phân tích lại từng icon

    override suspend fun installedPacks(): List<IconPackInfo> = withContext(io) {
        val start = SystemClock.elapsedRealtime()
        val found = LinkedHashMap<String, IconPackInfo>()
        for (intent in DISCOVERY_INTENTS) {
            val infos = try {
                queryActivities(intent)
            } catch (e: Exception) {
                Log.w(TAG, "discover query failed", e)
                emptyList()
            }
            for (info in infos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg in found || pkg == context.packageName) continue
                if (!hasAppFilter(pkg)) continue
                val label =
                    info.activityInfo.applicationInfo.loadLabel(pm).toString().ifBlank { pkg }
                found[pkg] = IconPackInfo(pkg, label)
            }
        }
        Log.i(TAG, "discover packs=${found.size} ms=${SystemClock.elapsedRealtime() - start}")
        found.values.sortedBy { it.label.lowercase() }
    }

    override fun labelOf(packageName: String): String? = try {
        val ai = if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getApplicationInfo(packageName, 0)
        }
        ai.loadLabel(pm).toString().ifBlank { packageName }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        Log.w(TAG, "labelOf failed", e)
        null
    }

    override fun keyOf(packageName: String): String? = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
        }
        "$packageName@${PackageInfoCompat.getLongVersionCode(info)}"
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        Log.w(TAG, "keyOf failed", e)
        null
    }

    /** Bỏ bản đã phân tích của một gói (gói cập nhật hoặc gỡ). */
    fun forget(packageName: String) {
        synchronized(lock) { parsed.keys.removeAll { it.startsWith("$packageName@") } }
    }

    /**
     * C11: chỉ giữ bản đã phân tích của gói [packKey] (khóa `package@versionCode`); null hoặc rỗng = không dùng gói nào, bỏ hết.
     * Gọi khi kiểu icon đổi gói. Một lần nạp đang bay theo gói cũ có thể phân tích lại gói đó (đua hẹp, nằm lại tới lần đổi gói kế).
     */
    fun retainOnly(packKey: String?) {
        synchronized(lock) {
            if (packKey.isNullOrEmpty()) parsed.clear() else parsed.keys.retainAll(setOf(packKey))
        }
    }

    /**
     * Dữ liệu đã phân tích của gói theo khóa `package@versionCode`, null nếu không dùng được. Chặn (lần đầu phân tích XML):
     * chỉ gọi từ luồng nền. Các lần sau chỉ tra map.
     */
    fun packFor(packKey: String): PackData? {
        synchronized(lock) {
            if (parsed.containsKey(packKey)) return parsed[packKey]
            val data = try {
                parse(packKey)
            } catch (e: Exception) {
                Log.w(TAG, "parse failed", e)
                null
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "parse OOM", e)
                null
            }
            parsed[packKey] = data
            return data
        }
    }

    private fun queryActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }

    private fun packContext(pkg: String): Context? = try {
        context.createPackageContext(pkg, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun hasAppFilter(pkg: String): Boolean {
        val ctx = packContext(pkg) ?: return false
        val res = ctx.resources
        if (res.getIdentifier(APPFILTER, "xml", pkg) != 0 || res.getIdentifier(
                APPFILTER,
                "raw",
                pkg
            ) != 0
        ) return true
        return try {
            ctx.assets.open("$APPFILTER.xml").use { true }
        } catch (e: Exception) {
            false
        }
    }

    private class Source(val parser: XmlPullParser, val close: () -> Unit)

    /** Thứ tự tìm theo tài liệu Lawnchair: res/xml -> res/raw -> assets. */
    private fun openSource(ctx: Context, pkg: String): Source? {
        val res = ctx.resources
        val xmlId = res.getIdentifier(APPFILTER, "xml", pkg)
        if (xmlId != 0) {
            val p = res.getXml(xmlId)
            return Source(p) { p.close() }
        }
        val rawId = res.getIdentifier(APPFILTER, "raw", pkg)
        if (rawId != 0) {
            val stream = res.openRawResource(rawId)
            return Source(Xml.newPullParser().apply { setInput(stream, null) }) { stream.close() }
        }
        return try {
            val stream = ctx.assets.open("$APPFILTER.xml")
            Source(Xml.newPullParser().apply { setInput(stream, null) }) { stream.close() }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(packKey: String): PackData? {
        val start = SystemClock.elapsedRealtime()
        val pkg = packKey.substringBeforeLast('@')
        val ctx = packContext(pkg) ?: return null
        val source = openSource(ctx, pkg) ?: return null
        val items = HashMap<String, String>()
        val backs = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        var skipped = 0
        try {
            val p = source.parser
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "item" -> {
                        val component = attr(p, "component")
                        val drawable = attr(p, "drawable")
                        val cn = component?.let(::parseComponent)
                        if (cn == null || drawable.isNullOrBlank()) skipped++
                        else items.putIfAbsent(cn.flattenToString(), drawable)
                    }

                    "iconback" -> imgAttrs(p).forEach { backs.add(it) }
                    "iconmask" -> mask = imgAttrs(p).firstOrNull() ?: mask
                    "iconupon" -> upon = imgAttrs(p).firstOrNull() ?: upon
                    "scale" -> scale =
                        attr(p, "factor")?.toFloatOrNull()?.takeIf { it > 0f } ?: scale
                }
            }
        } catch (e: Exception) {
            // XML hỏng giữa chừng: giữ phần đã đọc được, mục sau chỗ lỗi bị bỏ
            Log.w(TAG, "appfilter malformed after items=${items.size}", e)
        } finally {
            try {
                source.close()
            } catch (_: Exception) {
            }
        }
        Log.i(
            TAG,
            "parse items=${items.size} skipped=$skipped back=${backs.size} mask=${mask != null} upon=${upon != null} " +
                    "ms=${SystemClock.elapsedRealtime() - start}"
        )
        return PackData(pkg, ctx.resources, items, backs, mask, upon, scale)
    }

    private fun attr(p: XmlPullParser, name: String): String? = p.getAttributeValue(null, name)

    /** `img`, `img1`, `img2`... của iconback/iconmask/iconupon (Lawnchair ghi `img`, nhiều gói dùng `img1`). */
    private fun imgAttrs(p: XmlPullParser): List<String> {
        val out = ArrayList<String>(p.attributeCount)
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i).startsWith("img")) p.getAttributeValue(i)
                ?.takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }

    private fun parseComponent(raw: String): ComponentName? {
        val s = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}")
        return ComponentName.unflattenFromString(s)
    }

    /** Dữ liệu đã phân tích của một gói. Chỉ giữ tên; drawable nạp lúc cần và không giữ lại. */
    class PackData internal constructor(
        val packageName: String,
        private val res: Resources,
        private val items: Map<String, String>,
        private val backs: List<String>,
        private val maskName: String?,
        private val uponName: String?,
        val scale: Float
    ) {
        /** Gói có khai ít nhất một lớp để ghép icon cho app không có mục. */
        val hasFallback: Boolean get() = backs.isNotEmpty() || maskName != null || uponName != null

        /** Icon của gói cho component, null nếu không có mục hoặc không nạp được. */
        fun mapped(appPackage: String, className: String): Drawable? {
            val name = items[ComponentName(appPackage, className).flattenToString()] ?: return null
            return drawable(name)
        }

        /** Nền của icon dự phòng; nhiều ảnh thì chọn theo [seed] (ổn định cho từng app). */
        fun back(seed: Int): Drawable? =
            if (backs.isEmpty()) null else drawable(backs[Math.floorMod(seed, backs.size)])

        fun mask(): Drawable? = maskName?.let(::drawable)
        fun upon(): Drawable? = uponName?.let(::drawable)

        private fun drawable(name: String): Drawable? {
            return try {
                var id = res.getIdentifier(name, "drawable", packageName)
                if (id == 0) id = res.getIdentifier(name, "mipmap", packageName)
                if (id == 0) null else res.getDrawable(id, null)
            } catch (e: Exception) {
                Log.w(TAG, "pack drawable failed", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "IP"
        const val APPFILTER = "appfilter"

        /**
         * Các intent mà gói biểu tượng khai trong manifest. Nguồn: manifest mẫu công khai (InfamousProductions
         * Infamous_Development, AnderWeb ADW.Theme-Template) và tài liệu Lawnchair (docs.lawnchair.app, mục icon packs).
         * Phải khớp từng khối `<queries>` ở AndroidManifest.xml.
         */
        val DISCOVERY_INTENTS: List<Intent> = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent("org.adw.ActivityStarter.THEMES"),
            Intent("com.novalauncher.THEME"),
            Intent("com.anddoes.launcher.THEME"),
            Intent("com.gau.go.launcherex.theme"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.teslacoilsw.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.fede.launcher.THEME_ICONPACK")
        )
    }
}
