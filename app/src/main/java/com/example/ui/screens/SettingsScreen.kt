package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.delay
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.backup.BackupOperation
import com.example.data.backup.BackupPreview
import com.example.data.backup.DataTransferState
import com.example.data.backup.UserDataBackupManager
import com.example.data.ai.AiEndpointValidator
import com.example.data.ai.AiModelDescriptor
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiToolMode
import com.example.data.model.UserProfile
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.components.ProgressionLineChart
import com.example.ui.components.AboutDialog
import com.example.ui.components.UpdateDialog
import com.example.util.ApkInstaller
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
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.OnAccent
import com.example.ui.theme.OnDanger
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
 * Everything that configures the app lives here: weight unit, rest timer alerts, the AI provider,
 * model and credentials, supporting the project and the data management actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenWidgetAppearance: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    // Read once at the top: the autosave interval lives in the Preferences card and the update
    // toggle in the IronPulse card, and both have to see the same settings.
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val dataTransferState by viewModel.dataTransferState.collectAsStateWithLifecycle()
    val aiWorkoutDataAccess by viewModel.aiWorkoutDataAccess.collectAsStateWithLifecycle()
    val postWorkoutAiSummaries by viewModel.postWorkoutAiSummaries.collectAsStateWithLifecycle()

    var showAiAccessInfoDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRestoreWidgetsDialog by remember { mutableStateOf(false) }
    var widgetsRestored by remember { mutableStateOf(false) }
    var widgetsRestoreFailed by remember { mutableStateOf(false) }

    LaunchedEffect(widgetsRestored, widgetsRestoreFailed) {
        if (widgetsRestored || widgetsRestoreFailed) {
            delay(4_000)
            widgetsRestored = false
            widgetsRestoreFailed = false
        }
    }

    // The update checker and the About sheet.
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    // The downloaded update that is waiting to be installed, if there is one.
    var pendingInstall by remember { mutableStateOf<java.io.File?>(null) }
    // Shown when Android will not let the app install a package yet.
    var installPermissionNeeded by remember { mutableStateOf(false) }

    // AI provider, key, endpoint and model configuration
    val activeAiProvider by viewModel.activeAiProvider.collectAsStateWithLifecycle()
    val aiConfiguration by viewModel.aiConfiguration.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
    val modelDiscoveryError by viewModel.modelDiscoveryError.collectAsStateWithLifecycle()
    var customApiKeyInput by remember { mutableStateOf("") }
    var customBaseUrlInput by remember { mutableStateOf("") }
    var showApiKeyConfirmedBadge by remember { mutableStateOf(false) }
    var showApiKeyValue by remember { mutableStateOf(false) }

    var showModelConfigDialog by remember { mutableStateOf(false) }
    var showApiKeyHelpDialog by remember { mutableStateOf(false) }
    var showModelsListDropdown by remember { mutableStateOf(false) }
    var modelInputText by remember { mutableStateOf("") }
    var isValidatingModel by remember { mutableStateOf(false) }
    var modelValidationErrorMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestMessage by remember { mutableStateOf<String?>(null) }
    var connectionTestSucceeded by remember { mutableStateOf(false) }
    var showEndpointConsentDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Switching provider must never leave another provider's key or endpoint in the fields.
    LaunchedEffect(activeAiProvider) {
        customApiKeyInput = ""
        customBaseUrlInput = ""
        modelInputText = ""
        showApiKeyConfirmedBadge = false
        showApiKeyValue = false
        connectionTestMessage = null
        connectionTestSucceeded = false
    }

    LaunchedEffect(aiConfiguration) {
        customApiKeyInput = aiConfiguration.apiKey
        customBaseUrlInput = aiConfiguration.baseUrl
        if (aiConfiguration.apiKey.isBlank()) {
            showApiKeyConfirmedBadge = false
        }
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // Storage Access Framework: the athlete chooses where the archive goes and which file to
    // restore. No broad storage permission is requested.
    val exportDataLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportUserData(
                uri = uri,
                suggestedName = UserDataBackupManager.suggestedFileName(System.currentTimeMillis())
            )
        }
    }
    val importDataLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.inspectUserDataImport(uri)
        }
    }

    LaunchedEffect(dataTransferState) {
        showImportDialog = dataTransferState is DataTransferState.ReadyToImport
    }

    /**
     * The update state and the About information, read from the view model.
     *
     * They are read here rather than inside the dialogs so the Settings screen can show the
     * current version in its own row.
     */
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val aboutState by viewModel.aboutState.collectAsStateWithLifecycle()

    /**
     * Opens the system installer for a downloaded update.
     *
     * The file is shared through the app's own file provider, so it never has to be readable by
     * anything else on the device.
     */
    val launchInstaller: (java.io.File) -> Unit = { file ->
        try {
            context.startActivity(ApkInstaller.installIntent(context, file))
        } catch (_: Exception) {
            // No installer on the device, or it refused the intent. The dialog says so.
            installPermissionNeeded = false
            showUpdateDialog = true
        }
    }

    /**
     * Asks Android for the permission to install a package, and installs as soon as it is given.
     *
     * The permission is re-checked when the athlete comes back, rather than assumed: they can
     * refuse it, and the app has to notice.
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstall
        if (file == null) return@rememberLauncherForActivityResult
        if (ApkInstaller.canRequestPackageInstalls(context)) {
            pendingInstall = null
            launchInstaller(file)
        } else {
            installPermissionNeeded = true
        }
    }

    /**
     * Starts the installation of the downloaded update.
     */
    val startInstall: () -> Unit = {
        val ready = updateState as? com.example.data.model.UpdateState.ReadyToInstall
        val file = ready?.file
        if (file != null) {
            if (ApkInstaller.canRequestPackageInstalls(context)) {
                launchInstaller(file)
            } else {
                // Android 8 and later need the permission before an app may install a package.
                pendingInstall = file
                try {
                    permissionLauncher.launch(ApkInstaller.installPermissionIntent(context))
                } catch (_: Exception) {
                    pendingInstall = null
                    installPermissionNeeded = true
                }
            }
        }
    }

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
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
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

                    ThemeSelector(
                        selected = appSettings.themeMode,
                        onSelect = { viewModel.setThemeMode(it) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(2.dp))

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
                                    color = if (isSelected) OnAccent else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(2.dp))

                    // Where the week begins. It decides the order the seven days are shown in and
                    // where a sequence of workouts with no day of its own starts. It never moves a
                    // workout that already sits on a weekday.
                    Text(
                        text = "Week starts on",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextWhite
                    )
                    Text(
                        text = "Used by the week planner, the weekly summaries and the Home schedule. " +
                            "A workout already on a day stays on that day.",
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
                        listOf(true to "Monday", false to "Sunday").forEach { (option, label) ->
                            val isSelected = appSettings.weekStartsOnMonday == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) ElectricCyan else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setWeekStartsOnMonday(option) }
                                    .padding(vertical = 10.dp)
                                    .testTag("settings_week_start_${label.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) OnAccent else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(2.dp))

                    // The workout in progress is written to disk on this cadence. A short interval
                    // costs almost nothing and loses less work; a long one keeps a phone in a
                    // pocket quieter. The athlete knows which matters more for their session.
                    Text(
                        text = "Autosave interval",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextWhite
                    )
                    Text(
                        text = "How often the workout in progress is saved. The timer in the workout " +
                            "flashes its cloud on every save.",
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
                        UserPreferencesRepository.AUTO_SAVE_INTERVAL_OPTIONS.forEach { seconds ->
                            val isSelected = appSettings.autoSaveIntervalSeconds == seconds
                            val label = if (seconds < 60) "${seconds}s" else "${seconds / 60}m"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) MintGreen else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setAutoSaveIntervalSeconds(seconds) }
                                    .padding(vertical = 10.dp)
                                    .testTag("settings_autosave_${seconds}s"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) OnAccent else TextMuted
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

                    // The visual finish. It also owns the bottom bar magnification lens: turning it
                    // off removes the glass surfaces and the lens together, leaving navigation
                    // exactly as it was.
                    SettingSwitchRow(
                        title = "Liquid Glass",
                        subtitle = if (appSettings.liquidGlassEnabled) {
                            "Translucent glass surfaces are on. Press and hold the navigation bar to magnify it."
                        } else {
                            "Glass surfaces and the navigation bar magnifier are off. The original look is restored."
                        },
                        checked = appSettings.liquidGlassEnabled,
                        onCheckedChange = { viewModel.setLiquidGlassEnabled(it) },
                        tag = "settings_liquid_glass"
                    )

                    // Optional because a punch-hole, notch or conventional three-button navigation
                    // bar can make an always-immersive layout feel cramped. On by default.
                    SettingSwitchRow(
                        title = "Fullscreen Mode",
                        subtitle = "Hide system status and navigation bars to maximize available screen space.",
                        checked = appSettings.fullscreenEnabled,
                        onCheckedChange = { viewModel.setFullscreenEnabled(it) },
                        tag = "settings_fullscreen_mode"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(2.dp))

                    // The athlete decides whether their profile may be sent to the AI provider at all.
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

        // AI data access and automatic summaries.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_data_access_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Data Access",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Control exactly what the AI Coach may read and change. Both options are off by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow AI Coach to access workout data",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            Text(
                                text = if (aiWorkoutDataAccess) {
                                    "The Coach can retrieve your workouts, schedule, exercise history and catalog, and can propose routine changes for approval."
                                } else {
                                    "The Coach can chat normally but cannot read or change any workout data."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = { showAiAccessInfoDialog = true },
                            modifier = Modifier.testTag("settings_ai_data_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "What the AI can access",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Switch(
                            checked = aiWorkoutDataAccess,
                            onCheckedChange = { viewModel.setAiWorkoutDataAccess(it) },
                            modifier = Modifier.testTag("settings_ai_workout_data_access"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAccent,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingSwitchRow(
                        title = "Post-workout AI summaries",
                        subtitle = if (postWorkoutAiSummaries) {
                            "After you finish a workout, IronPulse sends that session and its closest previous comparison to your AI provider and saves the result."
                        } else {
                            "No automatic summaries are generated. Previously saved summaries stay available."
                        },
                        checked = postWorkoutAiSummaries,
                        onCheckedChange = { viewModel.setPostWorkoutAiSummaries(it) },
                        tag = "settings_post_workout_ai_summaries"
                    )
                }
            }
        }

        // AI provider settings card
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
                            text = "AI Fitness Coach",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.weight(1f))
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
                    Text(
                        text = "The provider and model used for coaching, workout generation and routine parsing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiProviderId.entries.forEach { provider ->
                            FilterChip(
                                selected = provider == activeAiProvider,
                                onClick = { viewModel.selectAiProvider(provider) },
                                label = {
                                    Text(provider.displayName, fontSize = 11.sp)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                                    selectedLabelColor = ElectricCyan,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextMuted
                                ),
                                modifier = Modifier.testTag("ai_provider_${provider.storageKey}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = aiConfiguration.model.ifBlank {
                                    if (activeAiProvider == AiProviderId.GOOGLE) {
                                        "gemini-3.5-flash-lite (default)"
                                    } else {
                                        "Not selected"
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan
                            )
                        }
                        TextButton(
                            onClick = {
                                modelInputText = aiConfiguration.model
                                modelValidationErrorMessage = null
                                showModelsListDropdown = false
                                showModelConfigDialog = true
                                viewModel.loadAvailableModels()
                            },
                            modifier = Modifier.testTag("ai_model_info_button")
                        ) {
                            Text(
                                text = "Choose model",
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    val hasBuiltinKey = activeAiProvider == AiProviderId.GOOGLE &&
                        BuildConfig.GEMINI_API_KEY.isNotBlank() &&
                        BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
                    val isKeyConfirmed = aiConfiguration.apiKey.isNotBlank() || hasBuiltinKey
                    val statusText = when {
                        activeAiProvider.isCustomEndpoint && aiConfiguration.baseUrl.isBlank() ->
                            "Status: endpoint not configured"
                        activeAiProvider.isCustomEndpoint && !aiConfiguration.isEndpointAcknowledged() ->
                            "Status: endpoint needs privacy confirmation"
                        isKeyConfirmed -> "Status: API key saved"
                        activeAiProvider.isCustomEndpoint ->
                            "Status: endpoint ready; no key (optional for local servers)"
                        else -> "Status: not configured"
                    }
                    val statusColor = when {
                        statusText == "Status: API key saved" -> Color.Green
                        statusText.contains("ready") -> MintGreen
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
                        onValueChange = {
                            customApiKeyInput = it
                            connectionTestMessage = null
                        },
                        placeholder = {
                            Text(
                                when {
                                    activeAiProvider == AiProviderId.GOOGLE -> "AIzaSy..."
                                    activeAiProvider.isCustomEndpoint ->
                                        "API key (optional for a local endpoint)"
                                    else -> "API key"
                                },
                                color = TextMuted
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input"),
                        colors = profileFieldColors(),
                        singleLine = true,
                        visualTransformation = if (showApiKeyValue) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showApiKeyValue = !showApiKeyValue }) {
                                Text(
                                    text = if (showApiKeyValue) "Hide" else "Show",
                                    color = ElectricCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    )

                    if (activeAiProvider.isCustomEndpoint) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customBaseUrlInput,
                            onValueChange = {
                                customBaseUrlInput = it
                                connectionTestMessage = null
                            },
                            label = { Text("Endpoint base URL") },
                            placeholder = { Text("https://host/v1", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_custom_base_url_input"),
                            colors = profileFieldColors(),
                            singleLine = true
                        )

                        val endpointCheck = remember(customBaseUrlInput) {
                            if (customBaseUrlInput.isBlank()) {
                                null
                            } else {
                                AiEndpointValidator.validateCustomBaseUrl(customBaseUrlInput)
                            }
                        }
                        endpointCheck?.exceptionOrNull()?.let { error ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = error.message ?: "Invalid endpoint URL.",
                                style = MaterialTheme.typography.labelSmall,
                                color = FireRed,
                                lineHeight = 15.sp,
                                modifier = Modifier.testTag("ai_custom_endpoint_error")
                            )
                        }
                        val endpoint = endpointCheck?.getOrNull()
                        if (endpoint != null && !endpoint.isHttps) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Plain HTTP is allowed only for a local or private-network host. " +
                                    "Prompts and credentials are not encrypted on this connection.",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldenAmber,
                                lineHeight = 15.sp,
                                modifier = Modifier.testTag("ai_custom_http_warning")
                            )
                        }
                        val acknowledged = endpoint != null &&
                            endpoint.baseUrl == aiConfiguration.acknowledgedEndpoint &&
                            endpoint.baseUrl == AiEndpointValidator.endpointIdentity(aiConfiguration.baseUrl)
                        if (endpoint != null && !acknowledged) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GoldenAmber.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                    .border(1.dp, GoldenAmber.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Privacy & security confirmation required",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = GoldenAmber
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Nothing is sent to this endpoint until you review where your " +
                                            "prompts, athlete context and tool results go.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextWhite,
                                        lineHeight = 15.sp
                                    )
                                    TextButton(
                                        onClick = { showEndpointConsentDialog = true },
                                        modifier = Modifier.testTag("ai_custom_endpoint_warning")
                                    ) {
                                        Text("Review warning", color = GoldenAmber, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.saveAiBaseUrl(customBaseUrlInput, acknowledge = false)
                                connectionTestSucceeded = false
                                connectionTestMessage =
                                    "Endpoint saved. Confirm the privacy warning before sending data."
                            },
                            enabled = endpoint != null,
                            modifier = Modifier.testTag("save_ai_endpoint_button")
                        ) {
                            Text("Save endpoint", color = ElectricCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveAiApiKey(customApiKeyInput)
                                showApiKeyConfirmedBadge = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OnAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_api_key_button")
                        ) {
                            Text("Save API Key", color = OnAccent, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isTestingConnection = true
                                    connectionTestMessage = null
                                    val result = viewModel.saveAndTestAiConnection(
                                        apiKey = customApiKeyInput,
                                        baseUrl = if (activeAiProvider.isCustomEndpoint) customBaseUrlInput else null,
                                        acknowledgeEndpoint = activeAiProvider.isCustomEndpoint &&
                                            aiConfiguration.isEndpointAcknowledged()
                                    )
                                    isTestingConnection = false
                                    connectionTestSucceeded = result.isSuccess
                                    connectionTestMessage = result.fold(
                                        onSuccess = {
                                            "${activeAiProvider.displayName} answered with " +
                                                aiConfiguration.model.ifBlank { "the selected model" } + "."
                                        },
                                        onFailure = { it.message ?: "The connection test failed." }
                                    )
                                }
                            },
                            enabled = !isTestingConnection,
                            modifier = Modifier.testTag("test_ai_connection_button")
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    color = ElectricCyan,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...", color = ElectricCyan, fontSize = 12.sp)
                            } else {
                                Text("Save & test", color = ElectricCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = connectionTestMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(
                                    (if (connectionTestSucceeded) MintGreen else FireRed).copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    (if (connectionTestSucceeded) MintGreen else FireRed).copy(alpha = 0.6f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                                .testTag("ai_connection_test_result")
                        ) {
                            Text(
                                text = connectionTestMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextWhite,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showApiKeyConfirmedBadge || isKeyConfirmed,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.padding(top = 8.dp),
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
                                    text = "API key saved",
                                    color = Color.Green,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // The app itself: the version that is running, the update checker and the release notes.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IronPulse",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "version ${aboutState.installedVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Check for updates.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showUpdateDialog = true
                                viewModel.checkForUpdates()
                            }
                            .padding(vertical = 10.dp)
                            .testTag("check_for_updates_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = MintGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Check for updates",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            Text(
                                text = updateStatusText(updateState, aboutState),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    // The startup half of the update checker. The manual entry above always works;
                    // this decides whether opening the app is enough to hear about a release.
                    SettingSwitchRow(
                        title = "Check for updates on app start",
                        subtitle = if (appSettings.autoCheckUpdatesOnStart) {
                            "A new release is looked up when IronPulse opens, and its changes are shown once."
                        } else {
                            "Updates are only looked up when you tap Check for updates."
                        },
                        checked = appSettings.autoCheckUpdatesOnStart,
                        onCheckedChange = { viewModel.setAutoCheckUpdatesOnStart(it) },
                        tag = "settings_auto_check_updates"
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(6.dp))

                    // About and what's new.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAboutDialog = true
                                viewModel.loadAboutInfo()
                            }
                            .padding(vertical = 10.dp)
                            .testTag("about_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "About IronPulse",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextWhite
                            )
                            Text(
                                text = "The version, and what is new in the official releases",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
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

        // Home widget layout. This is configuration, not user data: resetting it restores the
        // stock dashboard and leaves every workout, weigh-in, routine and profile row alone.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_widgets_settings_card"),
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
                            text = "Home Widgets",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose, arrange and personalize the cards on your Home tab. " +
                            "Restoring the defaults brings back IronPulse's original dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showRestoreWidgetsDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldenAmber),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            GoldenAmber.copy(alpha = 0.65f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_default_widgets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = GoldenAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restore Default Home Widgets",
                            color = GoldenAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (widgetsRestored) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Default Home widgets restored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MintGreen,
                            modifier = Modifier.testTag("widgets_restored_message")
                        )
                    }

                    if (widgetsRestoreFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The Home widgets could not be restored. Your current setup was kept. Try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FireRed,
                            modifier = Modifier.testTag("widgets_restore_failed_message")
                        )
                    }
                }
            }
        }

        // Native launcher widgets have their own independent appearance settings.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("android_widgets_settings_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android Home Screen Widgets",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add the Workout Streak, Weekly Workout Overview and Workout Profile widgets from your launcher's widget picker. Their appearances are independent of the app theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenWidgetAppearance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_android_widget_appearance"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                    ) {
                        Text("Widget appearance", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Data Management: portable backup/restore plus the destructive reset.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_management_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                val transfer = dataTransferState
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Management",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create a portable .ironpulse backup of your IronPulse data, or replace this device's data with a backup you made earlier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val busy = transfer is DataTransferState.Running ||
                            transfer is DataTransferState.ReadyToImport
                        OutlinedButton(
                            onClick = {
                                exportDataLauncher.launch(
                                    UserDataBackupManager.suggestedFileName(System.currentTimeMillis())
                                )
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_user_data_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                importDataLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "application/x-zip-compressed"
                                    )
                                )
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_user_data_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen.copy(alpha = 0.65f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import", fontWeight = FontWeight.Bold)
                        }
                    }

                    when (transfer) {
                        is DataTransferState.Running -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (transfer.operation == BackupOperation.EXPORT) {
                                        "Exporting your data"
                                    } else {
                                        "Restoring your backup"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = transfer.progress.phase.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(7.dp))
                            LinearProgressIndicator(
                                progress = { transfer.progress.fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .testTag("data_transfer_progress"),
                                color = if (transfer.operation == BackupOperation.EXPORT) ElectricCyan else MintGreen,
                                trackColor = DarkSurfaceVariant
                            )
                            if (transfer.operation == BackupOperation.EXPORT) {
                                TextButton(
                                    onClick = { viewModel.cancelDataTransfer() },
                                    modifier = Modifier.testTag("cancel_data_transfer_button")
                                ) {
                                    Text("Cancel", color = TextMuted)
                                }
                            }
                        }

                        is DataTransferState.ReadyToImport -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "The backup passed validation and is ready for you to review.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MintGreen
                            )
                            TextButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.testTag("review_backup_button")
                            ) {
                                Text("Review backup", color = ElectricCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        is DataTransferState.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = transfer.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MintGreen,
                                modifier = Modifier.testTag("data_transfer_success")
                            )
                            Text(
                                text = transfer.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            TextButton(
                                onClick = { viewModel.dismissDataTransferMessage() },
                                modifier = Modifier.testTag("dismiss_data_transfer_button")
                            ) {
                                Text("Dismiss", color = TextMuted)
                            }
                        }

                        is DataTransferState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = transfer.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = FireRed,
                                modifier = Modifier.testTag("data_transfer_error")
                            )
                            if (transfer.destinationPreserved) {
                                Text(
                                    text = "Your existing IronPulse data was not replaced.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintGreen
                                )
                            }
                            TextButton(
                                onClick = { viewModel.dismissDataTransferMessage() },
                                modifier = Modifier.testTag("dismiss_data_transfer_button")
                            ) {
                                Text("Dismiss", color = TextMuted)
                            }
                        }

                        is DataTransferState.Cancelled -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = transfer.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.testTag("data_transfer_cancelled")
                            )
                            TextButton(
                                onClick = { viewModel.dismissDataTransferMessage() },
                                modifier = Modifier.testTag("dismiss_data_transfer_button")
                            ) {
                                Text("Dismiss", color = TextMuted)
                            }
                        }

                        DataTransferState.Idle -> Unit
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Backups contain personal data and are not encrypted. Store the file somewhere secure. API keys, your profile picture and exercise images/GIFs are never included; your current API keys and profile picture stay on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = FireRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete App Data",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Permanently erase workout sessions, logged sets, routines, weigh-ins, AI chat history and summaries, AI provider settings and API keys, your profile, rest timer presets, the Home widget layout and saved widget photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The exercise library is kept, including exercises created by the AI parser and your own custom exercises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )

                    Spacer(modifier = Modifier.height(14.dp))
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

    if (showAiAccessInfoDialog) {
        AiWorkoutAccessInfoDialog(onDismiss = { showAiAccessInfoDialog = false })
    }

    val pendingPreview = (dataTransferState as? DataTransferState.ReadyToImport)?.preview
    if (showImportDialog && pendingPreview != null) {
        ImportDataConfirmationDialog(
            preview = pendingPreview,
            onConfirm = {
                showImportDialog = false
                viewModel.confirmUserDataImport()
            },
            onDismiss = {
                showImportDialog = false
                viewModel.cancelPendingUserDataImport()
            }
        )
    }

    // The update checker.
    if (showUpdateDialog) {
        UpdateDialog(
            state = updateState,
            installedVersion = aboutState.installedVersion,
            releasesPageUrl = viewModel.releasesPageUrl(),
            onCheck = { viewModel.checkForUpdates() },
            onDownload = { viewModel.downloadUpdate() },
            onInstall = { startInstall() },
            onOpenReleasePage = { url -> openExternalUrl(url) },
            onDismiss = {
                showUpdateDialog = false
                viewModel.dismissUpdate()
            }
        )
    }

    // The About sheet, with the release notes of the official releases.
    if (showAboutDialog) {
        AboutDialog(
            state = aboutState,
            onDismiss = { showAboutDialog = false },
            onOpenReleasePage = { url -> openExternalUrl(url) },
            onCheckForUpdates = {
                showAboutDialog = false
                showUpdateDialog = true
                viewModel.checkForUpdates()
            }
        )
    }

    // Android will not install a package until the person allows this app to do so.
    if (installPermissionNeeded) {
        AlertDialog(
            onDismissRequest = { installPermissionNeeded = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Allow the installation",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Android only lets an app install a package when you allow it. " +
                        "Turn on \"Install unknown apps\" for IronPulse in the system settings, " +
                        "then come back and the update will install.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        installPermissionNeeded = false
                        try {
                            permissionLauncher.launch(ApkInstaller.installPermissionIntent(context))
                        } catch (_: Exception) {
                            // The settings screen is not there either, so there is nothing to do.
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open the settings", color = OnAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { installPermissionNeeded = false }) {
                    Text("Not now", color = TextMuted)
                }
            }
        )
    }

    // Restore Default Home Widgets confirmation.
    if (showRestoreWidgetsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreWidgetsDialog = false },
            containerColor = DarkSurface,
            modifier = Modifier.testTag("restore_widgets_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = FireRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restore default Home widgets?",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This permanently discards your current Home widget configuration:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Your widget selection and order\n" +
                            "• Your weekly goal target\n" +
                            "• Your photo widget image and its app-private copy",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Your workouts, weigh-ins, profile, routines, exercise library and " +
                            "every other setting are not touched.",
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
                        showRestoreWidgetsDialog = false
                        widgetsRestoreFailed = false
                        viewModel.restoreDefaultHomeWidgets { success ->
                            widgetsRestored = success
                            widgetsRestoreFailed = !success
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed, contentColor = OnDanger),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_restore_widgets")
                ) {
                    Text("Restore Defaults", color = OnDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreWidgetsDialog = false },
                    modifier = Modifier.testTag("cancel_restore_widgets")
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
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
                                    "• All saved AI monthly summaries\n" +
                                    "• Your AI provider settings, API keys and selected model\n" +
                                    "• Your custom rest timer presets and any running rest timer\n" +
                                    "• Your Home widget layout, customizations and saved widget photos",
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
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed, contentColor = OnDanger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete App Data", color = OnDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
    // Provider-aware guide: each service issues keys in its own console, and a custom endpoint
    // may not need a key at all.
    if (showApiKeyHelpDialog) {
        val help = aiKeyHelp(activeAiProvider)
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
                        text = help.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    // Step 1 - the link opens in the default browser, never inside the app
                    ApiKeyHelpStep(number = 1, text = help.firstStep)
                    if (help.consoleUrl != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable { openExternalUrl(help.consoleUrl) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("ai_provider_key_link"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = help.consoleName ?: help.consoleUrl,
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
                    }

                    help.steps.forEachIndexed { index, step ->
                        ApiKeyHelpStep(number = index + 2, text = step)
                    }

                    if (help.warningTitle != null && help.warningBody != null) {
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
                                    text = help.warningTitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = GoldenAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = help.warningBody,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextWhite,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = help.storageNote,
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
                        text = "Provider: ${activeAiProvider.displayName}. The model id is sent exactly " +
                            "as written, so a manually typed id is never rewritten.",
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
                                text = if (activeAiProvider == AiProviderId.GOOGLE) {
                                    "Default: gemini-3.5-flash-lite"
                                } else {
                                    "Live catalog: ${activeAiProvider.displayName}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (activeAiProvider == AiProviderId.GOOGLE) {
                                    "Leave the field blank to use Google's default model."
                                } else {
                                    "Refresh to load the provider's model list, or type a model id " +
                                        "manually. An empty catalog does not prevent a manual id."
                                },
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
                                    text = "Model Compatibility Warning",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = GoldenAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "A smaller or older model may not follow the app's formatting " +
                                        "requirements. Tools fall back to the text protocol automatically " +
                                        "when native tool calling is unavailable.",
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
                        label = { Text("Model ID") },
                        placeholder = {
                            Text(
                                if (activeAiProvider == AiProviderId.GOOGLE) "gemini-3.5-flash-lite"
                                else "model-id",
                                color = TextMuted
                            )
                        },
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
                                        contentDescription = "Show available models",
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
                                .heightIn(max = 260.dp)
                                .testTag("available_models_popup_card"),
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
                                        TextButton(
                                            onClick = { viewModel.loadAvailableModels() },
                                            modifier = Modifier.testTag("refresh_models_button")
                                        ) {
                                            Text("Refresh", color = ElectricCyan, fontSize = 10.sp)
                                        }
                                    }
                                }

                                modelDiscoveryError?.let { message ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(FireRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                            .testTag("model_discovery_error")
                                    ) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextWhite,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (!isLoadingModels && availableModels.isEmpty()) {
                                    Text(
                                        text = "No models were returned. Type a model id manually.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }

                                HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(availableModels, key = { it.id }) { model ->
                                        val isSelected = modelInputText.trim().equals(model.id, ignoreCase = true)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    modelInputText = model.id
                                                    showModelsListDropdown = false
                                                    modelValidationErrorMessage = null
                                                }
                                                .testTag("select_model_${model.id}"),
                                            color = if (isSelected) ElectricCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            border = if (isSelected) {
                                                androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan)
                                            } else {
                                                null
                                            }
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
                                                        text = model.id,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (model.isLive) ElectricCyan else TextMuted,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Column(horizontalAlignment = Alignment.End) {
                                                    val toolLabel = when (model.capabilities.nativeToolCalling) {
                                                        com.example.data.ai.AiCapability.SUPPORTED -> "Native tools"
                                                        com.example.data.ai.AiCapability.UNSUPPORTED -> "Text tools"
                                                        com.example.data.ai.AiCapability.UNKNOWN -> "Tools ?"
                                                    }
                                                    val toolColor = when (model.capabilities.nativeToolCalling) {
                                                        com.example.data.ai.AiCapability.SUPPORTED -> MintGreen
                                                        com.example.data.ai.AiCapability.UNSUPPORTED -> GoldenAmber
                                                        com.example.data.ai.AiCapability.UNKNOWN -> TextMuted
                                                    }
                                                    Text(
                                                        text = toolLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = toolColor
                                                    )
                                                    Text(
                                                        text = if (model.isLive) "Live" else "Known",
                                                        fontSize = 9.sp,
                                                        color = if (model.isLive) MintGreen else TextMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Tool calling",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiToolMode.entries.forEach { mode ->
                            FilterChip(
                                selected = aiConfiguration.toolMode == mode,
                                onClick = { viewModel.saveAiToolMode(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            AiToolMode.AUTO -> "Auto"
                                            AiToolMode.NATIVE -> "Native only"
                                            AiToolMode.COMPATIBILITY -> "Text fallback"
                                        },
                                        fontSize = 10.5.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                                    selectedLabelColor = ElectricCyan,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextMuted
                                ),
                                modifier = Modifier.testTag("ai_tool_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = when (aiConfiguration.toolMode) {
                            AiToolMode.AUTO -> "Try native tools first; fall back to the text protocol if the model rejects them."
                            AiToolMode.NATIVE -> "Only the provider's structured tool protocol. A model without it reports an error."
                            AiToolMode.COMPATIBILITY -> "Always use the bounded JSON text protocol, even if the model supports native tools."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )

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
                                text = "Testing with ${activeAiProvider.displayName}...",
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    isValidatingModel = true
                                    modelValidationErrorMessage = null
                                    val result = viewModel.validateAndSaveAiModel(modelInputText)
                                    isValidatingModel = false
                                    if (result.isSuccess) {
                                        showModelConfigDialog = false
                                        modelValidationErrorMessage = null
                                    } else {
                                        modelValidationErrorMessage = result.exceptionOrNull()?.message
                                            ?: "Could not verify the model. Check the model id and API key."
                                    }
                                }
                            },
                            enabled = !isValidatingModel,
                            modifier = Modifier.testTag("test_model_button")
                        ) {
                            Text("Test & save", color = ElectricCyan, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                viewModel.saveAiModel(modelInputText)
                                showModelConfigDialog = false
                                modelValidationErrorMessage = null
                            },
                            enabled = !isValidatingModel &&
                                (modelInputText.isNotBlank() || activeAiProvider == AiProviderId.GOOGLE),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OnAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_model_button")
                        ) {
                            Text("Save model", color = OnAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showEndpointConsentDialog) {
        val endpoint = AiEndpointValidator.validateCustomBaseUrl(customBaseUrlInput).getOrNull()
        AlertDialog(
            onDismissRequest = { showEndpointConsentDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Custom endpoint warning",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are about to send data to:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = endpoint?.baseUrl ?: customBaseUrlInput,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan
                    )
                    Text(
                        text = "• Your prompts and the athlete context you enabled in Settings.\n" +
                            "• Tool results such as exercise names, instructions and saved routines.\n" +
                            "• The API key, if one is set. It is sent only to this endpoint, never to another provider.\n" +
                            "• IronPulse does not disable TLS certificate checks and does not follow redirects from this endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    if (endpoint != null && !endpoint.isHttps) {
                        Text(
                            text = "This endpoint uses plain HTTP. Prompts and the key are not encrypted " +
                                "in transit. Only continue on a local or private network you control.",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldenAmber,
                            lineHeight = 15.sp
                        )
                    }
                    Text(
                        text = "Only continue if you control or trust this server.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GoldenAmber
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveAiBaseUrl(customBaseUrlInput, acknowledge = true)
                        showEndpointConsentDialog = false
                        connectionTestSucceeded = false
                        connectionTestMessage =
                            "Endpoint confirmed. Use Save & test to verify it before sending a real request."
                    },
                    modifier = Modifier.testTag("confirm_custom_endpoint")
                ) {
                    Text("I understand, use this endpoint", color = GoldenAmber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndpointConsentDialog = false },
                    modifier = Modifier.testTag("cancel_custom_endpoint")
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

/**
 * The line under the "Check for updates" row.
 *
 * It says what is known without making a second request: the version that is running, and what the
 * last check found.
 */
private fun updateStatusText(
    updateState: com.example.data.model.UpdateState,
    aboutState: com.example.data.model.AboutState
): String = when {
    updateState is com.example.data.model.UpdateState.Checking -> "Checking the official releases..."
    updateState is com.example.data.model.UpdateState.Available ->
        "Version ${updateState.release.versionText} is available"
    updateState is com.example.data.model.UpdateState.UpToDate ->
        "Version ${aboutState.installedVersion} is the latest version"
    updateState is com.example.data.model.UpdateState.Failed -> updateState.message
    aboutState.hasUpdate -> "Version ${aboutState.latestVersion} is available"
    aboutState.latestVersion != null -> "Version ${aboutState.installedVersion} is the latest version"
    else -> "Installed version ${aboutState.installedVersion}"
}

/**
 * One numbered line of the "How to get an API key" guide.
 */
/**
 * Provider-specific key instructions.
 *
 * The app has one AI layer, but the places that issue keys are genuinely different, so the help
 * dialog follows the active provider instead of pretending every service works like Google.
 */
private data class AiKeyHelp(
    val intro: String,
    val firstStep: String,
    val consoleName: String? = null,
    val consoleUrl: String? = null,
    val steps: List<String> = emptyList(),
    val warningTitle: String? = null,
    val warningBody: String? = null,
    val storageNote: String = "The key is stored only on your phone and is removed if you tap \"Delete App Data\"."
)

private fun aiKeyHelp(provider: AiProviderId): AiKeyHelp = when (provider) {
    AiProviderId.GOOGLE -> AiKeyHelp(
        intro = "IronPulse can use a Google AI Studio API key. It takes about a minute and unlocks the AI coach, the routine generator and the AI parser.",
        firstStep = "Open Google AI Studio by tapping the link below:",
        consoleName = "Google AI Studio",
        consoleUrl = "https://aistudio.google.com/apikey",
        steps = listOf(
            "Sign in with your Google account (a free Gmail account is enough).",
            "Click \"Create API key\", then choose an existing Google Cloud project or create a new one.",
            "Google will show you the generated key. Copy it with the copy button next to it.",
            "Paste the key in the API Key field above and tap \"Save & test\". Leave the model as default unless you want a different one."
        ),
        warningTitle = "You need a verified phone number",
        warningBody = "Google requires a phone number verified on your Google account before it lets you create an API key. If key creation is blocked, add and verify a phone number in your Google account settings and try again."
    )

    AiProviderId.OPENAI -> AiKeyHelp(
        intro = "OpenAI keys are created in the OpenAI platform dashboard. Usage is billed by OpenAI according to your account plan.",
        firstStep = "Open the OpenAI API keys page by tapping the link below:",
        consoleName = "OpenAI API keys",
        consoleUrl = "https://platform.openai.com/api-keys",
        steps = listOf(
            "Sign in or create an OpenAI account.",
            "Tap \"Create new secret key\", name it and copy the key immediately; it is shown only once.",
            "Paste the key in the API Key field above and tap \"Save & test\".",
            "Use \"Choose model\" above to pick from your live model list, or type a model id manually."
        ),
        storageNote = "The key is stored only on your phone and is sent only to api.openai.com. It is removed if you tap \"Delete App Data\"."
    )

    AiProviderId.ANTHROPIC -> AiKeyHelp(
        intro = "Anthropic keys are created in the Claude developer console. Usage is billed by Anthropic according to your account plan.",
        firstStep = "Open the Anthropic API keys page by tapping the link below:",
        consoleName = "Anthropic API keys",
        consoleUrl = "https://console.anthropic.com/settings/keys",
        steps = listOf(
            "Sign in or create an Anthropic account.",
            "Tap \"Create Key\", name it and copy the key immediately.",
            "Paste the key in the API Key field above and tap \"Save & test\".",
            "Use \"Choose model\" above to pick a Claude model, or type a model id manually."
        ),
        storageNote = "The key is stored only on your phone and is sent only to api.anthropic.com. It is removed if you tap \"Delete App Data\"."
    )

    AiProviderId.OPENROUTER -> AiKeyHelp(
        intro = "OpenRouter gives you one key for models from many vendors. Usage is billed by OpenRouter according to your account balance.",
        firstStep = "Open the OpenRouter keys page by tapping the link below:",
        consoleName = "OpenRouter API keys",
        consoleUrl = "https://openrouter.ai/keys",
        steps = listOf(
            "Sign in or create an OpenRouter account and add credit if needed.",
            "Tap \"Create Key\", name it and copy the key immediately.",
            "Paste the key in the API Key field above and tap \"Save & test\".",
            "Use \"Choose model\" above to search the live catalog, or type a model id such as vendor/model-name."
        ),
        storageNote = "The key is stored only on your phone and is sent only to openrouter.ai. It is removed if you tap \"Delete App Data\"."
    )

    AiProviderId.XAI -> AiKeyHelp(
        intro = "xAI keys are created in the xAI console. Usage is billed by xAI according to your account plan.",
        firstStep = "Open the xAI console by tapping the link below:",
        consoleName = "xAI console",
        consoleUrl = "https://console.x.ai/",
        steps = listOf(
            "Sign in or create an xAI account.",
            "Create an API key in the console and copy it immediately.",
            "Paste the key in the API Key field above and tap \"Save & test\".",
            "Use \"Choose model\" above to pick a Grok model, or type a model id manually."
        ),
        storageNote = "The key is stored only on your phone and is sent only to api.x.ai. It is removed if you tap \"Delete App Data\"."
    )

    AiProviderId.CUSTOM_OPENAI -> AiKeyHelp(
        intro = "A custom endpoint is any server that speaks the OpenAI chat-completions API, such as a local LLM runner or your own gateway. You control where your data goes.",
        firstStep = "Enter the endpoint base URL above, for example http://192.168.1.10:11434/v1 or https://ai.example.com/v1.",
        steps = listOf(
            "Start your local or hosted OpenAI-compatible server and make sure this phone can reach it.",
            "Tap \"Save endpoint\". The first use of a custom endpoint requires the privacy confirmation shown above.",
            "Most local servers need no key. If yours does, paste it in the API Key field and tap \"Save API Key\"; it is sent only to this endpoint.",
            "Tap \"Save & test\", then choose a discovered model or type its id manually."
        ),
        storageNote = "The endpoint and any key stay on your phone. HTTP is allowed only for loopback and private-network hosts; use HTTPS for public hosts. TLS certificate checks are never disabled."
    )
}

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
 * The theme picker.
 *
 * A plain menu rather than a set of preview cards: the whole app behind it changes the moment an
 * item is chosen, so the page itself is the preview. The selected row carries a check and an accent
 * label, and every item has its own test tag for the UI tests.
 */
@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextWhite
        )
        Text(
            text = "Applies immediately. Liquid Glass works with every theme.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkBg)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("settings_theme_selector"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = selected.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Choose theme",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(DarkSurface)
                    .testTag("settings_theme_menu")
            ) {
                ThemeMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = mode.displayName,
                                    color = if (mode == selected) ElectricCyan else TextWhite,
                                    fontWeight = if (mode == selected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(mode)
                        },
                        trailingIcon = {
                            if (mode == selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            }
                        },
                        modifier = Modifier.testTag("settings_theme_item_${mode.storageKey}")
                    )
                }
            }
        }
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
                checkedThumbColor = OnAccent,
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
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber, contentColor = OnAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_revert_catalog_button")
                ) {
                    Text("Revert", color = OnAccent, fontWeight = FontWeight.Bold)
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
private fun AiWorkoutAccessInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("ai_workout_access_info_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "What the AI Coach can access",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "THE AI COACH CAN RETRIEVE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MintGreen
                )
                BulletLine("Recent completed workouts and their sets, repetitions, weight, holds and cardio duration.", TextMuted)
                BulletLine("Your current weekly scheduled routine, including rest days and exercise order.", TextMuted)
                BulletLine("The exercise catalog, including your custom and AI-generated exercises.", TextMuted)
                BulletLine("Historical completed performance for an individual exercise.", TextMuted)
                BulletLine("Relevant retrieved information is sent to the AI provider you selected so it can answer the question.", TextMuted)

                Text(
                    text = "THE AI COACH CANNOT",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = FireRed
                )
                BulletLine("Access the database directly, run SQL, read arbitrary files, or change app settings.", TextMuted)
                BulletLine("Delete or modify completed workout history, your profile, body measurements, or existing catalog entries.", TextMuted)
                BulletLine("Create or modify exercises freely, or apply any change without an explicit approval.", TextMuted)

                Text(
                    text = "THE ONLY PERMITTED CHANGES",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = GoldenAmber
                )
                BulletLine("Propose a change to your weekly routine. It appears as a card in the conversation and is applied only after you press Grant permission.", TextMuted)
                BulletLine("If no existing exercise fits, the same approved proposal may create one AI-generated exercise and add it to that routine day in one transaction.", TextMuted)
                BulletLine("A proposal is one-time, scoped to the exact displayed change, and expires if the routine changes or permission is revoked.", TextMuted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_ai_workout_access_info")
            ) {
                Text("Got it", color = ElectricCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * The destructive confirmation for import.
 *
 * It spells out the replacement semantics before the athlete can proceed: this is not a merge, the
 * destination's local credentials and profile picture are kept, and the excluded exercise media
 * will not come back with the backup.
 */
@Composable
private fun ImportDataConfirmationDialog(
    preview: BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        modifier = Modifier.testTag("import_confirmation_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FireRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Replace all IronPulse data?", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This is a replacement, not a merge. Every workout, routine, weigh-in, AI conversation, saved summary, custom exercise, preference and Home customization currently on this device will be replaced by the backup's contents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite
                )
                Text(
                    text = "Backup: ${preview.fileName ?: "selected file"}\n" +
                        "Created: ${formatBackupDate(preview.exportedAtEpochMillis)}\n" +
                        "Source: IronPulse ${preview.appVersionName} (${preview.appVersionCode})\n" +
                        "Size: ${formatBackupBytes(preview.archiveSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    lineHeight = 17.sp
                )
                Text(
                    text = "Contents: ${preview.sectionCounts["workouts"] ?: 0} workout records, " +
                        "${preview.sectionCounts["routines"] ?: 0} routine records, " +
                        "${preview.sectionCounts["exercises"] ?: 0} custom/edited exercises, " +
                        "${preview.sectionCounts["ai"] ?: 0} AI records, " +
                        "${preview.sectionCounts["progress"] ?: 0} weigh-ins.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MintGreen,
                    lineHeight = 17.sp
                )
                Text(
                    text = "Kept on this device: your API keys and your current profile picture. " +
                        "Exercise images and GIF animations are not restored. Your current data cannot be recovered after this completes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    lineHeight = 17.sp
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
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FireRed, contentColor = OnDanger),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_import_data")
            ) {
                Text("Replace My Data", color = OnDanger, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_import_data")
            ) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

private fun formatBackupBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

private fun formatBackupDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))

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
