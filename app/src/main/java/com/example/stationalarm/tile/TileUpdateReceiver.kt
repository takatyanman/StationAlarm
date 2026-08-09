package com.example.stationalarm.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService

/** アプリ更新後に古いタイルレイアウトとクリックアクションを残さない。 */
class TileUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TileService.getUpdater(context)
                .requestUpdate(StationQuickStartTileService::class.java)
        }
    }
}
