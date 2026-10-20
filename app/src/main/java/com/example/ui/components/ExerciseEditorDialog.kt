package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.seed.toJsonArray
import com.example.ui.components.AnimatedDropdownField
import com.example.data.model.MuscleCategory
import com.example.data.model.MusclePart
import com.example.data.model.MusclePartResolver
import com.example.ui.theme.AiBlue
import com.example.ui.theme.CoralOrange
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
import com.example.ui.theme.VioletPurple

/**
 * The low level editor for a single exercise.
 *
 * Reached from the pen on the exercise information page, and deliberately buried there rather than
 * offered in a list: this is repair work, not routine maintenance, and a pen in the detail view keeps
 * it out of the way until someone goes looking for it.
 *
 * It edits every field that decides what the app asks for when logging, and that is the point. The
 * reported problem was an "explosive pull-up" typed as bodyweight, which made the app ask for hold
 * seconds instead of weight and reps. That is one wrong `exerciseType`, and it cannot be fixed by a
 * partial editor: the type, the muscle group, the target muscle and the equipment all have to be
 * settable together, or the athlete can only correct half of a wrong row.
 *
 * The same editor is used for the library, for the athlete's own exercises and for the ones the AI
 * registered. There is no separate "edit yours" screen any more, which is what made the three drift.
 *
 * @param onSave called with the fully edited exercise. Saving is the caller's job because it knows
 *   which of the two write paths to take.
 * @param onAttachMedia opens the gallery picker, or null when this exercise cannot own media.
 * @param onRemoveMedia clears an attached photo or video, or null when there is nothing to clear.
 */
@Composable
fun ExerciseEditorDialog(
    exercise: Exercise,
    onSave: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    onAttachMedia: (() -> Unit)? = null,
    onRemoveMedia: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(exercise.name) }
    var category by remember { mutableStateOf(exercise.category) }
    var type by remember { mutableStateOf(exercise.exerciseType) }
    var equipment by remember { mutableStateOf(exercise.equipment) }
    var bodyPart by remember { mutableStateOf(exercise.bodyPart) }
    var target by remember { mutableStateOf(exercise.target) }
    var secondary by remember { mutableStateOf(exercise.secondaryMuscleList) }
    var secondaryInput by remember { mutableStateOf(exercise.secondaryMuscleList.joinToString(", ")) }
    var guidance by remember {
        mutableStateOf(exercise.instructions.ifBlank { exercise.description })
    }

    // The library row that was edited, so the warning can name what is about to change.
    val isLibrary = !exercise.isCustom && !exercise.isAiGenerated
    // Menu state for the four dropdowns, kept beside the values they drive so the pairing is
    // obvious when reading the editor.
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var equipmentMenuExpanded by remember { mutableStateOf(false) }
    var bodyPartMenuExpanded by remember { mutableStateOf(false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }


    var confirmLibraryEdit by remember { mutableStateOf(false) }

    // Set once the change has been written. The dialog stays open and says so, instead of
    // vanishing the instant the athlete presses Save: a dialog that disappears gives no proof the
    // change landed, and this one holds fields the athlete typed.
    var saved by remember { mutableStateOf(false) }

    // The fields that follow from the type, so the athlete sees the consequence of the change while
    // they are making it rather than after they have saved.
    val previewFields = remember(type) { MusclePartResolver.trackedFields(type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Edit exercise",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = if (isLibrary) {
                            "IronPulse library exercise"
                        } else {
                            "Saved in your local database"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLibrary) FireRed else TextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---------------- Name ----------------
                EditorField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    testTag = "exercise_editor_name"
                )

                // ---------------- Muscle group tag ----------------
                // A dropdown, like the one on the create and edit screens. The athlete already
                // knows this control; a chip grid here meant two different ways to pick one value.
                AnimatedDropdownField(
                    label = "Muscle Group Tag *",
                    value = category,
                    icon = Icons.Default.FitnessCenter,
                    accent = VioletPurple,
                    options = MuscleCategory.ALL,
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    onOptionSelected = { category = it },
                    fieldTestTag = "exercise_editor_category_dropdown",
                    optionTestTagPrefix = "exercise_editor_category_option"
                )

                // ---------------- Exercise type ----------------
                // The whole reason this screen exists: the type decides which fields the logging
                // screen offers, and a wrong one is what made a run ask for barbell weight.
                EditorSectionLabel("EXERCISE TYPE")
                EditorSectionHint("This chooses the fields you log: ${previewFields.joinToString(" • ")}")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseType.ALL.forEach { option ->
                        val isSelected = type == option
                        val accent = when (option) {
                            ExerciseType.ADDED_WEIGHT -> CoralOrange
                            ExerciseType.PURE_BODYWEIGHT -> MintGreen
                            ExerciseType.CARDIO -> GoldenAmber
                            else -> ElectricCyan
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) accent.copy(alpha = 0.15f) else DarkBg,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) accent else DarkBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { type = option }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("exercise_editor_type_$option"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exerciseTypeLabel(option),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) accent else TextWhite
                                )
                                Text(
                                    text = exerciseTypeFieldSummary(option),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // ---------------- Equipment ----------------
                // A dropdown, matching the create screen. Free text made it far too easy to type a
                // near duplicate of an existing equipment tag, which then split the equipment filter.
                AnimatedDropdownField(
                    label = "Equipment *",
                    value = equipment,
                    icon = Icons.Default.Build,
                    accent = GoldenAmber,
                    options = EDITOR_EQUIPMENT,
                    expanded = equipmentMenuExpanded,
                    onExpandedChange = { equipmentMenuExpanded = it },
                    onOptionSelected = { equipment = it },
                    fieldTestTag = "exercise_editor_equipment_dropdown",
                    optionTestTagPrefix = "exercise_editor_equipment_option"
                )

                // ---------------- Body part ----------------
                // The dataset's own body part vocabulary, offered as a dropdown for the same reason
                // as the muscle group: it is a closed set, and a closed set belongs in a picker.
                AnimatedDropdownField(
                    label = "Body Part *",
                    value = bodyPart,
                    icon = Icons.Default.AccessibilityNew,
                    accent = GoldenAmber,
                    options = DATASET_BODY_PARTS,
                    expanded = bodyPartMenuExpanded,
                    onExpandedChange = { bodyPartMenuExpanded = it },
                    onOptionSelected = { bodyPart = it },
                    fieldTestTag = "exercise_editor_body_part_dropdown",
                    optionTestTagPrefix = "exercise_editor_body_part_option"
                )

                // ---------------- Target muscle ----------------
                // The dataset's muscle vocabulary. It is a closed list in the asset, so a picker is
                // both more accurate and faster than typing the same muscle three different ways.
                AnimatedDropdownField(
                    label = "Primary muscle worked *",
                    value = target,
                    icon = Icons.Default.Search,
                    accent = CoralOrange,
                    options = DATASET_TARGET_MUSCLES,
                    expanded = targetMenuExpanded,
                    onExpandedChange = { targetMenuExpanded = it },
                    onOptionSelected = { target = it },
                    fieldTestTag = "exercise_editor_target_dropdown",
                    optionTestTagPrefix = "exercise_editor_target_option"
                )

                // ---------------- Secondary muscles ----------------
                EditorSectionLabel("SECONDARY MUSCLES")
                EditorField(
                    value = secondaryInput,
                    onValueChange = { input ->
                        secondaryInput = input
                        secondary = input.split(',', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                    },
                    label = "Comma separated",
                    placeholder = "e.g. Triceps, Shoulders",
                    testTag = "exercise_editor_secondary"
                )

                // ---------------- Instructions ----------------
                EditorSectionLabel("INSTRUCTIONS")
                EditorField(
                    value = guidance,
                    onValueChange = { guidance = it },
                    label = "How it is performed",
                    minLines = 3,
                    singleLine = false,
                    placeholder = "Step by step, as the AI coach would read it back to you",
                    testTag = "exercise_editor_instructions"
                )

                // ---------------- Media ----------------
                if (onAttachMedia != null) {
                    EditorSectionLabel("YOUR MEDIA")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg, RoundedCornerShape(10.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = if (exercise.customMediaUri.isNotBlank()) ElectricCyan else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (exercise.customMediaUri.isBlank()) {
                                "No photo or video attached"
                            } else {
                                "Your photo or video is shown instead of the built-in animation"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onAttachMedia,
                            modifier = Modifier.testTag("exercise_editor_attach_media")
                        ) {
                            Text(
                                text = if (exercise.customMediaUri.isBlank()) "Add" else "Replace",
                                color = ElectricCyan
                            )
                        }
                        if (onRemoveMedia != null && exercise.customMediaUri.isNotBlank()) {
                            TextButton(
                                onClick = onRemoveMedia,
                                modifier = Modifier.testTag("exercise_editor_remove_media")
                            ) {
                                Text("Remove", color = FireRed)
                            }
                        }
                    }
                }

                // ---------------- Dataset keys (read only) ----------------
                // Shown so the low level editing is complete without being dangerous: these two tie the
                // row to the dataset record, and editing them would break the next catalog reseed.
                if (exercise.sourceId.isNotBlank() || exercise.mediaPath.isNotBlank()) {
                    EditorSectionLabel("DATASET KEYS (NOT EDITABLE)")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReadOnlyKeyRow("Dataset record", exercise.sourceId.ifBlank { "none" })
                        ReadOnlyKeyRow("Animation file", exercise.mediaPath.ifBlank { "none" })
                    }
                }
            }
        },
        confirmButton = {
            if (saved) {
                SaveConfirmation(modifier = Modifier.testTag("exercise_editor_saved_confirmation"))
            } else {
                Button(
                    onClick = {
                        // Editing the shipped library is a deliberate act, so it asks first. Everything
                        // else saves straight away: the athlete owns those rows already.
                        if (isLibrary) {
                            confirmLibraryEdit = true
                        } else {
                            onSave(buildEditedExercise(exercise, name, category, type, equipment, bodyPart, target, secondary, guidance))
                            saved = true
                        }
                    },
                    enabled = name.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLibrary) FireRed else MintGreen,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("exercise_editor_save_button")
                ) {
                    Text(
                        text = "Save",
                        color = if (name.trim().length >= 2) DarkBg else TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (saved) "Close" else "Cancel", color = TextMuted)
            }
        }
    )

    if (confirmLibraryEdit) {
        LibraryEditWarningDialog(
            exerciseName = exercise.name,
            onConfirm = {
                confirmLibraryEdit = false
                onSave(
                    buildEditedExercise(
                        exercise, name, category, type, equipment, bodyPart, target, secondary, guidance
                    )
                )
                saved = true
            },
            onDismiss = { confirmLibraryEdit = false }
        )
    }
}

/**
 * The confirmation that the exercise was saved.
 *
 * A circle springs in and the checkmark lands in it a moment later, so the athlete sees the change
 * land rather than being told it did. The dialog stays open underneath it: the fields they typed
 * are still there, and closing is their call.
 */
@Composable
private fun SaveConfirmation(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val circleScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.25f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "saved_circle_scale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 200, delayMillis = 130),
        label = "saved_check_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = circleScale
                    scaleY = circleScale
                }
                .background(MintGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = DarkBg,
                modifier = Modifier
                    .size(15.dp)
                    .alpha(checkAlpha)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Saved",
            color = MintGreen,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The confirmation before a library row is written.
 *
 * Red, and specific about what changes, because the athlete is about to overwrite data that shipped
 * with the app. Naming the fields they have changed rather than a generic warning means they can catch
 * a wrong tap before it lands, and the revert in Settings is the way back if they miss it.
 */
@Composable
private fun LibraryEditWarningDialog(
    exerciseName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FireRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Edit the shipped library?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "\"$exerciseName\" is part of the IronPulse exercise library, not an " +
                        "exercise you created.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(10.dp))
                BulletRow("Your change overwrites what the app shipped with.")
                BulletRow("Logging will use the new fields from now on.")
                BulletRow(
                    "Sets already logged keep the fields they were recorded under, so your history " +
                        "does not change."
                )
                BulletRow(
                    "Settings has \"Revert to original database\" if you want the shipped row back."
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Edit it only when the library really is wrong.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = FireRed
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_library_edit_button")
            ) {
                Text("Edit it anyway", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_library_edit_button")
            ) {
                Text("Keep the original", color = TextMuted)
            }
        }
    )
}

/** Builds the exercise to save, carrying over every field the editor does not expose. */
private fun buildEditedExercise(
    original: Exercise,
    name: String,
    category: String,
    type: String,
    equipment: String,
    bodyPart: String,
    target: String,
    secondary: List<String>,
    guidance: String
): Exercise = original.copy(
    name = name.trim(),
    category = category,
    exerciseType = type,
    equipment = equipment.trim().ifBlank { "Bodyweight" },
    bodyPart = bodyPart.trim(),
    target = target.trim(),
    secondaryMuscles = secondary.toJsonArray(),
    // The dataset rows carry their coaching text in `instructions`; the user's own rows only ever had
    // the free text `description` box. Writing both keeps whichever one a reader prefers populated,
    // so the detail page shows the same text whichever field it looks at first.
    instructions = guidance.trim(),
    description = guidance.trim()
)

/** The human label for an exercise type, shared with the detail page. */
internal fun exerciseTypeLabel(type: String): String = when (type) {
    ExerciseType.ADDED_WEIGHT -> "Added weight"
    ExerciseType.PURE_BODYWEIGHT -> "Bodyweight"
    ExerciseType.CARDIO -> "Cardio"
    else -> "Standard weight"
}

/** What this type logs, so the consequence of the choice is visible before saving. */
private fun exerciseTypeFieldSummary(type: String): String = when (type) {
    ExerciseType.ADDED_WEIGHT -> "Sets • Reps • Added weight (+kg / +lbs)"
    ExerciseType.PURE_BODYWEIGHT -> "Sets • Reps • Hold (seconds)"
    ExerciseType.CARDIO -> "Sets • Time (minutes) • Distance"
    else -> "Sets • Reps • Weight"
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = TextMuted
    )
}

@Composable
private fun EditorSectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ElectricCyan
    )
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    placeholder: String = "",
    minLines: Int = 1,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isBlank()) {
            null
        } else {
            { Text(placeholder, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        },
        minLines = minLines,
        maxLines = maxOf(minLines, 6),
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricCyan,
            unfocusedBorderColor = DarkBorder,
            focusedLabelColor = ElectricCyan,
            unfocusedLabelColor = TextMuted,
            cursorColor = ElectricCyan,
            focusedContainerColor = DarkBg,
            unfocusedContainerColor = DarkBg,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}


@Composable
private fun ReadOnlyKeyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBg)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(FireRed)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextWhite.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The equipment options the editor offers.
 *
 * The same closed list the create screen uses, so the two never disagree.
 */
private val EDITOR_EQUIPMENT = listOf(
    "Bodyweight", "Barbell", "Dumbbells", "Kettlebell", "Cable", "Machine",
    "Lever", "Smith Machine", "E-Z Bar", "Trap Bar", "Band", "Rings",
    "Pull-up Bar", "Dip Bar", "Bench", "Stability Ball", "Foam Roller", "Mat",
    "Treadmill", "Stationary Bike", "Elliptical Machine", "Rowing Machine",
    "Jump Rope", "Sled", "Tire", "Other"
)

/**
 * The dataset's own target muscle vocabulary, so the editor and the catalog agree on spelling.
 */
private val DATASET_TARGET_MUSCLES = listOf(
    "Pectorals", "Lats", "Upper Back", "Lower Back", "Trapezius", "Deltoids",
    "Rotator Cuff", "Biceps", "Triceps", "Forearms", "Abdominals", "Obliques",
    "Serratus Anterior", "Hip Flexors", "Adductors", "Abductors", "Glutes",
    "Hamstrings", "Quadriceps", "Calves", "Soleus", "Tibialis", "Ankles",
    "Neck", "Cardiovascular System", "Full Body"
)

/** The body part vocabulary the dataset itself uses, offered so the editor stays consistent with it. */
private val DATASET_BODY_PARTS = listOf(
    "chest", "back", "shoulders", "upper arms", "lower arms",
    "waist", "upper legs", "lower legs", "cardio", "neck"
)
