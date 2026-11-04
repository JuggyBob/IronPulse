/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * The post-workout comparison popup.
 *
 * It is intentionally read-only after success: the persisted markdown is passed in exactly as it
 * was stored, and no AI request is made by opening it.
 */
@Composable
fun WorkoutSummaryDialog(
    workoutTitle: String,
    workoutDate: String,
    markdown: String?,
    loading: Boolean,
    error: String?,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("workout_summary_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(32.dp)
                            .padding(0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(
                            "Workout Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            "$workoutTitle · $workoutDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_workout_summary")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            when {
                loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Comparing this workout with the closest previous session...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = FireRed)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Summary could not be generated",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = FireRed
                        )
                    }
                    Text(error, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(
                        "The workout itself was saved successfully. No summary was stored.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MintGreen
                    )
                }

                markdown != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownView(
                        markdown = markdown,
                        modifier = Modifier.fillMaxWidth(),
                        baseTextColor = TextWhite
                    )
                }

                else -> Text(
                    "No summary is available for this workout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            if (error != null && onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("retry_workout_summary")
                ) {
                    Text("Try again", color = OnAccent, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_workout_summary")
                ) {
                    Text("Close", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = if (error != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = TextMuted)
                }
            }
        } else {
            null
        }
    )
}
