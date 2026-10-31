package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.UpdateState
import com.example.ui.components.StartupUpdateDialog
import com.example.ui.screens.AiCoachScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.LogWorkoutScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.RoutineWorkoutScreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ApkInstaller
import java.io.File

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val tag: String) {
    object LogWorkout : Screen("log_workout", "Exercises", Icons.Default.FitnessCenter, "nav_log_workout")
    object Progress : Screen("progress", "Progress", Icons.Default.ShowChart, "nav_progress")
    object Home : Screen("home", "Home", Icons.Default.Home, "nav_home")
    object Profile : Screen("profile", "Profile", Icons.Default.Person, "nav_profile")
    object AiCoach : Screen("ai_coach", "AI Coach", Icons.Default.AutoAwesome, "nav_ai_coach")
    object Settings : Screen("settings", "Settings", Icons.Default.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    /**
     * Hides the status bar and the on screen navigation bar for as long as the app is in front.
     *
     * They are hidden rather than merely drawn over, because a training app that keeps two system
     * bars permanently visible spends a meaningful share of a phone screen on chrome the athlete
     * never uses while lifting. Swiping from an edge brings them back temporarily, and they fade out
     * again on their own, so the escape hatch stays without reserving space for it.
     *
     * The transient behaviour matters as much as the hiding: with it the bars appear as an overlay
     * and the layout does not resize when they slide in, which is what stops the whole page from
     * jumping under the athlete mid set.
     */
    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullScreen()
        setContent {
            MyApplicationTheme {
                IronPulseApp()
            }
        }
    }

    /**
     * Reapplies the hiding when the app comes back to the front.
     *
     * The bars can be left showing by the system after a notification shade pull or a settings
     * gesture, and without this they would stay open until the athlete deliberately hid them again.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()
    }
}

@Composable
fun IronPulseApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val activeRoutineWorkout by viewModel.activeRoutineWorkout.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    // Gate rather than a route: the landing pages are a modal flow with no back navigation, and
    // showing the tab bar behind them would let the athlete wander into an empty Profile mid form.
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

    // A workout that was in progress when the app was closed, waiting for the athlete to decide.
    val pendingDraft by viewModel.pendingWorkoutDraft.collectAsStateWithLifecycle()

    // The release the automatic startup check found, and the download state of its prompt.
    val startupUpdate by viewModel.startupUpdate.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    // Installing the downloaded update from the startup prompt needs the same Android permission
    // dance as the settings dialog: ask, wait for the answer, then open the installer.
    val context = LocalContext.current
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var installPermissionNeeded by remember { mutableStateOf(false) }
    val launchInstaller: (File) -> Unit = { file ->
        try {
            context.startActivity(ApkInstaller.installIntent(context, file))
        } catch (_: Exception) {
            installPermissionNeeded = false
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(
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
    val startInstall: () -> Unit = {
        val ready = updateState as? UpdateState.ReadyToInstall
        val file = ready?.file
        if (file != null) {
            if (ApkInstaller.canRequestPackageInstalls(context)) {
                launchInstaller(file)
            } else {
                pendingInstall = file
                try {
                    installPermissionLauncher.launch(ApkInstaller.installPermissionIntent(context))
                } catch (_: Exception) {
                    pendingInstall = null
                    installPermissionNeeded = true
                }
            }
        }
    }

    // The app going to the background is the last chance to write the workout down. The auto save
    // loop runs every ten seconds while the app is in front, so the copy on disk is at most that
    // old; this brings it right up to date at the moment the athlete leaves.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.saveActiveWorkoutDraftNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    KeepTheKeyboardFromComingBack()

    if (!onboardingCompleted) {
        OnboardingScreen(viewModel = viewModel)
        return
    }

    // The offer to continue a workout the athlete closed the app in the middle of.
    //
    // It is asked before anything else, because the answer decides what the app shows: continuing
    // puts the athlete straight back into their session, and discarding leaves them on a clean
    // start. Nothing is restored until they choose.
    if (pendingDraft != null) {
        AlertDialog(
            // Tapping outside the dialog, or pressing back, does nothing at all.
            //
            // The dialog is the only thing standing between the athlete and a workout they have
            // already done, so an accidental tap outside it must not throw that away. It is
            // answered with Continue or Discard, and nothing else closes it.
            onDismissRequest = { },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Unfinished workout",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "A workout was in progress when the app closed.",
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (pendingDraft!!.isRoutine) {
                            "${pendingDraft!!.title}: ${pendingDraft!!.sets.size} sets were saved, and ${pendingDraft!!.routineItems.size} exercises were on the plan. Continue?"
                        } else {
                            "${pendingDraft!!.title}: ${pendingDraft!!.sets.size} sets and ${pendingDraft!!.exerciseIds.size} exercises were saved. Continue?"
                        },
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restorePendingWorkoutDraft() }) {
                    Text("Continue", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardPendingWorkoutDraft() }) {
                    Text("Discard", color = TextMuted)
                }
            }
        )
    }

    // The automatic update prompt. It waits until the app is actually usable: no onboarding, no
    // unfinished-workout question and no live workout in front of it. Closing it without the
    // checkbox brings it back on the next start; the checkbox suppresses it for this version.
    val showingStartupUpdate =
        startupUpdate != null && pendingDraft == null && activeRoutineWorkout == null &&
            activeSession == null
    if (showingStartupUpdate) {
        StartupUpdateDialog(
            release = startupUpdate!!,
            state = updateState,
            installedVersion = BuildConfig.VERSION_NAME,
            onDownload = { viewModel.downloadUpdate() },
            onInstall = startInstall,
            onClose = { viewModel.closeStartupUpdatePrompt(dontShowAgain = false) },
            onDontShowAgain = { viewModel.closeStartupUpdatePrompt(dontShowAgain = true) }
        )
    }

    // Android does not let an app install a package until the athlete allows it in the system
    // settings. The prompt explains that instead of failing silently.
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
                    text = "Android needs your permission before IronPulse can open the installer " +
                        "for the downloaded update.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        installPermissionNeeded = false
                        try {
                            installPermissionLauncher.launch(
                                ApkInstaller.installPermissionIntent(context)
                            )
                        } catch (_: Exception) {
                            // No settings screen to open: there is nothing else to try.
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open the settings", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { installPermissionNeeded = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // The workout lives on its own screen, above the tab bar, and moving in and out of it is
    // animated so tapping a workout on Home feels like entering it rather than replacing the page.
    // The target is the *presence* of a workout, not its changing contents: a set completed during
    // the workout updates the state object constantly and must not replay the transition.
    var lastWorkout by remember { mutableStateOf(activeRoutineWorkout) }
    LaunchedEffect(activeRoutineWorkout) {
        activeRoutineWorkout?.let { lastWorkout = it }
    }

    AnimatedContent(
        targetState = activeRoutineWorkout != null,
        transitionSpec = {
            if (targetState) {
                // Opening: the workout rises over the page while the page fades back.
                (slideInVertically(
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    initialOffsetY = { height -> height / 5 }
                ) + fadeIn(tween(220))) togetherWith fadeOut(tween(180))
            } else {
                // Finishing or backing out: the tab page fades in underneath as the workout drops.
                fadeIn(tween(260)) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        targetOffsetY = { height -> height / 5 }
                    ) + fadeOut(tween(220))
                    )
            }
        },
        label = "workout_transition"
    ) { inWorkout ->
        if (inWorkout) {
            RoutineWorkoutScreen(
                viewModel = viewModel,
                workoutSnapshot = lastWorkout
            )
        } else {
        // The order of the bottom bar. Home sits between Progress and Profile, which is where the
        // athlete expects the entry point to the app to be.
        val screens = listOf(
            Screen.LogWorkout,
            Screen.Progress,
            Screen.Home,
            Screen.Profile,
            Screen.AiCoach
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

        val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                // Lifts the whole app, bottom navigation included, above the keyboard when it opens.
                // Without it the keyboard covers whatever is at the bottom of the page, which is
                // exactly where the weight and reps fields sit while logging a set.
                .imePadding(),
            bottomBar = {
                // The navigation bar is removed rather than lifted while typing: it cannot be used
                // during a text edit anyway, and leaving it would shrink the visible list to a row
                // of dead space above the keyboard.
                if (!isKeyboardOpen) {
                    IronBottomBar(
                        screens = screens,
                        currentRoute = currentRoute,
                        onSelect = { screen ->
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    // Reserved for nothing: the system bars are hidden by the window, so any inset
                    // the Scaffold reserved for them would show up as a dead band at the edge of
                    // every page. Consuming them here keeps the layout at the physical edges.
                    .consumeWindowInsets(WindowInsets(0, 0, 0, 0))
                    .background(DarkBg)
            ) {
                composable(Screen.LogWorkout.route) {
                    LogWorkoutScreen(viewModel = viewModel)
                }
                composable(Screen.Progress.route) {
                    ProgressScreen(viewModel = viewModel)
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        viewModel = viewModel,
                        onOpenSettings = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenSavedRoutines = {
                            // The saved routines live in a tab of the AI Coach screen rather than in
                            // a destination of their own, so the request goes through the view
                            // model and the tab opens when the coach screen is on screen.
                            viewModel.requestSavedRoutinesTab()
                            navController.navigate(Screen.AiCoach.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenProgress = {
                            navController.navigate(Screen.Progress.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenLogWorkout = {
                            navController.navigate(Screen.LogWorkout.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Screen.AiCoach.route) {
                    AiCoachScreen(
                        viewModel = viewModel,
                        onNavigateToLogWorkout = {
                            navController.navigate(Screen.LogWorkout.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
    }
}

/**
 * The bottom navigation bar.
 *
 * Built by hand rather than with the Material bar, for two reasons.
 *
 * The spacing: every item takes an equal share of the width, so the five destinations stay evenly
 * spread whatever is in the list and whatever the screen is. Nothing here depends on a fixed
 * offset or on the number of items, so the bar cannot drift out of alignment when one is added.
 *
 * The Home button: it is a circle in the middle of the bar, the way the entry point of a training
 * app is expected to look. It is dark like the rest of the app, and the ring and the icon light up
 * when it is the selected destination, which is the difference an athlete can read at a glance
 * without looking at the label.
 */
@Composable
private fun IronBottomBar(
    screens: List<Screen>,
    currentRoute: String,
    onSelect: (Screen) -> Unit
) {
    Surface(
        color = DarkSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("bottom_navigation_bar")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            val itemWidth = maxWidth / screens.size
            val selectedIndex = screens.indexOfFirst { it.route == currentRoute }
            val selectedIsHome = selectedIndex >= 0 && screens[selectedIndex] == Screen.Home

            // One indicator for the whole bar. Its width, position and colour animate together, so
            // the highlight travels between destinations instead of one highlight disappearing and
            // another appearing. The Home destination is a circle, which is why its width shrinks
            // to 40dp: with a 20dp corner radius, a 40dp box *is* a circle.
            //
            // The colour is the same ElectricCyan as every other destination. Home used to get an
            // 18% wash so it could keep a cyan icon, which read as "much less selected" than the
            // other tabs. The icon is dark instead, exactly like the other selected icons.
            val indicatorWidth by animateDpAsState(
                targetValue = if (selectedIsHome) 40.dp else 48.dp,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "bottom_nav_indicator_width"
            )
            val indicatorOffset by animateDpAsState(
                targetValue = if (selectedIndex >= 0) {
                    itemWidth * selectedIndex.toFloat() + (itemWidth - indicatorWidth) / 2
                } else {
                    (itemWidth - indicatorWidth) / 2
                },
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "bottom_nav_indicator_offset"
            )
            val indicatorColor by animateColorAsState(
                targetValue = ElectricCyan,
                animationSpec = tween(220),
                label = "bottom_nav_indicator_color"
            )

            if (selectedIndex >= 0) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset, y = 4.dp)
                        .width(indicatorWidth)
                        .height(40.dp)
                        .background(indicatorColor, RoundedCornerShape(20.dp))
                        .testTag("bottom_nav_indicator")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                screens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val isHome = screen == Screen.Home
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) DarkBg else TextMuted,
                        animationSpec = tween(200),
                        label = "bottom_nav_icon_${screen.route}"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) ElectricCyan else TextMuted,
                        animationSpec = tween(220),
                        label = "bottom_nav_label_${screen.route}"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(screen) },
                                role = Role.Tab
                            )
                            .padding(vertical = 4.dp)
                            .testTag(screen.tag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                // The Home destination is always a dark circle with a ring; the
                                // sliding indicator behind it supplies the fill when selected.
                                .then(
                                    if (isHome) {
                                        Modifier
                                            // Selected: the sliding indicator shows through the
                                            // ring. Unselected: the dark Home button of the bar.
                                            .background(
                                                if (isSelected) Color.Transparent else DarkBg,
                                                CircleShape
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) ElectricCyan else DarkBorder,
                                                shape = CircleShape
                                            )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = iconTint,
                                modifier = Modifier.size(if (isHome) 21.dp else 24.dp)
                            )
                        }
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = labelColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Greeting function retained for reference test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", color = TextWhite, modifier = modifier)
}

/**
 * Keeps the keyboard from coming back on its own.
 *
 * A text field keeps its focus after the keyboard is dismissed. The field stays selected, the focus
 * stays with it, and the next time the app is opened from the recents the field is still there
 * waiting for a keyboard that opens by itself. The athlete cannot get rid of it, because there is
 * nothing left to dismiss.
 *
 * So the focus is dropped in the two places it can go stale:
 *
 *  - when the keyboard is dismissed, by any means. The keyboard going away is the signal that the
 *    athlete is done with the field, and the field must let go at the same moment.
 *  - on the first frame, for a focus that came back with the saved state. That focus would
 *    otherwise open the keyboard before the athlete has touched anything.
 *
 * The text in the field is untouched: only the focus is dropped, so the field keeps what was typed.
 */
@Composable
internal fun KeepTheKeyboardFromComingBack() {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    // The focus restored with the saved state. Dropped before the keyboard can open for it.
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
    }

    // The keyboard is up, then it is not. The second time it happens the field is done.
    var keyboardWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            keyboardWasUp = true
        } else if (keyboardWasUp) {
            keyboardWasUp = false
            focusManager.clearFocus(force = true)
        }
    }
}
