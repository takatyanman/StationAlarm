package com.example.stationalarm.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import com.example.stationalarm.R
import com.example.stationalarm.presentation.theme.*
import androidx.compose.ui.graphics.Color

@Composable
fun StationAlarmScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        timeText = { TimeText() }
    ) {
        if (uiState.isTracking) {
            TrackingScreen(
                uiState = uiState,
                onStopClick = { viewModel.stopTracking() }
            )
        } else {
            SetupScreen(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun TrackingScreen(
    uiState: MainViewModel.UiState,
    onStopClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // 距離に応じた色を計算
    val distanceColor = uiState.currentDistance?.let {
        getDistanceColor(it, uiState.distanceThreshold)
    } ?: MaterialTheme.colors.secondary
    
    // プログレス計算（閾値の2倍から0までを0.0〜1.0にマッピング）
    val progress = uiState.currentDistance?.let { distance ->
        val maxDistance = uiState.distanceThreshold * 2f
        ((maxDistance - distance) / maxDistance).coerceIn(0f, 1f)
    } ?: 0f
    
    // 接近ステータスメッセージ
    val statusMessage = uiState.currentDistance?.let { distance ->
        when {
            distance <= uiState.distanceThreshold * 0.5f -> "🚃 まもなく到着！"
            distance <= uiState.distanceThreshold -> "📍 接近中..."
            else -> "🚄 移動中"
        }
    } ?: "位置を取得中..."

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 背景のプログレスリング
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = DarkSurfaceVariant,
            trackColor = DarkBackground,
            strokeWidth = 8.dp
        )
        
        // 実際の進捗リング（距離に応じて色が変わる）
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = distanceColor,
            trackColor = Color.Transparent,
            strokeWidth = 8.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // ステータスメッセージ
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.caption2,
                color = distanceColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 駅名（円形画面の端で見切れないよう1行省略表示）
            Text(
                text = uiState.stationName,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 距離表示（4 桁でも円形画面に収まるよう display2 に縮小）
            val distanceText = uiState.currentDistance?.let { "${it.toInt()}" } ?: "---"
            Text(
                text = distanceText,
                style = MaterialTheme.typography.display2,
                color = distanceColor
            )
            Text(
                text = "m",
                style = MaterialTheme.typography.body1,
                color = distanceColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 停止ボタン
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onStopClick()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.ui_stop)
                )
            }
        }
    }
}

@Composable
fun SetupScreen(
    uiState: MainViewModel.UiState,
    viewModel: MainViewModel
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent {
                coroutineScope.launch {
                    listState.scrollBy(it.verticalScrollPixels)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        // 円形画面の端で要素が見切れないよう余白を確保
        contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
        autoCentering = AutoCenteringParams(itemIndex = 0),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = stringResource(R.string.ui_title),
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.ui_station_label),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.secondary
                )
                androidx.compose.material.TextField(
                    value = uiState.stationNameInput,
                    onValueChange = { viewModel.updateStationNameInput(it) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.ui_input_hint),
                            style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
                            color = MaterialTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = androidx.compose.material.TextFieldDefaults.textFieldColors(
                        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colors.primary,
                        unfocusedIndicatorColor = MaterialTheme.colors.onSurfaceVariant
                    ),
                    textStyle = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (uiState.history.isNotEmpty()) {
            item {
                Text(stringResource(R.string.ui_history), style = MaterialTheme.typography.caption2)
            }
            items(uiState.history) { station ->
                Chip(
                    label = { Text(station) },
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.updateStationNameInput(station)
                    },
                    colors = ChipDefaults.childChipColors(),
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            // 距離しきい値の +/- 調整。Stepper はフルスクリーン部品のためリスト内では使わず Row で代替
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.updateDistanceThreshold(uiState.distanceThreshold - 100)
                    },
                    enabled = uiState.distanceThreshold > 100
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.ui_decrease))
                }
                Text(
                    text = stringResource(R.string.ui_distance_threshold, uiState.distanceThreshold),
                    style = MaterialTheme.typography.body2
                )
                CompactButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.updateDistanceThreshold(uiState.distanceThreshold + 100)
                    },
                    enabled = uiState.distanceThreshold < 2000
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ui_increase))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    viewModel.startTracking()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.stationNameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.ui_start)
                )
            }
        }
        
        if (uiState.message.isNotEmpty()) {
            item {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
