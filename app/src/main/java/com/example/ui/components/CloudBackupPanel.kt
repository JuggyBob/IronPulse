/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.cloud.CloudBackupUiState
import com.example.data.backup.cloud.CloudBackupWorkState
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.text.DateFormat
import java.util.Date

/**
 * The single cloud backup surface used by Settings and by onboarding.
 *
 * Keeping it one composable is deliberate: the folder row, the switch, the last successful
 * timestamp and the failure recovery must behave the same in both places.
 */
@Composable
fun CloudBackupPanel(
    state: CloudBackupUiState,
    onChooseFolder: () -> Unit,
    onChangeFolder: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    // Deliberately no PackageManager check for the Drive app. On Android 11+ package visibility
    // can hide an installed app, and the app package is not the same thing as the document
    // provider. DocumentsUI is the only authority on which providers are available.
    val config = state.config
    val persistentFailure = config.currentFailure
    val runtimeMessage = state.runtime.message?.takeIf { it.isNotBlank() }
    val displayedMessage = runtimeMessage ?: persistentFailure
    val messageIsError = runtimeMessage?.let { state.runtime.messageIsError } ?: (persistentFailure != null)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_backup_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(if (compact) 14.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(if (compact) 19.dp else 21.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cloud backup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Save the same .ironpulse backup you can export manually into a folder you choose, " +
                    "such as Google Drive or OneDrive. Automatic backups run after a workout, after a " +
                    "restore, and once a day as a safety net.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!config.isConfigured) {
                OutlinedButton(
                    onClick = onChooseFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("cloud_choose_folder_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Choose folder", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Google Drive, OneDrive and other document providers appear in the " +
                        "system picker when they are installed. Choose any folder you can write to.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    lineHeight = 15.sp
                )
            } else {
                Surface(
                    color = DarkSurfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (state.permissionValid) ElectricCyan else FireRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.folderName ?: "Selected folder",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite,
                                maxLines = 1
                            )
                            Text(
                                text = config.providerLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onChangeFolder,
                        modifier = Modifier.testTag("cloud_change_folder_button")
                    ) {
                        Text("Change folder", color = ElectricCyan, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = onDisconnect,
                        enabled = !state.isBusy,
                        modifier = Modifier.testTag("cloud_disconnect_button")
                    ) {
                        Text("Disconnect", color = FireRed)
                    }
                }

                if (!state.permissionValid) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = FireRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IronPulse no longer has permission to write to this folder. " +
                                "Choose it again or pick another folder.",
                            style = MaterialTheme.typography.labelSmall,
                            color = FireRed,
                            lineHeight = 16.sp,
                            modifier = Modifier.testTag("cloud_permission_error")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(8.dp))

                val autoActive = config.autoBackupEnabled && state.permissionValid
                val autoTitle = when {
                    !config.autoBackupEnabled -> "Automatic backups disabled"
                    !state.permissionValid -> "Automatic backups paused"
                    else -> "Automatic backups enabled"
                }
                val autoDetail = when {
                    !config.autoBackupEnabled ->
                        "Turn on to save after workouts and once a day. Manual backups still work."
                    !state.permissionValid ->
                        "Choose a working folder to resume automatic backups."
                    else ->
                        "Saving after workouts, after a restore and once a day."
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    color = if (autoActive) MintGreen.copy(alpha = 0.07f) else DarkBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (autoActive) MintGreen.copy(alpha = 0.34f) else DarkBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (autoActive) MintGreen.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (autoActive) MintGreen else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = autoTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (autoActive) MintGreen else TextWhite
                            )
                            Text(
                                text = autoDetail,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = config.autoBackupEnabled,
                            onCheckedChange = onToggleAuto,
                            enabled = state.permissionValid && !state.runtime.isRunning,
                            modifier = Modifier.testTag("cloud_auto_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAccent,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }

                if (displayedMessage != null) {
                    Spacer(modifier = Modifier.height(9.dp))
                    OperationStatusCard(
                        label = "Cloud backup",
                        title = cloudBackupStatusTitle(displayedMessage, messageIsError),
                        detail = displayedMessage,
                        isError = messageIsError,
                        onDismiss = onDismissMessage,
                        dismissTestTag = "cloud_dismiss_message_button",
                        modifier = Modifier.testTag(
                            if (messageIsError) "cloud_backup_error" else "cloud_backup_success"
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (config.lastSuccessEpochMillis > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MintGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Last cloud backup",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = formatCloudBackupDate(config.lastSuccessEpochMillis),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            config.lastSuccessFileName?.let { fileName ->
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (config.autoBackupEnabled) {
                            "No cloud backup has completed yet. The first one will start shortly."
                        } else {
                            "No cloud backup yet. Turn on automatic backups, or use Back up now."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        lineHeight = 15.sp
                    )
                }

                if (state.autoWorkState == CloudBackupWorkState.RUNNING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ElectricCyan,
                        trackColor = DarkSurfaceVariant
                    )
                    Text(
                        text = "Automatic backup in progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (state.runtime.isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Backing up now",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    val progressFraction = state.runtime.progressFraction
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("cloud_backup_progress"),
                            color = ElectricCyan,
                            trackColor = DarkSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("cloud_backup_progress"),
                            color = ElectricCyan,
                            trackColor = DarkSurfaceVariant
                        )
                    }
                    state.runtime.progressLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onBackupNow,
                    enabled = state.permissionValid && !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("cloud_backup_now_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.permissionValid) MintGreen.copy(alpha = 0.65f) else DarkBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Back up now", fontWeight = FontWeight.Bold)
                }

                if (state.autoWorkState == CloudBackupWorkState.FAILED && displayedMessage == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The last automatic backup failed. Try Back up now, or check the folder.",
                        style = MaterialTheme.typography.labelSmall,
                        color = FireRed,
                        lineHeight = 15.sp
                    )
                }

            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Cloud backups contain personal data and are not encrypted. Choose a private " +
                    "folder. API keys, your profile picture and exercise images/GIFs are never included. " +
                    "IronPulse keeps the five most recent cloud backups and never overwrites an older one " +
                    "with a failed attempt.",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                lineHeight = 16.sp
            )
        }
    }
}

private fun cloudBackupStatusTitle(message: String, isError: Boolean): String = when {
    message.contains("Backup saved", ignoreCase = true) -> "Cloud backup saved"
    message.contains("Automatic backups are on", ignoreCase = true) ->
        "Automatic backups enabled"
    message.contains("Automatic backups are off", ignoreCase = true) ->
        "Automatic backups off"
    message.contains("disconnected", ignoreCase = true) -> "Cloud backup disconnected"
    isError -> "Cloud backup needs attention"
    else -> "Cloud backup"
}

private fun formatCloudBackupDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMillis))
