package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CustomRoutinePromptState
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.ui.components.RoutineExerciseListDisplay
import com.example.ui.components.SelectRoutineDayDialog
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel

private data class PromptRoutineExample(
    val title: String,
    val prompt: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptWorkoutRoutineDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToSavedRoutines: () -> Unit,
    onStartWorkoutDirectly: () -> Unit
) {
    val promptState by viewModel.customRoutinePromptState.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    var routineNameInput by remember { mutableStateOf("") }
    var promptTextInput by remember { mutableStateOf("") }
    var showDayPickerForRoutine by remember { mutableStateOf<SavedRoutine?>(null) }

    val examplePrompts = listOf(
        PromptRoutineExample(
            title = "4-Day Upper/Lower Split",
            prompt = "4-day Upper/Lower split with weighted dips & pull-ups"
        ),
        PromptRoutineExample(
            title = "3-Day Full Body Hypertrophy",
            prompt = "3-day full body hypertrophy with dumbbells & cables"
        ),
        PromptRoutineExample(
            title = "Calisthenics Strength Mastery",
            prompt = "Calisthenics strength routine for muscle-up & front lever"
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                    .testTag("prompt_routine_dialog_surface"),
                color = DarkSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Prompt for a Workout Routine",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Text(
                                    text = "Generate a custom routine with Gemini 3.5 Flash Lite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_prompt_routine_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
                            )
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Instruction / Info Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 Tell Gemini AI what you need:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Describe your training goals, preferred schedule (e.g. 3 or 4-day split), target muscle groups, or available equipment. Gemini AI will craft a structured routine using your local exercise catalog (and dynamically add any missing exercises)!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Quick Example Chips
                        Column {
                            Text(
                                text = "TAP TO TRY EXAMPLES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                examplePrompts.forEach { example ->
                                    Box(
                                        modifier = Modifier
                                            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                promptTextInput = example.prompt
                                                routineNameInput = example.title
                                                viewModel.resetCustomRoutinePromptState()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = example.title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = ElectricCyan,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Routine Name (Optional)
                        OutlinedTextField(
                            value = routineNameInput,
                            onValueChange = { routineNameInput = it },
                            label = { Text("Routine Name (Optional)") },
                            placeholder = { Text("e.g. 4-Day Power Hypertrophy") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedLabelColor = ElectricCyan,
                                unfocusedLabelColor = TextMuted,
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prompt_routine_name_input")
                        )

                        // User Prompt Text Field
                        Column {
                            Text(
                                text = "YOUR WORKOUT REQUEST",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = promptTextInput,
                                onValueChange = {
                                    promptTextInput = it
                                    if (promptState is CustomRoutinePromptState.Error) {
                                        viewModel.resetCustomRoutinePromptState()
                                    }
                                },
                                placeholder = {
                                    Text(
                                        "e.g. I want a 4-day upper/lower split focusing on weighted dips, pull-ups, and squat strength with gymnastic rings accessories...",
                                        color = TextMuted.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                },
                                minLines = 4,
                                maxLines = 8,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_routine_text_input")
                            )
                        }

                        // Error Banner (when prompt is gibberish or too vague)
                        AnimatedVisibility(
                            visible = promptState is CustomRoutinePromptState.Error,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            val errorState = promptState as? CustomRoutinePromptState.Error
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_routine_error_banner"),
                                colors = CardDefaults.cardColors(containerColor = FireRed.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FireRed)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = FireRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Cannot Generate Routine",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = FireRed
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = errorState?.reason ?: "Please enter a specific workout request.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextWhite,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tip: Specify your goals, target muscle groups, days per week, or preferred exercises.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Success Card & Formatted Workout
                        AnimatedVisibility(
                            visible = promptState is CustomRoutinePromptState.Success,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            val successState = promptState as? CustomRoutinePromptState.Success
                            if (successState != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("prompt_routine_success_card"),
                                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f, fill = false),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MintGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Routine Generated!",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MintGreen,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Save Action Button
                                            Button(
                                                onClick = {
                                                    viewModel.saveCustomPromptRoutine(successState.routine)
                                                },
                                                enabled = !successState.isSaved,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (successState.isSaved) DarkSurfaceVariant else MintGreen,
                                                    contentColor = if (successState.isSaved) MintGreen else DarkBg,
                                                    disabledContainerColor = DarkSurfaceVariant,
                                                    disabledContentColor = MintGreen
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("save_prompt_routine_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (successState.isSaved) Icons.Default.BookmarkAdded else Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (successState.isSaved) "Saved" else "Save",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "'${successState.routine.name}' • ${successState.exerciseCount} exercises • ${successState.routine.daysPerWeek} training day${if (successState.routine.daysPerWeek > 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextWhite
                                        )

                                        if (successState.isSaved) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MintGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, MintGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.BookmarkAdded,
                                                        contentDescription = null,
                                                        tint = MintGreen,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Routine saved to 'Saved Routines' tab! You can 1-click launch it at any time.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MintGreen
                                                    )
                                                }
                                            }
                                        }

                                        // Dynamic exercises created badge
                                        if (successState.newExercisesCreated.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .padding(10.dp)
                                            ) {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = ElectricCyan,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Added ${successState.newExercisesCreated.size} New Exercise(s) to Local Database:",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = ElectricCyan
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    successState.newExercisesCreated.forEach { ex ->
                                                        Text(
                                                            text = "• ${ex.name} (${ex.category} • ${ex.equipment})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextWhite
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        val parsedDays = remember(successState.routine.exercisesJson, allExercises) {
                                            RoutineParser.parseDays(successState.routine.exercisesJson, allExercises)
                                        }
                                        var isPreviewExpanded by remember { mutableStateOf(true) }

                                        RoutineExerciseListDisplay(
                                            routine = successState.routine,
                                            allExercises = allExercises,
                                            isExpanded = isPreviewExpanded,
                                            onToggleExpand = { isPreviewExpanded = !isPreviewExpanded }
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Start Workout Button (1-click launchable)
                                        Button(
                                            onClick = {
                                                if (!successState.isSaved) {
                                                    viewModel.saveCustomPromptRoutine(successState.routine) { savedId ->
                                                        val savedRoutine = successState.routine.copy(id = savedId)
                                                        if (parsedDays.size > 1) {
                                                            showDayPickerForRoutine = savedRoutine
                                                        } else {
                                                            viewModel.startRoutineWorkout(savedRoutine, targetDayNumber = 1)
                                                            onStartWorkoutDirectly()
                                                        }
                                                    }
                                                } else {
                                                    if (parsedDays.size > 1) {
                                                        showDayPickerForRoutine = successState.routine
                                                    } else {
                                                        viewModel.startRoutineWorkout(successState.routine, targetDayNumber = 1)
                                                        onStartWorkoutDirectly()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(46.dp)
                                                .testTag("start_prompt_routine_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = DarkBg,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Start Workout Now",
                                                color = DarkBg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (successState.isSaved) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = onNavigateToSavedRoutines,
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                                    .testTag("view_saved_routines_from_prompt_button")
                                            ) {
                                                Text(
                                                    text = "Go to Saved Routines Tab",
                                                    color = ElectricCyan,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                    // Footer Bottom Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }

                        Button(
                            onClick = {
                                viewModel.generateRoutineFromCustomPrompt(
                                    userPrompt = promptTextInput,
                                    customName = routineNameInput
                                )
                            },
                            enabled = promptTextInput.isNotBlank() && promptState !is CustomRoutinePromptState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp)
                                .testTag("generate_prompt_routine_button")
                        ) {
                            if (promptState is CustomRoutinePromptState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = DarkBg,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating Routine...", color = DarkBg, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = DarkBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Routine", color = DarkBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showDayPickerForRoutine != null) {
            val pickerDays = remember(showDayPickerForRoutine, allExercises) {
                RoutineParser.parseDays(showDayPickerForRoutine!!.exercisesJson, allExercises)
            }
            SelectRoutineDayDialog(
                routineName = showDayPickerForRoutine!!.name,
                days = pickerDays,
                onSelectDay = { day ->
                    val r = showDayPickerForRoutine!!
                    viewModel.startRoutineWorkout(r, targetDayNumber = day.dayNumber)
                    showDayPickerForRoutine = null
                    onStartWorkoutDirectly()
                },
                onDismiss = { showDayPickerForRoutine = null }
            )
        }
    }
}
