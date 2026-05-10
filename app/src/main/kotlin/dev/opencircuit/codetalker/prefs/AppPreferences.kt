package dev.opencircuit.codetalker.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * CCT-32 Phase B — non-sensitive app settings, persisted via DataStore.
 *
 * Sensitive material (pairing token, daemon URL) lives in
 * [dev.opencircuit.codetalker.net.PairingFlow]'s EncryptedSharedPreferences.
 * Everything else — onboarding flag (B.2), boot opt-in (B.4), and the
 * activeSessionId restored on process death (B.5) — lands here.
 */
interface AppPreferences {
    val onboardingComplete: Flow<Boolean>
    val startOnBoot: Flow<Boolean>
    val activeSessionId: Flow<String?>

    suspend fun setOnboardingComplete(value: Boolean)
    suspend fun setStartOnBoot(value: Boolean)
    suspend fun setActiveSessionId(value: String?)

    companion object {
        /** Production factory — backed by androidx.datastore.preferences. */
        fun forContext(context: Context): AppPreferences =
            DataStoreAppPreferences(context.applicationContext.dataStore)
    }
}

object AppPreferenceKeys {
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
}

class DataStoreAppPreferences(
    private val store: DataStore<Preferences>,
) : AppPreferences {

    override val onboardingComplete: Flow<Boolean> = store.data.map {
        it[AppPreferenceKeys.ONBOARDING_COMPLETE] == true
    }

    override val startOnBoot: Flow<Boolean> = store.data.map {
        it[AppPreferenceKeys.START_ON_BOOT] == true
    }

    override val activeSessionId: Flow<String?> = store.data.map {
        it[AppPreferenceKeys.ACTIVE_SESSION_ID]
    }

    override suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETE] = value }
    }

    override suspend fun setStartOnBoot(value: Boolean) {
        store.edit { it[AppPreferenceKeys.START_ON_BOOT] = value }
    }

    override suspend fun setActiveSessionId(value: String?) {
        store.edit {
            if (value == null) it.remove(AppPreferenceKeys.ACTIVE_SESSION_ID)
            else it[AppPreferenceKeys.ACTIVE_SESSION_ID] = value
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "codetalker_prefs",
)
