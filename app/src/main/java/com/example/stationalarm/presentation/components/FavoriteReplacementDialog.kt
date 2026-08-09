package com.example.stationalarm.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.example.stationalarm.R
import com.example.stationalarm.presentation.theme.DarkBackground
import com.example.stationalarm.presentation.theme.DarkSurface
import com.example.stationalarm.presentation.theme.DarkSurfaceVariant
import com.example.stationalarm.presentation.theme.TextPrimary
import com.example.stationalarm.presentation.theme.TextSecondary

@Composable
fun FavoriteReplacementDialog(
    candidate: String,
    favorites: List<String>,
    onReplace: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val listState = rememberScalingLazyListState()
        val haptic = LocalHapticFeedback.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = 28.dp,
                    bottom = 32.dp,
                    start = 18.dp,
                    end = 18.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.ui_replace_favorite_title),
                            style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.ui_replace_favorite_detail, candidate),
                            style = MaterialTheme.typography.caption2,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                itemsIndexed(favorites) { index, station ->
                    Chip(
                        onClick = {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            onReplace(index)
                        },
                        label = {
                            Text(
                                text = station,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = {
                            Text(stringResource(R.string.ui_replace_this_favorite))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = DarkSurface)
                    )
                }
                item {
                    CompactChip(
                        onClick = onDismiss,
                        label = { Text(stringResource(R.string.ui_cancel)) },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = DarkSurfaceVariant)
                    )
                }
            }
            PositionIndicator(scalingLazyListState = listState)
        }
    }
}
