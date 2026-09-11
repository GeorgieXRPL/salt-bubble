package com.easton.saltbubble.send

import android.content.pm.PackageManager
import com.easton.saltbubble.data.GrokTargets

class GrokApps(private val packageManager: PackageManager) {
    fun installed(packageName: String): Boolean {
        val name = packageName.trim()
        if (name.isEmpty()) return false
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(name, 0)
            true
        }.getOrDefault(false)
    }

    fun firstInstalled(preferred: String): String? =
        GrokTargets.packages(preferred).firstOrNull { installed(it) }

    fun anyInstalled(preferred: String = GrokTargets.BOT): Boolean =
        firstInstalled(preferred) != null
}
