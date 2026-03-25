package it.manzolo.geojournal.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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
    val lastLocalBackupTimestamp: Long = 0L,
    val lastDriveBackupTimestamp: Long = 0L,
    val lastDriveBackupSuccess: Boolean = false,
    val hasSeenDataOnboarding: Boolean = false,
    val mapLat: Double = 0.0,
    val mapLon: Double = 0.0,
    val mapZoom: Double = 0.0,
    // Privacy sync: tutti OFF per default — l'utente sceglie cosa mandare su Firebase
    val syncGeoPointsEnabled: Boolean = false,
    val syncPhotosEnabled: Boolean = false,
    val syncRemindersEnabled: Boolean = false,
    val syncVisitLogsEnabled: Boolean = false
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
        val LAST_DRIVE_BACKUP = longPreferencesKey("last_drive_backup_timestamp")
        val LAST_DRIVE_BACKUP_SUCCESS = booleanPreferencesKey("last_drive_backup_success")
        val HAS_SEEN_DATA_ONBOARDING = booleanPreferencesKey("has_seen_data_onboarding")
        val MAP_LAT = doublePreferencesKey("map_lat")
        val MAP_LON = doublePreferencesKey("map_lon")
        val MAP_ZOOM = doublePreferencesKey("map_zoom")
        // Privacy sync flags
        val SYNC_GEO_POINTS = booleanPreferencesKey("sync_geo_points_enabled")
        val SYNC_PHOTOS = booleanPreferencesKey("sync_photos_enabled")
        val SYNC_REMINDERS = booleanPreferencesKey("sync_reminders_enabled")
        val SYNC_VISIT_LOGS = booleanPreferencesKey("sync_visit_logs_enabled")
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
                lastLocalBackupTimestamp = prefs[Keys.LAST_LOCAL_BACKUP] ?: 0L,
                lastDriveBackupTimestamp = prefs[Keys.LAST_DRIVE_BACKUP] ?: 0L,
                lastDriveBackupSuccess = prefs[Keys.LAST_DRIVE_BACKUP_SUCCESS] ?: false,
                hasSeenDataOnboarding = prefs[Keys.HAS_SEEN_DATA_ONBOARDING] ?: false,
                mapLat = prefs[Keys.MAP_LAT] ?: 0.0,
                mapLon = prefs[Keys.MAP_LON] ?: 0.0,
                mapZoom = prefs[Keys.MAP_ZOOM] ?: 0.0,
                syncGeoPointsEnabled = prefs[Keys.SYNC_GEO_POINTS] ?: false,
                syncPhotosEnabled = prefs[Keys.SYNC_PHOTOS] ?: false,
                syncRemindersEnabled = prefs[Keys.SYNC_REMINDERS] ?: false,
                syncVisitLogsEnabled = prefs[Keys.SYNC_VISIT_LOGS] ?: false
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

    suspend fun setLastDriveBackup(timestamp: Long, success: Boolean) {
        dataStore.edit {
            it[Keys.LAST_DRIVE_BACKUP] = timestamp
            it[Keys.LAST_DRIVE_BACKUP_SUCCESS] = success
        }
    }

    suspend fun setHasSeenDataOnboarding(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_DATA_ONBOARDING] = seen }
    }

    suspend fun setMapPosition(lat: Double, lon: Double, zoom: Double) {
        dataStore.edit {
            it[Keys.MAP_LAT] = lat
            it[Keys.MAP_LON] = lon
            it[Keys.MAP_ZOOM] = zoom
        }
    }

    suspend fun setSyncGeoPointsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_GEO_POINTS] = enabled }
    }

    suspend fun setSyncPhotosEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_PHOTOS] = enabled }
    }

    suspend fun setSyncRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_REMINDERS] = enabled }
    }

    suspend fun setSyncVisitLogsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_VISIT_LOGS] = enabled }
    }
}
