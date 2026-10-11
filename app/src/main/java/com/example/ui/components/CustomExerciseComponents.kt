package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.ReservedExerciseNames
import com.example.data.model.MuscleCategory
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

/**
 * The equipment options offered when creating or editing an exercise.
 *
 * Internal rather than private because the full exercise editor in `ExerciseEditorDialog` reuses this
 * same list. Two lists would drift, and an athlete who could pick "Dumbbells" while creating would be
 * offered "Dumbbell" while editing the same exercise.
 */
internal val COMMON_EQUIPMENT = listOf(
    "Bodyweight",
    "Barbell",
    "Dumbbells",
    "Kettlebell",
    "Cable",
    "Machine",
    "Resistance Band",
    "Pull-up Bar",
    "Rings",
    "Dip Belt",
    "Smith Machine",
    "Weight Vest"
)

private val EXERCISE_TYPE_OPTIONS = listOf(
    ExerciseType.STANDARD_WEIGHT to "Standard Weight",
    ExerciseType.ADDED_WEIGHT to "Added Weight",
    ExerciseType.PURE_BODYWEIGHT to "Bodyweight",
    // Cardio is a real type since the dataset was reclassified. Without it here an athlete could not
    // create a cardio exercise at all, and the ones the parser created were typed as weight exercises
    // and asked for barbell weight on a run.
    ExerciseType.CARDIO to "Cardio"
)

/**
 * Create / edit dialog for a custom exercise stored in the local database.
 *
 * Only exercises the user owns can ever reach the edit mode: the ones they created themselves and
 * the ones the AI parser registered. The built-in IronPulse library is locked by the database layer.
 *
 * @param existingExercise the exercise being edited, or null when creating a new one. When it was
 * registered by the AI the dialog says so explicitly (light blue) instead of "your exercise".
 * @param prefillName name typed by the user in the "does not exist, let's create it" shortcut.
 * @param findSimilar returns an already stored exercise that matches [name] (fuzzy), or null.
 */
@Composable
fun CustomExerciseDialog(
    existingExercise: Exercise?,
    prefillName: String = "",
    findSimilar: (name: String) -> Exercise?,
    onSave: (
        name: String,
        category: String,
        equipment: String,
        exerciseType: String,
        description: String
    ) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Opens the gallery picker for a photo or a video of the athlete's own variation.
     *
     * Null when the exercise cannot own media, which is every catalog row. The catalog is not the
     * athlete's to edit, and the picker is what would let them attach something to it.
     */
    onAttachMedia: (() -> Unit)? = null,
    /** Clears an attached photo or video. */
    onRemoveMedia: (() -> Unit)? = null,
    /** Whether media is attached right now, so the row can offer remove instead of attach. */
    hasAttachedMedia: Boolean = false
) {
    var nameInput by remember { mutableStateOf(existingExercise?.name ?: prefillName) }
    var categoryInput by remember { mutableStateOf(existingExercise?.category ?: MuscleCategory.CHEST) }
    var equipmentInput by remember { mutableStateOf(existingExercise?.equipment ?: "Bodyweight") }
    var typeInput by remember { mutableStateOf(existingExercise?.exerciseType ?: ExerciseType.STANDARD_WEIGHT) }
    var descriptionInput by remember { mutableStateOf(existingExercise?.description.orEmpty()) }

    // An exercise registered by the AI is never presented as if the user had typed it
    val isEditingAiExercise = existingExercise?.isAiGenerated == true
    val headerColor = if (isEditingAiExercise) AiBlue else ElectricCyan

    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var equipmentMenuExpanded by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var equipmentError by remember { mutableStateOf(false) }

    val duplicate = remember(nameInput) {
        if (nameInput.isBlank()) null else findSimilar(nameInput)
    }
    // "user_generated" / "ai_generated" are search keywords, so they can never be an exercise name
    val isReservedName = nameInput.isNotBlank() && ReservedExerciseNames.isReserved(nameInput)
    val isNameValid = nameInput.trim().length >= 2 && !isReservedName
    val isCategoryValid = categoryInput.isNotBlank()
    val isEquipmentValid = equipmentInput.isNotBlank()
    val canSave = isNameValid && isCategoryValid && isEquipmentValid && duplicate == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(headerColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEditingAiExercise) Icons.Default.AutoAwesome else Icons.Default.Check,
                        contentDescription = null,
                        tint = headerColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            existingExercise == null -> "Add Custom Exercise"
                            isEditingAiExercise -> "Edit AI Exercise"
                            else -> "Edit Your Exercise"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = if (isEditingAiExercise) {
                            "AI generated • saved to your local database"
                        } else {
                            "Saved to your local database"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
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
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Exercise Name *") },
                    placeholder = { Text("e.g. Z-Bar Curl", color = TextMuted) },
                    singleLine = true,
                    isError = nameInput.isNotBlank() && !isNameValid,
                    colors = customExerciseFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_exercise_name_input")
                )

                // ---------------- Reserved name warning ----------------
                if (isReservedName) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FireRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, FireRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("custom_exercise_reserved_name_warning"),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Reserved name",
                            tint = FireRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ReservedExerciseNames.explanation(nameInput),
                            style = MaterialTheme.typography.bodySmall,
                            color = FireRed
                        )
                    }
                }

                // ---------------- Duplicate warning ----------------
                if (duplicate != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FireRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, FireRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("custom_exercise_duplicate_warning"),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Duplicate exercise",
                            tint = FireRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "The database already contains an exercise named \"${duplicate.name}\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = FireRed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${duplicate.category} • ${duplicate.equipment} • ${duplicate.description.ifBlank { "No description" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Use the existing one or pick a different name.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextWhite
                            )
                        }
                    }
                }

                // ---------------- Muscle tag (animated drop down) ----------------
                AnimatedDropdownField(
                    label = "Muscle Group Tag *",
                    value = categoryInput,
                    icon = Icons.Default.FitnessCenter,
                    accent = ElectricCyan,
                    options = MuscleCategory.ALL,
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    onOptionSelected = { option ->
                        categoryInput = option
                        categoryError = false
                    },
                    isError = categoryError,
                    fieldTestTag = "custom_exercise_category_dropdown",
                    optionTestTagPrefix = "custom_exercise_category_option"
                )

                // ---------------- Exercise type ----------------
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Exercise Type *",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EXERCISE_TYPE_OPTIONS.forEach { (type, label) ->
                            val isSelected = typeInput == type
                            val accent = when (type) {
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
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) accent else DarkBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { typeInput = type }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("custom_exercise_type_$type"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) accent else TextWhite
                                )
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
                }

                // ---------------- Equipment (animated drop down) ----------------
                AnimatedDropdownField(
                    label = "Equipment *",
                    value = equipmentInput,
                    icon = Icons.Default.Build,
                    accent = GoldenAmber,
                    options = COMMON_EQUIPMENT,
                    expanded = equipmentMenuExpanded,
                    onExpandedChange = { equipmentMenuExpanded = it },
                    onOptionSelected = { option ->
                        equipmentInput = option
                        equipmentError = false
                    },
                    isError = equipmentError,
                    fieldTestTag = "custom_exercise_equipment_dropdown",
                    optionTestTagPrefix = "custom_exercise_equipment_option"
                )

                // ---------------- Description ----------------
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Short Description *") },
                    placeholder = { Text("How is it performed? e.g. Grip the bar narrower than shoulder width...", color = TextMuted) },
                    minLines = 2,
                    maxLines = 4,
                    colors = customExerciseFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_exercise_description_input")
                )

                if (descriptionInput.isNotBlank()) {
                    Text(
                        text = "Tip: a clear description helps the AI coach give you better cues.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                // ---------------- Own media ----------------
                // A photo or video of the athlete's own variation, attached from their gallery.
                //
                // It sits in the editor, not only in the detail dialog, because the natural moment to
                // want it is when creating the exercise: they have already decided this is their own
                // movement, and coming back for the media later means finding it again in a list of
                // 1,300.
                //
                // Null for catalog rows. Those belong to the app, not to the athlete, and offering to
                // attach media to them would let them edit the library through the back door.
                if (onAttachMedia != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Your media",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
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
                                tint = if (hasAttachedMedia) ElectricCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasAttachedMedia) {
                                        "Photo or video attached"
                                    } else {
                                        "No photo or video yet"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = TextWhite
                                )
                                Text(
                                    text = "Shown instead of the built-in animation, so you see your " +
                                        "own version of the movement.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            if (hasAttachedMedia && onRemoveMedia != null) {
                                TextButton(
                                    onClick = onRemoveMedia,
                                    modifier = Modifier.testTag("custom_exercise_remove_media_button")
                                ) {
                                    Text("Remove", color = FireRed)
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        // A brand new exercise has no row and therefore no id to
                                        // attach media to, so it is saved first.
                                        //
                                        // This used to only signal the screen, which closed the dialog
                                        // and then waited for a save that nobody had asked for: the
                                        // picker never appeared and the exercise was never created.
                                        if (existingExercise == null) {
                                            onSave(
                                                nameInput.trim(),
                                                categoryInput,
                                                equipmentInput.trim().ifBlank { "Bodyweight" },
                                                typeInput,
                                                descriptionInput.trim()
                                            )
                                        }
                                        onAttachMedia()
                                    },
                                    modifier = Modifier.testTag("custom_exercise_attach_media_button")
                                ) {
                                    Text("Add", color = ElectricCyan)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        nameInput.trim(),
                        categoryInput,
                        equipmentInput.trim().ifBlank { "Bodyweight" },
                        typeInput,
                        descriptionInput.trim()
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintGreen,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_custom_exercise_button")
            ) {
                Text(
                    text = if (existingExercise == null) "Add Exercise" else "Save Changes",
                    color = if (canSave) DarkBg else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

/**
 * Animated drop down field.
 *
 * Uses exactly the same reveal animation as the "Selected exercise" search drop down of the
 * progress tab (fade + vertical expand / fade + vertical shrink). The option list scrolls inside
 * itself (capped height) so the surrounding screen never has to grow.
 */
@Composable
fun AnimatedDropdownField(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit,
    isError: Boolean = false,
    fieldTestTag: String,
    optionTestTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Trigger row (looks like a text field, click anywhere on it)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBg, RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = when {
                        isError -> FireRed
                        expanded -> ElectricCyan
                        else -> DarkBorder
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .testTag(fieldTestTag),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (value.isNotBlank()) accent else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = value.ifBlank { "Select..." },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (value.isNotBlank()) TextWhite else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Close" else "Open",
                tint = if (expanded) ElectricCyan else TextMuted
            )
        }

        // Same fade + expand/shrink animation as the progress tab exercise selector
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(DarkBg, RoundedCornerShape(10.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp)
                    .testTag("${fieldTestTag}_menu")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) accent else DarkBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onOptionSelected(option) }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("${optionTestTagPrefix}_$option"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else icon,
                                contentDescription = null,
                                tint = if (isSelected) accent else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) accent else TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog shown before deleting an exercise the user owns (their own or an AI one).
 */
@Composable
fun DeleteCustomExerciseDialog(
    exercise: Exercise,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAiExercise = exercise.isAiGenerated
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FireRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAiExercise) "Delete AI Exercise?" else "Delete Your Exercise?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "\"${exercise.name}\" will be removed from your local database. " +
                            "Workout history where you logged it stays untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                if (isAiExercise) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AiBlue.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, AiBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AiBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This exercise was AI generated, not created by you.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AiBlue
                        )
                    }
                }
            }
        },
        modifier = if (isAiExercise) {
            Modifier.testTag("delete_ai_generated_exercise_dialog")
        } else {
            Modifier.testTag("delete_user_exercise_dialog")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_delete_custom_exercise_button")
            ) {
                Text("Delete", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun customExerciseFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkBg,
    unfocusedContainerColor = DarkBg,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted
)
