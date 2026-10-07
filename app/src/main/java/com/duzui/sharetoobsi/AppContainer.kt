package com.duzui.sharetoobsi

import android.content.Context
import com.duzui.sharetoobsi.data.AppDatabase
import com.duzui.sharetoobsi.data.PendingReturn
import com.duzui.sharetoobsi.data.SettingsStore
import com.duzui.sharetoobsi.send.ObsidianSender

/** Manual wiring. The app is small enough that a DI framework would be more machinery than help. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val pendingReturn: PendingReturn by lazy { PendingReturn(appContext) }
    val sender: ObsidianSender by lazy { ObsidianSender(appContext) }
}
