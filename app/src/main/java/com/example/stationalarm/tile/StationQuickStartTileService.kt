package com.example.stationalarm.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.stationalarm.presentation.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 駅近クイック起動タイル
 *
 * Wear OS のタイルから固定3駅 (西千葉 / お茶の水 / 代々木) のいずれかをタップすると、
 * その駅名と固定しきい値 (500m) で MainActivity を起動して即追跡を開始する。
 *
 * 駅名は Intent extras (EXTRA_QUICK_STATION) で MainActivity に渡される。
 */
class StationQuickStartTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val deviceParameters = requestParams.deviceConfiguration
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(buildLayout(deviceParameters))
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildLayout(
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        QUICK_STATIONS.forEachIndexed { index, station ->
            if (index > 0) {
                content.addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setHeight(dp(SPACING_DP))
                        .build()
                )
            }
            content.addContent(
                CompactChip.Builder(this, station, buildLaunchAction(station), deviceParameters)
                    .setChipColors(
                        ChipColors(argb(COLOR_JR_GREEN), argb(COLOR_ON_PRIMARY))
                    )
                    .build()
            )
        }

        // 円形タイル領域に3チップを収めるためタイトルラベルは省略する
        return PrimaryLayout.Builder(deviceParameters)
            .setContent(content.build())
            .build()
    }

    private fun buildLaunchAction(stationName: String): ModifiersBuilders.Clickable {
        return ModifiersBuilders.Clickable.Builder()
            .setId("quick_start_$stationName")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                EXTRA_QUICK_STATION,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(stationName)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    companion object {
        // MainActivity に駅名を渡すための Intent extras キー
        const val EXTRA_QUICK_STATION = "quick_station_name"

        // タイル経由で起動する場合の固定3駅と固定しきい値
        val QUICK_STATIONS = listOf("西千葉", "お茶の水", "代々木")
        const val QUICK_THRESHOLD = 500

        private const val RESOURCES_VERSION = "1"
        // タイルキャッシュの有効期間 (1時間)
        private const val FRESHNESS_INTERVAL_MS = 60L * 60L * 1000L

        private const val SPACING_DP = 2f

        // 色 (Theme.kt の JR グリーンに対応)
        private const val COLOR_JR_GREEN = 0xFF00A651.toInt()
        private const val COLOR_ON_PRIMARY = 0xFFFFFFFF.toInt()
    }
}
