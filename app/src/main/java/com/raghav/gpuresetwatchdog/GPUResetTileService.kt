package com.raghav.gpuresetwatchdog

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.os.HandlerCompat
import android.os.Looper

class GPUResetTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        qsTile?.let { tile ->
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.tile_starting)
            tile.updateTile()
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_start_reset", true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }

        HandlerCompat.createAsync(Looper.getMainLooper()).postDelayed({
            updateTile()
        }, 6000)
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_label)
            tile.contentDescription = getString(R.string.tile_content_description)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_gpu_reset)
            tile.updateTile()
        }
    }
}
