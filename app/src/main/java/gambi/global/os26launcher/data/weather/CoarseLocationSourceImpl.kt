package gambi.global.os26launcher.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.weather.CoarseFix
import gambi.global.os26launcher.domain.weather.CoarseLocationSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * G3e Task 6b (D6) — lấy vị trí thô MỘT LẦN, chỉ khi người dùng bấm "Dùng vị trí hiện tại" lúc app đang mở. Không `LocationListener` sống lâu,
 * không dịch vụ nền, không `ACCESS_BACKGROUND_LOCATION`. Dùng `LocationManager` hệ thống (không thêm thư viện fused, máy có thể không có
 * Google Play Services) với `NETWORK_PROVIDER` (đủ cho quyền thô).
 *
 * Giả định CHƯA kiểm: `NETWORK_PROVIDER` có trả vị trí trên Live 4 và Oppo (cần dịch vụ vị trí của máy bật); kiểm ở bài kiểm tay Task 6b.
 * API 30+: `getCurrentLocation`; API 26-29: `requestSingleUpdate`. Hết [TIMEOUT_MS] hoặc không có nhà cung cấp -> thử `getLastKnownLocation`, rồi null.
 * Không log tọa độ.
 */
@Singleton
class CoarseLocationSourceImpl @Inject constructor(@ApplicationContext private val context: Context) :
    CoarseLocationSource {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** null = không lấy được (không quyền, dịch vụ vị trí tắt, hết thời gian). */
    @SuppressLint("MissingPermission") // đã kiểm hasPermission() ngay dưới
    override suspend fun getOnce(): CoarseFix? {
        if (!hasPermission()) return null
        val lm =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = LocationManager.NETWORK_PROVIDER
        val enabled = try {
            lm.isProviderEnabled(provider)
        } catch (e: Exception) {
            false
        }
        var loc: Location? = null
        if (enabled) loc = withTimeoutOrNull(TIMEOUT_MS) { single(lm, provider) }
        if (loc == null) {
            loc = try {
                lm.getLastKnownLocation(provider)
            } catch (e: Exception) { // getOnce không được ném (S1): ROM lạ có thể ném ngoài SecurityException
                null
            }
        }
        return loc?.let {
            CoarseFix(
                OwmParser.roundCoord(it.latitude),
                OwmParser.roundCoord(it.longitude)
            )
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun single(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { if (cont.isActive) cont.resume(it) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: android.os.Bundle?
                        ) = Unit

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) } // không để listener sót khi hủy / hết giờ
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (e: IllegalArgumentException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private companion object {
        /** CHỈNH TAY: hết thời gian chờ vị trí. */
        const val TIMEOUT_MS = 10_000L
    }
}
