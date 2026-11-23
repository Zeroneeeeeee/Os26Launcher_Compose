package gambi.global.os26launcher.data.search

import gambi.global.os26launcher.domain.model.AppItem

object SystemAppFiller {

    private val PREFERRED_PACKAGES = listOf(
        "com.android.contacts", "com.google.android.dialer", "com.android.dialer",
        "com.android.messaging", "com.google.android.apps.messaging",
        "com.android.chrome", "com.google.android.browser",
        "com.android.camera", "com.google.android.GoogleCamera",
        "com.android.settings", "com.android.email", "com.google.android.gm"
    )

    fun fillSystemApps(allApps: List<AppItem>, existingApps: List<AppItem>, maxNeeded: Int): List<AppItem> {
        if (existingApps.size >= maxNeeded) return emptyList()
        val existingKeys = existingApps.mapTo(HashSet()) { it.key }

        val systemCandidates = allApps.filter { it.isSystem && it.key !in existingKeys }

        val sorted = systemCandidates.sortedWith(compareBy<AppItem> { item ->
            val idx = PREFERRED_PACKAGES.indexOf(item.key.packageName)
            if (idx == -1) Int.MAX_VALUE else idx
        }.thenBy { it.label.lowercase() })

        val needed = maxNeeded - existingApps.size
        return sorted.take(needed)
    }
}
