package com.example.stationalarm.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun arrivalProgressReachesOneAtThreshold() {
        assertEquals(0f, calculateArrivalProgress(distance = 1000f, threshold = 500), 0.001f)
        assertEquals(0.5f, calculateArrivalProgress(distance = 750f, threshold = 500), 0.001f)
        assertEquals(1f, calculateArrivalProgress(distance = 500f, threshold = 500), 0.001f)
        assertEquals(1f, calculateArrivalProgress(distance = 100f, threshold = 500), 0.001f)
    }

    @Test
    fun distanceBandUsesExpectedBoundaries() {
        assertEquals(DistanceBand.FAR, getDistanceBand(distance = 1001f, threshold = 500))
        assertEquals(DistanceBand.MEDIUM, getDistanceBand(distance = 1000f, threshold = 500))
        assertEquals(DistanceBand.NEAR, getDistanceBand(distance = 500f, threshold = 500))
        assertEquals(DistanceBand.VERY_NEAR, getDistanceBand(distance = 250f, threshold = 500))
    }

    @Test
    fun distanceDisplayUsesKilometersForLongDistances() {
        assertEquals(DistanceDisplay("---", "m"), formatDistance(null))
        assertEquals(DistanceDisplay("750", "m"), formatDistance(750f))
        assertEquals(DistanceDisplay("1.3", "km"), formatDistance(1_250f))
        assertEquals(DistanceDisplay("834", "km"), formatDistance(833_519f))
    }
}
