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
import androidx.compose.foundation.isSystemInDarkTheme
import com.xuan.fitai.theme.FitAITheme
import com.xuan.fitai.ui.components.LoadingDialog
import com.xuan.fitai.data.model.AppearanceSettings
import com.xuan.fitai.data.model.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var openRemindersRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitAIApplication
        handleNotificationIntent(intent)
        handleClipboardIntent(intent)

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
            val systemDark = isSystemInDarkTheme()
            val isDark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FitAITheme(
                darkTheme = isDark,
                dynamicColor = appearance.useWallpaperColors,
                customHue = appearance.customHue,
                customSaturation = appearance.customSaturation
            ) {
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
        handleClipboardIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) == true) {
            openRemindersRequest++
            intent.removeExtra(EXTRA_OPEN_REMINDERS)
        }
    }

    private fun handleClipboardIntent(intent: android.content.Intent?) {
        val text = intent?.getStringExtra("copy_to_clipboard")
        if (!text.isNullOrBlank()) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("debug_input", text))
            intent.removeExtra("copy_to_clipboard")
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
