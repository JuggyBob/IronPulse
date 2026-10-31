package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.util.WidgetPhotoStore
import java.io.File

/**
 * The Home widget manager.
 *
 * It edits the same configuration Home reads: the visible order and the set of removed widgets.
 * Adding is idempotent, removing only hides a card (its data stays in the database), and every
 * change is written immediately so closing the sheet, navigating away or killing the app cannot
 * lose it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWidgetManagerSheet(
    enabled: List<HomeWidget>,
    hasWeekRoutine: Boolean,
    photoPath: String,
    weeklyGoal: Int,
    photoError: String?,
    onDismiss: () -> Unit,
    onAdd: (HomeWidget) -> Unit,
    onRemove: (HomeWidget) -> Unit,
    onMove: (HomeWidget, Int) -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onGoalChange: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        contentColor = TextWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkBorder) },
        modifier = Modifier.testTag("home_widget_manager_sheet")
    ) {
        HomeWidgetManagerContent(
            enabled = enabled,
            hasWeekRoutine = hasWeekRoutine,
            photoPath = photoPath,
            weeklyGoal = weeklyGoal,
            photoError = photoError,
            onDismiss = onDismiss,
            onAdd = onAdd,
            onRemove = onRemove,
            onMove = onMove,
            onChoosePhoto = onChoosePhoto,
            onRemovePhoto = onRemovePhoto,
            onGoalChange = onGoalChange
        )
    }
}

@Composable
internal fun HomeWidgetManagerContent(
    enabled: List<HomeWidget>,
    hasWeekRoutine: Boolean,
    photoPath: String,
    weeklyGoal: Int,
    photoError: String?,
    onDismiss: () -> Unit,
    onAdd: (HomeWidget) -> Unit,
    onRemove: (HomeWidget) -> Unit,
    onMove: (HomeWidget, Int) -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onGoalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val available = HomeWidget.available(enabled)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Customize Home",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Text(
                    text = "Add, remove, reorder and personalize the cards on your Home tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("home_widget_manager_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close widget customization",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        WidgetSectionHeader(
            title = "ON YOUR HOME",
            count = enabled.size,
            accent = ElectricCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (enabled.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("widget_manager_empty"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Text(
                    text = "No widgets on your Home. Add one from the list below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                enabled.forEachIndexed { index, widget ->
                    EnabledWidgetCard(
                        widget = widget,
                        position = index,
                        lastPosition = enabled.lastIndex,
                        hasWeekRoutine = hasWeekRoutine,
                        photoPath = photoPath,
                        weeklyGoal = weeklyGoal,
                        photoError = photoError,
                        onRemove = { onRemove(widget) },
                        onMove = { delta -> onMove(widget, delta) },
                        onChoosePhoto = onChoosePhoto,
                        onRemovePhoto = onRemovePhoto,
                        onGoalChange = onGoalChange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        WidgetSectionHeader(
            title = "AVAILABLE",
            count = available.size,
            accent = MintGreen
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (available.isEmpty()) {
            Text(
                text = "Every widget is already on your Home.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { widget ->
                    AvailableWidgetCard(
                        widget = widget,
                        onAdd = { onAdd(widget) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tip: you can also long-press any card on Home to drag it into place. " +
                "Removing a card never deletes the data it shows.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun WidgetSectionHeader(title: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun EnabledWidgetCard(
    widget: HomeWidget,
    position: Int,
    lastPosition: Int,
    hasWeekRoutine: Boolean,
    photoPath: String,
    weeklyGoal: Int,
    photoError: String?,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onGoalChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("widget_row_${widget.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                WidgetBadge(widget)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = widget.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = widget.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (widget == HomeWidget.WEEK && !hasWeekRoutine) {
                        Text(
                            text = "Appears once you schedule a routine.",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldenAmber
                        )
                    }
                    if (widget == HomeWidget.PHOTO) {
                        Text(
                            text = if (WidgetPhotoStore.isUsable(photoPath)) {
                                "Photo selected"
                            } else {
                                "No photo selected"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (WidgetPhotoStore.isUsable(photoPath)) MintGreen else TextMuted
                        )
                    }
                    if (widget == HomeWidget.GOAL) {
                        Text(
                            text = "Target: ${weeklyGoal.coerceIn(1, 7)} workouts per week",
                            style = MaterialTheme.typography.labelSmall,
                            color = MintGreen
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("widget_remove_${widget.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove ${widget.title}",
                        tint = FireRed.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onMove(-1) },
                    enabled = position > 0,
                    modifier = Modifier.testTag("widget_move_up_${widget.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Up", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = { onMove(1) },
                    enabled = position < lastPosition,
                    modifier = Modifier.testTag("widget_move_down_${widget.id}")
                ) {
                    Text("Down", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (widget == HomeWidget.PHOTO) {
                HorizontalDivider(color = DarkBorder)
                PhotoWidgetControls(
                    photoPath = photoPath,
                    photoError = photoError,
                    onChoosePhoto = onChoosePhoto,
                    onRemovePhoto = onRemovePhoto
                )
            }

            if (widget == HomeWidget.GOAL) {
                HorizontalDivider(color = DarkBorder)
                Text(
                    text = "WEEKLY TARGET",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MintGreen
                )
                WeeklyGoalStepper(goal = weeklyGoal, onGoalChange = onGoalChange)
                Text(
                    text = "Your Home card counts finished workouts this week against this target.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun PhotoWidgetControls(
    photoPath: String,
    photoError: String?,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "PHOTO",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ElectricCyan
        )
        if (WidgetPhotoStore.isUsable(photoPath)) {
            SubcomposeAsyncImage(
                model = File(photoPath),
                contentDescription = "Selected motivation photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("widget_photo_preview"),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading photo", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Photo unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = FireRed
                        )
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onChoosePhoto,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("widget_photo_replace")
                ) {
                    Text("Replace photo", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onRemovePhoto,
                    modifier = Modifier.testTag("widget_photo_remove")
                ) {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = FireRed
                    )
                }
            }
        } else {
            Text(
                text = "Choose a picture from your gallery. IronPulse keeps its own copy, so " +
                    "deleting the original later does not remove it from Home.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Button(
                onClick = onChoosePhoto,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = DarkBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("widget_photo_choose")
            ) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Choose photo",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkBg
                )
            }
        }
        if (photoError != null) {
            Text(
                text = photoError,
                style = MaterialTheme.typography.bodySmall,
                color = FireRed,
                modifier = Modifier.testTag("widget_photo_error")
            )
        }
    }
}

@Composable
private fun AvailableWidgetCard(widget: HomeWidget, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("widget_available_${widget.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetBadge(widget)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = widget.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Text(
                    text = widget.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan.copy(alpha = 0.14f),
                    contentColor = ElectricCyan
                ),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("widget_add_${widget.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan
                )
            }
        }
    }
}

@Composable
private fun WidgetBadge(widget: HomeWidget) {
    val accent = widgetAccent(widget)
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = widgetIcon(widget),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(19.dp)
        )
    }
}

private fun widgetIcon(widget: HomeWidget): ImageVector = when (widget) {
    HomeWidget.TODAY -> Icons.Default.FitnessCenter
    HomeWidget.ACTIONS -> Icons.Default.Bolt
    HomeWidget.WEEK -> Icons.Default.CalendarToday
    HomeWidget.STATS -> Icons.Default.ShowChart
    HomeWidget.HEATMAP -> Icons.Default.GridView
    HomeWidget.WEIGHT -> Icons.Default.MonitorWeight
    HomeWidget.RECENT -> Icons.Default.History
    HomeWidget.QUOTE -> Icons.Default.FormatQuote
    HomeWidget.PHOTO -> Icons.Default.Photo
    HomeWidget.GOAL -> Icons.Default.Flag
}

private fun widgetAccent(widget: HomeWidget): Color = when (widget) {
    HomeWidget.ACTIONS, HomeWidget.HEATMAP, HomeWidget.GOAL -> MintGreen
    HomeWidget.WEEK, HomeWidget.WEIGHT -> GoldenAmber
    else -> ElectricCyan
}
