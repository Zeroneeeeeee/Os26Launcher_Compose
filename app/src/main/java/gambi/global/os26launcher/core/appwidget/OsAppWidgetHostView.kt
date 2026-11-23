package gambi.global.os26launcher.core.appwidget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView
import gambi.global.os26launcher.R

/** G3c Task 4 — host view với chữ lỗi của ta (tiếng Việt, qua `strings.xml`) thay cho "Problem loading widget" của hệ thống. */
class OsAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun getErrorView(): View = TextView(context).apply {
        setText(R.string.aw_error_load)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 12f
    }
}
