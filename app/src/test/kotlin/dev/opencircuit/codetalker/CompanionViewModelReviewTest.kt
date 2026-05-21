package dev.opencircuit.codetalker

import dev.opencircuit.codetalker.audio.STTEvent
import dev.opencircuit.codetalker.audio.STTRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-05-21 — covers the dictation REVIEW state machine.
 *
 * The new flow runs:
 *   IDLE → startHoldToTalk → RECORDING
 *   RECORDING → endHoldToTalk → REVIEW
 *   REVIEW → confirmSend → DISPATCHING → IDLE (on success)
 *   REVIEW → discardAndReRecord → RECORDING
 *   REVIEW → cancelDictation → IDLE
 *
 * The recorder is faked so tests can drive STTEvent timing deterministically.
 */
class CompanionViewModelReviewTest {

    /** Fake STTRecorder that emits whatever events the test pushes. */
    private class FakeSTTRecorder : STTRecorder {
        val emitter = MutableSharedFlow<STTEvent>(replay = 0, extraBufferCapacity = 8)
        var started = 0
        var stopped = 0
        var released = 0
        override val events = emitter
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun release() { released++ }
    }

    private fun makeVm(
        recorder: FakeSTTRecorder = FakeSTTRecorder(),
        injectCalls: MutableList<Pair<String, String>> = mutableListOf(),
        directSttCalls: MutableList<Pair<String, String>> = mutableListOf(),
        maxMs: Long = 30_000L,
        injectThrows: Throwable? = null,
    ): CompanionViewModel = CompanionViewModel(
        sttRecorder = recorder,
        inject = { sid, text ->
            if (injectThrows != null) throw injectThrows
            injectCalls.add(sid to text)
        },
        startBuddy = { sid -> "buddy-$sid" },
        directStt = { sid, text -> directSttCalls.add(sid to text) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        sttDispatcher = Dispatchers.Default,
        maxRecordingMs = maxMs,
    )

    @Test
    fun `tap-tap-send happy path lands the inject`() = runBlocking {
        val recorder = FakeSTTRecorder()
        val injected = mutableListOf<Pair<String, String>>()
        val vm = makeVm(recorder, injectCalls = injected)

        assertEquals(SttUiPhase.IDLE, vm.sttPhase.value)

        // Tap 1: start.
        vm.startHoldToTalkAndWait("sid-1", SttMode.BUDDY)
        // VM transitions to RECORDING synchronously.
        assertEquals(SttUiPhase.RECORDING, vm.sttPhase.value)

        // Recognizer emits a final transcript while recording.
        recorder.emitter.tryEmit(STTEvent.Final("write the unit tests"))
        // Give the collector a chance to flip phase.
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW }
        assertEquals("write the unit tests", vm.captionText.value)

        // Tap Send.
        vm.confirmSend()
        waitFor { vm.sttPhase.value == SttUiPhase.IDLE }
        // inject() is called with (buddy_id, text), not (session_id, text)
        // — the VM materializes a buddy via startBuddy(sid) and uses its id
        // as the inject target, so the LLM stream stays attached to that
        // buddy across subsequent injects on the same session.
        assertEquals(listOf("buddy-sid-1" to "write the unit tests"), injected)
        assertEquals("", vm.captionText.value)
    }

    @Test
    fun `re-record from REVIEW returns to RECORDING with a fresh caption`() = runBlocking {
        val recorder = FakeSTTRecorder()
        val vm = makeVm(recorder)

        vm.startHoldToTalkAndWait("sid-2", SttMode.DIRECT_CC)
        recorder.emitter.tryEmit(STTEvent.Final("delete production database"))
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW }

        vm.discardAndReRecord()
        delay(100)  // same collector-subscribe wait as startHoldToTalkAndWait
        assertEquals(SttUiPhase.RECORDING, vm.sttPhase.value)
        assertEquals("", vm.captionText.value)

        // New utterance lands.
        recorder.emitter.tryEmit(STTEvent.Final("delete the test data only"))
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW }
        assertEquals("delete the test data only", vm.captionText.value)
    }

    @Test
    fun `cancel from REVIEW returns to IDLE without dispatching`() = runBlocking {
        val recorder = FakeSTTRecorder()
        val injected = mutableListOf<Pair<String, String>>()
        val vm = makeVm(recorder, injectCalls = injected)

        vm.startHoldToTalkAndWait("sid-3", SttMode.BUDDY)
        recorder.emitter.tryEmit(STTEvent.Final("forget I said anything"))
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW }

        vm.cancelDictation()
        waitFor { vm.sttPhase.value == SttUiPhase.IDLE }
        assertTrue("cancel must not have dispatched", injected.isEmpty())
        assertEquals("", vm.captionText.value)
    }

    @Test
    fun `STT error during RECORDING bounces to IDLE not REVIEW`() = runBlocking {
        val recorder = FakeSTTRecorder()
        val vm = makeVm(recorder)

        vm.startHoldToTalkAndWait("sid-4", SttMode.BUDDY)
        recorder.emitter.tryEmit(STTEvent.Error(code = 7, message = "no match"))
        waitFor { vm.sttPhase.value == SttUiPhase.IDLE }
        // The error caption stays visible so the row can show it.
        assertTrue(vm.captionText.value.startsWith("["))
    }

    @Test
    fun `inject failure puts the user back in REVIEW for retry`() = runBlocking {
        val recorder = FakeSTTRecorder()
        val vm = makeVm(
            recorder = recorder,
            injectThrows = RuntimeException("daemon offline"),
        )

        vm.startHoldToTalkAndWait("sid-5", SttMode.BUDDY)
        recorder.emitter.tryEmit(STTEvent.Final("explain the test"))
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW }

        vm.confirmSend()
        // Should go through DISPATCHING then back to REVIEW with error caption.
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW && vm.captionText.value.startsWith("[") }
        assertTrue(vm.captionText.value.contains("daemon offline"))
    }

    @Test
    fun `30s cap force-finalizes the recording`() = runBlocking {
        val recorder = FakeSTTRecorder()
        // Override the cap to something testable.
        val vm = makeVm(recorder = recorder, maxMs = 150L)

        vm.startHoldToTalkAndWait("sid-6", SttMode.BUDDY)
        assertEquals(SttUiPhase.RECORDING, vm.sttPhase.value)

        // Don't emit any STTEvent; just wait past the cap. The VM should
        // call sttRecorder.stop() and force-transition to REVIEW or IDLE
        // depending on whether any partial landed.
        // We feed a partial so REVIEW can land with usable text.
        recorder.emitter.tryEmit(STTEvent.Partial("partial words"))
        runBlocking { delay(1200) }
        // After the cap fires + 800ms grace, phase should not still be
        // RECORDING — either REVIEW (if the partial was treated as final)
        // or IDLE (if no final landed).
        assertFalse(
            "30s cap must terminate the RECORDING phase",
            vm.sttPhase.value == SttUiPhase.RECORDING,
        )
    }

    @Test
    fun `start-while-not-idle cancels the prior recording first`() = runBlocking {
        val recorder = FakeSTTRecorder()
        val vm = makeVm(recorder)

        vm.startHoldToTalkAndWait("sid-A", SttMode.BUDDY)
        recorder.emitter.tryEmit(STTEvent.Final("first thought"))
        waitFor { vm.sttPhase.value == SttUiPhase.REVIEW }
        assertEquals("first thought", vm.captionText.value)

        // User taps a different session's button mid-review.
        vm.startHoldToTalkAndWait("sid-B", SttMode.DIRECT_CC)
        assertEquals(SttUiPhase.RECORDING, vm.sttPhase.value)
        // Caption must reset — we cancelled the prior REVIEW.
        assertEquals("", vm.captionText.value)
        assertEquals(SttMode.DIRECT_CC, vm.sttMode.value)
        assertEquals("sid-B", vm.activeSessionId.value)
    }

    // --- helpers ---------------------------------------------------------

    /**
     * 2026-05-21 — startHoldToTalk wrapped with a small delay so the
     * collector coroutine has time to subscribe to the recorder's
     * MutableSharedFlow before the test emits events. Without this, the
     * emit fires before the subscription is active and the event is
     * dropped (replay=0 semantics).
     */
    private suspend fun CompanionViewModel.startHoldToTalkAndWait(
        sessionId: String,
        mode: SttMode,
        subscribeWaitMs: Long = 100,
    ) {
        startHoldToTalk(sessionId, mode)
        delay(subscribeWaitMs)
    }

    private fun waitFor(timeoutMs: Long = 2000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            runBlocking { delay(20) }
        }
        throw AssertionError("predicate not satisfied within ${timeoutMs}ms")
    }
}
