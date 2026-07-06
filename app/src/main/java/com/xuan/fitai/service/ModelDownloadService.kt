package com.xuan.fitai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xuan.fitai.FitAIApplication
import com.xuan.fitai.MainActivity
import com.xuan.fitai.R
import com.xuan.fitai.data.model.ModelDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ModelDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val notificationId = notificationIdFor(modelId)
        val app = application as FitAIApplication

        startForeground(
            notificationId,
            buildNotification(
                title = "Model download",
                text = "Preparing model download...",
                progress = 0,
                indeterminate = true,
                ongoing = true
            )
        )

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            app.modelManager.downloadStates.collectLatest { states ->
                val state = states[modelId] ?: return@collectLatest
                when (state) {
                    is ModelDownloadState.Downloading -> {
                        val progress = (state.progress * 100).toInt().coerceIn(0, 100)
                        notificationManager.notify(
                            notificationId,
                            buildNotification(
                                title = "Model download",
                                text = "$progress% (${state.downloadedBytes.toMb()}MB / ${state.totalBytes.toMb()}MB)",
                                progress = progress,
                                indeterminate = state.totalBytes <= 0,
                                ongoing = true
                            )
                        )
                    }
                    ModelDownloadState.Completed -> {
                        notificationManager.notify(
                            notificationId,
                            buildNotification(
                                title = "Model download complete",
                                text = "The model has been downloaded and loaded.",
                                progress = 100,
                                indeterminate = false,
                                ongoing = false
                            )
                        )
                        delay(1500)
                        stopSelf(startId)
                    }
                    is ModelDownloadState.Failed -> {
                        notificationManager.notify(
                            notificationId,
                            buildNotification(
                                title = "Model download failed",
                                text = state.message,
                                progress = 0,
                                indeterminate = false,
                                ongoing = false
                            )
                        )
                        delay(3000)
                        stopSelf(startId)
                    }
                    ModelDownloadState.Cancelled -> {
                        notificationManager.notify(
                            notificationId,
                            buildNotification(
                                title = "Model download cancelled",
                                text = "The download was stopped.",
                                progress = 0,
                                indeterminate = false,
                                ongoing = false
                            )
                        )
                        stopSelf(startId)
                    }
                    ModelDownloadState.Idle -> Unit
                }
            }
        }

        app.modelManager.downloadModelInBackground(modelId, token)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model downloads",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows model download progress"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean,
        ongoing: Boolean
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(contentIntent())
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .setProgress(100, progress, indeterminate)
        .build()

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun notificationIdFor(modelId: String): Int = 1000 + (modelId.hashCode() and 0x7fffffff) % 9000

    private fun Long.toMb(): Long = if (this > 0) this / 1024 / 1024 else 0

    companion object {
        const val EXTRA_MODEL_ID = "extra_model_id"
        const val EXTRA_TOKEN = "extra_token"
        private const val CHANNEL_ID = "model_downloads"
    }
}
