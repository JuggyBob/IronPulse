package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.OnAccent
import com.example.util.WidgetPhotoStore
import java.io.File

/**
 * The photo widget: one app-private picture filling a 16:9 card.
 *
 * The card never assumes the file is still there. A missing, deleted or unreadable copy shows a
 * readable placeholder and a way to choose another picture instead of a broken image or a crash.
 * Both the empty and the failed state lead to the same picker action, so the athlete is never
 * stuck with a card they cannot fix.
 */
@Composable
fun HomePhotoWidget(
    photoPath: String,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_photo_widget"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (WidgetPhotoStore.isUsable(photoPath)) {
                SubcomposeAsyncImage(
                    model = File(photoPath),
                    contentDescription = "Motivation photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_photo_image"),
                    loading = {
                        PhotoPlaceholder(
                            title = "Loading photo",
                            message = "Reading your picture.",
                            actionLabel = null,
                            onAction = onChoosePhoto
                        )
                    },
                    error = {
                        PhotoPlaceholder(
                            title = "Photo unavailable",
                            message = "The picture could not be opened. Choose another one.",
                            actionLabel = "Choose another",
                            onAction = onChoosePhoto,
                            tag = "home_photo_unavailable"
                        )
                    },
                    success = {
                        // The image content belongs to this scope; the Box below opens a new one,
                        // so the scope is kept explicitly rather than lost to the nested lambda.
                        val imageScope = this
                        Box(modifier = Modifier.fillMaxSize()) {
                            with(imageScope) { SubcomposeAsyncImageContent() }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "MOTIVATION",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = "Your photo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                TextButton(
                                    onClick = onChoosePhoto,
                                    modifier = Modifier.testTag("home_photo_replace_button")
                                ) {
                                    Text(
                                        text = "Replace",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = TextWhite
                                    )
                                }
                            }
                        }
                    }
                )
            } else {
                PhotoPlaceholder(
                    title = "Your photo",
                    message = "Add a picture from your gallery to make this card your own.",
                    actionLabel = "Choose photo",
                    onAction = onChoosePhoto,
                    tag = "home_photo_empty"
                )
            }
        }
    }
}

@Composable
private fun PhotoPlaceholder(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    tag: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurfaceVariant)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(ElectricCyan.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("home_photo_choose_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnAccent
                    )
                }
            }
        }
    }
}

/**
 * The weekly goal widget: the athlete's own target against this week's finished workouts.
 *
 * The number of workouts is the same [com.example.data.model.HomeDashboard.WeekTotals] count the
 * training numbers use, so the two cards can never disagree about how many sessions this week had.
 */
@Composable
fun HomeWeeklyGoalWidget(
    workouts: Int,
    goal: Int,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeGoal = goal.coerceIn(1, 7)
    val progress = (workouts.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val remaining = (safeGoal - workouts).coerceAtLeast(0)
    val reached = workouts >= safeGoal

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_weekly_goal"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MintGreen.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = MintGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WEEKLY GOAL",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MintGreen
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onCustomize,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("home_goal_customize")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Customize weekly goal",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = workouts.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Text(
                    text = " / $safeGoal",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (workouts == 1) "workout" else "workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .testTag("home_goal_progress"),
                color = if (reached) MintGreen else ElectricCyan,
                trackColor = DarkSurfaceVariant
            )

            Text(
                text = if (reached) {
                    "Goal reached. Every workout from here is a bonus."
                } else {
                    "$remaining ${if (remaining == 1) "workout" else "workouts"} to go this week."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (reached) MintGreen else TextMuted,
                modifier = Modifier.testTag("home_goal_progress_text")
            )
        }
    }
}

/** The page the athlete sees when every widget has been removed. */
@Composable
fun HomeWidgetsEmptyState(
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_widgets_empty_state"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(ElectricCyan.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Your Home is empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
            Text(
                text = "Add the cards you want to see. You can change them again at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onOpenManager,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OnAccent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("home_widgets_empty_add")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add widgets",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnAccent
                )
            }
        }
    }
}

/**
 * The 1 to 7 workout stepper shared by the widget manager and the goal card's dialog.
 *
 * Two explicit buttons rather than a slider: the target is seven discrete choices, and a button
 * announces exactly what it does to a screen reader.
 */
@Composable
internal fun WeeklyGoalStepper(
    goal: Int,
    onGoalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeGoal = goal.coerceIn(1, 7)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Workouts per week",
            style = MaterialTheme.typography.bodySmall,
            color = TextWhite,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onGoalChange((safeGoal - 1).coerceAtLeast(1)) },
            enabled = safeGoal > 1,
            modifier = Modifier
                .size(40.dp)
                .testTag("weekly_goal_decrease")
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease weekly goal",
                tint = if (safeGoal > 1) ElectricCyan else TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = safeGoal.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = ElectricCyan,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 28.dp)
                .testTag("weekly_goal_value")
        )
        IconButton(
            onClick = { onGoalChange((safeGoal + 1).coerceAtMost(7)) },
            enabled = safeGoal < 7,
            modifier = Modifier
                .size(40.dp)
                .testTag("weekly_goal_increase")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase weekly goal",
                tint = if (safeGoal < 7) ElectricCyan else TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/** The goal card's own customization dialog, opened from its tune button. */
@Composable
fun WeeklyGoalDialog(
    goal: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var draft by remember(goal) { mutableStateOf(goal.coerceIn(1, 7)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        modifier = Modifier.testTag("weekly_goal_dialog"),
        title = {
            Text(
                text = "Weekly Goal",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "How many workouts do you want to complete each week?",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                WeeklyGoalStepper(goal = draft, onGoalChange = { draft = it })
                Text(
                    text = "The Home card counts finished workouts this week against this target.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OnAccent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("weekly_goal_save")
            ) {
                Text("Save", color = OnAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("weekly_goal_cancel")
            ) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
