package com.example.stationalarm.service

object ArrivalAlarmContract {
    const val EXTRA_AUTO_CLOSE_AT_ELAPSED_REALTIME =
        "com.example.stationalarm.extra.AUTO_CLOSE_AT_ELAPSED_REALTIME"
    const val COMPLETION_DELAY_MS = 5_000L

    fun remainingAutoCloseDelayMs(
        closeAtElapsedRealtime: Long,
        nowElapsedRealtime: Long,
        activityWasVisible: Boolean
    ): Long? {
        if (activityWasVisible || closeAtElapsedRealtime <= 0L) return null
        return (closeAtElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0L)
    }
}
