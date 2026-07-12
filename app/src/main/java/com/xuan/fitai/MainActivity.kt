package com.xuan.fitai

import android.Manifest
import android.graphics.Color
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xuan.fitai.theme.FitAITheme
import com.xuan.fitai.ui.components.LoadingDialog

class MainActivity : ComponentActivity() {
    private var openRemindersRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitAIApplication
        handleNotificationIntent(intent)
        requestNotificationPermissionIfNeeded()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            FitAITheme {
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
        const val EXTRA_OPEN_REMINDERS = "open_reminders"
    }
}

@Composable
private fun StartupLoadingDialog() {
    LoadingDialog(
        title = "FitAI",
        message = "Preparing your local AI experience."
    )
}
