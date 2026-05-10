package dev.opencircuit.codetalker

import dev.opencircuit.codetalker.input.ButtonState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import dev.opencircuit.codetalker.audio.STTEvent
import dev.opencircuit.codetalker.audio.STTRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task A.8 — CompanionViewModel coordinator unit tests.
 *
 * The view model orchestrates ButtonState transitions into STT start /
 * dispatch and into the daemon's /api/companion/inject endpoint. We
 * verify the orchestration with a fake STT and a fake injector lambda;
 * the daemon HTTP layer is covered by DaemonClientTest.
 */
class CompanionViewModelTest {

    private class FakeSTTRecorder : STTRecorder {
        val starts = mutableListOf<Long>()
        val stops = mutableListOf<Long>()
        // replay = 4 so events emitted before the collector subscribes
        // are still seen — keeps tests deterministic without exhaustive
        // dispatch-orchestration.
        private val _events = MutableSharedFlow<STTEvent>(replay = 4, extraBufferCapacity = 16)
        override val events: SharedFlow<STTEvent> = _events
        override fun start() { starts.add(System.nanoTime()) }
        override fun stop() { stops.add(System.nanoTime()) }
        override fun release() {}
        fun emit(e: STTEvent) { _events.tryEmit(e) }
    }

    @Test
    fun `Listening state starts STT recorder`() {
        val stt = FakeSTTRecorder()
        var injected: Pair<String, String>? = null
        val vm = CompanionViewModel(
            sttRecorder = stt,
            inject = { sid, text -> injected = sid to text },
            startBuddy = { "buddy-1" },
            sttDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        vm.activeSessionId.value = "sid-A"
        vm.handleButtonState(ButtonState.Listening)
        // start() is dispatched onto sttDispatcher; give it a moment.
        Thread.sleep(50)
        assertEquals(1, stt.starts.size)
        assertNull(injected)
    }

    @Test
    fun `DispatchListening with no active session is a no-op`() {
        val stt = FakeSTTRecorder()
        var injected: Pair<String, String>? = null
        val vm = CompanionViewModel(
            sttRecorder = stt,
            inject = { sid, text -> injected = sid to text },
            startBuddy = { "buddy-1" },
            sttDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        vm.handleButtonState(ButtonState.DispatchListening)
        assertNull(injected)
        assertEquals(0, stt.starts.size)
    }

    @Test
    fun `DispatchListening stops STT and injects final transcript`() = runBlocking {
        val stt = FakeSTTRecorder()
        val injectCalls = mutableListOf<Pair<String, String>>()
        var startBuddyCalls = 0
        val vm = CompanionViewModel(
            sttRecorder = stt,
            inject = { sid, text -> injectCalls.add(sid to text) },
            startBuddy = {
                startBuddyCalls++
                "buddy-${startBuddyCalls}"
            },
            sttDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        vm.activeSessionId.value = "sid-X"
        vm.handleButtonState(ButtonState.Listening)
        // simulate the user speaking
        stt.emit(STTEvent.Final("hello there"))
        // wait the dispatch + inject path to drain
        Thread.sleep(150)
        vm.handleButtonState(ButtonState.DispatchListening)
        Thread.sleep(200)
        assertEquals(1, stt.stops.size)
        assertTrue("buddy was started", startBuddyCalls > 0)
        assertEquals(1, injectCalls.size)
        assertEquals("buddy-1", injectCalls[0].first)
        assertEquals("hello there", injectCalls[0].second)
    }

    @Test
    fun `Partial events update captionText, Final overwrites`() = runBlocking {
        val stt = FakeSTTRecorder()
        val vm = CompanionViewModel(
            sttRecorder = stt,
            inject = { _, _ -> },
            startBuddy = { "b" },
            sttDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        vm.activeSessionId.value = "sid"
        vm.handleButtonState(ButtonState.Listening)
        stt.emit(STTEvent.Partial("how"))
        Thread.sleep(20)
        assertEquals("how", vm.captionText.value)
        stt.emit(STTEvent.Partial("how do"))
        Thread.sleep(20)
        assertEquals("how do", vm.captionText.value)
        stt.emit(STTEvent.Final("how do you do"))
        Thread.sleep(20)
        assertEquals("how do you do", vm.captionText.value)
    }

    @Test
    fun `buddy is reused for second dispatch on same session`() = runBlocking {
        val stt = FakeSTTRecorder()
        var startBuddyCalls = 0
        val vm = CompanionViewModel(
            sttRecorder = stt,
            inject = { _, _ -> },
            startBuddy = {
                startBuddyCalls++
                "b-$startBuddyCalls"
            },
            sttDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
        vm.activeSessionId.value = "sid"
        vm.handleButtonState(ButtonState.Listening)
        stt.emit(STTEvent.Final("first"))
        Thread.sleep(20)
        vm.handleButtonState(ButtonState.DispatchListening)
        Thread.sleep(20)
        vm.handleButtonState(ButtonState.Listening)
        stt.emit(STTEvent.Final("second"))
        Thread.sleep(20)
        vm.handleButtonState(ButtonState.DispatchListening)
        Thread.sleep(20)
        assertEquals("buddy started exactly once", 1, startBuddyCalls)
    }
}
