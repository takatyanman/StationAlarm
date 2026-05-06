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
    val ratio = distance / threshold
    return when {
        ratio > 2.0f -> DistanceFar        // 閾値の2倍以上: 青
        ratio > 1.0f -> DistanceMedium     // 閾値以上: 緑
        ratio > 0.5f -> DistanceNear       // 閾値の半分以上: オレンジ
        else -> DistanceVeryNear           // 閾値の半分以下: 赤
    }
}
