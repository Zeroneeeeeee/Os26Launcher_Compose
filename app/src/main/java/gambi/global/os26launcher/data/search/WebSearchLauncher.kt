package gambi.global.os26launcher.data.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.SearchManager
import android.widget.Toast
import gambi.global.os26launcher.R
import java.net.URLEncoder

object WebSearchLauncher {

    fun launchWebSearch(context: Context, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, trimmed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (webSearchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(webSearchIntent)
                return true
            }
        } catch (e: Exception) {
            // Fallback
        }

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            return true
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.app_launch_failed, trimmed), Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
