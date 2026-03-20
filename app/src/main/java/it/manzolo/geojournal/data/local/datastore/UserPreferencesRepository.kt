package it.manzolo.geojournal.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    val isDarkTheme: Boolean = false,
    val userId: String = "",
    val isGuest: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val autoBackupEnabled: Boolean = false,
    val driveBackupUri: String = "",   // URI SAF persistito per il backup su Drive/cloud
    val lastLocalBackupTimestamp: Long = 0L
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val USER_ID = stringPreferencesKey("user_id")
        val IS_GUEST = booleanPreferencesKey("is_guest")
        val LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val DRIVE_BACKUP_URI = stringPreferencesKey("drive_backup_uri")
        val LAST_LOCAL_BACKUP = longPreferencesKey("last_local_backup_timestamp")
    }

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            UserPreferences(
                isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: false,
                userId = prefs[Keys.USER_ID] ?: "",
                isGuest = prefs[Keys.IS_GUEST] ?: false,
                lastSyncTimestamp = prefs[Keys.LAST_SYNC] ?: 0L,
                autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
                driveBackupUri = prefs[Keys.DRIVE_BACKUP_URI] ?: "",
                lastLocalBackupTimestamp = prefs[Keys.LAST_LOCAL_BACKUP] ?: 0L
            )
        }

    suspend fun setDarkTheme(isDark: Boolean) {
        dataStore.edit { it[Keys.IS_DARK_THEME] = isDark }
    }

    suspend fun setUserId(userId: String) {
        dataStore.edit { it[Keys.USER_ID] = userId }
    }

    suspend fun setIsGuest(isGuest: Boolean) {
        dataStore.edit { it[Keys.IS_GUEST] = isGuest }
    }

    suspend fun setLastSync(timestamp: Long) {
        dataStore.edit { it[Keys.LAST_SYNC] = timestamp }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setDriveBackupUri(uri: String) {
        dataStore.edit { it[Keys.DRIVE_BACKUP_URI] = uri }
    }

    suspend fun setLastLocalBackup(timestamp: Long) {
        dataStore.edit { it[Keys.LAST_LOCAL_BACKUP] = timestamp }
    }
}
