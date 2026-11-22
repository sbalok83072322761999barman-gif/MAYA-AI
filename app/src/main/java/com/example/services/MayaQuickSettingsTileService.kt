package com.example.services

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class MayaQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        if (MayaAssistantService.isRunning.value) {
            startService(Intent(this, MayaAssistantService::class.java).setAction(MayaAssistantService.ACTION_STOP))
        } else {
            val intent = Intent(this, MayaAssistantService::class.java).setAction(MayaAssistantService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val active = MayaAssistantService.isRunning.value
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Maya Mic ON" else "Maya Mic"
        tile.updateTile()
    }
}
