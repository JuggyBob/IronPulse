package com.example.ui.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
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
 * Searchable muscle picker.
 *
 * Opens from the square button that sits on top of the muscle category chips. The body map is
 * interactive here: tapping a muscle highlights it and filters the exercise list underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleSelectorDialog(
    selected: MusclePart?,
    onSelect: (MusclePart?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            MusclePart.selectable
        } else {
            MusclePart.selectable.filter { part ->
                part.label.lowercase().contains(q) || part.searchTerms.any { it.contains(q) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp)
                .heightIn(max = 640.dp)
                .testTag("muscle_selector_dialog"),
            color = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Choose a body part",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "The exercise list is filtered to the muscles you pick.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                MuscleBodyMap(
                    highlighted = selected,
                    onSelect = { part -> onSelect(if (part == selected) null else part) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Search a muscle (e.g. glutes)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = ElectricCyan)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("muscle_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filtered.isEmpty()) {
                    Text(
                        text = "No muscle matches \"$query\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filtered.forEach { part ->
                            val isSelected = part == selected
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) ElectricCyan else DarkSurfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) ElectricCyan else DarkBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelect(if (isSelected) null else part) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("muscle_part_chip_${part.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = DarkBg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = part.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) DarkBg else TextWhite,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onSelect(null) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("muscle_picker_clear_button")
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MintGreen
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("muscle_picker_done_button")
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = DarkBg
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full information of a single exercise.
 *
 * The body illustration here is read-only on purpose: it is tied to the muscle group this exercise
 * trains, not to a filter, so tapping it does nothing.
 */
@Composable
fun ExerciseDetailDialog(
    exercise: Exercise,
    unit: String,
    onAddSet: () -> Unit,
    onDismiss: () -> Unit,
    /** Drives the "Autoplay exercise demos" setting. See [ExerciseGif]. */
    autoplayDemos: Boolean = true,
    /**
     * Offered only on exercises the athlete owns, since the shipped library is locked. Opens the
     * gallery picker so they can attach a photo or video of their own variation.
     */
    onAttachMedia: (() -> Unit)? = null,
    /** Clears an attached photo or video. Same ownership restriction as [onAttachMedia]. */
    onRemoveMedia: (() -> Unit)? = null,
    /**
     * Opens the low level editor for this exercise.
     *
     * Non-null for every exercise, library rows included. The shipped catalog is not always right,
     * and an athlete who can see the mistake has to be able to fix it; the editor itself shows the
     * warning before anything is written. See [ExerciseEditorDialog].
     */
    onEdit: (() -> Unit)? = null
) {
    val muscle = remember(exercise.id, exercise.category, exercise.equipment, exercise.name) {
        MusclePartResolver.resolve(exercise)
    }
    val fields = remember(exercise.exerciseType) {
        MusclePartResolver.trackedFields(exercise.exerciseType)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .heightIn(max = 640.dp)
                .testTag("exercise_detail_dialog"),
            color = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---------------- Header ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${exercise.equipment} • ${exercise.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                            .clickable { onDismiss() }
                            .testTag("close_exercise_detail_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                    }

                    // The pen opens the low level editor. Same icon everywhere, so the route to it is
                    // discoverable from the exercise itself rather than only from a search result.
                    if (onEdit != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (exercise.isCustom || exercise.isAiGenerated) {
                                        ElectricCyan.copy(alpha = 0.15f)
                                    } else {
                                        // Tinted for a library row on purpose: it is about to write
                                        // data that shipped with the app, and the affordance should
                                        // say so before the athlete taps it.
                                        FireRed.copy(alpha = 0.15f)
                                    },
                                    CircleShape
                                )
                                .clickable { onEdit() }
                                .testTag("edit_exercise_detail_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit this exercise",
                                tint = if (exercise.isCustom || exercise.isAiGenerated) {
                                    ElectricCyan
                                } else {
                                    FireRed
                                },
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // ---------------- Tags ----------------
                Spacer(modifier = Modifier.height(10.dp))
                TagFlow {
                    DetailTag(
                        text = when (exercise.exerciseType) {
                            ExerciseType.ADDED_WEIGHT -> "Added weight"
                            ExerciseType.PURE_BODYWEIGHT -> "Bodyweight"
                            else -> "Standard weight"
                        },
                        color = when (exercise.exerciseType) {
                            ExerciseType.ADDED_WEIGHT -> CoralOrange
                            ExerciseType.PURE_BODYWEIGHT -> MintGreen
                            else -> ElectricCyan
                        }
                    )
                    DetailTag(text = exercise.category, color = VioletPurple)
                    DetailTag(text = exercise.equipment, color = GoldenAmber)
                    if (exercise.isCustom) {
                        DetailTag(text = "Created by you", color = MintGreen)
                    } else if (exercise.isAiGenerated) {
                        DetailTag(text = "AI generated", color = AiBlue)
                    }
                    // Only when it says something the category does not already say
                    if (muscle != null && !muscle.label.equals(exercise.category, ignoreCase = true)) {
                        DetailTag(text = muscle.label, color = ElectricCyan)
                    }
                }

                // ---------------- Fields to log ----------------
                Spacer(modifier = Modifier.height(14.dp))
                SectionTitle("FIELDS YOU LOG")
                Spacer(modifier = Modifier.height(6.dp))
                TagFlow {
                    fields.forEach { field ->
                        Row(
                            modifier = Modifier
                                .background(DarkBg, RoundedCornerShape(8.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = field,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextWhite
                            )
                        }
                    }
                }

                // ---------------- Media ----------------
                // A video or photo the athlete attached wins over the dataset animation, and nothing
                // is bundled in the apk. Only their own exercises can have media attached at all.
                Spacer(modifier = Modifier.height(12.dp))
                ExerciseGif(
                    localUri = exercise.customMediaUri,
                    localIsVideo = exercise.customMediaIsVideo,
                    mediaPath = exercise.mediaPath,
                    autoplay = autoplayDemos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    showAttribution = true,
                    cornerRadius = 14
                )

                // Media the athlete added themselves. Only ever offered on their own exercises.
                if (onAttachMedia != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("exercise_media_actions")
                    ) {
                        FilledTonalButton(
                            onClick = onAttachMedia,
                            modifier = Modifier.testTag("exercise_attach_media_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (exercise.customMediaUri.isBlank()) {
                                    "Add your own photo / video"
                                } else {
                                    "Replace"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (onRemoveMedia != null && exercise.customMediaUri.isNotBlank()) {
                            TextButton(
                                onClick = onRemoveMedia,
                                modifier = Modifier.testTag("exercise_remove_media_button")
                            ) {
                                Text(
                                    text = "Remove",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FireRed
                                )
                            }
                        }
                    }
                }

                // ---------------- Instructions ----------------
                // Dataset exercises carry their real coaching text in `instructions`; the user's
                // own exercises only ever had the free text `description` box.
                val guidance = exercise.instructions.ifBlank { exercise.description }
                if (guidance.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    SectionTitle("INSTRUCTIONS")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite,
                        lineHeight = 18.sp
                    )
                }

                // ---------------- Target muscles ----------------
                // The dataset's primary target and its synergist list, shown as the existing tags.
                val secondary = exercise.secondaryMuscleList
                if (exercise.target.isNotBlank() || secondary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    SectionTitle("TARGETS")
                    Spacer(modifier = Modifier.height(6.dp))
                    TagFlow {
                        (listOf(exercise.target) + secondary)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .forEach { muscleName ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElectricCyan.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = muscleName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                    }
                }

                // ---------------- Body ----------------
                Spacer(modifier = Modifier.height(14.dp))
                SectionTitle("BODY PARTS TRAINED")
                Spacer(modifier = Modifier.height(6.dp))
                MuscleBodyMap(highlighted = muscle, onSelect = null, maxFigureHeight = 230.dp)

                // ---------------- Add set ----------------
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddSet,
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("exercise_detail_add_set_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = DarkBg,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Set",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = DarkBg
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = TextMuted
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTag(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
