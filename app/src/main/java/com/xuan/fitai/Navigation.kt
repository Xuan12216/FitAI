package com.xuan.fitai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.dp

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

    val showFloatingToolbar = isOnboardingCompleted == true && primaryDestinations.any { it.route == currentRoute }
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val toolbarContentPadding = FloatingToolbarDefaults.ContainerSize + navigationBarHeight + 8.dp
    androidx.compose.runtime.LaunchedEffect(openRemindersRequest, isOnboardingCompleted) {
        if (openRemindersRequest > 0 && isOnboardingCompleted == true) {
            navController.navigate("reminders") { launchSingleTop = true }
            onNavigateToRemindersHandled()
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { outerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(outerPadding)
    ) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 0.dp)
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
                },
                bottomContentPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
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
                onNavigateToSetup = { navController.navigate("setup") },
                bottomOverlayPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
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
                bottomContentPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
                showAddWorkoutFab = true,
                workoutFabBottomPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
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
                viewModel = reminderVm,
                bottomContentPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
            )
        }
    }
    if (showFloatingToolbar) {
        FloatingDestinationToolbar(
            destinations = primaryDestinations,
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
    }
    }
    }
}

@Composable
private fun LegacyFloatingDestinationToolbar(
    destinations: List<BottomDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val isSelected = destination.route == currentRoute
                    IconButton(onClick = { onNavigate(destination.route) }) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onPrimaryAction,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "新增餐點")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingDestinationToolbar(
    destinations: List<BottomDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        destinations.forEach { destination ->
            val isSelected = destination.route == currentRoute
            if (isSelected) {
                FilledTonalButton(
                    onClick = { onNavigate(destination.route) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(destination.label)
                }
            } else {
                TextButton(onClick = { onNavigate(destination.route) }) {
                    Text(destination.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
