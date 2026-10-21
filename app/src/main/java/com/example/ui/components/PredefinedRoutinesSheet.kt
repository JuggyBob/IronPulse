package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Exercise
import com.example.data.model.PredefinedRoutines
import com.example.data.model.RoutineParser
import com.example.data.model.Weekday
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * The starter routine browser, opened from the square button next to "Create New Routine".
 *
 * A full window page, not a sheet.
 *
 * This used to be a bottom sheet: a rounded panel lifted over the AI coach screen with a tap-outside
 * target to dismiss it. It read as floating chrome, and the rounded corners were the wrong shape for
 * something that spans the whole screen — the panel sat against the bottom edge while its bottom
 * corners were cut away, which is precisely what "the edges and vertices are wrong" looked like. The
 * layout is a page now, and it is a page the whole way out: one background from the status bar to the
 * bottom, a header row that closes and goes back, and the list or the preview filling whatever is
 * left over. Nothing floats and nothing is clipped.
 *
 * Nothing here is saved until the athlete picks one. The point is to show the whole routine and
 * explain it before committing, because adopting a template and then finding out what is in it is a
 * worse experience than reading it first.
 */
@Composable
fun PredefinedRoutinesSheet(
    allExercises: List<Exercise>,
    onAdopt: (PredefinedRoutines.Template) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<PredefinedRoutines.Template?>(null) }

    // Fades in rather than sliding up. The sheet it replaces slid in from the bottom edge, which is
    // what made it read as a panel; a page has nowhere to slide from, so it just arrives.
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetSurface)
            .testTag("predefined_routines_sheet")
    ) {
        // Header. The X is on the list, and becomes a back arrow once a template is open, so the
        // route out is always in the same place rather than moving with the content.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (selected != null) selected = null else onDismiss()
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .testTag("predefined_routines_back")
            ) {
                Icon(
                    imageVector = if (selected != null) Icons.Default.ArrowBack else Icons.Default.Close,
                    contentDescription = if (selected != null) "Back to the list" else "Close",
                    tint = ElectricCyan,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (selected != null) "Routine details" else "Starter Routines",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                modifier = Modifier.testTag("predefined_routines_title")
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DarkBorder))

        AnimatedVisibility(visibleState = visible, enter = fadeIn()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val chosen = selected
                if (chosen == null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Pick one to copy into your routines, then edit it however you like.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        // The list of templates. Each card previews the day structure so the athlete
                        // can tell the three apart without opening anything.
                        //
                        // No height cap: the list is the page now, and it scrolls against the bottom
                        // of the window rather than inside a fixed 420 dp box.
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(PredefinedRoutines.all, key = { it.id }) { template ->
                                TemplateCard(
                                    template = template,
                                    allExercises = allExercises,
                                    onClick = { selected = template }
                                )
                            }
                        }
                    }
                } else {
                    TemplatePreview(
                        template = chosen,
                        allExercises = allExercises,
                        onBack = { selected = null },
                        onAdopt = { onAdopt(chosen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: PredefinedRoutines.Template,
    allExercises: List<Exercise>,
    onClick: () -> Unit
) {
    val missing = remember_missing(template, allExercises)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Borderless, and a tint of the panel rather than a separate surface. The old card had
            // its own background and a border, which against a differently coloured panel made the
            // panel look like two stacked sheets and left the rounded corners mismatched. A single
            // accent rule inside the panel reads as one list.
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("predefined_template_card_${template.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${template.daysPerWeek} days",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = template.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            template.days.forEach { day ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ElectricCyan.copy(alpha = 0.1f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = Weekday.shortName(day.weekday),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${missing.size} movement(s) are not in your catalog and will be added as " +
                    "custom exercises when you adopt this.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun TemplatePreview(
    template: PredefinedRoutines.Template,
    allExercises: List<Exercise>,
    onBack: () -> Unit,
    onAdopt: () -> Unit
) {
    // Scrolls the whole page, including the button. It used to be capped at 420 dp with the button
    // inside the cap, so on a long routine the call to action ended up out of reach.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = template.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextWhite
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = template.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip("${template.daysPerWeek} days a week")
            InfoChip(template.goal)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Suited to: ${template.suitedTo}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(12.dp))
        template.days.forEach { day ->
            Text(
                text = "${Weekday.name(day.weekday)} • ${day.focus}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            day.entries.forEach { entry ->
                val name = entry.substringBefore(" (")
                val prescription = entry.substringAfter(" (", "").removeSuffix(")")
                val inCatalog = RoutineParser.findMatchingExercise(name, allExercises) != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (inCatalog) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (inCatalog) MintGreen else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = prescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onAdopt,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DarkBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("adopt_predefined_routine_button")
        ) {
            Text(
                text = "Copy this routine",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ElectricCyan.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
    }
}

/**
 * Template movements the catalog cannot resolve.
 *
 * Surfaced in the preview rather than swallowed: a template that silently references a movement the
 * app does not have would create a misspelled custom exercise on adoption, which is exactly the kind
 * of broken default the athlete is trying to get away from.
 */
private fun remember_missing(
    template: PredefinedRoutines.Template,
    allExercises: List<Exercise>
): List<String> = template.days
    .flatMap { it.entries }
    .map { it.substringBefore(" (") }
    .filter { name -> RoutineParser.findMatchingExercise(name, allExercises) == null }
    .distinct()

/**
 * The single colour this page is painted in.
 *
 * Every surface uses it: the backdrop, the header, the list and the cards' surroundings. Keeping it
 * in one place is what makes it read as one surface; spreading the same value across several
 * literals is how a two-tone split appeared in the first place.
 */
private val SheetSurface = DarkBg
