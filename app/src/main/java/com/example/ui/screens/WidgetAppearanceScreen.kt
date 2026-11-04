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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AndroidWidgetKind
import com.example.data.model.AndroidWidgetThemes
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
                weeklyTitle = weeklyTitle
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
    weeklyTitle: String
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streakAlive) palette.danger else palette.textDisabled,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            currentStreak.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        Text(
                            if (streakAlive) "current workout streak" else "streak broken",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                }
                Text(
                    "Best: $bestStreak workout days",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }

            AndroidWidgetKind.WEEKLY -> {
                Text("LAST 7 DAYS", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { index ->
                        val active = index % 3 == 0 || index == 6
                        Box(
                            modifier = Modifier
                                .size(27.dp)
                                .background(
                                    if (active) palette.accentSecondary else palette.surfaceVariant,
                                    RoundedCornerShape(7.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) palette.accentSecondary else palette.border,
                                    RoundedCornerShape(7.dp)
                                )
                        )
                    }
                }
                Text(
                    if (weeklyWorkoutDay) "Today is $weeklyTitle" else "Today is a rest day",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
                Box(
                    modifier = Modifier
                        .background(palette.accentPrimary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (weeklyWorkoutDay) "Start workout" else "Start another workout",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onAccent
                    )
                }
            }

            AndroidWidgetKind.PROFILE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(palette.accentPrimary.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            profileName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.accentPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            profileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        Text("Workout Profile", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PreviewMetric("Weight", weightText, palette)
                    PreviewMetric("Height", heightText, palette)
                    PreviewMetric("BMI", bmiText, palette)
                }
            }
        }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String, palette: AppPalette) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = palette.textPrimary
        )
    }
}
