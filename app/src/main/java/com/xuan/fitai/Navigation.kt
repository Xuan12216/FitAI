package com.xuan.fitai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
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
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToWorkout = { navController.navigate("workout") },
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
                onNavigateBack = { navController.popBackStack() },
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
                viewModel = workoutVm,
                onNavigateBack = { navController.popBackStack() }
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
    }
}
