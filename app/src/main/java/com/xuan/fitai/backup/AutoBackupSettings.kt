package com.xuan.fitai.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

const val MIN_BACKUP_MINUTES = 15L
const val MAX_BACKUP_MINUTES = 43_200L
const val DRIVE_DOCUMENT_AUTHORITY = "com.google.android.apps.docs.storage"

data class AutoBackupSettings(
    val enabled: Boolean = false,
    val destination: String = "",
    val fileName: String = "",
    val intervalMinutes: Long = 1440,
    val wifiOnly: Boolean = true,
    val generation: Long = 0,
    val lastDeliveredAt: Long = 0,
    val lastAttemptAt: Long = 0,
    val outcome: String = "",
    val error: String = "",
)

fun backupIntervalLabel(minutes: Long): String = when {
    minutes % 1440 == 0L -> "每 ${minutes / 1440} 天"
    minutes % 60 == 0L -> "每 ${minutes / 60} 小時"
    else -> "每 $minutes 分鐘"
}

private val Context.autoBackupPreferences by preferencesDataStore("auto_backup_preferences")

class AutoBackupStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.autoBackupPreferences)
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val destination = stringPreferencesKey("destination")
        val fileName = stringPreferencesKey("file_name")
        val interval = longPreferencesKey("interval_minutes")
        val wifi = booleanPreferencesKey("wifi_only")
        val generation = longPreferencesKey("generation")
        val delivered = longPreferencesKey("last_delivered")
        val attempted = longPreferencesKey("last_attempted")
        val outcome = stringPreferencesKey("outcome")
        val error = stringPreferencesKey("error")
    }
    val settings = store.data.map { p ->
        AutoBackupSettings(p[Keys.enabled] ?: false, p[Keys.destination].orEmpty(), p[Keys.fileName].orEmpty(),
            (p[Keys.interval] ?: 1440).coerceIn(MIN_BACKUP_MINUTES, MAX_BACKUP_MINUTES), p[Keys.wifi] ?: true,
            p[Keys.generation] ?: 0, p[Keys.delivered] ?: 0, p[Keys.attempted] ?: 0, p[Keys.outcome].orEmpty(), p[Keys.error].orEmpty())
    }
    suspend fun connect(destination: String, name: String) = store.edit {
        require(destination.isNotBlank())
        it[Keys.destination] = destination; it[Keys.fileName] = name
        it[Keys.generation] = (it[Keys.generation] ?: 0) + 1
        it[Keys.enabled] = true; it[Keys.delivered] = 0; it[Keys.attempted] = 0
        it[Keys.outcome] = ""; it[Keys.error] = ""
    }
    suspend fun disconnect() = store.edit {
        it[Keys.enabled] = false; it.remove(Keys.destination); it.remove(Keys.fileName)
        it[Keys.generation] = (it[Keys.generation] ?: 0) + 1
        it[Keys.delivered] = 0; it[Keys.attempted] = 0; it[Keys.outcome] = ""; it[Keys.error] = ""
    }
    suspend fun setEnabled(enabled: Boolean) = store.edit {
        require(!enabled || !it[Keys.destination].isNullOrBlank()) { "請先選擇 Google Drive 備份檔" }
        it[Keys.enabled] = enabled
    }
    suspend fun setInterval(minutes: Long) = store.edit {
        require(minutes in MIN_BACKUP_MINUTES..MAX_BACKUP_MINUTES)
        it[Keys.interval] = minutes
    }
    suspend fun setWifiOnly(value: Boolean) = store.edit { it[Keys.wifi] = value }
    suspend fun record(snapshot: AutoBackupSettings, outcome: String, error: String = "", deliveredAt: Long? = null) = store.edit {
        if (it[Keys.destination] != snapshot.destination || (it[Keys.generation] ?: 0) != snapshot.generation) return@edit
        it[Keys.outcome] = outcome; it[Keys.error] = error; it[Keys.attempted] = System.currentTimeMillis()
        if (deliveredAt != null) it[Keys.delivered] = deliveredAt
        if (outcome == "needs_access") it[Keys.enabled] = false
    }
}
