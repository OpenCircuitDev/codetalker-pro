package dev.opencircuit.codetalker.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Phase B — AppPreferences contract tests against the in-memory
 * fake. The same contract must hold for DataStoreAppPreferences (covered
 * by the live E2Es).
 */
class AppPreferencesTest {

    @Test
    fun `defaults are false and null`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        assertFalse(prefs.onboardingComplete.first())
        assertFalse(prefs.startOnBoot.first())
        assertNull(prefs.activeSessionId.first())
    }

    @Test
    fun `setOnboardingComplete persists across reads`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        prefs.setOnboardingComplete(true)
        assertTrue(prefs.onboardingComplete.first())
        prefs.setOnboardingComplete(false)
        assertFalse(prefs.onboardingComplete.first())
    }

    @Test
    fun `setStartOnBoot persists`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        prefs.setStartOnBoot(true)
        assertTrue(prefs.startOnBoot.first())
    }

    @Test
    fun `setActiveSessionId stores and clears via null`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        prefs.setActiveSessionId("sid-123")
        assertEquals("sid-123", prefs.activeSessionId.first())
        prefs.setActiveSessionId(null)
        assertNull(prefs.activeSessionId.first())
    }

    @Test
    fun `flows reflect current value at subscription time`() = runBlocking {
        val prefs = InMemoryAppPreferences()
        prefs.setOnboardingComplete(true)
        prefs.setActiveSessionId("sid-Z")
        assertTrue(prefs.onboardingComplete.first())
        assertEquals("sid-Z", prefs.activeSessionId.first())
    }
}
