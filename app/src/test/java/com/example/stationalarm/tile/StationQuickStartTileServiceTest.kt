package com.example.stationalarm.tile

import androidx.wear.protolayout.ActionBuilders
import com.example.stationalarm.domain.model.FavoriteStationDefaults
import com.example.stationalarm.domain.model.toFavoriteStations
import com.example.stationalarm.domain.model.toTileRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationQuickStartTileServiceTest {

    @Test
    fun favoriteStationsHaveExpectedDefaultsAndStableSlotIds() {
        val favorites = FavoriteStationDefaults.names.toFavoriteStations()

        assertEquals(
            listOf("西千葉", "お茶の水", "代々木", "新宿", "錦糸町"),
            favorites.map { it.name }
        )
        assertEquals(favorites.size, favorites.map { it.clickableId }.toSet().size)
        favorites.forEachIndexed { index, favorite ->
            assertTrue(favorite.clickableId.startsWith("favorite_${index}_"))
        }
    }

    @Test
    fun tileRowsContainEveryStationExactlyOnce() {
        val favorites = FavoriteStationDefaults.names.toFavoriteStations()
        val stationsInRows = favorites.toTileRows().flatten()

        assertEquals(favorites, stationsInRows)
    }

    @Test
    fun everyTileActionSendsTheStationShownOnItsChip() {
        FavoriteStationDefaults.names.toFavoriteStations().forEach { station ->
            val clickable = buildQuickStartClickable(
                packageName = "com.example.stationalarm",
                activityClassName = "com.example.stationalarm.presentation.MainActivity",
                station = station
            )
            val launchAction = clickable.onClick as ActionBuilders.LaunchAction
            val stationExtra = requireNotNull(launchAction.androidActivity)
                .keyToExtraMapping[QuickStartContract.EXTRA_STATION_NAME] as
                    ActionBuilders.AndroidStringExtra

            assertEquals(station.clickableId, clickable.id)
            assertEquals(station.name, stationExtra.value)
            assertEquals(MIN_CLICKABLE_SIZE_DP, clickable.minimumClickableWidth.value, 0.001f)
            assertEquals(MIN_CLICKABLE_SIZE_DP, clickable.minimumClickableHeight.value, 0.001f)
        }
    }

    @Test
    fun tileRowsLeaveEnoughSpaceForExpandedTouchTargets() {
        val requiredSpacing = MIN_CLICKABLE_SIZE_DP -
            StationQuickStartTileService.COMPACT_CHIP_HEIGHT_DP

        assertTrue(StationQuickStartTileService.VERTICAL_SPACING_DP >= requiredSpacing)
    }

    @Test
    fun longFavoriteNameIsShortenedOnlyOnTheTileLabel() {
        val favorite = listOf("東京テレポート駅").toFavoriteStations().single()

        assertEquals("東京テレ…", favorite.tileLabel)
        assertEquals("東京テレポート駅", favorite.name)
    }

    @Test
    fun replacingAStationAlsoChangesItsClickableId() {
        val before = listOf("西千葉").toFavoriteStations().single()
        val after = listOf("渋谷").toFavoriteStations().single()

        assertTrue(before.clickableId != after.clickableId)
    }
}
