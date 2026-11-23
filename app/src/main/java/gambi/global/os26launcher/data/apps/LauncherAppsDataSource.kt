package gambi.global.os26launcher.data.apps

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.PackageEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherAppsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val labelCache: LabelCache
) {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    /**
     * Đọc mọi activity mở được của mọi user profile. Chậm (hàng trăm activity, mỗi cái nạp nhãn)
     * nên CHỈ gọi từ luồng nền. Không sắp xếp ở đây.
     * Đọc nhãn SONG SONG, giới hạn [LABEL_CONCURRENCY] việc cùng lúc (M3 Task 1 Bước 2, đo 2026-10-26
     * trên Vsmart Live 4: readLabels chiếm 601/637ms tổng, là thủ phạm đúng như giả thuyết trong AGENTS.md).
     * Một app bị gỡ giữa lúc đọc nhãn không được làm rỗng cả danh sách: dùng packageName làm nhãn dự phòng.
     */
    suspend fun loadApps(): List<AppItem> {
        return try {
            val perProfile = userManager.userProfiles.map { user ->
                Triple(
                    userManager.getSerialNumberForUser(user),
                    user,
                    launcherApps.getActivityList(null, user)
                )
            }

            // LC: nhãn đã có trong cache (cùng khóa, cùng stamp APK) thì bỏ qua đọc. Khóa luôn lấy từ getActivityList.
            val cached = labelCache.load()
            val semaphore = Semaphore(LABEL_CONCURRENCY)
            val loaded = coroutineScope {
                perProfile.flatMap { (serial, user, infos) ->
                    infos.map { info ->
                        async {
                            val appInfo = info.applicationInfo
                            val key = ComponentKey(
                                packageName = info.componentName.packageName,
                                className = info.componentName.className,
                                userSerial = serial
                            )
                            val stamp = appInfo.sourceDir.orEmpty()
                            val hit = cached?.get(key)?.takeIf { it.stamp == stamp }?.label
                            var labelOk = true
                            val label = hit ?: semaphore.withPermit {
                                try {
                                    info.label.toString()
                                } catch (e: Exception) {
                                    Log.w(
                                        TAG,
                                        "loadApps: label failed for ${info.componentName.packageName}",
                                        e
                                    )
                                    labelOk = false
                                    info.componentName.packageName
                                }
                            }
                            Loaded(
                                AppItem(
                                    key = key,
                                    label = label,
                                    category = mapPlatformCategory(appInfo.category),
                                    isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                                    versionStamp = stamp, // A4/B7
                                    a11yLabel = if (serial == mainUserSerial) "" else badgedLabel(label, user), // C10
                                    firstInstallMillis = info.firstInstallTime // LO D6
                                ),
                                stamp,
                                labelOk
                            )
                        }
                    }
                }.awaitAll()
            }
            // Nhãn dự phòng (đọc lỗi) KHÔNG vào cache để khỏi ghim nhãn sai. Ghi nền, không chờ.
            val toSave = HashMap<ComponentKey, LabelCache.Entry>(loaded.size * 2)
            for (l in loaded) if (l.labelOk) toSave[l.item.key] =
                LabelCache.Entry(l.item.label, l.stamp)
            labelCache.scheduleSave(toSave)
            // IC: dấu vân tay APK cho cache icon đĩa, tái dùng stamp đã đọc ở trên (không IPC thêm).
            val fresh = HashMap<ComponentKey, String>(loaded.size * 2)
            for (l in loaded) if (l.stamp.isNotEmpty()) fresh[l.item.key] = l.stamp
            sourceDirs.putAll(fresh)
            sourceDirs.keys.retainAll(fresh.keys)
            loaded.map { it.item }
        } catch (e: SecurityException) {
            Log.e(TAG, "loadApps: SecurityException", e)
            emptyList()
        }
    }

    /** C10: serial của user chính (chủ process); user khác là hồ sơ (công việc...) và được vẽ huy hiệu. */
    private val mainUserSerial: Long by lazy { userManager.getSerialNumberForUser(Process.myUserHandle()) }

    /** C10: tập số serial của mọi hồ sơ hiện có của user chính (rỗng nếu hệ thống từ chối: không kết luận được gì). */
    fun profileSerials(): Set<Long> = try {
        userManager.userProfiles.mapTo(HashSet()) { userManager.getSerialNumberForUser(it) }
    } catch (e: Exception) {
        Log.w(TAG, "profileSerials failed", e)
        emptySet()
    }

    /**
     * C10: hồ sơ [serial] đã bị bỏ: vắng ở `userProfiles` (danh sách này loại hồ sơ đang gỡ dở; còn hồ sơ ở chế độ yên lặng/tạm dừng).
     * Không dùng `getUserForSerialNumber` để xác nhận: đo trên Live 4 (2026-11-11) hệ thống còn trả user lúc đang gỡ dở nên không bao giờ
     * kết luận được. Danh sách rỗng (hệ thống từ chối) thì không kết luận. Không bao giờ đúng với user chính.
     */
    fun isUserGone(serial: Long): Boolean {
        if (!isWorkUser(serial)) return false
        val profiles = profileSerials()
        return profiles.isNotEmpty() && serial !in profiles
    }

    /** C10: icon của user này cần huy hiệu (khác user chính). */
    fun isWorkUser(userSerial: Long): Boolean = userSerial != mainUserSerial

    /** C10: nhãn có hậu tố hồ sơ do hệ thống dịch ("Chrome (Công việc)"); lỗi thì trả nhãn nguyên. */
    private fun badgedLabel(label: String, user: UserHandle): String = try {
        context.packageManager.getUserBadgedLabel(label, user).toString()
    } catch (e: Exception) {
        Log.w(TAG, "badgedLabel failed", e)
        label
    }

    /** RN: như [badgedLabel] nhưng theo số serial của user; user không còn hoặc lỗi thì trả nhãn nguyên. Chặn (IPC), chỉ gọi từ luồng nền. */
    fun badgedLabelFor(label: String, userSerial: Long): String {
        val user = try {
            userManager.getUserForSerialNumber(userSerial)
        } catch (e: Exception) {
            Log.w(TAG, "badgedLabelFor failed", e)
            null
        } ?: return label
        return badgedLabel(label, user)
    }

    /**
     * C10: vẽ huy hiệu hồ sơ của hệ thống lên bitmap icon ĐÃ render (cách Launcher3 làm; giữ nguyên đường vẽ adaptive/Tô màu/3D/gói icon vì
     * huy hiệu đến cuối cùng). User chính trả nguyên [icon]. Mọi lỗi trả nguyên [icon].
     */
    fun badgeForUser(icon: Bitmap, userSerial: Long): Bitmap {
        if (!isWorkUser(userSerial)) return icon
        return try {
            val user = userManager.getUserForSerialNumber(userSerial) ?: return icon
            val badged = context.packageManager.getUserBadgedIcon(
                BitmapDrawable(context.resources, icon),
                user
            )
            if (badged is BitmapDrawable && badged.bitmap != null) {
                badged.bitmap
            } else {
                val out = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
                badged.setBounds(0, 0, icon.width, icon.height)
                badged.draw(Canvas(out))
                out
            }
        } catch (e: Exception) {
            Log.w(TAG, "badgeForUser failed", e)
            icon
        }
    }

    // IC: khóa -> sourceDir, điền cuối loadApps. IconLoader chỉ hỏi khóa đã có trong apps nên luôn có sẵn.
    private val sourceDirs = java.util.concurrent.ConcurrentHashMap<ComponentKey, String>()

    /** IC: sourceDir của APK chứa [key], null nếu chưa thấy trong loadApps (khi đó bỏ qua cache đĩa). */
    fun sourceDirOf(key: ComponentKey): String? = sourceDirs[key]

    /**
     * Icon gốc của một activity, null nếu không tìm thấy (app có thể đã bị gỡ ngay trước lời gọi này,
     * hoặc user profile không còn). Chạy chậm, chỉ gọi từ luồng nền.
     */
    fun loadIconDrawable(key: ComponentKey): Drawable? {
        return try {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: return null
            launcherApps.getActivityList(key.packageName, user)
                .firstOrNull { it.componentName.className == key.className }
                ?.getIcon(0) // 0 = density của màn hình
        } catch (e: SecurityException) {
            Log.e(TAG, "loadIconDrawable: SecurityException", e)
            null
        }
    }

    /**
     * Phát sự kiện package CÓ KIỂU (M4) mỗi khi có app được cài, gỡ, cập nhật, bật/tắt hoặc tạm khóa.
     * Phải phủ đủ cả 7 callback: thiếu onPackageChanged là bỏ mất trường hợp CẬP NHẬT app (icon và nhãn
     * đổi mà danh sách không đổi), trường hợp hay gặp nhất. Callback luôn hủy đăng ký khi flow bị hủy.
     */
    fun observeChanges(): Flow<PackageEvent> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            private fun changed(
                type: PackageEvent.Type,
                user: UserHandle,
                vararg packages: String
            ) {
                trySend(
                    PackageEvent(
                        type,
                        packages.toList(),
                        userManager.getSerialNumberForUser(user)
                    )
                )
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) =
                changed(PackageEvent.Type.ADDED, user, packageName)

            override fun onPackageRemoved(packageName: String, user: UserHandle) =
                changed(PackageEvent.Type.REMOVED, user, packageName)

            override fun onPackageChanged(packageName: String, user: UserHandle) =
                changed(PackageEvent.Type.CHANGED, user, packageName)

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) =
                changed(PackageEvent.Type.AVAILABLE, user, *packageNames)

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) =
                changed(PackageEvent.Type.UNAVAILABLE, user, *packageNames)

            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
                changed(PackageEvent.Type.SUSPENDED, user, *packageNames)

            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
                changed(PackageEvent.Type.UNSUSPENDED, user, *packageNames)
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    /**
     * B9/C10: tín hiệu hệ thống buộc nạp lại danh sách app dù không có sự kiện package: đổi ngôn ngữ (nhãn và thứ tự Collator đổi) và
     * năm broadcast của hồ sơ công việc (thêm, bỏ, bật/tắt, mở khóa). Receiver chỉ `trySend`, không làm việc nặng; đăng ký theo vòng đời
     * của flow. Process không chạy lúc đó thì lần khởi động sau tự nạp lại (header LC khác locale; hồ sơ thì catchUp).
     */
    fun observeReloadTriggers(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // đã hủy đăng ký: bỏ qua
            }
        }
    }

    /**
     * Package còn được cài cho user đó không — kể cả đang bị tắt, tạm khóa hay trên thẻ nhớ đã tháo (M4 Task 4).
     *
     * KHÔNG dùng `LauncherApps.isPackageEnabled`: tài liệu ghi "true if the package exists and is enabled", tức là
     * trả false cho app bị `pm disable-user`, không phân biệt được với app đã gỡ (Review Focus 2 sẽ xóa oan).
     * Thay vào đó hỏi `getApplicationInfo` với `MATCH_UNINSTALLED_PACKAGES` rồi xem cờ `FLAG_INSTALLED`: app gỡ bằng
     * `-k` (còn dữ liệu) vẫn được trả về nhưng cờ này tắt. Chỉ coi là ĐÃ GỠ khi hệ thống nói rõ (không tìm thấy,
     * cờ tắt, hoặc user profile không còn); mọi lỗi khác trả true để giữ hàng.
     * Chặn, chỉ gọi từ luồng nền.
     */
    fun isPackageInstalled(packageName: String, userSerial: Long): Boolean {
        val user = userManager.getUserForSerialNumber(userSerial)
        if (user == null) {
            return false
        }
        return try {
            val info = launcherApps.getApplicationInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES,
                user
            )
            (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "isPackageInstalled: unsure for $packageName, keeping row", e)
            true
        }
    }

    /**
     * Mở app bằng API dành cho launcher (không dùng context.startActivity): nó xử lý đúng user profile
     * và cho hệ thống dựng animation từ [sourceBounds]. false nếu app đã bị gỡ hoặc hệ thống từ chối.
     */
    fun startMainActivity(key: ComponentKey, sourceBounds: Rect?): Boolean {
        return try {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: return false
            launcherApps.startMainActivity(
                ComponentName(key.packageName, key.className),
                user,
                sourceBounds,
                null
            )
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "startMainActivity: not found ${key.packageName}", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "startMainActivity: denied ${key.packageName}", e)
            false
        }
    }

    /**
     * M7 Task 10 Step 5: "Thông tin ứng dụng" qua API dành cho launcher (xử lý đúng user profile), không dùng
     * `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` như plan gốc (intent đó luôn mở theo user chính).
     */
    fun startAppDetails(key: ComponentKey): Boolean {
        return try {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: return false
            launcherApps.startAppDetailsActivity(
                ComponentName(key.packageName, key.className),
                user,
                null,
                null
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "startAppDetails failed ${key.packageName}", e)
            false
        }
    }

    /**
     * M7 Task 10 Step 2: `ACTION_DELETE` tới package installer của hệ thống (không cần quyền, không cần `<queries>`).
     * Context ứng dụng nên cần FLAG_ACTIVITY_NEW_TASK.
     */
    fun requestUninstall(key: ComponentKey): Boolean {
        return try {
            val intent =
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            userManager.getUserForSerialNumber(key.userSerial)
                ?.let { intent.putExtra(Intent.EXTRA_USER, it) }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestUninstall failed ${key.packageName}", e)
            false
        }
    }

    /**
     * G2c Task 11, B1: mở app Đồng hồ (màn báo thức). Ưu tiên [startSystemHandler] với `AlarmClock.ACTION_SHOW_ALARMS`: chỉ nhận trình xử lý
     * của hệ thống và mở bằng component cụ thể nên không bao giờ hiện hộp chọn app (Oppo có 3 trình xử lý, đo 2026-11-05). Không cần `<queries>`
     * mới: package Đồng hồ hệ thống đã thấy được nhờ `<queries>` MAIN/LAUNCHER. Nếu không có trình xử lý hệ thống thì dùng [CLOCK_PACKAGES].
     */
    fun openClock(): Boolean =
        startSystemHandler(Intent(AlarmClock.ACTION_SHOW_ALARMS)) || startFirstInstalled(
            CLOCK_PACKAGES
        )

    /**
     * B1: mở [probe] bằng component cụ thể của trình xử lý HỆ THỐNG (cùng cách `SystemSettingsRepositoryImpl`). Lọc `FLAG_SYSTEM` và bỏ
     * `ResolverActivity`; nếu người dùng đã đặt mặc định là app hệ thống (`resolveActivity` nằm trong danh sách lọc) thì dùng nó, không thì
     * lấy cái đầu tiên. Trả false khi không có trình xử lý hệ thống hoặc có lỗi (đã `Log.w`), để người gọi thử bước dự phòng.
     */
    private fun startSystemHandler(probe: Intent): Boolean {
        return try {
            val pm = context.packageManager
            val handlers = pm.queryIntentActivities(probe, 0).filter {
                (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && it.activityInfo.name != RESOLVER
            }
            val preferred = pm.resolveActivity(probe, 0)?.activityInfo
                ?.takeIf { info -> handlers.any { it.activityInfo.name == info.name && it.activityInfo.packageName == info.packageName } }
            val chosen = preferred ?: handlers.firstOrNull()?.activityInfo ?: return false
            startSystemActivity(
                Intent(probe).setComponent(
                    ComponentName(
                        chosen.packageName,
                        chosen.name
                    )
                )
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "startSystemHandler failed ${probe.action}", e)
            false
        }
    }

    /**
     * Dự phòng khi không có app nào nhận `ACTION_SHOW_ALARMS` (hoặc ROM đòi quyền `SET_ALARM` mà launcher không khai): mở màn chính của một
     * app đồng hồ quen thuộc nếu đã cài. Dùng `LauncherApps` (đã thấy mọi app qua `<queries>` MAIN/LAUNCHER của M2 Task 1B), không cần quyền mới.
     * Danh sách tên package là SUY LUẬN chưa kiểm chứng trên máy thật.
     */
    private fun startFirstInstalled(packages: List<String>): Boolean {
        val user = android.os.Process.myUserHandle()
        for (pkg in packages) {
            try {
                val component =
                    launcherApps.getActivityList(pkg, user).firstOrNull()?.componentName ?: continue
                launcherApps.startMainActivity(component, user, null, null)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startFirstInstalled failed $pkg", e)
            }
        }
        return false
    }

    /**
     * G2c Task 11, B1: mở app Lịch: ưu tiên trình xử lý hệ thống của danh mục `CATEGORY_APP_CALENDAR` qua [startSystemHandler] (không hộp chọn),
     * dự phòng URI thời gian của Calendar provider.
     */
    fun openCalendar(): Boolean =
        startSystemHandler(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)) ||
                startSystemActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
                    )
                )

    private fun startSystemActivity(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "startSystemActivity failed ${intent.action}", e)
        false
    }

    private class Loaded(val item: AppItem, val stamp: String, val labelOk: Boolean)

    private companion object {
        const val TAG = "LauncherAppsDS"
        const val RESOLVER = "com.android.internal.app.ResolverActivity"

        /** G2c Task 11: app đồng hồ quen thuộc để mở khi `ACTION_SHOW_ALARMS` thất bại (suy luận chưa kiểm chứng). */
        val CLOCK_PACKAGES = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            "com.huawei.android.deskclock"
        )

        // M3 Task 1 Bước 3 (đo 2026-10-26, Live 4, readLabels ms): tuần tự 601 · Semaphore(8)=504
        // · Semaphore(4)=843 (có app nền, nhiễu) · Semaphore(16)=489. Đo lại sau khi đóng app nền:
        // Semaphore(4)=456 · Semaphore(8)=458 — gần như giống nhau, chênh lệch lần đo trước là nhiễu,
        // không phải do số luồng. Không đạt ngưỡng giảm ≥50% ms/app (chỉ ~24% ở điều kiện sạch): nghi vấn
        // nạp resource label có khoá nội bộ ở framework/AssetManager, không chỉ là chờ IPC.
        // Chọn 8 (khuyến nghị gốc của plan): tương đương 4 ở Live 4, an toàn hơn khi sang Oppo A3x 344 app.
        // Ghi vào AGENTS.md ở Task 5.
        const val LABEL_CONCURRENCY = 8
    }
}
