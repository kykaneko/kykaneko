package jp.kykaneko.sleep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sleep_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val targetSleepMinutes: Flow<Int> = dataStore.data.map { preferences ->
        preferences[TARGET_SLEEP_MINUTES] ?: DEFAULT_TARGET_SLEEP_MINUTES
    }

    suspend fun setTargetSleepMinutes(minutes: Int) {
        require(minutes in MIN_TARGET_SLEEP_MINUTES..MAX_TARGET_SLEEP_MINUTES) {
            "目標睡眠時間は1分から23時間59分の範囲で設定してください。"
        }
        dataStore.edit { preferences ->
            preferences[TARGET_SLEEP_MINUTES] = minutes
        }
    }

    companion object {
        const val DEFAULT_TARGET_SLEEP_MINUTES = 7 * 60 + 30
        const val MIN_TARGET_SLEEP_MINUTES = 1
        const val MAX_TARGET_SLEEP_MINUTES = 23 * 60 + 59
        private val TARGET_SLEEP_MINUTES = intPreferencesKey("target_sleep_minutes")
    }
}
