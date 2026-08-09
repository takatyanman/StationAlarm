package com.example.stationalarm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteStationTest {

    @Test
    fun replacesOnlyTheSelectedFavorite() {
        val updated = FavoriteStationDefaults.names.withFavoriteReplaced(1, " 渋谷 ")

        assertEquals(listOf("西千葉", "渋谷", "代々木", "新宿", "錦糸町"), updated)
    }

    @Test
    fun rejectsDuplicateBlankAndOutOfRangeFavorites() {
        val favorites = FavoriteStationDefaults.names

        assertNull(favorites.withFavoriteReplaced(0, "新宿"))
        assertNull(favorites.withFavoriteReplaced(0, "  "))
        assertNull(favorites.withFavoriteReplaced(5, "渋谷"))
    }
}
