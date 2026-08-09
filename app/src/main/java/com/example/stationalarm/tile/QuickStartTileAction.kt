package com.example.stationalarm.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.ModifiersBuilders
import com.example.stationalarm.domain.model.FavoriteStation

internal const val MIN_CLICKABLE_SIZE_DP = 48f

/**
 * 表示駅と送信駅を同じ [FavoriteStation] から生成し、配列順による対応ずれを防ぐ。
 */
internal fun buildQuickStartClickable(
    packageName: String,
    activityClassName: String,
    station: FavoriteStation
): ModifiersBuilders.Clickable {
    return ModifiersBuilders.Clickable.Builder()
        .setId(station.clickableId)
        .setMinimumClickableWidth(dp(MIN_CLICKABLE_SIZE_DP))
        .setMinimumClickableHeight(dp(MIN_CLICKABLE_SIZE_DP))
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(activityClassName)
                        .addKeyToExtraMapping(
                            QuickStartContract.EXTRA_STATION_NAME,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(station.name)
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()
}
