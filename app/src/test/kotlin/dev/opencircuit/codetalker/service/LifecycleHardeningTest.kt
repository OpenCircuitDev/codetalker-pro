package dev.opencircuit.codetalker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task B.5 — verifies the screen / network state flows.
 *
 * The receivers themselves require a Context, but the flow-level
 * behaviour (default values + setOnline transitions + reconnectTick
 * increments) is pure and unit-testable.
 */
class LifecycleHardeningTest {

    @Test
    fun `screenOn defaults to true`() {
        val obs = ScreenStateObserver()
        assertTrue(obs.screenOn.value)
    }

    @Test
    fun `setScreenOn flips the flow`() {
        val obs = ScreenStateObserver()
        obs.setScreenOn(false)
        assertFalse(obs.screenOn.value)
        obs.setScreenOn(true)
        assertTrue(obs.screenOn.value)
    }

    @Test
    fun `network online defaults to true`() {
        val obs = NetworkStateObserver(cm = mockCM())
        assertTrue(obs.online.value)
    }

    @Test
    fun `setOnline drops then restores connection and bumps reconnectTick`() {
        val obs = NetworkStateObserver(cm = mockCM())
        val before = obs.reconnectTick.value
        obs.setOnline(false)
        assertFalse(obs.online.value)
        assertEquals("no tick on drop", before, obs.reconnectTick.value)
        obs.setOnline(true)
        assertTrue(obs.online.value)
        assertEquals("tick on restore", before + 1, obs.reconnectTick.value)
    }

    @Test
    fun `setOnline is idempotent — duplicate true does not bump tick`() {
        val obs = NetworkStateObserver(cm = mockCM())
        val initial = obs.reconnectTick.value
        obs.setOnline(true)
        obs.setOnline(true)
        assertEquals(initial, obs.reconnectTick.value)
    }

    /**
     * NetworkStateObserver only calls register/unregister on the CM,
     * neither of which we exercise in unit tests. mockk provides a
     * relaxed instance whose register/unregister are no-ops.
     */
    private fun mockCM(): android.net.ConnectivityManager =
        io.mockk.mockk(relaxed = true)
}
