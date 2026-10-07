package com.duzui.sharetoobsi.send

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.duzui.sharetoobsi.data.KindleImportStore

/**
 * Whether the collector is actually running — which is not the same as whether the system
 * says it is enabled.
 *
 * Observed on this device: the enabled-services setting listed our service while
 * `dumpsys accessibility` reported `Bound services:{}`, so nothing was listening and every
 * arming silently did nothing. The service reporting in is the only signal that means the
 * thing we care about. Stale is possible if the process is killed without unbinding, and
 * harmless: arming with nothing listening just does nothing.
 */
fun isKindleServiceEnabled(context: Context): Boolean =
    KindleImportStore(context).connected

/** The settings page the user has to visit; an app cannot grant this to itself. */
fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
