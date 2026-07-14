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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import com.example.stationalarm.R
import com.example.stationalarm.presentation.theme.*
import androidx.compose.ui.graphics.Color

@Composable
fun StationAlarmScreen(
    viewModel: MainViewModel,
    onStartRequested: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        timeText = { TimeText() }
    ) {
        if (uiState.hasArrived) {
            ArrivalScreen(
                uiState = uiState,
                onConfirmClick = { viewModel.stopTracking() }
            )
        } else if (uiState.isTracking) {
            TrackingScreen(
                uiState = uiState,
                onStopClick = { viewModel.stopTracking() }
            )
        } else {
            SetupScreen(
                uiState = uiState,
                onStationNameChange = viewModel::updateStationNameInput,
                onHistoryClick = viewModel::updateStationNameInput,
                onDistanceChange = viewModel::updateDistanceThreshold,
                onStartClick = onStartRequested,
                onOpenAppSettings = onOpenAppSettings
            )
        }
    }
}

@Composable
fun ArrivalScreen(
    uiState: MainViewModel.UiState,
    onConfirmClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = DistanceVeryNear,
            trackColor = DarkBackground,
            strokeWidth = 8.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 22.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = null,
                tint = DistanceVeryNear,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.ui_arrived_title),
                style = MaterialTheme.typography.title3,
                color = DistanceVeryNear
            )
            Text(
                text = uiState.stationName,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            CompactChip(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onConfirmClick()
                },
                label = { Text(stringResource(R.string.ui_confirm)) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null
                    )
                },
                colors = ChipDefaults.primaryChipColors(backgroundColor = DistanceVeryNear)
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
    
    // 通知距離の 2 倍で 0%、通知距離到達で 100% になるように計算する
    val progress = uiState.currentDistance?.let { distance ->
        calculateArrivalProgress(distance, uiState.distanceThreshold)
    } ?: 0f
    
    // 接近ステータスメッセージ
    val statusMessage = if (uiState.messageIsError && uiState.message.isNotBlank()) {
        uiState.message
    } else {
        uiState.currentDistance?.let { distance ->
            when {
                distance <= uiState.distanceThreshold * 0.5f -> stringResource(R.string.ui_status_soon)
                distance <= uiState.distanceThreshold -> stringResource(R.string.ui_status_near)
                else -> stringResource(R.string.ui_status_moving)
            }
        } ?: stringResource(R.string.ui_location_acquiring)
    }

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

            uiState.locationAccuracy?.let { accuracy ->
                Text(
                    text = stringResource(R.string.ui_location_accuracy, accuracy.toInt()),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 停止ボタン
            CompactChip(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onStopClick()
                },
                label = { Text(stringResource(R.string.ui_stop)) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null
                    )
                },
                colors = ChipDefaults.primaryChipColors(backgroundColor = MaterialTheme.colors.error)
            )
        }
    }
}

@Composable
fun SetupScreen(
    uiState: MainViewModel.UiState,
    onStationNameChange: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onDistanceChange: (Int) -> Unit,
    onStartClick: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    onValueChange = onStationNameChange,
                    enabled = !uiState.isSearching,
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
                        textColor = MaterialTheme.colors.onSurface,
                        cursorColor = MaterialTheme.colors.primary,
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
                        onHistoryClick(station)
                    },
                    enabled = !uiState.isSearching,
                    colors = ChipDefaults.childChipColors(),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
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
                        onDistanceChange(uiState.distanceThreshold - 100)
                    },
                    enabled = uiState.distanceThreshold > 100 && !uiState.isSearching
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
                        onDistanceChange(uiState.distanceThreshold + 100)
                    },
                    enabled = uiState.distanceThreshold < 2000 && !uiState.isSearching
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ui_increase))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Chip(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onStartClick()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.stationNameInput.isNotBlank() && !uiState.isSearching,
                label = {
                    Text(
                        if (uiState.isSearching) {
                            stringResource(R.string.ui_searching)
                        } else {
                            stringResource(R.string.ui_start_tracking)
                        }
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                },
                colors = ChipDefaults.primaryChipColors(backgroundColor = MaterialTheme.colors.primary)
            )
        }
        
        if (uiState.message.isNotEmpty()) {
            item {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.caption2,
                    color = if (uiState.messageIsError) {
                        MaterialTheme.colors.error
                    } else {
                        MaterialTheme.colors.secondary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (uiState.requiresAppSettings) {
            item {
                Chip(
                    onClick = onOpenAppSettings,
                    label = { Text(stringResource(R.string.ui_open_settings)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
        }

        PositionIndicator(scalingLazyListState = listState)
    }
}
