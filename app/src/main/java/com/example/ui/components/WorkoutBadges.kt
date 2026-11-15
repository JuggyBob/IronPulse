/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExerciseType
import com.example.ui.theme.CoralOrange
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.ElectricCyan

/** The existing exercise-type classification, written the way the athlete reads it. */
fun exerciseTypeTagLabel(type: String): String = when (type) {
    ExerciseType.ADDED_WEIGHT -> "Added Weight / Calisthenics"
    ExerciseType.PURE_BODYWEIGHT -> "Bodyweight"
    ExerciseType.CARDIO -> "Cardio"
    else -> "Standard Weight"
}

/** Accent used by the exercise-type tag and by the matching workout fields. */
fun exerciseTypeTagColor(type: String) = when (type) {
    ExerciseType.ADDED_WEIGHT -> CoralOrange
    ExerciseType.PURE_BODYWEIGHT -> MintGreen
    ExerciseType.CARDIO -> GoldenAmber
    else -> ElectricCyan
}

/**
 * A readable warm-up marker.
 *
 * The label carries the meaning, not the colour alone, so the distinction survives a grayscale
 * screen and every IronPulse theme. [compact] is used in dense history rows.
 */
@Composable
fun WarmupBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier.testTag("warmup_badge"),
        color = CoralOrange.copy(alpha = 0.15f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, CoralOrange.copy(alpha = 0.45f))
    ) {
        Text(
            text = if (compact) "WARM" else "WARM-UP",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                fontSize = if (compact) 8.sp else 9.sp
            ),
            color = CoralOrange,
            modifier = Modifier.padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 1.dp)
        )
    }
}

/** The exercise's existing type tag, not a new per-exercise hardcoded classification. */
@Composable
fun ExerciseTypeTag(
    exerciseType: String,
    modifier: Modifier = Modifier
) {
    val color = exerciseTypeTagColor(exerciseType)
    Surface(
        modifier = modifier.testTag("exercise_type_tag"),
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = exerciseTypeTagLabel(exerciseType),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
