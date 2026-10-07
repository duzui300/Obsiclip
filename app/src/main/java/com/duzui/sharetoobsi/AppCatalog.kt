package com.duzui.sharetoobsi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val packageName: String, val label: String)

/**
 * Apps that can share to us, for the per-app profile mapping.
 *
 * Enumerating these relies on the MAIN/LAUNCHER `<queries>` entry the return-to-source
 * feature needs anyway — without it the list comes back empty on Android 11+.
 */
fun launchableApps(context: Context): List<AppEntry> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
