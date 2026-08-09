package com.example.stationalarm.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import kotlin.math.roundToInt

// OLED画面で明るく見える、少し青みを含んだグリーン
val JRGreen = Color(0xFF35D07F)
val JRGreenLight = Color(0xFF74E6A5)
val JRGreenDark = Color(0xFF17945A)

// Railway Blue - アクセントカラー
val RailwayBlue = Color(0xFF54A8FF)
val RailwayBlueLight = Color(0xFF89C5FF)

// Status Colors - 距離に応じた色変化用
val DistanceFar = Color(0xFF54A8FF)      // 青 - 遠い
val DistanceMedium = Color(0xFF35D07F)   // 緑 - 中間
val DistanceNear = Color(0xFFFFB347)     // オレンジ - 近い
val DistanceVeryNear = Color(0xFFFF5A6F) // 赤 - 非常に近い

// Background Colors
val DarkBackground = Color(0xFF070B12)
val DarkSurface = Color(0xFF111A24)
val DarkSurfaceVariant = Color(0xFF1B2A38)

// Text Colors
val TextPrimary = Color(0xFFF3F7FA)
val TextSecondary = Color(0xFF9FB0BE)

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

data class DistanceDisplay(
    val value: String,
    val unit: String
)

/**
 * 丸型画面で桁あふれしないよう、遠距離はkm表記へ変換する。
 */
fun formatDistance(distance: Float?): DistanceDisplay {
    if (distance == null || !distance.isFinite() || distance < 0f) {
        return DistanceDisplay(value = "---", unit = "m")
    }

    return when {
        distance >= 10_000f -> DistanceDisplay(
            value = (distance / 1_000f).roundToInt().toString(),
            unit = "km"
        )
        distance >= 1_000f -> {
            val tenths = (distance / 100f).roundToInt()
            DistanceDisplay(
                value = "${tenths / 10}.${tenths % 10}",
                unit = "km"
            )
        }
        else -> DistanceDisplay(
            value = distance.roundToInt().toString(),
            unit = "m"
        )
    }
}
