package com.example.stationalarm.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.stationalarm.data.StationRepository
import com.example.stationalarm.domain.model.FavoriteStation
import com.example.stationalarm.domain.model.toFavoriteStations
import com.example.stationalarm.domain.model.toTileRows
import com.example.stationalarm.presentation.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 駅近クイック起動タイル
 *
 * Wear OS のタイルからユーザーのお気に入り5駅のいずれかをタップすると、
 * その駅名と固定しきい値 (500m) で MainActivity を起動して即追跡を開始する。
 *
 * 駅名は Intent extras ([QuickStartContract.EXTRA_STATION_NAME]) で MainActivity に渡される。
 */
class StationQuickStartTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val deviceParameters = requestParams.deviceConfiguration
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(
                buildLayout(
                    deviceParameters = deviceParameters,
                    favorites = StationRepository.getInstance(applicationContext)
                        .favoriteStations.value
                        .toFavoriteStations()
                )
            ))
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
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        favorites: List<FavoriteStation>
    ): LayoutElementBuilders.LayoutElement {
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        favorites.toTileRows().forEachIndexed { rowIndex, stations ->
            if (rowIndex > 0) {
                content.addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setHeight(dp(VERTICAL_SPACING_DP))
                        .build()
                )
            }

            val row = LayoutElementBuilders.Row.Builder()
            stations.forEachIndexed { stationIndex, station ->
                if (stationIndex > 0) {
                    row.addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(dp(HORIZONTAL_SPACING_DP))
                            .build()
                    )
                }
                row.addContent(
                    CompactChip.Builder(
                        this,
                        station.tileLabel,
                        buildQuickStartClickable(
                            packageName = packageName,
                            activityClassName = MainActivity::class.java.name,
                            station = station
                        ),
                        deviceParameters
                    )
                        .setChipColors(
                            ChipColors(argb(COLOR_JR_GREEN), argb(COLOR_ON_PRIMARY))
                        )
                        .build()
                )
            }
            content.addContent(row.build())
        }

        // 円形タイルの下端で切れないよう、5チップを2段の中央揃えにしてタイトルは省略する
        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setContent(content.build())
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "3"
        // タイルキャッシュの有効期間 (1時間)
        private const val FRESHNESS_INTERVAL_MS = 60L * 60L * 1000L

        // CompactChip は表示高32dpに対してタップ領域が最低48dpになるため、上下の重なりを防ぐ
        internal const val COMPACT_CHIP_HEIGHT_DP = 32f
        internal const val VERTICAL_SPACING_DP = MIN_CLICKABLE_SIZE_DP - COMPACT_CHIP_HEIGHT_DP
        private const val HORIZONTAL_SPACING_DP = 8f

        // 色 (Theme.kt の JR グリーンに対応)
        private const val COLOR_JR_GREEN = 0xFF35D07F.toInt()
        private const val COLOR_ON_PRIMARY = 0xFFFFFFFF.toInt()
    }
}
