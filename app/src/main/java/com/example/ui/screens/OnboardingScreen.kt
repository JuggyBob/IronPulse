package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Alignment
import com.example.ui.viewmodel.MainViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * First run landing pages.
 *
 * Shown once, to an athlete who has just installed the app, and never again. It exists because the
 * app used to invent an athlete ("Alex Stone", 180 cm, 77.5 kg) along with weigh-ins, workouts and
 * routines: invented history on a Progress tab is indistinguishable from real history, and it is
 * exactly the sort of thing an athlete cannot be expected to notice and delete.
 *
 * So there is no mock data. The first four pages explain what the app does, and the last two ask for
 * the profile the app genuinely needs, which then creates the first real weigh-in.
 */
@Composable
private fun OnboardingSurface(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) { content() }
}

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { OnboardingPage.count })
    val scope = rememberCoroutineScope()

    // Held in the composable rather than the view model: this is discarded the moment onboarding
    // ends, and nothing else in the app should ever read a half filled form.
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    // Each weight remembers the unit it was typed in, so switching the chips converts what is
    // already on screen. Without it an athlete who types 80 kg and then picks pounds is left looking
    // at "80", which is 35 kg, and that wrong number becomes the first weigh-in in the history.
    var currentWeight by remember { mutableStateOf("") }
    var currentWeightUnit by remember { mutableStateOf(weightUnit) }
    var targetWeight by remember { mutableStateOf("") }
    var targetWeightUnit by remember { mutableStateOf(weightUnit) }
    var fitnessLevel by remember { mutableStateOf("Beginner") }

    // Converted on unit change rather than on every keystroke: retyping the field mid-edit would
    // fight the athlete, and converting only when the unit actually changes keeps the number still
    // while they type it.
    LaunchedEffect(weightUnit) {
        currentWeight = convertTypedUnit(currentWeight, currentWeightUnit, weightUnit)
        currentWeightUnit = weightUnit
        targetWeight = convertTypedUnit(targetWeight, targetWeightUnit, weightUnit)
        targetWeightUnit = weightUnit
    }

    val profilePageIndex = OnboardingPage.count - 1
    val isProfilePage = pagerState.currentPage >= profilePageIndex

    // Everything the profile needs must be present before it can be saved. Height is the only field
    // that is not derivable, because every body weight percentage in the app depends on it.
    // The weights are parsed in whatever unit they currently read in, so an athlete who chose pounds
    // on this page gets kilograms in storage like everybody else.
    val parsedAge = age.toIntOrNull() ?: 0
    val parsedHeight = height.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedCurrentDisplay = currentWeight.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedTargetDisplay = targetWeight.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedCurrent = WeightConversion.toKg(parsedCurrentDisplay, weightUnit)
    val parsedTarget = WeightConversion.toKg(parsedTargetDisplay, weightUnit)
    val profileComplete = parsedAge in 13..120 &&
        parsedHeight in 50.0..260.0 &&
        parsedCurrent > 0.0 &&
        parsedTarget > 0.0

    OnboardingSurface {
        // Keyboard aware, exactly like the rest of the app.
        //
        // This screen sits outside the app's Scaffold, so it would otherwise get no ime padding: the
        // keyboard rose over the bottom of the profile page and covered the weight fields, and the
        // one frame where the window resized before the app caught up was the visible jitter when
        // swiping into "About you". Reserving the keyboard height up front removes both.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Skip is only offered on the explanation pages: the profile is not optional, and a
            // silently empty profile is what the old mock data was papering over.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(visible = !isProfilePage, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(profilePageIndex)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("onboarding_skip")
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding_pager"),
                pageSpacing = 0.dp
            ) { page ->
                // Each page fades and slides in as it becomes the current one. Keyed on the page so
                // the animation restarts on arrival instead of running once for the first page and
                // never again, which is what made the first swipe feel dead.
                val isCurrent = pagerState.currentPage == page
                val transition = updateTransition(
                    targetState = isCurrent,
                    label = "onboarding_page_$page"
                )
                val alpha by transition.animateFloat(
                    transitionSpec = { tween(durationMillis = 400) },
                    label = "alpha"
                ) { current -> if (current) 1f else 0.35f }
                val slide by transition.animateDp(
                    transitionSpec = { tween(durationMillis = 420, easing = FastOutSlowInEasing) },
                    label = "slide"
                ) { current -> if (current) 0.dp else 28.dp }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = slide)
                        .alpha(alpha)
                ) {
                    when (page) {
                        OnboardingPage.WELCOME -> WelcomePage()
                        OnboardingPage.LOG -> LogPage()
                        OnboardingPage.PROGRESS -> ProgressPage()
                        OnboardingPage.AI -> AiPage()
                        OnboardingPage.MEDIA -> MediaPage()
                        OnboardingPage.PROFILE -> ProfilePage(
                            weightUnit = weightUnit,
                            name = name,
                            onNameChange = { name = it },
                            age = age,
                            onAgeChange = { age = it.filter(Char::isDigit).take(3) },
                            height = height,
                            onHeightChange = { height = it },
                            currentWeight = currentWeight,
                            onCurrentWeightChange = { currentWeight = it },
                            targetWeight = targetWeight,
                            onTargetWeightChange = { targetWeight = it },
                            fitnessLevel = fitnessLevel,
                            onFitnessLevelChange = { fitnessLevel = it },
                            onToggleUnit = { viewModel.setWeightUnit(it.suffix) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedSecondaryButton(
                        text = "Previous",
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_previous_button")
                    )
                }

                if (isProfilePage) {
                    Button(
                        onClick = {
                            viewModel.completeOnboardingWithProfile(
                                name = name,
                                age = parsedAge,
                                heightCm = parsedHeight,
                                currentWeightKg = parsedCurrent,
                                targetWeightKg = parsedTarget,
                                fitnessLevel = fitnessLevel,
                                unit = weightUnit
                            )
                        },
                        enabled = profileComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = DarkBg,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("onboarding_finish_button")
                    ) {
                        Text(
                            text = "Start training",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = DarkBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Page dots, so the athlete can see how much is left.
            //
            // Animated per dot rather than swapped between two sizes: the current dot grows out of the
                // row and the others settle back, so progress is visible even mid swipe.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(OnboardingPage.count) { index ->
                    val isCurrent = index == pagerState.currentPage
                    val size by animateDpAsState(
                        targetValue = if (isCurrent) 10.dp else 6.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(size)
                            .clip(CircleShape)
                            .background(if (isCurrent) ElectricCyan else DarkBorder)
                    )
                }
            }
        }
    }
}

private object OnboardingPage {
    const val WELCOME = 0
    const val LOG = 1
    const val PROGRESS = 2
    const val AI = 3
    const val MEDIA = 4
    const val PROFILE = 5
    const val count = 6
}

@Composable
private fun WelcomePage() {
    PageScaffold(
        icon = Icons.Default.FitnessCenter,
        accent = ElectricCyan,
        title = "Welcome to IronPulse",
        subtitle = "A workout logger that keeps the numbers straight",
        testTag = "onboarding_page_welcome"
    ) {
        Bullet("1,334 built in exercises, each with an animation of the movement.")
        Bullet("A library sorted into muscle groups, equipment and movement type, so searching by body " +
            "part finds what you actually did.")
        Bullet("Build a routine once and it becomes a week: every day of it has its own workout, and " +
            "a day with nothing on it is a rest day.")
        Bullet("Everything is stored on your phone. No account to make, and nothing uploaded unless " +
            "you ask for AI help.")
    }
}

@Composable
private fun LogPage() {
    PageScaffold(
        icon = Icons.Default.Timer,
        accent = MintGreen,
        title = "Log a workout",
        subtitle = "Three ways in, depending on the session",
        testTag = "onboarding_page_log"
    ) {
        Bullet("Quick workout: start it, tap the exercises you did, tick the sets off as you go.")
        Bullet("Saved routine: run a routine you built once, and pick which day of the week it is.")
        Bullet("Three ready made routines, so you have somewhere to start before you build your own.")
        Bullet("The rest timer and the set renumbering are handled for you while you lift.")
        Bullet("Tap an exercise name mid set to check the movement, without leaving the workout.")
    }
}

@Composable
private fun ProgressPage() {
    PageScaffold(
        icon = Icons.Default.ShowChart,
        accent = GoldenAmber,
        title = "See your progress",
        subtitle = "What actually changed this month",
        testTag = "onboarding_page_progress"
    ) {
        Bullet("Volume, best estimated one rep max, and your training streak, all from your own sets.")
        Bullet("Every set is remembered with the weight, reps and, for cardio, the distance you ran.")
        Bullet("Weigh-ins on the profile tab, plotted over time.")
        Bullet("A monthly summary written from your logged numbers, not from a guess.")
        Bullet("Your history is searchable and filtered by exercise, so you can see one lift's trend.")
    }
}

@Composable
private fun AiPage() {
    PageScaffold(
        icon = Icons.Default.Bolt,
        accent = ElectricCyan,
        title = "AI when you want it",
        subtitle = "Entirely optional, and off by default for your data",
        testTag = "onboarding_page_ai"
    ) {
        Bullet("Paste any workout program in any format and it becomes a structured routine.")
        Bullet("Describe a goal and get a plan built from the exercises the app actually has.")
        Bullet("The coach chat answers questions about the session you just logged.")
        Bullet("Nothing personal is ever sent unless you turn sharing on in Settings, and it is off.")
        Bullet("You bring your own API key, so the AI parts work only when you want them to.")
    }
}

@Composable
private fun MediaPage() {
    PageScaffold(
        icon = Icons.Default.FitnessCenter,
        accent = MintGreen,
        title = "See the movement",
        subtitle = "An animation for nearly every exercise",
        testTag = "onboarding_page_media"
    ) {
        Bullet("Tap any exercise to see it demonstrated before you log it.")
        Bullet("On your own exercises you can attach a photo or video of your exact variation.")
        Bullet("Animations stream on demand, so the app stays small.")
        Bullet("The exercise list stays still: an animation appears once you open an exercise.")
        Bullet("Prefer to tap first? Turn autoplay off in Settings.")
    }
}

@Composable
private fun ProfilePage(
    weightUnit: WeightUnit,
    name: String,
    onNameChange: (String) -> Unit,
    age: String,
    onAgeChange: (String) -> Unit,
    height: String,
    onHeightChange: (String) -> Unit,
    currentWeight: String,
    onCurrentWeightChange: (String) -> Unit,
    targetWeight: String,
    onTargetWeightChange: (String) -> Unit,
    fitnessLevel: String,
    onFitnessLevelChange: (String) -> Unit,
    onToggleUnit: (WeightUnit) -> Unit
) {
    PageScaffold(
        icon = Icons.Default.Person,
        accent = ElectricCyan,
        title = "About you",
        subtitle = "The app needs these to show your progress and progression",
        testTag = "onboarding_page_profile"
    ) {
        OnboardingField(
            value = name,
            onValueChange = onNameChange,
            label = "Name",
            keyboardType = KeyboardType.Text,
            testTag = "onboarding_field_name",
            enterDelayMillis = 0
        )

        // The unit is asked for up front rather than defaulted to kg, because every weight on this
        // page has to be read in the unit the athlete thinks in.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeightUnit.entries.forEach { unit ->
                FilterChip(
                    selected = weightUnit == unit,
                    onClick = { onToggleUnit(unit) },
                    label = { Text(unit.suffix) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan,
                        selectedLabelColor = DarkBg,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = weightUnit == unit,
                        borderColor = DarkBorder,
                        selectedBorderColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("onboarding_unit_${unit.suffix}")
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingField(
                value = age,
                onValueChange = onAgeChange,
                label = "Age",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                testTag = "onboarding_field_age",
                enterDelayMillis = 90
            )
            OnboardingField(
                value = height,
                onValueChange = onHeightChange,
                label = "Height (cm)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                testTag = "onboarding_field_height",
                enterDelayMillis = 150
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingField(
                value = currentWeight,
                onValueChange = onCurrentWeightChange,
                label = "Current weight (${weightUnit.suffix})",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                testTag = "onboarding_field_current_weight",
                enterDelayMillis = 210
            )
            OnboardingField(
                value = targetWeight,
                onValueChange = onTargetWeightChange,
                label = "Target weight (${weightUnit.suffix})",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                testTag = "onboarding_field_target_weight",
                enterDelayMillis = 270
            )
        }

        Text(
            text = "Fitness level",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                FilterChip(
                    selected = fitnessLevel == level,
                    onClick = { onFitnessLevelChange(level) },
                    label = { Text(level) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan,
                        selectedLabelColor = DarkBg,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = fitnessLevel == level,
                        borderColor = DarkBorder,
                        selectedBorderColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("onboarding_level_${level.lowercase()}")
                )
            }
        }

        Text(
            text = "Your current weight also becomes your first weigh-in, so the Progress tab has a " +
                "starting point. You can change anything later on the profile tab.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

/**
 * Rewrites a typed weight in a new unit, leaving anything that is not a number alone.
 *
 * Used by the "About you" form when the athlete changes the unit chips: the number on screen was
 * typed in the old unit, and a bare switch would leave "80" meaning 80 lb where 80 kg was
 * meant. Blank and half typed values are returned untouched, so nothing is written into a field
 * the athlete is still editing.
 */
private fun convertTypedUnit(text: String, from: WeightUnit, to: WeightUnit): String {
    if (from == to) return text
    val value = text.replace(',', '.').toDoubleOrNull() ?: return text
    if (value <= 0.0) return text
    // Through kilograms, which is the single unit every weight in the app is stored in.
    val kg = WeightConversion.toKg(value, from)
    // One decimal and no trailing ".0", so 80 kg reads as 176 lb rather than 176.4.
    return WeightConversion.format(kg, to, decimals = 1, withUnit = false)
}

/** Shared page frame: the icon, the headline, and a scrollable body of bullets. */
@Composable
private fun PageScaffold(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .testTag(testTag)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // The icon scales in from slightly large rather than merely fading. A newcomer's first
        // impression of the app is this tile, and a scale pop is what makes it read as arriving.
        val iconScale = rememberIconPopScale()
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(iconScale)
                .clip(RoundedCornerShape(18.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        // The headline and the body slide in from the right as the page arrives. Direction mirrors
        // the pager's own motion, so the two read as one movement rather than two competing ones.
        val headlineOffset = rememberEnterOffsetDp()
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextWhite,
            modifier = Modifier.offset(x = headlineOffset)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.offset(x = headlineOffset)
        )
        Spacer(modifier = Modifier.height(18.dp))

        // Always scrollable, and explicitly weighted.
        //
        // The weight is what stops the jitter on the profile page. Without it a scrollable column
        // inside a non scrolling parent takes whatever height its children need on the first
        // measurement, then gets re-measured once the text fields are laid out, so the page resized
        // for a frame or two as the athlete swiped in. Claiming the remaining height up front means
        // one measurement, one layout, and no visible correction.
        //
        // The profile page is also taller than most phones, and a page that cannot reach its own
        // fields is unusable rather than merely awkward.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * Scale for the page icon: a short pop from 0.7 to 1.0, settling with a small overshoot.
 */
@Composable
private fun rememberIconPopScale(): Float {
    val animated = remember { Animatable(initialValue = 0.72f) }
    LaunchedEffect(Unit) {
        animated.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    return animated.value
}

/**
 * Horizontal offset for the headline and body: they start shifted to the right and slide home.
 */
@Composable
private fun rememberEnterOffsetDp(): Dp {
    val animated = remember { Animatable(initialValue = 36f) }
    LaunchedEffect(Unit) {
        animated.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
        )
    }
    return animated.value.dp
}

@Composable
private fun ColumnScope.Bullet(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(ElectricCyan)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextWhite.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    testTag: String,
    enterDelayMillis: Int = 0
) {
    // Fields rise into place one after another rather than all at once. Staggered entrances read as
    // deliberate and give the eye somewhere to travel; a simultaneous fade reads as a glitch.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(enterDelayMillis.toLong())
        visible = true
    }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "field_appear_$testTag"
    )

    // The entrance is drawn, never laid out.
    //
    // This was an AnimatedVisibility around the text field, which broke the profile page: a
    // visibility container inserts its own layout node between the row and the field, and that node
    // does not implement Row/Column weight semantics. Every `Modifier.weight(1f)` passed to a field
    // was therefore silently dropped, so the two-up rows (Age / Height, Current / Target) were
    // measured with no width bound, collapsed to their narrowest width and fell onto their own
    // lines. The result was exactly the reported "field textboxes on a newline, squashed thin".
    //
    // graphicsLayer is a draw-only modifier: it changes alpha and offset and nothing about layout,
    // so the weight still reaches the text field and the row still measures as designed.
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricCyan,
            unfocusedBorderColor = DarkBorder,
            focusedLabelColor = ElectricCyan,
            unfocusedLabelColor = TextMuted,
            cursorColor = ElectricCyan,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        ),
        modifier = modifier
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 28.dp.toPx()
            }
            .testTag(testTag)
    )
}

@Composable
private fun OutlinedSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(50.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
