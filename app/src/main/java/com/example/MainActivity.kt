package com.example

import android.content.Intent
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
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
import com.example.ui.components.LiquidGlassLens
import com.example.ui.components.LocalLiquidGlassEnabled
import com.example.ui.components.StartupUpdateDialog
import com.example.ui.components.WorkoutSummaryDialog
import com.example.ui.components.detectLiquidGlassLensGesture
import com.example.ui.components.drawLiquidGlassLens
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassBackdrop
import com.example.ui.components.rememberReducedMotion
import com.example.ui.screens.AiCoachScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.LogWorkoutScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WidgetAppearanceScreen
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
import com.example.ui.theme.ThemeRuntime
import com.example.ui.theme.OnAccent
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ApkInstaller
import java.io.File
import kotlin.math.abs

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val tag: String) {
    object LogWorkout : Screen("log_workout", "Exercises", Icons.Default.FitnessCenter, "nav_log_workout")
    object Progress : Screen("progress", "Progress", Icons.Default.ShowChart, "nav_progress")
    object Home : Screen("home", "Home", Icons.Default.Home, "nav_home")
    object Profile : Screen("profile", "Profile", Icons.Default.Person, "nav_profile")
    object AiCoach : Screen("ai_coach", "AI Coach", Icons.Default.AutoAwesome, "nav_ai_coach")
    object Settings : Screen("settings", "Settings", Icons.Default.Settings, "nav_settings")
    object WidgetAppearance : Screen(
        "widget_appearance",
        "Android Widgets",
        Icons.Default.Settings,
        "nav_widget_appearance"
    )

    companion object {
        /**
         * The tab that should stay selected for a pushed detail route.
         *
         * Settings and the widget appearance screen are opened from Settings and are not
         * destinations of their own, so the dock would otherwise have no selected index.
         */
        fun selectedTabRoute(route: String): String =
            if (route == Settings.route || route == WidgetAppearance.route) Profile.route else route
    }
}

data class WidgetNavigationRequest(val id: Long, val action: String)

class MainActivity : ComponentActivity() {

    /** The last mode the athlete chose; reapplied when the window regains focus. */
    private var fullscreenEnabled = true

    private val widgetNavigation = kotlinx.coroutines.flow.MutableStateFlow<WidgetNavigationRequest?>(null)
    private var widgetNavigationSequence = 0L

    private fun handleWidgetIntent(intent: Intent?) {
        val action = intent?.getStringExtra(com.example.widget.WidgetNavigation.EXTRA_ACTION)
            ?: intent?.action
            ?: return
        if (action !in setOf(
                com.example.widget.WidgetNavigation.ACTION_OPEN_STREAK,
                com.example.widget.WidgetNavigation.ACTION_START_TODAY,
                com.example.widget.WidgetNavigation.ACTION_SAVED_ROUTINES,
                com.example.widget.WidgetNavigation.ACTION_OPEN_PROFILE
            )
        ) {
            return
        }
        widgetNavigation.value = WidgetNavigationRequest(
            id = ++widgetNavigationSequence,
            action = action
        )
    }

    private fun consumeWidgetNavigation(id: Long) {
        if (widgetNavigation.value?.id == id) widgetNavigation.value = null
    }

    /**
     * Applies the optional fullscreen policy immediately.
     *
     * Enabled hides both system bars and lets the page render into the cutout area; the Compose
     * root then pads only the display cutout, so the camera never covers important UI. Disabled
     * shows both bars and the root pads `safeDrawing`, so no page content sits underneath them.
     *
     * Swiping from an edge still reveals the bars temporarily in fullscreen. That transient
     * overlay behaviour is what stops the layout from jumping while the athlete is mid-set.
     */
    fun setFullscreenMode(enabled: Boolean) {
        fullscreenEnabled = enabled
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setFullscreenMode(true)
        handleWidgetIntent(intent)
        setContent {
            IronPulseApp(
                widgetNavigation = widgetNavigation,
                onWidgetNavigationHandled = { consumeWidgetNavigation(it) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    /**
     * Reapplies the current policy when the app comes back to the front.
     *
     * The bars can be left showing by the system after a notification shade pull or a settings
     * gesture; without this, fullscreen would stay open until the athlete toggled it again.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setFullscreenMode(fullscreenEnabled)
    }
}

/**
 * The cutout inset used in fullscreen, measured to the actual camera obstruction.
 *
 * `WindowInsets.displayCutout` reports a safe rectangle that is often the full status-bar height
 * (128px on the test device), while the punch-hole itself ends much sooner. Reserving the whole
 * rectangle on every page is what produced the oversized blank band. On API 33+ the cutout path's
 * actual bottom is used; the result is still a safe inset because the path is the physical
 * obstruction. Older versions keep the platform's safe top inset, so no device is made less safe.
 *
 * The ordinary page top padding (8dp after this change) remains inside the inset, giving a small
 * breathing margin without recreating the old blank band.
 */
@Composable
private fun fullscreenCutoutInsets(): WindowInsets {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val cutout = WindowInsets.displayCutout
    val safeTopPx = cutout.getTop(density)
    val actualTopPx = remember(safeTopPx, view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && safeTopPx > 0) {
            val path = ViewCompat.getRootWindowInsets(view)?.displayCutout?.cutoutPath
            if (path != null) {
                val bounds = RectF()
                path.computeBounds(bounds, true)
                val pathBottom = bounds.bottom.toInt()
                // Only a top cutout may reduce the top reserve. A side/waterfall cutout path can
                // span the full display height, and must never become a giant top padding.
                pathBottom.takeIf { it in 1..safeTopPx } ?: safeTopPx
            } else {
                safeTopPx
            }
        } else {
            safeTopPx
        }
    }
    return WindowInsets(
        left = with(density) { cutout.getLeft(density, layoutDirection).toDp() },
        top = with(density) { actualTopPx.toDp() },
        right = with(density) { cutout.getRight(density, layoutDirection).toDp() },
        bottom = with(density) { cutout.getBottom(density).toDp() }
    )
}

@Composable
fun IronPulseApp(
    widgetNavigation: kotlinx.coroutines.flow.StateFlow<WidgetNavigationRequest?>? = null,
    onWidgetNavigationHandled: (Long) -> Unit = {}
) {
    val viewModel: MainViewModel = viewModel()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val fullscreenEnabled by viewModel.fullscreenEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The activity owns the window policy; Compose owns the preference. The two are connected here
    // so a toggle applies immediately without recreating the activity.
    LaunchedEffect(fullscreenEnabled) {
        (context as? MainActivity)?.setFullscreenMode(fullscreenEnabled)
    }
    LaunchedEffect(Unit) {
        com.example.widget.WorkoutWidgetUpdater.updateAll(context)
        com.example.widget.WidgetMidnightScheduler.schedule(context)
    }

    // The theme wraps the whole app, including the navigation bar and every dialog, and is read
    // from its own flow so an unrelated setting change never re-applies it. Liquid Glass stays a
    // separate provider: it is an effect, not a theme, and works in all four looks.
    MyApplicationTheme(themeMode = themeMode) {
        CompositionLocalProvider(LocalLiquidGlassEnabled provides liquidGlassEnabled) {
            val insetModifier = if (fullscreenEnabled) {
                Modifier.windowInsetsPadding(fullscreenCutoutInsets())
            } else {
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Paint the cutout/inset strip in the page colour rather than leaving the
                    // window's default black visible as an unexplained band above the content.
                    .background(DarkBg)
                    .then(insetModifier)
            ) {
                IronPulseAppContent(
                    viewModel = viewModel,
                    widgetNavigation = widgetNavigation,
                    onWidgetNavigationHandled = onWidgetNavigationHandled
                )
            }
        }
    }
}

@Composable
private fun IronPulseAppContent(
    viewModel: MainViewModel,
    widgetNavigation: kotlinx.coroutines.flow.StateFlow<WidgetNavigationRequest?>? = null,
    onWidgetNavigationHandled: (Long) -> Unit = {}
) {
    val navController = rememberNavController()
    val navigationState = widgetNavigation ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow<WidgetNavigationRequest?>(null)
    }
    val widgetRequest by navigationState.collectAsStateWithLifecycle()

    LaunchedEffect(widgetRequest?.id) {
        val request = widgetRequest ?: return@LaunchedEffect
        val navigateHome = {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        when (request.action) {
            com.example.widget.WidgetNavigation.ACTION_OPEN_STREAK -> {
                navigateHome()
                viewModel.requestHomeStreakFocus()
            }
            com.example.widget.WidgetNavigation.ACTION_START_TODAY -> {
                navigateHome()
                viewModel.startTodayWorkoutFromWidget()
            }
            com.example.widget.WidgetNavigation.ACTION_SAVED_ROUTINES -> {
                viewModel.requestSavedRoutinesTab()
                navController.navigate(Screen.AiCoach.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            com.example.widget.WidgetNavigation.ACTION_OPEN_PROFILE -> {
                navController.navigate(Screen.Profile.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        onWidgetNavigationHandled(request.id)
    }
    val activeRoutineWorkout by viewModel.activeRoutineWorkout.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val workoutSummaryState by viewModel.workoutSummaryState.collectAsStateWithLifecycle()
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
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open the settings", color = OnAccent, fontWeight = FontWeight.Bold)
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
        // Settings is a pushed detail reachable from Profile, not a sixth tab. Keep Profile
        // highlighted while it is open instead of clearing the indicator because no tab owns the
        // Settings route. Navigation callbacks still compare against the real route above.
        val selectedTabRoute = Screen.selectedTabRoute(currentRoute)

        val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val glassEnabled = LocalLiquidGlassEnabled.current
        var navBounds by remember { mutableStateOf<Rect?>(null) }
        var contentBounds by remember { mutableStateOf<Rect?>(null) }
        val dockBounds = remember(navBounds, contentBounds) {
            val nav = navBounds
            val content = contentBounds
            if (nav == null || content == null) {
                null
            } else {
                Rect(
                    left = nav.left - content.left,
                    top = nav.top - content.top,
                    right = nav.right - content.left,
                    bottom = nav.bottom - content.top
                )
            }
        }
        val navigate: (Screen) -> Unit = { screen ->
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

        // The dock is an overlay, not a Scaffold bottom bar. A reserved bottom bar would paint an
        // opaque page-coloured strip behind the glass and prevent any content from ever showing
        // through it. The NavHost now fills the screen and the floating dock draws above it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { contentBounds = it.boundsInRoot() }
                    // Records the pages into a graphics layer and draws a genuinely blurred copy
                    // only inside the dock silhouette. This is the backdrop the glass sits on.
                    .liquidGlassBackdrop(
                        enabled = glassEnabled && !isKeyboardOpen,
                        bounds = dockBounds,
                        cornerRadius = 28.dp
                    )
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
                        onBack = { navController.popBackStack() },
                        onOpenWidgetAppearance = {
                            navController.navigate(Screen.WidgetAppearance.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.WidgetAppearance.route) {
                    WidgetAppearanceScreen(
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

            when (val summaryState = workoutSummaryState) {
                is MainViewModel.WorkoutSummaryState.Loading -> {
                    val session = allSessions.firstOrNull { it.id == summaryState.workoutId }
                    WorkoutSummaryDialog(
                        workoutTitle = session?.title ?: "Workout",
                        workoutDate = summaryDate(session?.startTime ?: 0L),
                        markdown = null,
                        loading = true,
                        error = null,
                        onRetry = null,
                        onDismiss = { viewModel.dismissWorkoutSummary() }
                    )
                }

                is MainViewModel.WorkoutSummaryState.Success -> {
                    val session = allSessions.firstOrNull { it.id == summaryState.summary.workoutId }
                    WorkoutSummaryDialog(
                        workoutTitle = session?.title ?: "Workout",
                        workoutDate = summaryDate(session?.startTime ?: 0L),
                        markdown = summaryState.summary.summaryMarkdown,
                        loading = false,
                        error = null,
                        onRetry = null,
                        onDismiss = { viewModel.dismissWorkoutSummary() }
                    )
                }

                is MainViewModel.WorkoutSummaryState.Error -> {
                    val session = allSessions.firstOrNull { it.id == summaryState.workoutId }
                    WorkoutSummaryDialog(
                        workoutTitle = session?.title ?: "Workout",
                        workoutDate = summaryDate(session?.startTime ?: 0L),
                        markdown = null,
                        loading = false,
                        error = summaryState.message,
                        onRetry = { viewModel.retryWorkoutSummary(summaryState.workoutId) },
                        onDismiss = { viewModel.dismissWorkoutSummary() }
                    )
                }

                MainViewModel.WorkoutSummaryState.Idle -> Unit
            }

            // Hidden while typing rather than lifted: it cannot be used during a text edit and
            // would shrink the visible list to a row of dead space above the keyboard.
            if (!isKeyboardOpen) {
                IronBottomBar(
                    screens = screens,
                    currentRoute = selectedTabRoute,
                    onSelect = navigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (glassEnabled) {
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            } else {
                                Modifier
                            }
                        ),
                    onBoundsChanged = { navBounds = it }
                )
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
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    onBoundsChanged: (Rect) -> Unit = {}
) {
    val glassEnabled = LocalLiquidGlassEnabled.current
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val influenceRadiusPx = with(density) { LiquidGlassLens.INFLUENCE_RADIUS_DP.dp.toPx() }
    val lensRadiusPx = with(density) { LiquidGlassLens.LENS_RADIUS_DP.dp.toPx() }
    val liftPx = with(density) { LiquidGlassLens.LIFT_DP.dp.toPx() }

    var lensActive by remember { mutableStateOf(false) }
    val lensCenterX = remember { mutableFloatStateOf(0f) }
    var dockBounds by remember { mutableStateOf<Rect?>(null) }
    var itemBounds by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    // The pointerInput block lives for the life of the dock and would otherwise capture the first
    // composition's onSelect/currentRoute. That made Home a no-op because the app starts on Home:
    // every later release was compared against the initial route. Read the latest callback instead.
    val latestOnSelect = rememberUpdatedState(onSelect)
    val lensProgress = remember { Animatable(0f) }
    val lensProgressState = lensProgress.asState()

    LaunchedEffect(lensActive, reducedMotion) {
        val target = if (lensActive) 1f else 0f
        if (reducedMotion) {
            lensProgress.snapTo(target)
        } else {
            lensProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = if (lensActive) 120 else 180,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Box(modifier = modifier.testTag("bottom_navigation_bar")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    val bounds = it.boundsInRoot()
                    dockBounds = bounds
                    onBoundsChanged(bounds)
                }
                .then(
                    if (glassEnabled) {
                        Modifier
                            .liquidGlass(
                                shape = RoundedCornerShape(26.dp),
                                enabled = true,
                                tint = ElectricCyan,
                                cornerRadius = 26.dp,
                                elevation = 16.dp,
                                // The page behind the dock is already blurred by the NavHost's
                                // liquidGlassBackdrop modifier. A lighter body lets that real
                                // backdrop show instead of covering it with a gradient.
                                backdropBlurred = true
                            )
                            // The detector lives on the glass dock, so a press anywhere on the bar
                            // can start the lens. It watches the Initial pass and only consumes
                            // after the hold threshold, which keeps ordinary taps untouched.
                            .pointerInput(Unit) {
                                detectLiquidGlassLensGesture(
                                    onStart = { position ->
                                        lensCenterX.floatValue = position.x
                                        lensActive = true
                                    },
                                    onMove = { position -> lensCenterX.floatValue = position.x },
                                    onRelease = { position ->
                                        val dock = dockBounds
                                        if (dock != null) {
                                            val rootPosition = Offset(
                                                x = dock.left + position.x,
                                                y = dock.top + position.y
                                            )
                                            val measured = screens.indices.map { index ->
                                                itemBounds[index] ?: Rect.Zero
                                            }
                                            LiquidGlassLens.itemIndexAt(
                                                position = rootPosition,
                                                itemBounds = measured
                                            )?.let { index ->
                                                latestOnSelect.value(screens[index])
                                            }
                                        }
                                        // The lens dismisses after the destination is processed.
                                        lensActive = false
                                    },
                                    onCancel = { lensActive = false }
                                )
                            }
                    } else {
                        // With Liquid Glass off, restore the conventional full-width bar rather
                        // than leaving a floating glass silhouette in a solid colour.
                        Modifier.background(DarkSurface)
                    }
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                val itemWidth = maxWidth / screens.size
                val itemWidthPx = with(density) { itemWidth.toPx() }
                val selectedIndex = screens.indexOfFirst { it.route == currentRoute }
                val selectedIsHome = selectedIndex >= 0 && screens[selectedIndex] == Screen.Home

                // One indicator for the whole bar. Its width, position and colour animate together,
                // so the highlight travels between destinations instead of one highlight
                // disappearing and another appearing. The Home destination is a circle, which is
                // why its width shrinks to 40dp: with a 20dp corner radius, a 40dp box *is* a
                // circle.
                //
                // The colour is the same ElectricCyan as every other destination. Home used to get
                // an 18% wash so it could keep a cyan icon, which read as "much less selected" than
                // the other tabs. The icon is dark instead, exactly like the other selected icons.
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
                    screens.forEachIndexed { index, screen ->
                        val isSelected = currentRoute == screen.route
                        val isHome = screen == Screen.Home
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) OnAccent else TextMuted,
                            animationSpec = tween(200),
                            label = "bottom_nav_icon_${screen.route}"
                        )
                        val labelColor by animateColorAsState(
                            targetValue = if (isSelected) ElectricCyan else TextMuted,
                            animationSpec = tween(220),
                            label = "bottom_nav_label_${screen.route}"
                        )
                        val itemCenterPx = itemWidthPx * (index + 0.5f)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                // Measure the slot before the lens graphics layer scales it. The
                                // hit region must stay the untouched tab slot, not the temporarily
                                // magnified bounds (which overlap the neighbours).
                                .onGloballyPositioned { coordinates ->
                                    itemBounds = itemBounds + (index to coordinates.boundsInRoot())
                                }
                                // The magnification is a draw-time property. Reading the lens
                                // state inside graphicsLayer keeps finger movement out of
                                // composition: only this item's layer is invalidated, not the
                                // whole navigation bar.
                                .graphicsLayer {
                                    if (glassEnabled) {
                                        val progress = lensProgressState.value
                                        if (progress > 0.001f) {
                                            val distance = abs(itemCenterPx - lensCenterX.floatValue)
                                            val falloff = LiquidGlassLens.falloff(
                                                distancePx = distance,
                                                influenceRadiusPx = influenceRadiusPx
                                            )
                                            val scale = 1f + (LiquidGlassLens.MAX_SCALE - 1f) *
                                                falloff * progress
                                            scaleX = scale
                                            scaleY = scale
                                            translationY = -liftPx * falloff * progress
                                        } else {
                                            scaleX = 1f
                                            scaleY = 1f
                                            translationY = 0f
                                        }
                                    }
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                // The Material ripple is a rectangle over the whole item, which
                                // becomes an obvious wash while the lens is held. In glass mode
                                // it is replaced by a quiet rounded press highlight that yields to
                                // the lens; with glass off the original ripple is untouched.
                                .background(
                                    color = if (glassEnabled && isPressed && !lensActive) {
                                        ThemeRuntime.palette.glass.press.copy(alpha = 0.06f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .selectable(
                                    selected = isSelected,
                                    interactionSource = interactionSource,
                                    indication = if (glassEnabled) null else LocalIndication.current,
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
                                    // The Home destination is always a dark circle with a ring;
                                    // the sliding indicator behind it supplies the fill when
                                    // selected.
                                    .then(
                                        if (isHome) {
                                            Modifier
                                                // Selected: the sliding indicator shows through
                                                // the ring. Unselected: the dark Home button of the
                                                // bar.
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

                if (glassEnabled) {
                    // The lens is drawn above the items. It has no pointer input of its own: the
                    // dock's detector owns the gesture, and the Canvas must never intercept taps.
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val progress = lensProgressState.value
                        if (progress > 0.001f) {
                            val maxCenterX = (size.width - lensRadiusPx).coerceAtLeast(lensRadiusPx)
                            val centerX = lensCenterX.floatValue.coerceIn(lensRadiusPx, maxCenterX)
                            drawLiquidGlassLens(
                                center = Offset(centerX, 30.dp.toPx()),
                                radiusPx = lensRadiusPx,
                                progress = progress,
                                tint = ElectricCyan
                            )
                        }
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

private fun summaryDate(epochMillis: Long): String =
    if (epochMillis <= 0L) "" else java.text.SimpleDateFormat(
        "MMM d, yyyy",
        java.util.Locale.getDefault()
    ).format(java.util.Date(epochMillis))
