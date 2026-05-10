package dev.opencircuit.codetalker.telemetry

import dev.opencircuit.codetalker.prefs.InMemoryAppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task G.2 — locks down the consent flow's state transitions.
 *
 * Three invariants:
 *   1. The dialog is NOT shown until onboarding completes.
 *   2. Once consent has been asked (regardless of the answer), the
 *      dialog is never shown again.
 *   3. recordConsent / recordDecline both flip
 *      crashReportingConsentAsked = true; only recordConsent(true)
 *      flips crashReportingEnabled = true.
 */
class ConsentFlowTest {

    @Test
    fun `dialog not shown before onboarding completes`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        val flow = ConsentFlow(prefs)
        assertFalse(flow.shouldShowConsent.first())
    }

    @Test
    fun `dialog shown once onboarding completes and consent not yet asked`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        val flow = ConsentFlow(prefs)
        prefs.setOnboardingComplete(true)
        assertTrue(flow.shouldShowConsent.first())
    }

    @Test
    fun `dialog hidden after consent is asked even if declined`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        val flow = ConsentFlow(prefs)
        prefs.setOnboardingComplete(true)
        flow.recordDecline()
        assertFalse(flow.shouldShowConsent.first())
        assertFalse(prefs.crashReportingEnabled.first())
        assertTrue(prefs.crashReportingConsentAsked.first())
    }

    @Test
    fun `recordConsent enables crash reporting and marks asked`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        val flow = ConsentFlow(prefs)
        prefs.setOnboardingComplete(true)
        flow.recordConsent(enabled = true)
        assertTrue(prefs.crashReportingEnabled.first())
        assertTrue(prefs.crashReportingConsentAsked.first())
        assertFalse(flow.shouldShowConsent.first())
    }

    @Test
    fun `recordConsent false leaves crash reporting off but marks asked`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        val flow = ConsentFlow(prefs)
        prefs.setOnboardingComplete(true)
        flow.recordConsent(enabled = false)
        assertFalse(prefs.crashReportingEnabled.first())
        assertTrue(prefs.crashReportingConsentAsked.first())
        assertFalse(flow.shouldShowConsent.first())
    }

    @Test
    fun `setting can be flipped after the consent dialog`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        val flow = ConsentFlow(prefs)
        prefs.setOnboardingComplete(true)
        flow.recordDecline()
        // User changes mind in Preferences.
        prefs.setCrashReportingEnabled(true)
        assertTrue(prefs.crashReportingEnabled.first())
        assertEquals(true, prefs.crashReportingConsentAsked.first())
        // Dialog still doesn't reappear.
        assertFalse(flow.shouldShowConsent.first())
    }
}
