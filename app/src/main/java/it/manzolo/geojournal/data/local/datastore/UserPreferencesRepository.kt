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
    val isPro: Boolean = false,
    val userId: String = "",
    val lastSyncTimestamp: Long = 0L
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val USER_ID = stringPreferencesKey("user_id")
        val LAST_SYNC = longPreferencesKey("last_sync_timestamp")
    }

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            UserPreferences(
                isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: false,
                isPro = prefs[Keys.IS_PRO] ?: false,
                userId = prefs[Keys.USER_ID] ?: "",
                lastSyncTimestamp = prefs[Keys.LAST_SYNC] ?: 0L
            )
        }

    suspend fun setDarkTheme(isDark: Boolean) {
        dataStore.edit { it[Keys.IS_DARK_THEME] = isDark }
    }

    suspend fun setIsPro(isPro: Boolean) {
        dataStore.edit { it[Keys.IS_PRO] = isPro }
    }

    suspend fun setUserId(userId: String) {
        dataStore.edit { it[Keys.USER_ID] = userId }
    }

    suspend fun setLastSync(timestamp: Long) {
        dataStore.edit { it[Keys.LAST_SYNC] = timestamp }
    }
}
