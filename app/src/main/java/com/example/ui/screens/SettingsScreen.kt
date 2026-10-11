package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.UserProfile
import com.example.ui.components.ProgressionLineChart
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults

/**
 * Settings page, opened from the gear icon in the top right corner of the profile tab.
 *
 * Everything that configures the app lives here: weight unit, rest timer alerts, the Gemini
 * model and API key, supporting the project and the data management actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }

    // API Key & Model in settings
    val persistedApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val persistedModelName by viewModel.geminiModelName.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
    var customApiKeyInput by remember { mutableStateOf("") }
    var showApiKeyConfirmedBadge by remember { mutableStateOf(false) }

    var showModelConfigDialog by remember { mutableStateOf(false) }
    var showApiKeyHelpDialog by remember { mutableStateOf(false) }
    var showModelsListDropdown by remember { mutableStateOf(false) }
    var modelInputText by remember { mutableStateOf("") }
    var isValidatingModel by remember { mutableStateOf(false) }
    var modelValidationErrorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(persistedApiKey, userProfile.customApiKey) {
        val storedKey = persistedApiKey.ifBlank { userProfile.customApiKey ?: "" }
        if (storedKey.isBlank()) {
            // The key was cleared (e.g. "Delete App Data") -> drop it from the field & badge too
            customApiKeyInput = ""
            showApiKeyConfirmedBadge = false
        } else if (customApiKeyInput.isBlank()) {
            customApiKeyInput = storedKey
        }
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    /**
     * Opens [url] with an explicit ACTION_VIEW intent so the link always goes to the device's
     * default browser and never to an in-app browser view.
     */
    val openExternalUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        } catch (_: Exception) {
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {}
        }
    }


    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header with the back button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(ElectricCyan.copy(alpha = 0.12f), CircleShape)
                        .clickable { onBack() }
                        .testTag("settings_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Units, alerts, AI and your data",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        // ==================== PREFERENCES ====================
        item {
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            val shareAthleteDataWithAi by viewModel.shareAthleteDataWithAi.collectAsStateWithLifecycle()
    val autoplayExerciseDemos by viewModel.autoplayExerciseDemos.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preferences_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Preferences",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Weight unit",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextWhite
                    )
                    Text(
                        text = "Used by every weight, chart and AI calculation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg, RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("kg", "lb").forEach { option ->
                            val isSelected = appSettings.unit.equals(option, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) ElectricCyan else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setWeightUnit(option) }
                                    .padding(vertical = 10.dp)
                                    .testTag("settings_unit_${option}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (option == "kg") "Kilograms (kg)" else "Pounds (lb)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) DarkBg else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(2.dp))

                    SettingSwitchRow(
                        title = "Rest timer vibration",
                        subtitle = "Vibrate when a rest period finishes.",
                        checked = appSettings.restTimerVibration,
                        onCheckedChange = { viewModel.setRestTimerVibration(it) },
                        tag = "settings_rest_timer_vibration"
                    )

                    SettingSwitchRow(
                        title = "Rest timer sound",
                        subtitle = "Play a sound when a rest period finishes.",
                        checked = appSettings.restTimerSound,
                        onCheckedChange = { viewModel.setRestTimerSound(it) },
                        tag = "settings_rest_timer_sound"
                    )

                    // Exercise demos are streamed, so autoplay is a data cost, not just a taste one.
                    // Off means every animation waits for a tap.
                    SettingSwitchRow(
                        title = "Autoplay exercise demos",
                        subtitle = if (autoplayExerciseDemos) {
                            "Exercise animations start as soon as you open one."
                        } else {
                            "Exercise animations only play after you tap them."
                        },
                        checked = autoplayExerciseDemos,
                        onCheckedChange = { viewModel.setAutoplayExerciseDemos(it) },
                        tag = "settings_autoplay_exercise_demos"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(2.dp))

                    // The athlete decides whether their profile may be sent to Gemini at all.
                    // Off means the prompts carry no personal data: no name, no weight, no records.
                    SettingSwitchRow(
                        title = "Share athlete data with AI",
                        subtitle = if (shareAthleteDataWithAi) {
                            "Your name, weight, fitness level and benchmark records are sent with every AI request."
                        } else {
                            "AI requests are sent without any of your personal data."
                        },
                        checked = shareAthleteDataWithAi,
                        onCheckedChange = { viewModel.setShareAthleteDataWithAi(it) },
                        tag = "settings_share_athlete_data"
                    )
                }
            }
        }

        // Gemini AI Settings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_settings_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Fitness Coach API Key",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                modelInputText = persistedModelName
                                modelValidationErrorMessage = null
                                showModelsListDropdown = false
                                showModelConfigDialog = true
                                viewModel.loadAvailableModels()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("ai_model_info_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Configure AI Model",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                modelInputText = persistedModelName
                                modelValidationErrorMessage = null
                                showModelsListDropdown = false
                                showModelConfigDialog = true
                                viewModel.loadAvailableModels()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("ai_model_warning_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(GoldenAmber.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, GoldenAmber.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "AI Model Selection Warning",
                                    tint = GoldenAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // "?" opens ONLY the step by step guide on how to obtain an API key
                        IconButton(
                            onClick = { showApiKeyHelpDialog = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("api_key_help_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(MintGreen.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, MintGreen.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Model: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = if (persistedModelName.isNotBlank()) persistedModelName else "gemini-3.5-flash-lite (Default)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val isKeyConfirmed = persistedApiKey.isNotBlank() || (userProfile.customApiKey?.isNotBlank() == true)
                    val hasBuiltinKey = BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

                    val statusText = when {
                        isKeyConfirmed -> "Status: API Key Confirmed"
                        hasBuiltinKey -> "Status: Pre-configured via Secrets Panel (Active). You can optionally override it below."
                        else -> "Status: Not configured in secrets. Enter your Google AI Studio Gemini API key below to enable live responses."
                    }

                    val statusColor = when {
                        isKeyConfirmed -> Color.Green
                        hasBuiltinKey -> MintGreen
                        else -> GoldenAmber
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isKeyConfirmed) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customApiKeyInput,
                        onValueChange = { customApiKeyInput = it },
                        placeholder = { Text("AIzaSy...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                        colors = profileFieldColors(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveGeminiApiKey(customApiKeyInput)
                                showApiKeyConfirmedBadge = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_api_key_button")
                        ) {
                            Text("Save API Key", color = DarkBg, fontWeight = FontWeight.Bold)
                        }

                        AnimatedVisibility(
                            visible = showApiKeyConfirmedBadge || isKeyConfirmed,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = Color.Green.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Green.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("api_key_confirmed_badge"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Confirmed",
                                        tint = Color.Green,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "API key confirmed",
                                        color = Color.Green,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Revert the exercise catalog Card
        item {
            RevertCatalogCard(
                onRevert = { keepUser, keepAi -> viewModel.revertCatalogToDatabase(keepUser, keepAi) }
            )
        }

        // Data Management & Danger Zone Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_management_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = FireRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Management & History",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Permanently erase every piece of data you have stored in this app: workout sessions, logged sets, saved routines, weigh-ins, AI coach chat history, your Gemini monthly summaries, your Gemini API key, your profile and rest timer presets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your exercise library is kept, including exercises created by the AI parser and your own custom exercises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Delete App Data Button
                    Button(
                        onClick = { showClearDataDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = FireRed.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FireRed.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_all_data_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = FireRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete App Data", color = FireRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Delete All App Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
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
                    Text("Delete All App Data?", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This permanently deletes:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "• All workout sessions, logged sets and diary notes\n" +
                                    "• All saved & AI generated routines\n" +
                                    "• All body weight entries and profile data (name, height, weights, age)\n" +
                                    "• The entire AI coach chat history\n" +
                                    "• All saved Gemini monthly summaries\n" +
                                    "• Your Gemini API key and selected AI model\n" +
                                    "• Your custom rest timer presets and any running rest timer",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 18.sp
                        )
                    }
                    Text(
                        text = "Kept: your full exercise library, including exercises generated by the AI parser and the custom exercises you created.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                    Text(
                        text = "This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = FireRed
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllAppData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete App Data", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
    // Step by step guide: how to get a Google AI Studio API key
    if (showApiKeyHelpDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyHelpDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MintGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MintGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "How to Get Your API Key",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "IronPulse uses a free Google Gemini API key. It is free, takes about a minute and unlocks the AI coach, the routine generator and the AI parser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    // Step 1 - the link opens in the default browser, never inside the app
                    ApiKeyHelpStep(number = 1, text = "Open Google AI Studio by tapping the link below:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable { openExternalUrl("https://aistudio.google.com/apikey") }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("google_ai_studio_link"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Google AI Studio",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            ),
                            color = ElectricCyan
                        )
                        Text(
                            text = "opens in your browser",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    ApiKeyHelpStep(
                        number = 2,
                        text = "Sign in with your Google account (a free Gmail account is enough)."
                    )
                    ApiKeyHelpStep(
                        number = 3,
                        text = "Click \"Create API key\", then choose an existing Google Cloud project or create a new one."
                    )
                    ApiKeyHelpStep(
                        number = 4,
                        text = "Google will show you the generated key. Copy it with the copy button next to it."
                    )
                    ApiKeyHelpStep(
                        number = 5,
                        text = "Paste the key in the API Key field above and tap \"Save API Key\". Leave the model as default unless you want a different one."
                    )

                    // Google requires a verified phone number before it will issue an API key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GoldenAmber.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                            .border(1.dp, GoldenAmber.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("api_key_verified_phone_warning"),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Important",
                            tint = GoldenAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "You need a verified phone number",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = GoldenAmber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Google requires a phone number verified on your Google account before it lets you create an API key. If key creation is blocked, add and verify a phone number in your Google account settings and try again.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextWhite,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Text(
                        text = "The key is stored only on your phone and is removed if you tap \"Delete App Data\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MintGreen
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showApiKeyHelpDialog = false },
                    modifier = Modifier.testTag("close_api_key_help_button")
                ) {
                    Text("Got it", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = null
        )
    }

    if (showModelConfigDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isValidatingModel) {
                    showModelConfigDialog = false
                    modelValidationErrorMessage = null
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AI Model Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Configure the Gemini model used for AI coaching, workout generation, and routine parsing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "💡 Default Model: gemini-3.5-flash-lite",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "If you don't type in anything (leave it blank), it automatically defaults to the current gemini-3.5-flash-lite model by Google.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GoldenAmber.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, GoldenAmber.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Model Selection Warning",
                                tint = GoldenAmber,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "⚠️ Model Compatibility Warning",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = GoldenAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Be aware that changing the model to a dumber one (that may not understand formatting requirements) may very well result in weird behaviour and unwanted bugs, and it is up to the user to be aware of them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = modelInputText,
                        onValueChange = {
                            modelInputText = it
                            modelValidationErrorMessage = null
                        },
                        label = { Text("Gemini Model Name") },
                        placeholder = { Text("gemini-3.5-flash-lite", color = TextMuted) },
                        singleLine = true,
                        colors = profileFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_model_name_input"),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showModelsListDropdown = !showModelsListDropdown
                                    if (showModelsListDropdown) {
                                        viewModel.loadAvailableModels()
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("show_available_models_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Show Available Gemini & Gemma Models",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = showModelsListDropdown,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .testTag("available_models_popup_card"),
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AVAILABLE MODELS (${availableModels.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricCyan,
                                        fontSize = 11.sp
                                    )

                                    if (isLoadingModels) {
                                        CircularProgressIndicator(
                                            color = ElectricCyan,
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp
                                        )
                                    } else {
                                        Text(
                                            text = "Tap to select",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(availableModels, key = { it.apiName }) { model ->
                                        val isSelected = modelInputText.trim().equals(model.apiName, ignoreCase = true)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    modelInputText = model.apiName
                                                    showModelsListDropdown = false
                                                    modelValidationErrorMessage = null
                                                }
                                                .testTag("select_model_${model.apiName}"),
                                            color = if (isSelected) ElectricCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan) else null
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = model.displayName,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = TextWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = model.apiName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (model.isGemma) GoldenAmber else ElectricCyan,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (model.isGemma) GoldenAmber.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (model.isGemma) "Gemma" else "Gemini",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (model.isGemma) GoldenAmber else ElectricCyan
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (modelValidationErrorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FireRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, FireRed, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .testTag("ai_model_validation_error")
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = FireRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = modelValidationErrorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (isValidatingModel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ElectricCyan,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Validating model with Gemini API...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            showModelConfigDialog = false
                            modelValidationErrorMessage = null
                        },
                        enabled = !isValidatingModel,
                        modifier = Modifier.testTag("cancel_model_button")
                    ) {
                        Text("Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isValidatingModel = true
                                modelValidationErrorMessage = null
                                val result = viewModel.validateAndSaveGeminiModel(modelInputText)
                                isValidatingModel = false
                                if (result.isSuccess) {
                                    showModelConfigDialog = false
                                    modelValidationErrorMessage = null
                                } else {
                                    modelValidationErrorMessage = result.exceptionOrNull()?.message
                                        ?: "Could not verify model. Please check the model name or API key."
                                }
                            }
                        },
                        enabled = !isValidatingModel,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_model_button")
                    ) {
                        if (isValidatingModel) {
                            CircularProgressIndicator(
                                color = DarkBg,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...", color = DarkBg, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Save Model", color = DarkBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = null
        )
    }
}

/**
 * One numbered line of the "How to get an API key" guide.
 */
@Composable
private fun ApiKeyHelpStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextWhite,
            lineHeight = 17.sp
        )
    }
}

/**
 * One labelled on/off row of the preferences card.
 */
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBg,
                checkedTrackColor = MintGreen,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceVariant,
                uncheckedBorderColor = DarkBorder
            )
        )
    }
}

/**
 * "Revert to original database".
 *
 * The escape hatch for the low level editor. Once an athlete can edit the shipped library, they can
 * get it wrong, and they need a way back that does not cost them the exercises they created.
 *
 * The two switches default to keeping both, which makes the plain action "restore the library" rather
 * than "delete my work". Turning them off is the extra step, and the dialog restates the counts before
 * anything is removed.
 */
@Composable
private fun RevertCatalogCard(
    onRevert: (keepUserCreated: Boolean, keepAiCreated: Boolean) -> Unit
) {
    var keepUserCreated by remember { mutableStateOf(true) }
    var keepAiCreated by remember { mutableStateOf(true) }
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("revert_catalog_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = GoldenAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Revert to Original Database",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Restores every IronPulse library exercise to the values it shipped with, " +
                    "undoing any library edits. Use this if an edit went wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "KEEP AFTER REVERTING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            SettingSwitchRow(
                title = "My custom exercises",
                subtitle = "Exercises you created by hand",
                checked = keepUserCreated,
                onCheckedChange = { keepUserCreated = it },
                tag = "revert_keep_user_created"
            )
            SettingSwitchRow(
                title = "AI generated exercises",
                subtitle = "Exercises the AI parser added",
                checked = keepAiCreated,
                onCheckedChange = { keepAiCreated = it },
                tag = "revert_keep_ai_created"
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (keepUserCreated && keepAiCreated) {
                    "Both are kept, so only the library is restored."
                } else {
                    "Turning a switch off deletes those exercises permanently. Your logged workouts " +
                        "stay exactly as they are."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (keepUserCreated && keepAiCreated) MintGreen else FireRed
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { showConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldenAmber.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("revert_catalog_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = GoldenAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Revert to Original Database", color = GoldenAmber, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FireRed,
                    modifier = Modifier.size(26.dp)
                )
            },
            title = {
                Text(
                    "Revert to the original database?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Every IronPulse library exercise goes back to the values it shipped " +
                            "with.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite
                    )
                    BulletLine(
                        "Your workouts, logged sets, routines, weigh-ins and summaries are not touched.",
                        MintGreen
                    )
                    BulletLine(
                        if (keepUserCreated) {
                            "Your custom exercises are kept."
                        } else {
                            "Your custom exercises are deleted."
                        },
                        if (keepUserCreated) MintGreen else FireRed
                    )
                    BulletLine(
                        if (keepAiCreated) {
                            "AI generated exercises are kept."
                        } else {
                            "AI generated exercises are deleted."
                        },
                        if (keepAiCreated) MintGreen else FireRed
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onRevert(keepUserCreated, keepAiCreated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_revert_catalog_button")
                ) {
                    Text("Revert", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirm = false },
                    modifier = Modifier.testTag("cancel_revert_catalog_button")
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun BulletLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
