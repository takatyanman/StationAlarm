package com.example.stationalarm.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalAlarmContractTest {

    @Test
    fun `自動表示された画面は残り時間後に閉じる`() {
        val delay = ArrivalAlarmContract.remainingAutoCloseDelayMs(
            closeAtElapsedRealtime = 15_000L,
            nowElapsedRealtime = 10_000L,
            activityWasVisible = false
        )

        assertEquals(5_000L, delay)
    }

    @Test
    fun `終了時刻を過ぎていたら直ちに閉じる`() {
        val delay = ArrivalAlarmContract.remainingAutoCloseDelayMs(
            closeAtElapsedRealtime = 10_000L,
            nowElapsedRealtime = 15_000L,
            activityWasVisible = false
        )

        assertEquals(0L, delay)
    }

    @Test
    fun `通常起動中の画面は閉じない`() {
        val delay = ArrivalAlarmContract.remainingAutoCloseDelayMs(
            closeAtElapsedRealtime = 15_000L,
            nowElapsedRealtime = 10_000L,
            activityWasVisible = true
        )

        assertNull(delay)
    }
}
