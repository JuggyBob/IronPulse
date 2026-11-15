/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.WeightConversion
import com.example.ui.components.ProgressionLineChart
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The Body Weight Timeline and Weigh-in Logs moved from Profile to Progress.
 *
 * They read the same ViewModel flows and use the same WeighInLogsPanel, storage and conversions as
 * before. Only their location changed.
 */
@Composable
fun BodyWeightProgressSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val weightLogs by viewModel.weightLogs.collectAsStateWithLifecycle()
    val chartPointsKg by viewModel.bodyWeightChartPoints.collectAsStateWithLifecycle()

    var showLogDialog by remember { mutableStateOf(false) }
    var weighInsExpanded by remember { mutableStateOf(false) }
    var newWeightInput by remember { mutableStateOf("") }
    var weightNoteInput by remember { mutableStateOf("") }
    val sortedLogs = remember(weightLogs) { weightLogs.sortedByDescending { it.timestamp } }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("body_weight_timeline_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BODY WEIGHT TIMELINE",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            text = "Target reference line in green",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Button(
                        onClick = {
                            newWeightInput = WeightConversion.format(
                                userProfile.currentWeightKg,
                                weightUnit,
                                withUnit = false
                            )
                            weightNoteInput = MainViewModel.WEIGH_IN_NOTE_MORNING
                            showLogDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintGreen,
                            contentColor = OnAccent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("log_weight_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Today", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                ProgressionLineChart(
                    points = remember(chartPointsKg, weightUnit) {
                        chartPointsKg.map { point ->
                            point.copy(value = WeightConversion.fromKg(point.value, weightUnit))
                        }
                    },
                    yAxisUnit = weightUnit.suffix,
                    lineColor = MintGreen,
                    referenceLineValue = WeightConversion.fromKg(userProfile.targetWeightKg, weightUnit),
                    referenceLineLabel = "Target ${WeightConversion.format(userProfile.targetWeightKg, weightUnit)}"
                )
            }
        }

        // The timeline card and the logs belong together: the old 16dp gap plus the 48dp header
        // row left a wide empty band between the two cards.
        Spacer(modifier = Modifier.height(10.dp))
        if (weightLogs.isEmpty()) {
            Text(
                text = "No weigh-in logs recorded yet. Tap 'Log Today' above to start tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.testTag("weigh_in_logs_empty")
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().testTag("weigh_in_logs_section")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .testTag("weigh_in_logs_header"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WEIGH-IN LOGS (${weightLogs.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                    // Only the expanded state has an action. The row reserves the same 48dp
                    // height in both states, so swapping the action in cannot push the panel
                    // down; the collapsed card already provides its own View all button.
                    if (weighInsExpanded) {
                        Text(
                            text = "Hide all",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { weighInsExpanded = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("collapse_weigh_in_logs")
                        )
                    }
                }
                // The header-to-panel gap is explicit so it cannot change when the panel swaps
                // between the compact summary and the expanded log. The collapsed reference is
                // the only spacing used here.
                Spacer(modifier = Modifier.height(8.dp))
                WeighInLogsPanel(
                    expanded = weighInsExpanded,
                    logs = sortedLogs,
                    weightUnit = weightUnit,
                    dateFormat = dateFormat,
                    onShowAll = { weighInsExpanded = true },
                    onDelete = { viewModel.deleteWeightLog(it) }
                )
            }
        }
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            containerColor = DarkSurface,
            title = { Text("Log Body Weight", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter morning weigh-in:",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newWeightInput,
                        onValueChange = { newWeightInput = it },
                        placeholder = { Text("e.g. 77.5", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = weightFieldColors(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = weightNoteInput,
                        onValueChange = { weightNoteInput = it },
                        placeholder = { Text("Note (e.g. Fasted, post-workout)", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = weightFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = WeightConversion.parseToKg(newWeightInput, weightUnit)
                        if (parsed != null) viewModel.addWeightLog(parsed, weightNoteInput)
                        showLogDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintGreen,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Save Weigh-in", color = OnAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun weightFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite
)
