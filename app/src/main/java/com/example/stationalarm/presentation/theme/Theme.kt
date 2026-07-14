package com.example.stationalarm.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// JR Green Color Palette - 山手線をイメージ
val JRGreen = Color(0xFF00A651)
val JRGreenLight = Color(0xFF4DD97E)
val JRGreenDark = Color(0xFF007A3D)

// Railway Blue - アクセントカラー
val RailwayBlue = Color(0xFF0066B3)
val RailwayBlueLight = Color(0xFF4D9FD9)

// Status Colors - 距離に応じた色変化用
val DistanceFar = Color(0xFF0066B3)      // 青 - 遠い
val DistanceMedium = Color(0xFF00A651)   // 緑 - 中間
val DistanceNear = Color(0xFFFFA500)     // オレンジ - 近い
val DistanceVeryNear = Color(0xFFE94560) // 赤 - 非常に近い

// Background Colors
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val DarkSurfaceVariant = Color(0xFF1F3460)

// Text Colors
val TextPrimary = Color(0xFFE8E8E8)
val TextSecondary = Color(0xFFB0B0B0)

private val StationAlarmColors = Colors(
    primary = JRGreen,
    primaryVariant = JRGreenLight,
    secondary = RailwayBlue,
    secondaryVariant = RailwayBlueLight,
    background = DarkBackground,
    surface = DarkSurface,
    error = DistanceVeryNear,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    onError = Color.White
)

@Composable
fun StationAlarmTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = StationAlarmColors,
        content = content
    )
}

/**
 * 距離に応じた色を返す
 * @param distance 現在の距離 (メートル)
 * @param threshold 閾値距離 (メートル)
 */
fun getDistanceColor(distance: Float, threshold: Int): Color {
    return when (getDistanceBand(distance, threshold)) {
        DistanceBand.FAR -> DistanceFar
        DistanceBand.MEDIUM -> DistanceMedium
        DistanceBand.NEAR -> DistanceNear
        DistanceBand.VERY_NEAR -> DistanceVeryNear
    }
}

enum class DistanceBand {
    FAR,
    MEDIUM,
    NEAR,
    VERY_NEAR
}

/**
 * 通知距離に対する現在地の距離帯を返す。
 */
fun getDistanceBand(distance: Float, threshold: Int): DistanceBand {
    val ratio = distance / threshold.coerceAtLeast(1)
    return when {
        ratio > 2.0f -> DistanceBand.FAR
        ratio > 1.0f -> DistanceBand.MEDIUM
        ratio > 0.5f -> DistanceBand.NEAR
        else -> DistanceBand.VERY_NEAR
    }
}

/**
 * 通知距離の 2 倍を 0%、通知距離到達を 100% とする進捗値を返す。
 */
fun calculateArrivalProgress(distance: Float, threshold: Int): Float {
    val safeThreshold = threshold.coerceAtLeast(1).toFloat()
    return ((safeThreshold * 2f - distance) / safeThreshold).coerceIn(0f, 1f)
}
