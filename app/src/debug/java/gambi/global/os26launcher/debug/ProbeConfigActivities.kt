package gambi.global.os26launcher.debug

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import gambi.global.os26launcher.R

/** G3c Task 1 — CHỈ BẢN DEBUG. Cấu hình thử: cập nhật widget rồi trả RESULT_OK ngay (provider B và D). */
class ProbeConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val manager = AppWidgetManager.getInstance(this)
            manager.updateAppWidget(id, ProbeViews.build(this, "cfg", id))
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }
        finish()
    }
}

/** Cấu hình thử C: không bao giờ tự kết thúc. Mặc định kết quả là CANCELED (Back / bị đóng). */
class ProbeConfigHangActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(TextView(this).apply {
            setText(R.string.probe_config_hang)
            setPadding(48, 96, 48, 48)
        })
    }
}
