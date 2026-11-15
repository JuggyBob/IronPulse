package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseFieldCatalog
import com.example.data.model.ExerciseType
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * The explicit supported-field configuration for one exercise.
 *
 * Both the create and edit dialogs use this. Tags/types only supply the initial suggestion; the
 * selected list is authoritative and is never recomputed from the type once the athlete changes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseFieldSelector(
    selected: List<ExerciseField>,
    onToggle: (ExerciseField) -> Unit,
    onUseTypeDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ordered = selected.distinct()
    val incompatible = rememberIncompatibleFields(ordered)
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SUPPORTED SET FIELDS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Use defaults",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan,
                modifier = Modifier
                    .clickable(onClick = onUseTypeDefaults)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("exercise_fields_use_defaults")
            )
        }
        Text(
            "The first two are shown inline during a set; the rest appear in the set's details. " +
                "Changing the type no longer removes fields you chose.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 7.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExerciseField.entries.forEach { field ->
                val checked = field in ordered
                val disabled = !checked && field in incompatible
                FieldChip(
                    field = field,
                    checked = checked,
                    enabled = !disabled,
                    onToggle = { onToggle(field) }
                )
            }
        }
        if (ordered.isNotEmpty()) {
            val inline = ordered.take(2).joinToString(" + ") { fieldDisplayName(it) }
            val details = ordered.drop(2).joinToString(", ") { fieldDisplayName(it) }
            Text(
                text = buildString {
                    append("Inline: ")
                    append(inline)
                    if (details.isNotBlank()) {
                        append("  ·  Details: ")
                        append(details)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (incompatible.isNotEmpty()) {
            Text(
                "Weight and added weight cannot be combined. Hold and time share one duration " +
                    "value, so choose one.",
                style = MaterialTheme.typography.labelSmall,
                color = com.example.ui.theme.GoldenAmber,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun FieldChip(
    field: ExerciseField,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val accent = when (field) {
        ExerciseField.WEIGHT, ExerciseField.ADDED_WEIGHT -> ElectricCyan
        ExerciseField.REPS -> com.example.ui.theme.MintGreen
        ExerciseField.HOLD_SECONDS, ExerciseField.TIME_SECONDS -> com.example.ui.theme.GoldenAmber
        ExerciseField.DISTANCE_KM -> com.example.ui.theme.CoralOrange
    }
    Surface(
        modifier = Modifier
            .alpha(if (enabled || checked) 1f else 0.38f)
            .clickable(enabled = enabled || checked, onClick = onToggle)
            .testTag("exercise_field_${field.id}"),
        color = if (checked) accent.copy(alpha = 0.14f) else DarkBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (checked) accent else DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                fieldDisplayName(field),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (checked) accent else TextWhite
            )
        }
    }
}

/** Which fields are blocked by an incompatible selected field. */
private fun rememberIncompatibleFields(selected: List<ExerciseField>): Set<ExerciseField> {
    val result = mutableSetOf<ExerciseField>()
    if (ExerciseField.WEIGHT in selected) result.add(ExerciseField.ADDED_WEIGHT)
    if (ExerciseField.ADDED_WEIGHT in selected) result.add(ExerciseField.WEIGHT)
    if (ExerciseField.HOLD_SECONDS in selected) result.add(ExerciseField.TIME_SECONDS)
    if (ExerciseField.TIME_SECONDS in selected) result.add(ExerciseField.HOLD_SECONDS)
    return result
}

fun fieldDisplayName(field: ExerciseField): String = when (field) {
    ExerciseField.REPS -> "Reps"
    ExerciseField.WEIGHT -> "Weight"
    ExerciseField.ADDED_WEIGHT -> "Added weight"
    ExerciseField.HOLD_SECONDS -> "Hold (s)"
    ExerciseField.TIME_SECONDS -> "Time (min)"
    ExerciseField.DISTANCE_KM -> "Distance"
}


/** Convenience for dialogs: the type's suggested ordered fields. */
fun suggestedFields(exerciseType: String): List<ExerciseField> =
    ExerciseFieldCatalog.defaultsForType(exerciseType)
