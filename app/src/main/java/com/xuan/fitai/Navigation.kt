package com.xuan.fitai

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.xuan.fitai.ui.chat.ChatScreen
import com.xuan.fitai.ui.chat.ChatViewModel
import com.xuan.fitai.ui.dashboard.DashboardScreen
import com.xuan.fitai.ui.dashboard.DashboardViewModel
import com.xuan.fitai.ui.onboarding.OnboardingScreen
import com.xuan.fitai.ui.onboarding.OnboardingViewModel
import com.xuan.fitai.ui.scanner.FoodScannerScreen
import com.xuan.fitai.ui.scanner.FoodScannerViewModel
import com.xuan.fitai.ui.setup.SettingsScreen
import com.xuan.fitai.ui.setup.ModelSetupViewModel
import com.xuan.fitai.ui.workout.WorkoutScreen
import com.xuan.fitai.ui.workout.WorkoutViewModel
import com.xuan.fitai.ui.reminders.ReminderScreen
import com.xuan.fitai.ui.reminders.ReminderViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    openRemindersRequest: Int = 0,
    onNavigateToRemindersHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FitAIApplication
    val hazeState = rememberHazeState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassTint = if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.12f)

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
        BottomDestination("workout", "運動", Icons.Default.FitnessCenter),
        BottomDestination("reminders", "提醒", Icons.Default.Notifications)
    )

    val adaptiveInfo = com.xuan.fitai.ui.adaptive.LocalAdaptiveInfo.current
    val useRail = adaptiveInfo.useRail
    val isPrimaryRoute = primaryDestinations.any { it.route == currentRoute }
    val showFloatingToolbar = isOnboardingCompleted == true && isPrimaryRoute && !useRail
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val toolbarContentPadding = 60.dp + navigationBarHeight + 8.dp

    var isNavBarCompact by rememberSaveable { mutableStateOf(false) }

    // Reset to full expanded size whenever the user navigates between top-level tabs
    LaunchedEffect(currentRoute) {
        isNavBarCompact = false
    }

    val currentIsPrimaryRoute by rememberUpdatedState(isPrimaryRoute)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!currentIsPrimaryRoute) return Offset.Zero
                val delta = available.y
                // Swiping up (reading downwards): delta < -8f -> shrink navigation bar
                // Swiping down (reading upwards / back to top): delta > 8f -> expand navigation bar
                if (delta < -8f && !isNavBarCompact) {
                    isNavBarCompact = true
                } else if (delta > 8f && isNavBarCompact) {
                    isNavBarCompact = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!currentIsPrimaryRoute) return Offset.Zero
                // If user reached top edge and pulled down further
                if (available.y > 0f && isNavBarCompact) {
                    isNavBarCompact = false
                }
                return Offset.Zero
            }
        }
    }

    val navigateToTopLevel: (String) -> Unit = { route ->
        isNavBarCompact = false
        val startDestinationId = navController.graph.findStartDestination().id
        val isStartDestination = route == "dashboard"
        navController.navigate(route) {
            popUpTo(startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = !isStartDestination
        }
    }

    androidx.compose.runtime.LaunchedEffect(openRemindersRequest, isOnboardingCompleted) {
        if (openRemindersRequest > 0 && isOnboardingCompleted == true) {
            navigateToTopLevel("reminders")
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
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .padding(start = if (useRail && isPrimaryRoute) 88.dp else 0.dp)
            ) {
                composable("onboarding") {
                    val onboardingVm: OnboardingViewModel = viewModel(
                        factory = OnboardingViewModel.Factory(app.userRepository)
                    )
                    val canNavigateBack = navController.previousBackStackEntry != null
                    com.xuan.fitai.ui.adaptive.AdaptiveContent {
                    OnboardingScreen(
                        viewModel = onboardingVm,
                        onNavigateBack = if (canNavigateBack) {
                            { navController.popBackStack() }
                        } else null,
                        onNavigateToDashboard = {
                            if (canNavigateBack) {
                                navController.popBackStack()
                            } else {
                                navController.navigate("dashboard") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                    )
                    }
                }

                composable("dashboard") {
                    val dashboardVm: DashboardViewModel = viewModel(
                        factory = DashboardViewModel.Factory(
                            app.userRepository,
                            app.mealRepository,
                            app.gemmaHelper,
                            app.reminderRepository,
                            app.database.progressDao(),
                            app.foodAssessment
                        )
                    )
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onNavigateToScanner = { navController.navigate("scanner") },
                        onNavigateToSetup = { navController.navigate("setup") },
                        onNavigateToModelSetup = { navController.navigate("setup?tab=1") },
                        onNavigateToReminders = { navigateToTopLevel("reminders") },
                        onAskAdvice = { seed -> navController.navigate("chat?prompt=" + android.net.Uri.encode(seed)) { launchSingleTop = true } },
                        onNavigateToProfile = {
                            navController.navigate("onboarding") { launchSingleTop = true }
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
                            app.classifierHelper,
                            app.foodAssessment
                        )
                    )
                    com.xuan.fitai.ui.adaptive.AdaptiveContent {
                    FoodScannerScreen(
                        viewModel = scannerVm,
                        onNavigateBack = { navController.popBackStack() },
                        onSaveComplete = {
                            navController.popBackStack()
                        }
                    )
                    }
                }

                composable("chat?prompt={prompt}", arguments = listOf(navArgument("prompt") { type = NavType.StringType; defaultValue = "" })) { chatEntry ->
                    val chatVm: ChatViewModel = viewModel(
                        factory = ChatViewModel.Factory(
                            app.userRepository,
                            app.mealRepository,
                            app.database.chatDao(),
                            app.gemmaHelper
                        )
                    )
                    com.xuan.fitai.ui.adaptive.AdaptiveContent {
                    ChatScreen(
                        viewModel = chatVm,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSetup = { navController.navigate("setup?tab=1") },
                        bottomOverlayPadding = navigationBarHeight,
                        initialPrompt = chatEntry.arguments?.getString("prompt").orEmpty(),
                    )
                    }
                }

                composable("workout") {
                    val workoutVm: WorkoutViewModel = viewModel(
                        factory = WorkoutViewModel.Factory(
                            app.userRepository,
                            app.workoutRepository,
                            app.gemmaHelper,
                            app.healthConnectHelper,
                            app.userPreferenceStore,
                            app.database.progressDao()
                        )
                    )
                    com.xuan.fitai.ui.adaptive.AdaptiveContent {
                    WorkoutScreen(
                        viewModel = workoutVm,
                        bottomContentPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
                        showAddWorkoutFab = true,
                        workoutFabBottomPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
                    )
                    }
                }

                composable("setup?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                    val setupVm: ModelSetupViewModel = viewModel(
                        factory = ModelSetupViewModel.Factory(
                            app.modelRepository,
                            app.userPreferenceStore,
                            app.modelManager
                        )
                    )
                    com.xuan.fitai.ui.adaptive.AdaptiveContent {
                    SettingsScreen(
                        viewModel = setupVm,
                        initialTab = entry.arguments?.getInt("tab") ?: 0,
                        onNavigateBack = { navController.popBackStack() }
                    )
                    }
                }

                composable("reminders") {
                    val reminderVm: ReminderViewModel = viewModel(
                        factory = ReminderViewModel.Factory(app.reminderRepository)
                    )
                    com.xuan.fitai.ui.adaptive.AdaptiveContent {
                    ReminderScreen(
                        viewModel = reminderVm,
                        bottomContentPadding = if (showFloatingToolbar) toolbarContentPadding else 0.dp,
                    )
                    }
                }
            }
            if (useRail && isPrimaryRoute && isOnboardingCompleted == true) {
                // Let the rail draw behind system bars; its own windowInsets keep items safe.
                NavigationRail(modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Spacer(Modifier.height(16.dp))
                    primaryDestinations.forEach { destination ->
                        NavigationRailItem(selected = currentRoute == destination.route, onClick = { navigateToTopLevel(destination.route) },
                            icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label) })
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(selected = false, onClick = { navController.navigate("chat") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.SmartToy, "AI 助理") }, label = { Text("AI 助理") })
                }
            }
            val animatedBottomPadding by animateDpAsState(
                targetValue = if (isNavBarCompact) 6.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "bottomPadding"
            )

            // Frosted Glass Floating Bottom Navigation Toolbar
            AnimatedVisibility(
                visible = showFloatingToolbar,
                enter = fadeIn(animationSpec = tween(220)) +
                        slideInVertically(animationSpec = tween(220), initialOffsetY = { it }),
                exit = fadeOut(animationSpec = tween(160)) +
                        slideOutVertically(animationSpec = tween(160), targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = animatedBottomPadding)
            ) {
                val animatedAssistantSize by animateDpAsState(
                    targetValue = if (isNavBarCompact) 48.dp else 56.dp,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    label = "assistantSize"
                )
                val animatedAssistantIconSize by animateDpAsState(
                    targetValue = if (isNavBarCompact) 21.dp else 24.dp,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    label = "assistantIconSize"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingDestinationToolbar(
                        destinations = primaryDestinations,
                        currentRoute = currentRoute,
                        isCompact = isNavBarCompact,
                        hazeState = hazeState,
                        onNavigate = { route ->
                            isNavBarCompact = false
                            navigateToTopLevel(route)
                        }
                    )

                    // Quick AI assistant button
                    Box(
                        modifier = Modifier
                            .size(animatedAssistantSize)
                            .shadow(
                                elevation = if (isDark) 0.dp else 3.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.08f)
                            )
                            .clip(CircleShape)
                            .hazeEffect(state = hazeState) {
                                blurRadius = 6.dp
                                tints = listOf(HazeTint(glassTint))
                                noiseFactor = 0f
                            }
                            .background(
                                if (isDark) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.22f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                                shape = CircleShape
                            )
                            .clickable {
                                isNavBarCompact = false
                                navController.navigate("chat") { launchSingleTop = true }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "開啟 AI 助理",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(animatedAssistantIconSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingDestinationToolbar(
    destinations: List<BottomDestination>,
    currentRoute: String?,
    isCompact: Boolean,
    hazeState: HazeState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val toolbarGlassTint = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.32f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    val animatedHeight by animateDpAsState(
        targetValue = if (isCompact) 48.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "toolbarHeight"
    )
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isCompact) 24.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "cornerRadius"
    )
    val animatedSlotSpacing by animateDpAsState(
        targetValue = if (isCompact) 44.dp else 54.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "slotSpacing"
    )
    val animatedCapsuleWidth by animateDpAsState(
        targetValue = if (isCompact) 48.dp else 60.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "capsuleWidth"
    )
    val animatedCapsuleHeight by animateDpAsState(
        targetValue = if (isCompact) 40.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "capsuleHeight"
    )
    val animatedCapsuleRadius by animateDpAsState(
        targetValue = if (isCompact) 20.dp else 23.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "capsuleRadius"
    )
    val animatedEndMargin by animateDpAsState(
        targetValue = if (isCompact) 4.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "endMargin"
    )
    val animatedInnerSlotPadding by animateDpAsState(
        targetValue = if (isCompact) 6.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "innerSlotPadding"
    )
    val animatedIconSize by animateDpAsState(
        targetValue = if (isCompact) 21.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "iconSize"
    )

    val pillShape = RoundedCornerShape(animatedCornerRadius)
    val capsuleShape = RoundedCornerShape(animatedCapsuleRadius)

    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val animatedCapsuleOffset by animateDpAsState(
        targetValue = animatedEndMargin + (animatedSlotSpacing.value * selectedIndex).dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "capsuleOffset"
    )

    Surface(
        modifier = modifier
            .height(animatedHeight)
            .shadow(
                elevation = if (isDark) 0.dp else 4.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(pillShape)
            .hazeEffect(state = hazeState) {
                blurRadius = 6.dp
                tints = listOf(HazeTint(toolbarGlassTint))
                noiseFactor = 0f
            },
        shape = pillShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.38f)
                else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.50f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
        ),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Sliding Capsule Indicator (underlay that overlaps into adjacent slot space)
            Box(
                modifier = Modifier
                    .offset(x = animatedCapsuleOffset)
                    .size(width = animatedCapsuleWidth, height = animatedCapsuleHeight)
                    .clip(capsuleShape)
                    .background(
                        if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
                        else MaterialTheme.colorScheme.primaryContainer
                    )
            )

            // Equal-spaced Stationary Icon Slots
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = animatedInnerSlotPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    key(destination.route) {
                        val isSelected = destination.route == currentRoute
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "iconTint_${destination.route}"
                        )

                        Box(
                            modifier = Modifier
                                .width(animatedSlotSpacing)
                                .fillMaxHeight()
                                .clip(capsuleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onNavigate(destination.route)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = iconTint,
                                modifier = Modifier.size(animatedIconSize)
                            )
                        }
                    }
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
