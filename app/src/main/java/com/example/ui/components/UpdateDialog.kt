package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UpdateState
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
 * The update dialog: check, download, install.
 *
 * Every state the check can be in has its own face, so the athlete always knows what is happening
 * and what can be done next. Nothing is downloaded until the athlete asks for it.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    installedVersion: String,
    releasesPageUrl: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        modifier = Modifier.testTag("update_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Check for updates",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (state) {
                    is UpdateState.Idle -> {
                        Text(
                            text = "IronPulse $installedVersion is the build that is running. " +
                                "Check the official releases for a newer one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        UpdateButton(
                            text = "Check now",
                            onClick = onCheck,
                            testTag = "update_check_button"
                        )
                    }

                    is UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Looking at the official releases...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite
                            )
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "IronPulse $installedVersion is the latest version.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                state.release?.let { release ->
                                    Text(
                                        text = "${release.name} is the newest release.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.Available -> {
                        Text(
                            text = "Version ${state.release.versionText} is available.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = GoldenAmber
                        )
                        Text(
                            text = "You are running $installedVersion. The update is downloaded from " +
                                "the official release and Android asks you to confirm the install.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        if (state.asset == null) {
                            Text(
                                text = "This release does not carry an installable Android package, " +
                                    "so it cannot be installed from here. The release page has everything.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldenAmber
                            )
                            UpdateButton(
                                text = "Open the release page",
                                onClick = { onOpenReleasePage(state.release.htmlUrl) },
                                testTag = "update_release_page_button"
                            )
                        } else {
                            UpdateButton(
                                text = "Download the update",
                                onClick = onDownload,
                                testTag = "update_download_button"
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = "Downloading ${state.asset.name}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                        val percent = if (state.totalBytes > 0L) {
                            ((state.bytesRead * 100L) / state.totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_download_progress"),
                            color = ElectricCyan,
                            trackColor = DarkSurfaceVariant
                        )
                        Text(
                            text = "$percent% of ${state.totalBytes / 1024 / 1024} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "The update is ready to install.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Text(
                                    text = "Android will ask you to confirm the installation.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                        UpdateButton(
                            text = "Install the update",
                            onClick = onInstall,
                            testTag = "update_install_button"
                        )
                    }

                    is UpdateState.Failed -> {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = FireRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite
                            )
                        }
                        if (state.canRetry) {
                            UpdateButton(
                                text = "Try again",
                                onClick = onCheck,
                                testTag = "update_retry_button"
                            )
                        }
                        UpdateButton(
                            text = "Open the release page",
                            onClick = { onOpenReleasePage(releasesPageUrl) },
                            testTag = "update_release_page_button",
                            outlined = true
                        )
                    }

                    is UpdateState.Cancelled -> {
                        Text(
                            text = "The check was cancelled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        UpdateButton(
                            text = "Check again",
                            onClick = onCheck,
                            testTag = "update_check_button"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("update_close_button")) {
                Text("Close", color = ElectricCyan)
            }
        }
    )
}

/**
 * The main action of the update dialog.
 */
@Composable
private fun UpdateButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = DarkBg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkBg
            )
        }
    }
}
