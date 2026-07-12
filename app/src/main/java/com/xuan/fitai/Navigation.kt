package com.xuan.fitai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xuan.fitai.ui.chat.ChatScreen
import com.xuan.fitai.ui.chat.ChatViewModel
import com.xuan.fitai.ui.dashboard.DashboardScreen
import com.xuan.fitai.ui.dashboard.DashboardViewModel
import com.xuan.fitai.ui.onboarding.OnboardingScreen
import com.xuan.fitai.ui.onboarding.OnboardingViewModel
import com.xuan.fitai.ui.scanner.FoodScannerScreen
import com.xuan.fitai.ui.scanner.FoodScannerViewModel
import com.xuan.fitai.ui.setup.ModelSetupScreen
import com.xuan.fitai.ui.setup.ModelSetupViewModel
import com.xuan.fitai.ui.workout.WorkoutScreen
import com.xuan.fitai.ui.workout.WorkoutViewModel
import com.xuan.fitai.ui.reminders.ReminderScreen
import com.xuan.fitai.ui.reminders.ReminderViewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.foundation.layout.WindowInsets

@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    openRemindersRequest: Int = 0,
    onNavigateToRemindersHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FitAIApplication
    val coroutineScope = rememberCoroutineScope()

    val isOnboardingCompleted by app.userRepository.isOnboardingCompleted.collectAsState(initial = null)

    if (isOnboardingCompleted == null) {
        // Wait for preference loading
        return
    }

    val startDestination = if (isOnboardingCompleted == true) "dashboard" else "onboarding"
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val primaryDestinations = listOf(
        BottomDestination("dashboard", "首頁", Icons.Default.Home),
        BottomDestination("chat", "AI", Icons.AutoMirrored.Filled.Chat),
        BottomDestination("workout", "運動", Icons.Default.FitnessCenter),
        BottomDestination("reminders", "提醒", Icons.Default.Notifications)
    )

    androidx.compose.runtime.LaunchedEffect(openRemindersRequest, isOnboardingCompleted) {
        if (openRemindersRequest > 0 && isOnboardingCompleted == true) {
            navController.navigate("reminders") { launchSingleTop = true }
            onNavigateToRemindersHandled()
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isOnboardingCompleted == true && primaryDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    primaryDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(outerPadding)
    ) {
        composable("onboarding") {
            val onboardingVm: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(app.userRepository)
            )
            OnboardingScreen(
                viewModel = onboardingVm,
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            val dashboardVm: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(
                    app.userRepository,
                    app.mealRepository,
                    app.gemmaHelper
                )
            )
            DashboardScreen(
                viewModel = dashboardVm,
                onNavigateToScanner = { navController.navigate("scanner") },
                onNavigateToSetup = { navController.navigate("setup") },
                onResetOnboarding = {
                    coroutineScope.launch {
                        app.userRepository.resetOnboarding()
                    }
                    navController.navigate("onboarding") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("scanner") {
            val scannerVm: FoodScannerViewModel = viewModel(
                factory = FoodScannerViewModel.Factory(
                    app.userRepository,
                    app.mealRepository,
                    app.gemmaHelper,
                    app.classifierHelper
                )
            )
            FoodScannerScreen(
                viewModel = scannerVm,
                onNavigateBack = { navController.popBackStack() },
                onSaveComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable("chat") {
            val chatVm: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    app.userRepository,
                    app.mealRepository,
                    app.database.chatDao(),
                    app.gemmaHelper
                )
            )
            ChatScreen(
                viewModel = chatVm,
                onNavigateToSetup = { navController.navigate("setup") }
            )
        }

        composable("workout") {
            val workoutVm: WorkoutViewModel = viewModel(
                factory = WorkoutViewModel.Factory(
                    app.userRepository,
                    app.workoutRepository,
                    app.gemmaHelper,
                    app.healthConnectHelper,
                    app.userPreferenceStore
                )
            )
            WorkoutScreen(
                viewModel = workoutVm
            )
        }

        composable("setup") {
            val setupVm: ModelSetupViewModel = viewModel(
                factory = ModelSetupViewModel.Factory(
                    app.modelRepository,
                    app.userPreferenceStore,
                    app.modelManager
                )
            )
            ModelSetupScreen(
                viewModel = setupVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("reminders") {
            val reminderVm: ReminderViewModel = viewModel(
                factory = ReminderViewModel.Factory(app.reminderRepository)
            )
            ReminderScreen(
                viewModel = reminderVm
            )
        }
    }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
