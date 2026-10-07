package com.duzui.sharetoobsi.ui

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.duzui.sharetoobsi.MainActivity

/**
 * One swipe and a tap to capture whatever was just copied.
 *
 * Exists for the reading apps whose share sheet refuses a long selection: select, Copy,
 * open the shade, tap here. The tile always reads the clipboard, whether or not the
 * "read on open" setting is on — that is the whole point of tapping it.
 */
class QuoteTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_FROM_TILE, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
