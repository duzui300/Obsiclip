package com.duzui.sharetoobsi.send

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Whether the collector has been switched on in system settings, which only the user can do. */
fun isKindleServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, KindleNotebookService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

/** The settings page the user has to visit; an app cannot grant this to itself. */
fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
