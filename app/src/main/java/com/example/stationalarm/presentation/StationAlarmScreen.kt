package com.example.stationalarm.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.stationalarm.R
import com.example.stationalarm.presentation.theme.DarkBackground
import com.example.stationalarm.presentation.theme.DarkSurface
import com.example.stationalarm.presentation.theme.DarkSurfaceVariant
import com.example.stationalarm.presentation.theme.DistanceVeryNear
import com.example.stationalarm.presentation.theme.JRGreen
import com.example.stationalarm.presentation.components.FavoriteReplacementDialog
import com.example.stationalarm.presentation.screens.StationCandidateScreen
import com.example.stationalarm.presentation.theme.TextPrimary
import com.example.stationalarm.presentation.theme.TextSecondary
import com.example.stationalarm.presentation.theme.calculateArrivalProgress
import com.example.stationalarm.presentation.theme.formatDistance
import com.example.stationalarm.presentation.theme.getDistanceColor
import kotlinx.coroutines.launch

@Composable
fun StationAlarmScreen(
    viewModel: MainViewModel,
    onStartRequested: () -> Unit,
    onStationInputRequested: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        timeText = {
            if (!uiState.hasArrived) {
                TimeText()
            }
        }
    ) {
        when {
            uiState.hasArrived -> ArrivalScreen(
                uiState = uiState,
                onConfirmClick = viewModel::stopTracking
            )

            uiState.isTracking -> TrackingScreen(
                uiState = uiState,
                onStopClick = viewModel::stopTracking
            )

            uiState.stationCandidates.isNotEmpty() -> StationCandidateScreen(
                candidates = uiState.stationCandidates,
                onCandidateClick = viewModel::selectStationCandidate,
                onCancelClick = viewModel::cancelStationSelection
            )

            else -> SetupScreen(
                uiState = uiState,
                onHistoryClick = viewModel::updateStationNameInput,
                onDistanceChange = viewModel::updateDistanceThreshold,
                onReplaceFavorite = viewModel::replaceFavoriteStation,
                onStationInputRequested = onStationInputRequested,
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
    val background = Brush.radialGradient(
        colors = listOf(DistanceVeryNear.copy(alpha = 0.28f), DarkBackground),
        radius = 420f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
            indicatorColor = DistanceVeryNear,
            trackColor = DarkSurface,
            strokeWidth = 6.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(DistanceVeryNear.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    tint = DistanceVeryNear,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ui_arrived_title),
                style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                text = uiState.stationName,
                style = MaterialTheme.typography.body1,
                color = DistanceVeryNear,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.ui_arrived_detail),
                style = MaterialTheme.typography.caption2,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            CompactChip(
                onClick = {
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    onConfirmClick()
                },
                label = {
                    Text(
                        text = stringResource(R.string.ui_confirm),
                        style = MaterialTheme.typography.caption1.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null
                    )
                },
                modifier = Modifier.width(150.dp),
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
    val targetColor = uiState.currentDistance?.let {
        getDistanceColor(it, uiState.distanceThreshold)
    } ?: MaterialTheme.colors.secondary
    val distanceColor by animateColorAsState(
        targetValue = targetColor,
        label = "distanceColor"
    )
    val targetProgress = uiState.currentDistance?.let { distance ->
        calculateArrivalProgress(distance, uiState.distanceThreshold)
    } ?: 0f
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        label = "trackingProgress"
    )
    val statusMessage = if (uiState.messageIsError && uiState.message.isNotBlank()) {
        uiState.message
    } else {
        uiState.currentDistance?.let { distance ->
            when {
                distance <= uiState.distanceThreshold * 0.5f ->
                    stringResource(R.string.ui_status_soon)
                distance <= uiState.distanceThreshold ->
                    stringResource(R.string.ui_status_near)
                else -> stringResource(R.string.ui_status_moving)
            }
        } ?: stringResource(R.string.ui_location_acquiring)
    }
    val background = Brush.radialGradient(
        colors = listOf(distanceColor.copy(alpha = 0.18f), DarkBackground),
        radius = 420f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
            indicatorColor = DarkSurfaceVariant,
            trackColor = DarkBackground,
            strokeWidth = 6.dp
        )
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
            indicatorColor = distanceColor,
            trackColor = Color.Transparent,
            strokeWidth = 6.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        distanceColor.copy(alpha = 0.14f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = distanceColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.caption2,
                    color = distanceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = uiState.stationName,
                style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ui_remaining_distance),
                    style = MaterialTheme.typography.caption3,
                    color = TextSecondary
                )
                uiState.locationAccuracy?.let { accuracy ->
                    Text(
                        text = " ・ ",
                        style = MaterialTheme.typography.caption3,
                        color = TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.ui_location_accuracy, accuracy.toInt()),
                        style = MaterialTheme.typography.caption3,
                        color = TextSecondary
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                val distanceDisplay = formatDistance(uiState.currentDistance)
                Text(
                    text = distanceDisplay.value,
                    style = MaterialTheme.typography.display2.copy(fontWeight = FontWeight.Bold),
                    color = distanceColor,
                    maxLines = 1
                )
                Text(
                    text = distanceDisplay.unit,
                    style = MaterialTheme.typography.body2,
                    color = distanceColor,
                    modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                )
            }
        }

        CompactChip(
            onClick = {
                haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                onStopClick()
            },
            label = { Text(stringResource(R.string.ui_stop)) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .width(94.dp),
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = DistanceVeryNear.copy(alpha = 0.9f)
            )
        )
    }
}

@Composable
fun SetupScreen(
    uiState: MainViewModel.UiState,
    onHistoryClick: (String) -> Unit,
    onDistanceChange: (Int) -> Unit,
    onReplaceFavorite: (Int, String) -> Unit,
    onStationInputRequested: () -> Unit,
    onStartClick: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var favoriteCandidate by rememberSaveable { mutableStateOf<String?>(null) }
    val recentStations = remember(uiState.history, uiState.favoriteStations) {
        uiState.history
            .filterNot { it in uiState.favoriteStations }
            .take(3)
    }
    val background = Brush.radialGradient(
        colors = listOf(JRGreen.copy(alpha = 0.12f), DarkBackground),
        radius = 420f
    )

    LaunchedEffect(Unit) {
        // タイトルを時刻の下に残しながら、最初から駅名入力欄全体を見せる
        listState.scrollToItem(index = 1, scrollOffset = -24)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
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
            contentPadding = PaddingValues(
                top = 28.dp,
                bottom = 40.dp,
                start = 18.dp,
                end = 18.dp
            ),
            autoCentering = AutoCenteringParams(itemIndex = 1),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_title),
                        style = MaterialTheme.typography.title3.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.ui_setup_subtitle),
                        style = MaterialTheme.typography.caption2,
                        color = TextSecondary
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    SectionLabel(text = stringResource(R.string.ui_station_label))
                    Spacer(modifier = Modifier.height(4.dp))
                    Chip(
                        onClick = onStationInputRequested,
                        enabled = !uiState.isSearching,
                        label = {
                            Text(
                                text = uiState.stationNameInput.ifBlank {
                                    stringResource(R.string.ui_input_hint)
                                },
                                maxLines = 1
                            )
                        },
                        secondaryLabel = {
                            Text(stringResource(R.string.ui_station_input_action))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = JRGreen
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = DarkSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel(
                        text = stringResource(R.string.ui_quick_stations),
                        modifier = Modifier.padding(top = 8.dp, bottom = 1.dp)
                    )
                    Text(
                        text = stringResource(R.string.ui_favorite_edit_hint),
                        style = MaterialTheme.typography.caption2,
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    StationChipGrid(
                        stations = uiState.favoriteStations,
                        selectedStation = uiState.stationNameInput,
                        enabled = !uiState.isSearching,
                        onStationClick = {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            onHistoryClick(it)
                        }
                    )
                    val favoriteName = uiState.stationNameInput.trim()
                    if (favoriteName.isNotEmpty() && favoriteName !in uiState.favoriteStations) {
                        Spacer(modifier = Modifier.height(6.dp))
                        CompactChip(
                            onClick = {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                )
                                focusManager.clearFocus()
                                favoriteCandidate = favoriteName
                            },
                            label = { Text(stringResource(R.string.ui_save_favorite)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null
                                )
                            },
                            enabled = !uiState.isSearching,
                            colors = ChipDefaults.secondaryChipColors(backgroundColor = DarkSurfaceVariant)
                        )
                    }
                }
            }

            if (recentStations.isNotEmpty()) {
                item {
                    SectionLabel(
                        text = stringResource(R.string.ui_history),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(recentStations) { station ->
                    Chip(
                        label = {
                            Text(
                                text = station,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            onHistoryClick(station)
                        },
                        enabled = !uiState.isSearching,
                        colors = ChipDefaults.childChipColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )
                }
            }

            item {
                DistanceThresholdCard(
                    distance = uiState.distanceThreshold,
                    enabled = !uiState.isSearching,
                    onDistanceChange = onDistanceChange
                )
            }

            item {
                Chip(
                    onClick = {
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        focusManager.clearFocus()
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
                    secondaryLabel = {
                        Text(
                            if (uiState.stationNameInput.isBlank()) {
                                stringResource(R.string.ui_input_required)
                            } else {
                                stringResource(
                                    R.string.ui_start_summary,
                                    uiState.stationNameInput.trim(),
                                    uiState.distanceThreshold
                                )
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null
                        )
                    },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = JRGreen)
                )
            }

            if (uiState.message.isNotEmpty()) {
                item {
                    StatusMessage(
                        message = uiState.message,
                        isError = uiState.messageIsError
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

        favoriteCandidate?.let { candidate ->
            FavoriteReplacementDialog(
                candidate = candidate,
                favorites = uiState.favoriteStations,
                onReplace = { index ->
                    onReplaceFavorite(index, candidate)
                    favoriteCandidate = null
                },
                onDismiss = { favoriteCandidate = null }
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
        color = TextSecondary,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun StationChipGrid(
    stations: List<String>,
    selectedStation: String,
    enabled: Boolean,
    onStationClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        stations.chunked(2).forEach { rowStations ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rowStations.size == 1) {
                    Spacer(modifier = Modifier.weight(0.5f))
                }
                rowStations.forEach { station ->
                    val selected = station == selectedStation.trim()
                    CompactChip(
                        onClick = { onStationClick(station) },
                        label = {
                            Text(
                                text = station,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        },
                        enabled = enabled,
                        colors = if (selected) {
                            ChipDefaults.primaryChipColors(backgroundColor = JRGreen)
                        } else {
                            ChipDefaults.secondaryChipColors(
                                backgroundColor = DarkSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowStations.size == 1) {
                    Spacer(modifier = Modifier.weight(0.5f))
                }
            }
        }
    }
}

@Composable
private fun DistanceThresholdCard(
    distance: Int,
    enabled: Boolean,
    onDistanceChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(DarkSurface, RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactButton(
            onClick = {
                haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                onDistanceChange(distance - 100)
            },
            enabled = distance > 100 && enabled,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = DarkSurfaceVariant,
                contentColor = TextPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.ui_decrease)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.ui_distance_threshold, distance),
                style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.ui_distance_label),
                style = MaterialTheme.typography.caption3,
                color = TextSecondary
            )
        }
        CompactButton(
            onClick = {
                haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                onDistanceChange(distance + 100)
            },
            enabled = distance < 2000 && enabled,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = DarkSurfaceVariant,
                contentColor = TextPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.ui_increase)
            )
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean
) {
    val accent = if (isError) DistanceVeryNear else MaterialTheme.colors.secondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) {
                Icons.Filled.ErrorOutline
            } else {
                Icons.Filled.MyLocation
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.caption2,
            color = accent,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}
