package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AiCoachScreen
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
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val tag: String) {
    object LogWorkout : Screen("log_workout", "Exercises", Icons.Default.FitnessCenter, "nav_log_workout")
    object Progress : Screen("progress", "Progress", Icons.Default.ShowChart, "nav_progress")
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
    // Gate rather than a route: the landing pages are a modal flow with no back navigation, and
    // showing the tab bar behind them would let the athlete wander into an empty Profile mid form.
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

    if (!onboardingCompleted) {
        OnboardingScreen(viewModel = viewModel)
        return
    }

    if (activeRoutineWorkout != null) {
        RoutineWorkoutScreen(viewModel = viewModel)
    } else {
        val screens = listOf(
            Screen.LogWorkout,
            Screen.Progress,
            Screen.Profile,
            Screen.AiCoach
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.LogWorkout.route

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
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        screens.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DarkBg,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(screen.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.LogWorkout.route,
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

// Greeting function retained for reference test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", color = TextWhite, modifier = modifier)
}
