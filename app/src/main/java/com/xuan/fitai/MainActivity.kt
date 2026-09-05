package com.xuan.fitai

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import com.xuan.fitai.theme.FitAITheme
import com.xuan.fitai.ui.components.LoadingDialog
import com.xuan.fitai.data.model.AppearanceSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var openRemindersRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitAIApplication
        handleNotificationIntent(intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val appearance by app.userPreferenceStore.appearanceFlow.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            FitAITheme(dynamicColor = appearance.useWallpaperColors, customHue = appearance.customHue) {
                var startupInitializing by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    try {
                        app.modelManager.initializeDefaultModels()
                    } finally {
                        startupInitializing = false
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (startupInitializing) {
                        StartupLoadingDialog()
                    } else {
                        MainNavigation(
                            openRemindersRequest = openRemindersRequest,
                            onNavigateToRemindersHandled = { openRemindersRequest = 0 }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) == true) {
            openRemindersRequest++
            intent.removeExtra(EXTRA_OPEN_REMINDERS)
        }
    }

    private companion object {
        const val EXTRA_OPEN_REMINDERS = "open_reminders"
    }
}

@Composable
private fun StartupLoadingDialog() {
    LoadingDialog(
        title = "FitAI",
        message = "正在準備你的 AI 助理…"
    )
}
