package dev.opencircuit.codetalker.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonRouterTest {

    @Test
    fun `starts idle`() {
        val r = ButtonRouter()
        assertEquals(ButtonState.Idle, r.state.value)
    }

    @Test
    fun `single click in idle moves to Listening`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)
        assertEquals(ButtonState.Listening, r.state.value)
    }

    @Test
    fun `double click in idle goes to Menu`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)   // → Listening (first click)
        r.handle(ButtonInput.Click, now = 1100L)   // within 350ms → Menu (double-click upgrade)
        val s = r.state.value
        assertTrue("expected Menu, got $s", s is ButtonState.Menu)
    }

    @Test
    fun `click outside double-click window does not double-trigger`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)   // → Listening
        r.handle(ButtonInput.Click, now = 9000L)   // 8s later → DispatchListening (not double)
        assertEquals(ButtonState.DispatchListening, r.state.value)
    }

    @Test
    fun `click while listening dispatches`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)
        r.handle(ButtonInput.Click, now = 5000L)  // outside double window
        assertEquals(ButtonState.DispatchListening, r.state.value)
    }

    @Test
    fun `silence while listening dispatches`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)
        r.handle(ButtonInput.Silence)
        assertEquals(ButtonState.DispatchListening, r.state.value)
    }

    @Test
    fun `long press from any state returns to idle`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)        // Listening
        r.handle(ButtonInput.LongPress)
        assertEquals(ButtonState.Idle, r.state.value)
    }

    @Test
    fun `rocker down increments menu selectedIndex`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)
        r.handle(ButtonInput.Click, now = 1100L)        // Menu(0)
        r.handle(ButtonInput.RockerDown)
        r.handle(ButtonInput.RockerDown)
        val s = r.state.value as ButtonState.Menu
        assertEquals(2, s.selectedIndex)
    }

    @Test
    fun `rocker up never goes below zero`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)
        r.handle(ButtonInput.Click, now = 1100L)        // Menu(0)
        r.handle(ButtonInput.RockerUp)
        r.handle(ButtonInput.RockerUp)
        val s = r.state.value as ButtonState.Menu
        assertEquals(0, s.selectedIndex)
    }

    @Test
    fun `rocker outside menu does nothing`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.RockerDown)
        assertEquals(ButtonState.Idle, r.state.value)
    }

    @Test
    fun `click in menu confirms selection`() {
        val r = ButtonRouter()
        r.handle(ButtonInput.Click, now = 1000L)
        r.handle(ButtonInput.Click, now = 1100L)        // Menu(0)
        r.handle(ButtonInput.RockerDown)                 // Menu(1)
        r.handle(ButtonInput.Click, now = 5000L)         // outside double window
        val s = r.state.value as ButtonState.MenuSelect
        assertEquals(1, s.index)
    }
}
