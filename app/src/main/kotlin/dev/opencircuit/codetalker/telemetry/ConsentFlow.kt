package dev.opencircuit.codetalker.telemetry

import dev.opencircuit.codetalker.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * CCT-32 Task G.2 — first-launch crash-reporting consent flow.
 *
 * The flow's job is to decide WHETHER to surface the consent dialog at
 * launch time:
 *
 *   - The user finished onboarding (so we're past first-launch noise).
 *   - The user has not yet been asked about crash reports.
 *
 * Once those two conditions are met, the dialog should be shown. The
 * caller persists the user's choice via [recordConsent] (or
 * [recordDecline]); both paths flip `crashReportingConsentAsked` to
 * true so the dialog never appears again.
 */
class ConsentFlow(private val prefs: AppPreferences) {

    /**
     * Emits true when the consent dialog should be shown — i.e. the user
     * has completed onboarding and consent has not yet been asked.
     */
    val shouldShowConsent: Flow<Boolean> =
        combine(prefs.onboardingComplete, prefs.crashReportingConsentAsked) { onboarded, asked ->
            onboarded && !asked
        }

    /** User accepted: record the toggle and mark consent as asked. */
    suspend fun recordConsent(enabled: Boolean) {
        prefs.setCrashReportingEnabled(enabled)
        prefs.setCrashReportingConsentAsked(true)
    }

    /**
     * User declined the dialog. Equivalent to [recordConsent] with
     * `enabled = false`; kept as a separate method to make the intent
     * explicit at call sites.
     */
    suspend fun recordDecline() {
        prefs.setCrashReportingEnabled(false)
        prefs.setCrashReportingConsentAsked(true)
    }
}
