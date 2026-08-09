package com.example.stationalarm.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.example.stationalarm.R
import com.example.stationalarm.presentation.MainViewModel
import com.example.stationalarm.presentation.theme.DarkBackground
import com.example.stationalarm.presentation.theme.DarkSurface
import com.example.stationalarm.presentation.theme.DarkSurfaceVariant
import com.example.stationalarm.presentation.theme.RailwayBlue
import com.example.stationalarm.presentation.theme.TextPrimary
import com.example.stationalarm.presentation.theme.TextSecondary

@Composable
fun StationCandidateScreen(
    candidates: List<MainViewModel.StationCandidate>,
    onCandidateClick: (MainViewModel.StationCandidate) -> Unit,
    onCancelClick: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val haptic = LocalHapticFeedback.current

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(RailwayBlue.copy(alpha = 0.16f), DarkBackground),
                    radius = 420f
                )
            )
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 30.dp, bottom = 36.dp, start = 18.dp, end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.ui_select_station_title),
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.ui_select_station_detail),
                        style = MaterialTheme.typography.caption2,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(candidates) { candidate ->
                Chip(
                    onClick = {
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        onCandidateClick(candidate)
                    },
                    label = {
                        Text(
                            text = candidate.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = candidate.address.ifBlank {
                                stringResource(R.string.ui_address_unknown)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = DarkSurface)
                )
            }
            item {
                CompactChip(
                    onClick = onCancelClick,
                    label = { Text(stringResource(R.string.ui_cancel)) },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = DarkSurfaceVariant)
                )
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}
