package dev.opencircuit.codetalker.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * CCT-32 Phase B — in-memory AppPreferences fake for unit tests.
 * Behavior should match DataStoreAppPreferences: defaults are false /
 * null and writes propagate to the corresponding flow.
 *
 * Phase G adds the crash-reporting consent flags.
 */
class InMemoryAppPreferences : AppPreferences {
    private val _onboarding = MutableStateFlow(false)
    private val _boot = MutableStateFlow(false)
    private val _active = MutableStateFlow<String?>(null)
    private val _crashEnabled = MutableStateFlow(false)
    private val _consentAsked = MutableStateFlow(false)
    private val _sessionFilter = MutableStateFlow("live")
    private val _sessionCollapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    override val onboardingComplete: Flow<Boolean> = _onboarding
    override val startOnBoot: Flow<Boolean> = _boot
    override val activeSessionId: Flow<String?> = _active
    override val crashReportingEnabled: Flow<Boolean> = _crashEnabled
    override val crashReportingConsentAsked: Flow<Boolean> = _consentAsked
    override val sessionFilter: Flow<String> = _sessionFilter
    override val sessionCollapsedGroups: Flow<Set<String>> = _sessionCollapsedGroups

    override suspend fun setOnboardingComplete(value: Boolean) { _onboarding.value = value }
    override suspend fun setStartOnBoot(value: Boolean) { _boot.value = value }
    override suspend fun setActiveSessionId(value: String?) { _active.value = value }
    override suspend fun setCrashReportingEnabled(value: Boolean) { _crashEnabled.value = value }
    override suspend fun setCrashReportingConsentAsked(value: Boolean) { _consentAsked.value = value }
    override suspend fun setSessionFilter(value: String) { _sessionFilter.value = value }
    override suspend fun setSessionCollapsedGroups(value: Set<String>) { _sessionCollapsedGroups.value = value }
}
