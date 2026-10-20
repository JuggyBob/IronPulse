package com.example.ui.screens

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import coil.compose.SubcomposeAsyncImage
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
import com.example.ui.components.AppVersionFooter
import com.example.ui.components.AvatarCropper
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.UserProfile
import com.example.ui.components.SupportProjectCard
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
import com.example.util.ImageCropper
import com.example.util.ProfileMediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Settings

/**
 * Profile tab: the athlete's own metrics, the body weight timeline and the benchmark records.
 *
 * Everything that configures the app (API key, AI model, units, rest timer alerts,
 * data deletion and supporting the project) lives in the settings page behind the gear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weightLogs by viewModel.weightLogs.collectAsStateWithLifecycle()
    val benchmarkPushUps by viewModel.benchmarkPushUps.collectAsStateWithLifecycle()
    val benchmarkPullUps by viewModel.benchmarkPullUps.collectAsStateWithLifecycle()
    val benchmarkBulgarianSplitSquat by viewModel.benchmarkBulgarianSplitSquat.collectAsStateWithLifecycle()
    val benchmarkPushUpsAddedWeight by viewModel.benchmarkPushUpsAddedWeight.collectAsStateWithLifecycle()
    val benchmarkPullUpsAddedWeight by viewModel.benchmarkPullUpsAddedWeight.collectAsStateWithLifecycle()
    val benchmarkBulgarianSplitSquatAddedWeight by viewModel.benchmarkBulgarianSplitSquatAddedWeight.collectAsStateWithLifecycle()
    val sortedWeightLogs = remember(weightLogs) { weightLogs.sortedByDescending { it.timestamp } }
    val bodyWeightChartPoints by viewModel.bodyWeightChartPoints.collectAsStateWithLifecycle()

    // Weights are stored in kilograms; this is the unit the athlete reads and types in
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()

    // The profile picture, as a path inside the app's own storage.
    val avatarPath by viewModel.profileAvatarPath.collectAsStateWithLifecycle()
    // The dialog that asks whether to change or remove the picture, shown only when there is one.
    var showAvatarActionsDialog by remember { mutableStateOf(false) }
    var avatarImportFailed by remember { mutableStateOf(false) }
    // The picture the athlete picked, waiting to be framed.
    var pendingAvatarCrop by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            // The picture is read into memory and framed before anything is stored: the avatar is
            // shown in a circle, so what is kept is the square the athlete chose, not the whole
            // photo. The bytes are copied into the app's own storage afterwards, because the
            // picker's grant is revoked as soon as the picker closes.
            val decoded = ImageCropper.decode(context, uri)
            if (decoded != null) {
                pendingAvatarCrop = decoded
            } else {
                avatarImportFailed = true
            }
        }
    )

    // Takes the framed picture and stores it.
    fun acceptCrop(bitmap: Bitmap) {
        val localPath = ProfileMediaStore.write(context, bitmap)
        pendingAvatarCrop = null
        if (localPath != null) {
            viewModel.setProfileAvatar(localPath)
        } else {
            avatarImportFailed = true
        }
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAiDataInfoDialog by remember { mutableStateOf(false) }
    var showLogWeightDialog by remember { mutableStateOf(false) }

    // The weigh-in history starts collapsed: a year of logs is a wall of cards.
    var weighInsExpanded by remember { mutableStateOf(false) }

    val weightDateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

    // Weigh-in dialog inputs
    var newWeightInput by remember { mutableStateOf("") }
    var weightNoteInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header, with the gear icon that opens the settings page
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Athlete Profile & Metrics",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Body weight timeline and physical stats",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .background(ElectricCyan.copy(alpha = 0.12f), CircleShape)
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }


        // Profile Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The avatar. Tapping it picks a picture, or asks what to do with the one
                        // that is already there. The copy is kept in the app's own storage, so it
                        // survives both an app restart and the athlete deleting the original from
                        // their gallery.
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                                .border(1.5.dp, ElectricCyan, CircleShape)
                                .clickable {
                                    if (avatarPath.isBlank()) {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        showAvatarActionsDialog = true
                                    }
                                }
                                .testTag("profile_avatar_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarPath.isNotBlank()) {
                                SubcomposeAsyncImage(
                                    model = File(avatarPath),
                                    contentDescription = "Profile picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.name.ifBlank { "Athlete" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${userProfile.fitnessLevel} • ${userProfile.age} yrs • ${userProfile.heightCm.toInt()} cm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }

                        // Only the pen lives next to the athlete name. The explanation of the
                        // shared data belongs to the benchmark records below, where it is about.
                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid stats
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricBox(
                            label = "CURRENT WEIGHT",
                            value = WeightConversion.format(userProfile.currentWeightKg, weightUnit),
                            color = ElectricCyan,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        ProfileMetricBox(
                            label = "TARGET WEIGHT",
                            value = WeightConversion.format(userProfile.targetWeightKg, weightUnit),
                            color = MintGreen,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        val bmi = calculateBmi(userProfile.currentWeightKg, userProfile.heightCm)
                        ProfileMetricBox(
                            label = "BMI",
                            value = "%.1f".format(bmi),
                            color = GoldenAmber,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Target delta (the difference is converted as well, not just its label)
                    val diff = userProfile.targetWeightKg - userProfile.currentWeightKg
                    val diffText = WeightConversion.format(diff, weightUnit, withUnit = false)
                    val deltaText = if (diff > 0) {
                        "+$diffText ${weightUnit.suffix} to target (Bulking / Strength Surplus)"
                    } else if (diff < 0) {
                        "$diffText ${weightUnit.suffix} to target (Cutting / Lean Deficit)"
                    } else {
                        "Target reached! Maintenance phase."
                    }
                    Text(
                        text = "Goal Strategy: $deltaText",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (diff >= 0) MintGreen else GoldenAmber
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // === BODYWEIGHT BENCHMARK RECORDS (READ ONLY) ===
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BENCHMARK RECORDS",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Text(
                                text = "Best bodyweight reps ever logged • shared with the AI for tailored advice",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { showAiDataInfoDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("benchmark_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About benchmark records",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fixed three, never editable: the values come from the logged workouts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BenchmarkRecordBox(
                            label = "Push Ups",
                            reps = benchmarkPushUps,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("benchmark_push_ups")
                        )
                        BenchmarkRecordBox(
                            label = "Pull Ups",
                            reps = benchmarkPullUps,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("benchmark_pull_ups")
                        )
                        BenchmarkRecordBox(
                            label = "Single Leg Squat",
                            reps = benchmarkBulgarianSplitSquat,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("benchmark_single_leg_split_squat")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // The load each record was done at, spelled out under the boxes rather than
                    // squeezed into them. Three numbers across a row is already tight, and
                    // "12 reps + 10 kg" would not fit at that width without shrinking the digits
                    // the athlete is actually here to read.
                    Text(
                        text = "Load on each record: Push-ups " +
                                loadedLabel(benchmarkPushUpsAddedWeight, weightUnit) +
                                ", Pull-ups " + loadedLabel(benchmarkPullUpsAddedWeight, weightUnit) +
                                ", Single leg squat " +
                                loadedLabel(benchmarkBulgarianSplitSquatAddedWeight, weightUnit) +
                                ".",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.testTag("benchmark_loads_summary")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Single leg squat = the best set of Dumbbell Single Leg Split Squat, " +
                                "or any single leg split squat variant. Every set counts, loaded or " +
                                "bodyweight, and a heavier set is the record even with fewer reps. " +
                                "Records are read-only and never drop: deleting the workout that set " +
                                "one keeps it here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        // Body Weight Timeline Graph Section
        item {
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
                        // Prefilled in the athlete's unit; the value is converted back on save
                        newWeightInput = WeightConversion.format(
                            userProfile.currentWeightKg,
                            weightUnit,
                            withUnit = false
                        )
                        weightNoteInput = "Morning weigh-in"
                        showLogWeightDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("log_weight_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = DarkBg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Log Today",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DarkBg
                    )
                }
            }
        }

        // Weight Progression Line Chart
        item {
            // The stored kilograms are converted for the athlete's unit before they are drawn
            ProgressionLineChart(
                points = remember(bodyWeightChartPoints, weightUnit) {
                    bodyWeightChartPoints.map { point ->
                        point.copy(value = WeightConversion.fromKg(point.value, weightUnit))
                    }
                },
                yAxisUnit = weightUnit.suffix,
                lineColor = MintGreen,
                referenceLineValue = WeightConversion.fromKg(userProfile.targetWeightKg, weightUnit),
                referenceLineLabel = "Target ${WeightConversion.format(userProfile.targetWeightKg, weightUnit)}"
            )
        }

        // Weight Entries Log List. The heading carries the count and no longer carries the toggle:
        // the "view all" affordance lives in the summary box below it, next to the explanation of
        // what it does. It used to sit up here, next to a heading, which put the button for the thing
        // the athlete was looking at in a different place from the thing itself.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WEIGH-IN LOGS (${weightLogs.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("weigh_in_logs_heading")
                )
                // The way back out. Only offered once the list is open, because the way in is the
                // button inside the summary box below: one affordance per state rather than the same
                // control doing both jobs from two places.
                if (weighInsExpanded) {
                    TextButton(
                        onClick = { weighInsExpanded = false },
                        modifier = Modifier.testTag("toggle_weigh_in_logs_button")
                    ) {
                        Text(
                            text = "Hide all",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        // One branch, not two. The collapsed summary and the "nothing yet" message used to be
        // separate cards both keyed off an empty-ish state, so a new athlete with no weigh-ins got
        // two full width cards stacked saying there was nothing, which is what the big empty row
        // under the heading was. The summary is now a single compact line that doubles as the empty
        // state, and it only renders when there is at least one entry to talk about.
        if (weightLogs.isNotEmpty() && !weighInsExpanded) {
            item {
                // The same boxed style the "Support the Project" card uses: a surface with a hairline
                // border, a round icon badge, a bold heading, the explanation, and the action.
                //
                // This used to be a bare line of text sitting between two cards, which is what read
                // as being in a weird position. Giving it the card's own container puts it in the
                // same place visually that it was already in structurally: beside the other panel on
                // the page rather than floating loose under the log list.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weigh_in_logs_summary"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MintGreen.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, MintGreen.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                // A scales emoji rather than an icon: the brief asked for an
                                // emoticon, and it keeps this box visually related to the one it
                                // copies without competing with it for the same artwork.
                                Text(
                                    text = "\u2696\uFE0F",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${sortedWeightLogs.size} weigh-in" +
                                        "${if (sortedWeightLogs.size == 1) "" else "s"} recorded",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tap view all to view all weigh in logs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { weighInsExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DarkBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("view_all_weigh_in_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = DarkBg,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View all",
                                color = DarkBg,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        if (weightLogs.isEmpty()) {
            item {
                Text(
                    text = "No weigh-in logs recorded yet. Tap 'Log Today' above to start tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weigh_in_logs_empty")
                )
            }
        } else if (weighInsExpanded) {
            items(items = sortedWeightLogs, key = { it.id }, contentType = { "weight_log_card" }) { log ->
                // A weight changed in the "Edit Athlete Profile" popup is a profile update, not a
                // morning weigh-in: it is labelled differently so the two are never confused.
                val isProfileUpdate = log.note == MainViewModel.WEIGH_IN_NOTE_PROFILE_UPDATE
                val entryColor = if (isProfileUpdate) ElectricCyan else MintGreen
                val entryTypeLabel = when {
                    isProfileUpdate -> "Profile update"
                    log.note.isBlank() -> "Weigh-in"
                    else -> log.note
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(entryColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isProfileUpdate) {
                                        Icons.Default.Person
                                    } else {
                                        Icons.Default.MonitorWeight
                                    },
                                    contentDescription = null,
                                    tint = entryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${WeightConversion.format(log.weightKg, weightUnit)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Text(
                                    text = entryTypeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = entryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = weightDateFormat.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.deleteWeightLog(log) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_weigh_in_${log.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete weigh-in",
                                tint = FireRed.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Creator YouTube subscription support card
        item {
            SupportProjectCard()
        }

        // The build stamp, at the very bottom of the profile.
        item(key = "app_version_footer", contentType = "footer") {
            AppVersionFooter()
        }
    }

    // Explains the athlete data and the AI privacy warning
    if (showAiDataInfoDialog) {
        AthleteAiDataInfoDialog(onDismiss = { showAiDataInfoDialog = false })
    }

    // What to do with the picture that is already there.
    //
    // The picture used to carry a small cross in its corner, which was too small to see and too
    // easy to miss. Tapping the picture itself is the way in, and it asks what the athlete wants
    // to do rather than guessing.
    if (showAvatarActionsDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarActionsDialog = false },
            containerColor = DarkSurface,
            title = { Text("Profile picture", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You can pick a different picture, or take this one away. The copy in this app " +
                        "is deleted when you remove it; the original in your gallery is not touched.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAvatarActionsDialog = false
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("change_avatar_button")
                ) {
                    Text("Change picture", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showAvatarActionsDialog = false
                            viewModel.clearProfileAvatar()
                        },
                        modifier = Modifier.testTag("remove_avatar_button")
                    ) {
                        Text("Remove", color = FireRed)
                    }
                    TextButton(onClick = { showAvatarActionsDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            }
        )
    }

    // The picture the athlete picked, waiting to be framed.
    pendingAvatarCrop?.let { bitmap ->
        AvatarCropper(
            source = bitmap,
            onCrop = { cropped -> acceptCrop(cropped) },
            onCancel = { pendingAvatarCrop = null }
        )
    }

    // The picture could not be copied out of the gallery.
    if (avatarImportFailed) {
        AlertDialog(
            onDismissRequest = { avatarImportFailed = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    "Could not use that picture",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("avatar_import_failed_title")
                )
            },
            text = {
                Text(
                    "The file could not be copied into the app. Pick another one.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { avatarImportFailed = false }) {
                    Text("OK", color = ElectricCyan)
                }
            }
        )
    }

    // Log Today Weight Dialog
    if (showLogWeightDialog) {
        AlertDialog(
            onDismissRequest = { showLogWeightDialog = false },
            containerColor = DarkSurface,
            title = { Text("Log Body Weight", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter morning weigh-in:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newWeightInput,
                        onValueChange = { newWeightInput = it },
                        placeholder = { Text("e.g. 77.5", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = profileFieldColors(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = weightNoteInput,
                        onValueChange = { weightNoteInput = it },
                        placeholder = { Text("Note (e.g. Fasted, post-workout)", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = profileFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Typed in the athlete's unit, stored as kilograms
                        val parsed = WeightConversion.parseToKg(newWeightInput, weightUnit)
                        if (parsed != null) {
                            viewModel.addWeightLog(parsed, weightNoteInput)
                        }
                        showLogWeightDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Weigh-in", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogWeightDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(userProfile.name) }
        var heightInput by remember { mutableStateOf(userProfile.heightCm.toInt().toString()) }
        var currentWeightInput by remember {
            mutableStateOf(WeightConversion.format(userProfile.currentWeightKg, weightUnit, withUnit = false))
        }
        var targetWeightInput by remember {
            mutableStateOf(WeightConversion.format(userProfile.targetWeightKg, weightUnit, withUnit = false))
        }
        var ageInput by remember { mutableStateOf(userProfile.age.toString()) }
        var levelInput by remember { mutableStateOf(userProfile.fitnessLevel) }
        var unitInput by remember { mutableStateOf(userProfile.preferredUnit) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = DarkSurface,
            title = { Text("Edit Athlete Profile", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { input ->
                            // Hard limit: the athlete name can never exceed 15 characters
                            if (input.length <= MainViewModel.MAX_NAME_LENGTH) nameInput = input
                        },
                        label = { Text("Name (max ${MainViewModel.MAX_NAME_LENGTH} characters)") },
                        supportingText = {
                            Text(
                                text = "${nameInput.length}/${MainViewModel.MAX_NAME_LENGTH}",
                                color = TextMuted
                            )
                        },
                        isError = nameInput.length >= MainViewModel.MAX_NAME_LENGTH,
                        colors = profileFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text("Height (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = profileFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { ageInput = it },
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = profileFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currentWeightInput,
                            onValueChange = { currentWeightInput = it },
                            label = { Text("Current Wt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = profileFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetWeightInput,
                            onValueChange = { targetWeightInput = it },
                            label = { Text("Target Wt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = profileFieldColors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fitness level selector chips
                    Text("Fitness Level:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Beginner", "Intermediate", "Advanced").forEach { lvl ->
                            FilterChip(
                                selected = levelInput == lvl,
                                onClick = { levelInput = lvl },
                                label = { Text(lvl, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan,
                                    selectedLabelColor = DarkBg,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextWhite
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = heightInput.toDoubleOrNull() ?: userProfile.heightCm
                        // What was typed is in the athlete's unit and is stored as kilograms
                        val cw = WeightConversion.parseToKg(currentWeightInput, weightUnit)
                            ?: userProfile.currentWeightKg
                        val tw = WeightConversion.parseToKg(targetWeightInput, weightUnit)
                            ?: userProfile.targetWeightKg
                        val age = ageInput.toIntOrNull() ?: userProfile.age
                        viewModel.updateProfile(
                            userProfile.copy(
                                name = nameInput.trim().take(MainViewModel.MAX_NAME_LENGTH),
                                heightCm = h,
                                currentWeightKg = cw,
                                targetWeightKg = tw,
                                age = age,
                                fitnessLevel = levelInput,
                                preferredUnit = unitInput
                            )
                        )
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Changes", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun ProfileMetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * How a benchmark's load reads in the Profile UI.
 *
 * "bodyweight" when the set had nothing on the bar, otherwise the kilograms in the athlete's own
 * unit. Kept next to [BenchmarkRecordBox] because that is the only place it is used.
 */
private fun loadedLabel(addedKg: Int, unit: WeightUnit): String =
    if (addedKg <= 0) "bodyweight" else WeightConversion.format(addedKg.toDouble(), unit)

/**
 * One read-only benchmark record box.
 *
 * The user can only ever look at these: they are filled automatically from the logged workouts and
 * sent to the AI for tailored advice, never typed by hand.
 */
@Composable
private fun BenchmarkRecordBox(
    label: String,
    reps: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (reps > 0) {
            Text(
                text = "$reps",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MintGreen,
                maxLines = 1
            )
            Text(
                text = "reps",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        } else {
            Text(
                text = "--",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextDim,
                maxLines = 1
            )
            Text(
                text = "not logged",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
        }
    }
}

/**
 * Explains the athlete data and warns that it is sent to the AI.
 */
@Composable
private fun AthleteAiDataInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("athlete_data_info_dialog"),
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
                    text = "Your Athlete Data & The AI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "BENCHMARK RECORDS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan
                )
                Text(
                    text = "The three records above are your best bodyweight reps ever logged, taken " +
                            "automatically from your saved workouts. They are read-only: you cannot edit " +
                            "them, and deleting the workout where you set one will NOT remove the record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = "DATA SENT TO THE AI",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = GoldenAmber
                )
                Text(
                    text = "Your athlete profile (name, age, height, weight, target weight, skill level) " +
                            "and these three records are sent silently to Gemini with the AI coach chat, " +
                            "the routine generator and the routine prompter, so the advice and the programs " +
                            "are tailored to you. Nothing is shown on screen when this happens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = "They are never sent with the Gemini monthly summary, which only analyses one " +
                            "exercise at a time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_athlete_data_info_button")
            ) {
                Text("Got it", color = ElectricCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun calculateBmi(weightKg: Double, heightCm: Double): Double {
    if (heightCm <= 0) return 0.0
    val heightM = heightCm / 100.0
    return weightKg / (heightM * heightM)
}

@Composable
fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted
)
