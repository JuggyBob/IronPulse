/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Exercise
import com.example.data.model.ExerciseSearch
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.theme.CoralOrange
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated, full-catalog exercise search for the Routine editor.
 *
 * Results are ranked by [ExerciseSearch], never truncated, and virtualised in a lazy list. Category
 * chips narrow the ranked set without changing the relevance order.
 */
@Composable
fun PlanExerciseSearchDialog(
    allExercises: List<Exercise>,
    alreadyAddedIds: Set<Long>,
    unitSuffix: String,
    onAdd: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORIES) }
    var visible by remember { mutableStateOf(false) }
    var detailExercise by remember { mutableStateOf<Exercise?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { visible = true }

    val categories = remember(allExercises) {
        listOf(ALL_CATEGORIES) + allExercises
            .mapNotNull { it.category.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()
    }

    val resultListState = rememberLazyListState()
    LaunchedEffect(query, selectedCategory) {
        resultListState.scrollToItem(0)
    }

    val results = remember(allExercises, query, selectedCategory) {
        val pool = if (selectedCategory == ALL_CATEGORIES) {
            allExercises
        } else {
            allExercises.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        ExerciseSearch.rank(pool, query)
    }

    val close: () -> Unit = {
        if (visible) {
            visible = false
            scope.launch {
                delay(150)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(170)) + scaleIn(tween(200), initialScale = 0.96f),
                exit = fadeOut(tween(130)) + scaleOut(tween(150), targetScale = 0.97f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .testTag("plan_exercise_search_dialog"),
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    shadowElevation = 18.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(ElectricCyan.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Add Exercises",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextWhite
                                )
                                Text(
                                    "Search the full IronPulse catalog",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            IconButton(
                                onClick = close,
                                modifier = Modifier.testTag("plan_exercise_search_close")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close exercise search",
                                    tint = TextMuted
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search exercises") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = TextMuted
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = exerciseSearchFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_exercise_search_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 1.dp)
                        ) {
                            items(categories, key = { it }) { category ->
                                val selected = category == selectedCategory
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = category },
                                    label = {
                                        Text(
                                            text = category,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = DarkBorder,
                                        selectedBorderColor = ElectricCyan.copy(alpha = 0.7f)
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = DarkBg,
                                        labelColor = TextMuted,
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag(
                                        "plan_exercise_category_${category.lowercase()}"
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    query.isBlank() && selectedCategory == ALL_CATEGORIES ->
                                        "All exercises (${results.size})"
                                    query.isBlank() -> "$selectedCategory (${results.size})"
                                    else -> "${results.size} result${if (results.size == 1) "" else "s"}"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Tap Add to keep searching",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        if (results.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextMuted.copy(alpha = 0.55f),
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No matching exercises",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextWhite
                                )
                                Text(
                                    "Try a different name, muscle group or equipment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = resultListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 6.dp)
                            ) {
                                items(results, key = { it.id }) { exercise ->
                                    val added = exercise.id in alreadyAddedIds
                                    ExerciseSearchResultRow(
                                        exercise = exercise,
                                        added = added,
                                        onShowDetails = { detailExercise = exercise },
                                        onAdd = { onAdd(exercise) }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = close,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_exercise_search_done"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = OnAccent
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    detailExercise?.let { exercise ->
        val alreadyAdded = alreadyAddedIds.contains(exercise.id)
        ExerciseDetailDialog(
            exercise = exercise,
            unit = unitSuffix,
            onAddSet = {
                if (!alreadyAdded) onAdd(exercise)
                detailExercise = null
            },
            addLabel = if (alreadyAdded) "Added to Routine" else "Add to Routine",
            onDismiss = { detailExercise = null }
        )
    }
}

@Composable
private fun ExerciseSearchResultRow(
    exercise: Exercise,
    added: Boolean,
    onShowDetails: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_exercise_search_result_${exercise.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (added) DarkBg.copy(alpha = 0.6f) else DarkBg,
        border = BorderStroke(
            1.dp,
            if (added) MintGreen.copy(alpha = 0.35f) else DarkBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (added) MintGreen.copy(alpha = 0.12f) else ElectricCyan.copy(alpha = 0.10f),
                        RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = if (added) MintGreen else ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onShowDetails() }
                    .testTag("plan_exercise_name_${exercise.id}")
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(exercise.equipment, exercise.category)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAdd,
                enabled = !added,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = MintGreen
                ),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                modifier = Modifier.testTag("plan_exercise_add_${exercise.id}")
            ) {
                Icon(
                    imageVector = if (added) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (added) "Added" else "Add",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun exerciseSearchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkBg,
    unfocusedContainerColor = DarkBg,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted,
    cursorColor = ElectricCyan
)

private const val ALL_CATEGORIES = "All"
