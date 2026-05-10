package dev.opencircuit.codetalker.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * CCT-32 Phase B — in-memory AppPreferences fake for unit tests.
 * Behavior should match DataStoreAppPreferences: defaults are false /
 * null and writes propagate to the corresponding flow.
 */
class InMemoryAppPreferences : AppPreferences {
    private val _onboarding = MutableStateFlow(false)
    private val _boot = MutableStateFlow(false)
    private val _active = MutableStateFlow<String?>(null)

    override val onboardingComplete: Flow<Boolean> = _onboarding
    override val startOnBoot: Flow<Boolean> = _boot
    override val activeSessionId: Flow<String?> = _active

    override suspend fun setOnboardingComplete(value: Boolean) { _onboarding.value = value }
    override suspend fun setStartOnBoot(value: Boolean) { _boot.value = value }
    override suspend fun setActiveSessionId(value: String?) { _active.value = value }
}
