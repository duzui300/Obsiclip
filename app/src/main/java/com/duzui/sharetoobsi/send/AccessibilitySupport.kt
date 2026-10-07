package com.duzui.sharetoobsi.send

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.duzui.sharetoobsi.data.KindleImportStore

/**
 * Whether the collector is actually running.
 *
 * Three signals, because no single one is reliable here. The service reporting in is the
 * strongest — it is the thing we care about, rather than a setting that claims it — and on
 * this ROM it is also the only one that works: MIUI hides `Settings.Secure` from third-party
 * apps, so reading the enabled-services list returns nothing even when the service is on.
 * A stale flag is harmless, since arming with nothing listening simply does nothing.
 */
fun isKindleServiceEnabled(context: Context): Boolean {
    if (KindleImportStore(context).connected) return true

    val expected = ComponentName(context, KindleNotebookService::class.java)
    Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )?.let { stored ->
        if (stored.split(':').any { ComponentName.unflattenFromString(it) == expected }) return true
    }

    val manager = context.getSystemService(AccessibilityManager::class.java)
    val enabled = manager?.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabled?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true
}

/** The settings page the user has to visit; an app cannot grant this to itself. */
fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
