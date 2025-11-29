package com.example.stationalarm.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.wear.compose.foundation.lazy.items
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@Composable
fun StationAlarmScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
        autoCentering = AutoCenteringParams(itemIndex = 0),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "駅近振動通知",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title2,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (uiState.isTracking) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "監視中: ${uiState.stationName}",
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.currentDistance?.let { "${it.toInt()}m" } ?: "計測中..."}",
                        style = MaterialTheme.typography.display1,
                        color = if ((uiState.currentDistance ?: Float.MAX_VALUE) <= uiState.distanceThreshold) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "残り距離",
                        style = MaterialTheme.typography.caption1,
                        color = androidx.compose.ui.graphics.Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.stopTracking() 
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text("停止")
                    }
                }
            }
        } else {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material.TextField(
                        value = uiState.stationNameInput,
                        onValueChange = { viewModel.updateStationNameInput(it) },
                        label = { Text("駅名", color = androidx.compose.ui.graphics.Color.LightGray) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        singleLine = true,
                        colors = androidx.compose.material.TextFieldDefaults.textFieldColors(
                            textColor = androidx.compose.ui.graphics.Color.White,
                            backgroundColor = androidx.compose.ui.graphics.Color.DarkGray,
                            cursorColor = androidx.compose.ui.graphics.Color.White,
                            focusedIndicatorColor = MaterialTheme.colors.primary,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Gray,
                            focusedLabelColor = MaterialTheme.colors.primary,
                            unfocusedLabelColor = androidx.compose.ui.graphics.Color.LightGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (uiState.history.isNotEmpty()) {
                        Text("履歴", style = MaterialTheme.typography.caption1)
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.history.forEach { station ->
                            androidx.wear.compose.material.Chip(
                                label = { Text(station) },
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.updateStationNameInput(station) 
                                },
                                colors = androidx.wear.compose.material.ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text("通知距離: ${uiState.distanceThreshold}m")
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateDistanceThreshold(uiState.distanceThreshold - 100) 
                            },
                            modifier = Modifier.size(40.dp)
                        ) { Text("-") }
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateDistanceThreshold(uiState.distanceThreshold + 100) 
                            },
                            modifier = Modifier.size(40.dp)
                        ) { Text("+") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startTracking() 
                        },
                        enabled = uiState.stationNameInput.isNotBlank()
                    ) {
                        Text("開始")
                    }
                }
            }
        }

        if (uiState.message.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colors.secondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption1
                )
            }
        }
    }
}
