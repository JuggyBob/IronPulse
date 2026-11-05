/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActivityHeatmap
import com.example.data.model.AndroidWidgetKind
import com.example.data.model.AndroidWidgetThemes
import com.example.data.model.HomeDashboard
import com.example.data.model.LocalDateKey
import com.example.data.model.ProfileMetrics
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.theme.AppPalette
import com.example.ui.theme.AppPalettes
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Independent appearance settings for the three native launcher widgets.
 *
 * The previews are Compose drawings of the same layouts the RemoteViews providers render, using
 * the same palette tokens. Changing one widget never changes the other two.
 */
@Composable
fun WidgetAppearanceScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themes by viewModel.androidWidgetThemes.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val streak by viewModel.streakSummary.collectAsStateWithLifecycle()
    val activeDays by viewModel.activeRoutineDays.collectAsStateWithLifecycle()
    val workoutHistory by viewModel.workoutHistory.collectAsStateWithLifecycle()
    val weeklyPreviewDays = remember(workoutHistory) {
        val today = HomeDashboard.startOfDay(System.currentTimeMillis())
        val trained = workoutHistory
            .map { HomeDashboard.startOfDay(it.session.startTime) }
            .toSet()
        (6 downTo 0).map { offset ->
            val day = ActivityHeatmap.addDays(today, -offset)
            val label = SimpleDateFormat("EEE", Locale.getDefault())
                .format(Date(day))
                .take(1)
            label to trained.contains(day)
        }
    }
    val todayPlan = activeDays.firstOrNull {
        it.weekday == LocalDateKey.isoDayOfWeek(LocalDateKey.keyOf(System.currentTimeMillis())) &&
            it.exercises.isNotEmpty()
    }
    val weeklyWorkoutDay = todayPlan != null
    val weeklyTitle = todayPlan?.let { it.dayTitle.ifBlank { it.dayName } }.orEmpty()
    val unit = WeightUnit.fromLabel(profile.preferredUnit)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(ElectricCyan.copy(alpha = 0.12f), CircleShape)
                        .testTag("widget_appearance_back"),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ElectricCyan)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Android Widgets",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        "Each launcher widget has its own appearance",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Add widgets from your launcher",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Long-press your home screen and open the widget picker, then choose Workout Streak, Weekly Workout Overview or Workout Profile. The theme selected below applies to every installed copy of that widget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        AndroidWidgetKind.entries.forEach { kind ->
            item(key = kind.storageKey) {
                WidgetAppearanceCard(
                    kind = kind,
                    selected = themes.forKind(kind),
                    themes = themes,
                    profileName = profile.name.ifBlank { "Athlete" },
                    weightText = WeightConversion.format(profile.currentWeightKg, unit),
                    heightText = "${profile.heightCm.roundToInt()} cm",
                    bmiText = ProfileMetrics.bmiText(profile.currentWeightKg, profile.heightCm),
                    currentStreak = streak.currentStreak,
                    bestStreak = streak.bestStreak,
                    streakAlive = streak.isAlive,
                    weeklyWorkoutDay = weeklyWorkoutDay,
                    weeklyTitle = weeklyTitle,
                    weeklyDays = weeklyPreviewDays,
                    onSelect = { mode -> viewModel.setAndroidWidgetTheme(kind, mode) }
                )
            }
        }
    }
}

@Composable
private fun WidgetAppearanceCard(
    kind: AndroidWidgetKind,
    selected: ThemeMode,
    themes: AndroidWidgetThemes,
    profileName: String,
    weightText: String,
    heightText: String,
    bmiText: String,
    currentStreak: Int,
    bestStreak: Int,
    streakAlive: Boolean,
    weeklyWorkoutDay: Boolean,
    weeklyTitle: String,
    weeklyDays: List<Pair<String, Boolean>>,
    onSelect: (ThemeMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("widget_appearance_${kind.storageKey}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                kind.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
            Text(kind.description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(12.dp))
            WidgetPreview(
                kind = kind,
                palette = AppPalettes.forMode(selected),
                profileName = profileName,
                weightText = weightText,
                heightText = heightText,
                bmiText = bmiText,
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                streakAlive = streakAlive,
                weeklyWorkoutDay = weeklyWorkoutDay,
                weeklyTitle = weeklyTitle,
                weeklyDays = weeklyDays
            )
            Spacer(Modifier.height(12.dp))
            Text("Appearance", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    ThemeMode.LIGHT,
                    ThemeMode.DARK,
                    ThemeMode.AMOLED,
                    ThemeMode.IRONPULSE
                ).forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = {
                            Text(
                                if (mode == ThemeMode.IRONPULSE) "IronPulse Theme" else mode.displayName,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("widget_theme_${kind.storageKey}_${mode.storageKey}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan,
                            selectedLabelColor = com.example.ui.theme.OnAccent,
                            labelColor = TextMuted
                        )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(Modifier.height(6.dp))
            Text(
                if (selected == ThemeMode.AMOLED) {
                    "AMOLED uses true black for the widget background."
                } else {
                    "This choice is independent of the app theme and the other two widgets."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun WidgetPreview(
    kind: AndroidWidgetKind,
    palette: AppPalette,
    profileName: String,
    weightText: String,
    heightText: String,
    bmiText: String,
    currentStreak: Int,
    bestStreak: Int,
    streakAlive: Boolean,
    weeklyWorkoutDay: Boolean,
    weeklyTitle: String,
    weeklyDays: List<Pair<String, Boolean>>
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, shape)
            .border(1.dp, palette.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (kind) {
            AndroidWidgetKind.STREAK -> {
                // Same vertical hierarchy as the native widget: title, flame, count, label, best.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "WORKOUT STREAK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = palette.textSecondary
                    )
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streakAlive) palette.danger else palette.textDisabled,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(40.dp)
                    )
                    Text(
                        currentStreak.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.textPrimary
                    )
                    Text(
                        if (streakAlive) "workout days" else "streak broken",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    Text(
                        "Best: $bestStreak workout days",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }

            AndroidWidgetKind.WEEKLY -> {
                // Same structure as the native widget: the full seven-day strip on the first line,
                // then today's workout and its action button.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        weeklyDays.forEach { (label, active) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .background(
                                        if (active) palette.accentSecondary else palette.surfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) palette.accentSecondary else palette.border,
                                        RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (active) palette.onAccent else palette.textSecondary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (weeklyWorkoutDay) "Today: $weeklyTitle" else "Today is a rest day",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(palette.accentPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (weeklyWorkoutDay) "Start" else "Open",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = palette.onAccent
                            )
                        }
                    }
                }
            }

            AndroidWidgetKind.PROFILE -> {
                // Same four-card row as the native widget: every card centres its own text.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.25f)
                            .background(palette.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(palette.accentPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                profileName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = palette.onAccent
                            )
                        }
                        Text(
                            profileName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            "Profile",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    PreviewMetricCard("WEIGHT", weightText, palette)
                    PreviewMetricCard("HEIGHT", heightText, palette)
                    PreviewMetricCard("BMI", bmiText, palette)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PreviewMetricCard(
    label: String,
    value: String,
    palette: AppPalette
) {
    Column(
        modifier = Modifier
            .weight(0.95f)
            .background(palette.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = palette.textPrimary,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
