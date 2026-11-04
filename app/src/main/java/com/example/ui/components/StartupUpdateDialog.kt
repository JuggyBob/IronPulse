package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GithubRelease
import com.example.data.model.UpdateState
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.OnAccent

/**
 * The update prompt the app opens by itself when a newer release exists.
 *
 * It differs from the settings dialog in three ways, and each one is the point of the screen:
 *
 *  - the release notes are shown up front, because "a new update is available" without saying
 *    what changed asks the athlete to install blind;
 *  - closing it is allowed without cancelling the update, so the prompt comes back next launch;
 *  - closing it with "Do not show this again" is remembered for the running version only. The
 *    notice returns after the athlete updates the app, which is exactly when a new release may
 *    matter again.
 */
@Composable
fun StartupUpdateDialog(
    release: GithubRelease,
    state: UpdateState,
    installedVersion: String,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    var dontShowAgain by remember(release.tagName) { mutableStateOf(false) }

    val close = {
        if (dontShowAgain) onDontShowAgain() else onClose()
    }

    AlertDialog(
        onDismissRequest = close,
        containerColor = DarkSurface,
        modifier = Modifier.testTag("startup_update_dialog"),
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
                    text = "Update available",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "IronPulse ${release.versionText} is available. You are running $installedVersion.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextWhite
                )

                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "WHAT'S NEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ReleaseNotesText(notes = release.body)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 4.dp)
                        .testTag("startup_update_dont_show_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ElectricCyan,
                            uncheckedColor = TextMuted,
                            checkmarkColor = OnAccent
                        ),
                        modifier = Modifier.testTag("startup_update_dont_show_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Do not show this again",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                        Text(
                            text = "Comes back after you install a new version.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                // The body below the checkbox changes with the download. The notes and the choice
                // stay visible, so the dialog does not jump to a different screen mid download.
                when (state) {
                    is UpdateState.Downloading -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        val percent = if (state.totalBytes > 0L) {
                            ((state.bytesRead * 100L) / state.totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Downloading the update... $percent%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("startup_update_progress"),
                            color = ElectricCyan,
                            trackColor = DarkSurfaceVariant
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The download is checked and ready. Android will ask you to confirm the installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MintGreen
                        )
                    }

                    is UpdateState.Failed -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = FireRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite
                            )
                        }
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> {
                    if (state.asset != null) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("startup_update_download_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = OnAccent,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download",
                                color = OnAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is UpdateState.ReadyToInstall -> {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("startup_update_install_button")
                    ) {
                        Text(
                            text = "Install",
                            color = OnAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is UpdateState.Failed -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("startup_update_retry_button")
                    ) {
                        Text(
                            text = "Try again",
                            color = OnAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                else -> Unit
            }
        },
        dismissButton = {
            TextButton(
                onClick = close,
                modifier = Modifier.testTag("startup_update_close_button")
            ) {
                Text("Close", color = ElectricCyan)
            }
        }
    )
}
