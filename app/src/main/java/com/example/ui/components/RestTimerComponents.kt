package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.data.model.ActiveRestTimerState
import com.example.data.model.CustomRestTimer
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt

/**
 * Docked button on the lower left (right on top of the number of sets completed).
 * Can be collapsed/hidden with an arrow on its right side so it slides into the app's border.
 * Formatted compactly so it never overlaps with the Exit/Finish buttons on the lower right.
 */
@Composable
fun RestTimerDockedButton(
    viewModel: MainViewModel,
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTimer by viewModel.activeRestTimer.collectAsStateWithLifecycle()
    var isHiddenInBorder by rememberSaveable { mutableStateOf(false) }

    // Subtle pulsing animation when timer is active
    val infiniteTransition = rememberInfiniteTransition(label = "timer_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !isHiddenInBorder,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            Surface(
                color = when {
                    activeTimer.hasFinished -> ElectricCyan.copy(alpha = 0.2f)
                    activeTimer.isRunning -> MintGreen.copy(alpha = 0.16f)
                    else -> DarkSurface
                },
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 0.dp, bottomStart = 0.dp),
                border = BorderStroke(
                    1.dp,
                    when {
                        activeTimer.hasFinished -> ElectricCyan
                        activeTimer.isRunning -> MintGreen.copy(alpha = pulseAlpha)
                        else -> DarkBorder
                    }
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .widthIn(max = 165.dp)
                    .height(34.dp)
                    .testTag("rest_timer_docked_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                ) {
                    // Clickable timer display area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onOpenDialog() }
                    ) {
                        Icon(
                            imageVector = when {
                                activeTimer.hasFinished -> Icons.Default.Vibration
                                activeTimer.isRunning -> Icons.Default.Timer
                                else -> Icons.Default.Timer
                            },
                            contentDescription = null,
                            tint = when {
                                activeTimer.hasFinished -> ElectricCyan
                                activeTimer.isRunning -> MintGreen
                                else -> TextMuted
                            },
                            modifier = Modifier.size(15.dp)
                        )

                        Text(
                            text = when {
                                activeTimer.hasFinished -> "Done! 📳"
                                activeTimer.isRunning -> activeTimer.formattedRemaining
                                else -> "Rest Timer"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (activeTimer.isRunning) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = when {
                                activeTimer.hasFinished -> ElectricCyan
                                activeTimer.isRunning -> MintGreen
                                else -> TextWhite
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider before the hide arrow
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(DarkBorder)
                    )

                    // Arrow button on right side to hide the timer into the border
                    IconButton(
                        onClick = { isHiddenInBorder = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("hide_rest_timer_arrow")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Hide timer into border",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Tucked tab in the border when hidden
        AnimatedVisibility(
            visible = isHiddenInBorder,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            Surface(
                color = when {
                    activeTimer.hasFinished -> ElectricCyan.copy(alpha = 0.25f)
                    activeTimer.isRunning -> MintGreen.copy(alpha = 0.2f)
                    else -> DarkSurfaceVariant
                },
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp, topStart = 0.dp, bottomStart = 0.dp),
                border = BorderStroke(
                    1.dp,
                    when {
                        activeTimer.hasFinished -> ElectricCyan
                        activeTimer.isRunning -> MintGreen
                        else -> DarkBorder
                    }
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .height(34.dp)
                    .clickable { isHiddenInBorder = false }
                    .testTag("show_rest_timer_tab")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    if (activeTimer.isRunning || activeTimer.hasFinished) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (activeTimer.hasFinished) ElectricCyan else MintGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Expand timer",
                        tint = when {
                            activeTimer.hasFinished -> ElectricCyan
                            activeTimer.isRunning -> MintGreen
                            else -> TextWhite
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Rest Timer pop-up dialog.
 * Features sliding animations for minutes and seconds, custom named timer saving,
 * and phone vibration with a brief audio ding sound (<1s) upon completion.
 */
@Composable
fun RestTimerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val activeTimer by viewModel.activeRestTimer.collectAsStateWithLifecycle()
    val savedTimers by viewModel.savedRestTimers.collectAsStateWithLifecycle()

    val animScope = rememberCoroutineScope()
    val minutesAnim = remember { Animatable(1f) }
    val secondsAnim = remember { Animatable(30f) }
    var timerNameInput by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = New / Quick Timer, 1 = Saved Timers

    val currentMinutes = minutesAnim.value.roundToInt().coerceIn(0, 15)
    val currentSeconds = secondsAnim.value.roundToInt().coerceIn(0, 59)
    val totalSelectedSeconds = remember(currentMinutes, currentSeconds) {
        ((currentMinutes * 60) + currentSeconds).coerceAtLeast(0)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("rest_timer_popup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MintGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Workout Rest Timer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Countdown Card (if currently running, paused, or finished)
                if (activeTimer.isRunning || activeTimer.hasFinished || activeTimer.isPaused) {
                    ActiveTimerStatusCard(
                        activeTimer = activeTimer,
                        onPause = { viewModel.pauseRestTimer() },
                        onResume = { viewModel.resumeRestTimer() },
                        onReset = { viewModel.resetRestTimer() },
                        onStop = { viewModel.stopRestTimer() },
                        onAdd30s = { viewModel.addSecondsToRestTimer(30) }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = DarkSurfaceVariant,
                    contentColor = MintGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Text(
                                "Set Timer",
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Text(
                                "Saved (${savedTimers.size})",
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (activeTab == 0) {
                    // Set New Timer with Sliding Animation for Minutes and Seconds
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Digital Clock Display with smooth vertical sliding animation for digits
                        Surface(
                            color = DarkBg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    AnimatedContent(
                                        targetState = currentMinutes,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                slideInVertically { height -> height } + fadeIn() togetherWith
                                                        slideOutVertically { height -> -height } + fadeOut()
                                            } else {
                                                slideInVertically { height -> -height } + fadeIn() togetherWith
                                                        slideOutVertically { height -> height } + fadeOut()
                                            }.using(SizeTransform(clip = false))
                                        },
                                        label = "min_roll"
                                    ) { minVal ->
                                        Text(
                                            text = String.format("%02d", minVal),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                            ),
                                            color = MintGreen
                                        )
                                    }

                                    Text(
                                        text = " : ",
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        ),
                                        color = MintGreen
                                    )

                                    AnimatedContent(
                                        targetState = currentSeconds,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                slideInVertically { height -> height } + fadeIn() togetherWith
                                                        slideOutVertically { height -> -height } + fadeOut()
                                            } else {
                                                slideInVertically { height -> -height } + fadeIn() togetherWith
                                                        slideOutVertically { height -> height } + fadeOut()
                                            }.using(SizeTransform(clip = false))
                                        },
                                        label = "sec_roll"
                                    ) { secVal ->
                                        Text(
                                            text = String.format("%02d", secVal),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                            ),
                                            color = ElectricCyan
                                        )
                                    }
                                }

                                Text(
                                    text = "Rest Duration",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Minutes Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Minutes",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                AnimatedContent(
                                    targetState = currentMinutes,
                                    transitionSpec = {
                                        slideInVertically { height -> height } + fadeIn() togetherWith
                                                slideOutVertically { height -> -height } + fadeOut()
                                    },
                                    label = "min_badge"
                                ) { mVal ->
                                    Text(
                                        text = "$mVal min",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = minutesAnim.value,
                            onValueChange = { newVal ->
                                animScope.launch { minutesAnim.snapTo(newVal) }
                            },
                            valueRange = 0f..15f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = MintGreen,
                                activeTrackColor = MintGreen,
                                inactiveTrackColor = DarkBorder
                            ),
                            modifier = Modifier.testTag("rest_timer_minutes_slider")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Seconds Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Seconds",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                AnimatedContent(
                                    targetState = currentSeconds,
                                    transitionSpec = {
                                        slideInVertically { height -> height } + fadeIn() togetherWith
                                                slideOutVertically { height -> -height } + fadeOut()
                                    },
                                    label = "sec_badge"
                                ) { sVal ->
                                    Text(
                                        text = "$sVal sec",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = secondsAnim.value,
                            onValueChange = { newVal ->
                                animScope.launch { secondsAnim.snapTo(newVal) }
                            },
                            valueRange = 0f..55f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = DarkBorder
                            ),
                            modifier = Modifier.testTag("rest_timer_seconds_slider")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Preset Chips with animated sliding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "30s" to 30,
                                "60s" to 60,
                                "90s" to 90,
                                "2m" to 120,
                                "3m" to 180
                            ).forEach { (label, totalSec) ->
                                val isSelected = totalSelectedSeconds == totalSec
                                OutlinedButton(
                                    onClick = {
                                        val targetM = (totalSec / 60).toFloat()
                                        val targetS = (totalSec % 60).toFloat()
                                        animScope.launch {
                                            minutesAnim.animateTo(
                                                targetM,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                        }
                                        animScope.launch {
                                            secondsAnim.animateTo(
                                                targetS,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MintGreen else DarkBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) MintGreen.copy(alpha = 0.15f) else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MintGreen else TextWhite
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Optional Timer Name Field
                        OutlinedTextField(
                            value = timerNameInput,
                            onValueChange = { timerNameInput = it },
                            placeholder = { Text("Timer Name (Optional, e.g. Compound)", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MintGreen,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("rest_timer_name_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Start & Save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Save Button
                            OutlinedButton(
                                onClick = {
                                    val name = timerNameInput.ifBlank {
                                        "${currentMinutes}m ${currentSeconds}s Rest"
                                    }
                                    viewModel.saveCustomRestTimer(name, totalSelectedSeconds)
                                    timerNameInput = ""
                                },
                                enabled = totalSelectedSeconds > 0,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .weight(0.45f)
                                    .height(44.dp)
                                    .testTag("save_custom_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Save",
                                    color = ElectricCyan,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Start Timer Button
                            Button(
                                onClick = {
                                    if (totalSelectedSeconds > 0) {
                                        val name = timerNameInput.ifBlank { "Rest Timer" }
                                        viewModel.startRestTimer(totalSelectedSeconds, name)
                                        onDismiss()
                                    }
                                },
                                enabled = totalSelectedSeconds > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(0.55f)
                                    .height(44.dp)
                                    .testTag("start_rest_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DarkBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Start Timer",
                                    color = DarkBg,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                } else {
                    // Saved Timers List
                    if (savedTimers.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            Text(
                                text = "No saved timers yet.\nCustomize and save your preferred rest times above!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            items(savedTimers, key = { it.id }) { timer ->
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = timer.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = TextWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = timer.formattedDuration,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = ElectricCyan
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Start Button
                                            Button(
                                                onClick = {
                                                    viewModel.startRestTimer(timer.totalSeconds, timer.name)
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = DarkBg,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Start",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = DarkBg
                                                )
                                            }

                                            // Delete Button
                                            IconButton(
                                                onClick = { viewModel.deleteCustomRestTimer(timer.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = FireRed.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerStatusCard(
    activeTimer: ActiveRestTimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onStop: () -> Unit,
    onAdd30s: () -> Unit
) {
    Surface(
        color = DarkBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (activeTimer.hasFinished) ElectricCyan else MintGreen
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeTimer.timerName.isNotBlank()) activeTimer.timerName else "Resting",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted
                )

                Surface(
                    color = when {
                        activeTimer.hasFinished -> ElectricCyan.copy(alpha = 0.2f)
                        activeTimer.isPaused -> GoldenAmber.copy(alpha = 0.2f)
                        else -> MintGreen.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when {
                            activeTimer.hasFinished -> "Rest Finished! 📳"
                            activeTimer.isPaused -> "Paused"
                            else -> "Resting..."
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            activeTimer.hasFinished -> ElectricCyan
                            activeTimer.isPaused -> GoldenAmber
                            else -> MintGreen
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = activeTimer.formattedRemaining,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = when {
                    activeTimer.hasFinished -> ElectricCyan
                    activeTimer.isPaused -> GoldenAmber
                    else -> MintGreen
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { activeTimer.progressFraction },
                color = if (activeTimer.hasFinished) ElectricCyan else MintGreen,
                trackColor = DarkSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Controls Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeTimer.isPaused) {
                    OutlinedButton(
                        onClick = onResume,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = MintGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Resume", color = MintGreen, style = MaterialTheme.typography.labelSmall)
                    }
                } else if (activeTimer.isRunning) {
                    OutlinedButton(
                        onClick = onPause,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Pause, null, tint = GoldenAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Pause", color = GoldenAmber, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // +30s Button
                OutlinedButton(
                    onClick = onAdd30s,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("+30s", color = TextWhite, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }

                // Reset Button
                OutlinedButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }

                // Stop Button
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Stop, null, tint = FireRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
