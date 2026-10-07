package com.duzui.sharetoobsi.send

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.duzui.sharetoobsi.data.KindleImportStore

/**
 * Three states, because "is it on" turned out not to be a yes/no question.
 *
 * Observed on this device: the enabled-services setting listed our service while the system
 * reported `Bound services:{}` — enabled on paper, nothing running, and every arming
 * silently doing nothing. A plain boolean cannot say that, and saying "not enabled" would
 * send the user to a switch that already looks on.
 */
enum class CollectorStatus {
    /** Not switched on anywhere. */
    NotEnabled,

    /** Switched on, but not running — the state a re-toggle fixes, and the one that wasted the most time. */
    EnabledNotRunning,

    /** Connected and listening. */
    Running,
}

fun collectorStatus(context: Context): CollectorStatus {
    if (KindleImportStore(context).connected) return CollectorStatus.Running
    return if (listedAsEnabled(context)) CollectorStatus.EnabledNotRunning else CollectorStatus.NotEnabled
}

private fun listedAsEnabled(context: Context): Boolean {
    val expected = ComponentName(context, KindleNotebookService::class.java)
    Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )?.let { stored ->
        if (stored.split(':').any { ComponentName.unflattenFromString(it) == expected }) return true
    }
    val manager = context.getSystemService(AccessibilityManager::class.java)
    return manager?.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true
}

/** The settings page the user has to visit; an app cannot grant this to itself. */
fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
