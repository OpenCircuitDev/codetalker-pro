package dev.opencircuit.codetalker.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    /**
     * 2026-05-11 — multi-active. Set of session_ids the phone is currently
     * subscribed to. Daemon and TTSPlayer both fan in via this set. The
     * legacy [activeSessionId] flow is still emitted (= first member of the
     * set, lex-sorted) so any single-slot reader keeps working until
     * migrated.
     */
    val activeSessionIds: Flow<Set<String>>

    /**
     * CCT-32 Task G.2 — opt-in crash reporting flag. Default false.
     * `crashReportingConsentAsked` distinguishes "user has not yet been
     * asked" (show ConsentFlow once) from "user explicitly declined"
     * (consent asked = true, enabled = false).
     */
    val crashReportingEnabled: Flow<Boolean>
    val crashReportingConsentAsked: Flow<Boolean>

    /**
     * CCT-32 v0.1.0 polish — Sessions list filter selection.
     * One of: "live" (default), "all", "dormant", "active".
     */
    val sessionFilter: Flow<String>
    /**
     * CCT-32 v0.1.0 polish — set of collapsed project group slugs in the
     * Sessions list. Empty set = all groups expanded (default).
     */
    val sessionCollapsedGroups: Flow<Set<String>>

    suspend fun setOnboardingComplete(value: Boolean)
    suspend fun setStartOnBoot(value: Boolean)
    suspend fun setActiveSessionId(value: String?)
    suspend fun setActiveSessionIds(value: Set<String>)
    suspend fun setCrashReportingEnabled(value: Boolean)
    suspend fun setCrashReportingConsentAsked(value: Boolean)
    suspend fun setSessionFilter(value: String)
    suspend fun setSessionCollapsedGroups(value: Set<String>)

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
    val ACTIVE_SESSION_IDS = stringSetPreferencesKey("active_session_ids")
    val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
    val CRASH_REPORTING_CONSENT_ASKED = booleanPreferencesKey("crash_reporting_consent_asked")
    val SESSION_FILTER = stringPreferencesKey("session_filter")
    val SESSION_COLLAPSED_GROUPS = stringSetPreferencesKey("session_collapsed_groups")
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
        // Multi-active migration: emit the first member of the set so legacy
        // single-slot readers (rare now, the few remaining still want a
        // "primary") keep functioning. Falls back to the old single key for
        // pre-migration installs.
        val setMembers = it[AppPreferenceKeys.ACTIVE_SESSION_IDS]
        if (!setMembers.isNullOrEmpty()) {
            setMembers.sorted().firstOrNull()
        } else {
            it[AppPreferenceKeys.ACTIVE_SESSION_ID]
        }
    }

    override val activeSessionIds: Flow<Set<String>> = store.data.map {
        val setMembers = it[AppPreferenceKeys.ACTIVE_SESSION_IDS]
        if (setMembers != null) return@map setMembers
        // Migration: lift the single legacy key into a 1-member set so an
        // upgraded user doesn't lose their last-active session.
        val legacy = it[AppPreferenceKeys.ACTIVE_SESSION_ID]
        if (legacy.isNullOrBlank()) emptySet() else setOf(legacy)
    }

    override val crashReportingEnabled: Flow<Boolean> = store.data.map {
        it[AppPreferenceKeys.CRASH_REPORTING_ENABLED] == true
    }

    override val crashReportingConsentAsked: Flow<Boolean> = store.data.map {
        it[AppPreferenceKeys.CRASH_REPORTING_CONSENT_ASKED] == true
    }

    override val sessionFilter: Flow<String> = store.data.map {
        // 2026-05-16 — reverted default to "live". The "active" default
        // assumed the user would tap a session within ~30s of opening
        // the app; in practice the Sessions screen was rendering as a
        // single-row list because no session had a fresh interaction
        // OR a companion-active flag, leaving every live CC session
        // hidden. "Live" matches the user's mental model ("show me
        // what's running") while "Active" stays one tap away for the
        // noise-reduction case. Old values ("all" / "dormant" from
        // prior versions) still get migrated so the UI doesn't render
        // with an unknown selection.
        val stored = it[AppPreferenceKeys.SESSION_FILTER]
        when (stored) {
            null, "all", "dormant" -> "live"
            else -> stored
        }
    }

    override val sessionCollapsedGroups: Flow<Set<String>> = store.data.map {
        it[AppPreferenceKeys.SESSION_COLLAPSED_GROUPS] ?: emptySet()
    }

    override suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETE] = value }
    }

    override suspend fun setStartOnBoot(value: Boolean) {
        store.edit { it[AppPreferenceKeys.START_ON_BOOT] = value }
    }

    override suspend fun setActiveSessionId(value: String?) {
        // Legacy single-slot writer — kept for callers that haven't migrated.
        // ALSO updates the set so the two flows stay in sync; passing null
        // clears both. New callers should prefer setActiveSessionIds.
        store.edit {
            if (value.isNullOrBlank()) {
                it.remove(AppPreferenceKeys.ACTIVE_SESSION_ID)
                it[AppPreferenceKeys.ACTIVE_SESSION_IDS] = emptySet()
            } else {
                it[AppPreferenceKeys.ACTIVE_SESSION_ID] = value
                it[AppPreferenceKeys.ACTIVE_SESSION_IDS] = setOf(value)
            }
        }
    }

    override suspend fun setActiveSessionIds(value: Set<String>) {
        store.edit {
            it[AppPreferenceKeys.ACTIVE_SESSION_IDS] = value
            // Keep legacy single-slot pointing at the first member so any
            // not-yet-migrated reader sees a deterministic value.
            val first = value.sorted().firstOrNull()
            if (first == null) {
                it.remove(AppPreferenceKeys.ACTIVE_SESSION_ID)
            } else {
                it[AppPreferenceKeys.ACTIVE_SESSION_ID] = first
            }
        }
    }

    override suspend fun setCrashReportingEnabled(value: Boolean) {
        store.edit { it[AppPreferenceKeys.CRASH_REPORTING_ENABLED] = value }
    }

    override suspend fun setCrashReportingConsentAsked(value: Boolean) {
        store.edit { it[AppPreferenceKeys.CRASH_REPORTING_CONSENT_ASKED] = value }
    }

    override suspend fun setSessionFilter(value: String) {
        store.edit { it[AppPreferenceKeys.SESSION_FILTER] = value }
    }

    override suspend fun setSessionCollapsedGroups(value: Set<String>) {
        store.edit { it[AppPreferenceKeys.SESSION_COLLAPSED_GROUPS] = value }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "codetalker_prefs",
)
