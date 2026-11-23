package gambi.global.os26launcher.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import gambi.global.os26launcher.R

/**
 * AB — mở link ngoài của nhóm "Liên hệ" (Play, trình duyệt). Chỉ dùng Intent thuần, không thư viện. Máy thiếu Play hoặc trình
 * duyệt, hoặc ROM chặn: Toast, không bao giờ crash.
 */
object AboutLinks {

    /** Trang Play của [packageName]: app Play trước, dự phòng trang web, cuối cùng Toast. */
    fun openPlay(context: Context, packageName: String) {
        if (start(context, "market://details?id=$packageName")) return
        if (start(context, "https://play.google.com/store/apps/details?id=$packageName")) return
        Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_LONG).show()
    }

    /** Mở [url] (chỉ https) bằng trình duyệt của máy. */
    fun openUrl(context: Context, url: String) {
        if (!url.startsWith("https://") || !start(context, url)) {
            Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun start(context: Context, uri: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) { // ROM không cho mở dù resolve được
        false
    }
}
