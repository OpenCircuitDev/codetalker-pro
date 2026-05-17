package dev.opencircuit.codetalker.input

/**
 * CCT-32 v0.1.0 polish — per-screen hardware-button handler.
 *
 * Each top-level screen (SessionList, SessionDetail) registers a handler
 * when it becomes visible and clears it on dispose. MainActivity's
 * HardwareKeys callback dispatches Click / LongPress / RockerUp / RockerDown
 * to the currently-registered handler. Screens that don't care can omit
 * registration (events fall through to the legacy ButtonRouter for the
 * existing in-glasses voice flow).
 *
 * Button → ButtonInput mapping (2026-05-12 binding move):
 *
 * | Hardware              | Short press         | Long press (>300ms)     |
 * |-----------------------|---------------------|-------------------------|
 * | Side button           | [ButtonInput.Click] | — (unbound; system kept)|
 * | Volume DOWN rocker    | [ButtonInput.RockerDown] | [ButtonInput.LongPress] + [ButtonInput.HoldEnd] on release |
 * | Volume UP rocker      | [ButtonInput.RockerUp]   | (no long-press)         |
 *
 * Per-screen semantics:
 *
 * | Screen          | Click               | Vol-DOWN hold       | Vol-UP hold              | Vol-up tap     | Vol-down tap   |
 * |-----------------|---------------------|---------------------|--------------------------|----------------|----------------|
 * | SessionList     | Select highlighted  | —                   | —                        | Highlight ↑    | Highlight ↓    |
 * | SessionDetail   | Toggle mute         | Buddy STT           | Direct-STT into CC       | Mode → live    | Mode → brief   |
 *
 * The two STT routes are distinct products:
 *   - Buddy STT (vol-down hold) → /api/companion/inject → intermediate LLM
 *     agent → response narrated back. Lightweight Q&A.
 *   - Direct-STT (vol-up hold) → /api/companion/direct-stt → daemon types
 *     the transcript into the OS-foreground window (presumed CC session)
 *     via SendKeys, then Enter. Wireless dictation mic for the active
 *     Claude Code session; reply auto-narrates via existing hook pipeline.
 *
 * Rationale for moving STT from side-button long-press to vol-down long-press:
 * the side button has system defaults (power menu on POWER-mapped firmware)
 * that are valuable to preserve. Vol-down's only conflict is the Android
 * Assistant launch gesture, which (a) only fires when the foreground app
 * doesn't consume the event, and (b) can be disabled in OS Settings.
 *
 * Implementations default each method to no-op so callers only override what
 * they handle.
 */
interface CompanionButtonHandler {
    fun onClick() {}
    /** Volume-down held past 300ms threshold — pair this with [onHoldEnd]
     *  for "hold to talk" semantics. Routes to Buddy STT on SessionDetail. */
    fun onLongPress() {}
    /** Volume-down released after [onLongPress] fired — release-half of the
     *  Buddy hold-to-talk gesture. Not called when the press was a short tap. */
    fun onHoldEnd() {}
    /** 2026-05-12 — Volume-UP held past 300ms threshold. Routes to direct-STT
     *  (transcript → daemon SendKeys → active CC session) on SessionDetail.
     *  Pair with [onRockerUpHoldEnd] for release-on-stop semantics. */
    fun onRockerUpLongPress() {}
    /** Volume-up released after [onRockerUpLongPress] fired — release-half
     *  of the direct-STT hold-to-talk gesture. Not called for short taps. */
    fun onRockerUpHoldEnd() {}
    fun onRockerUp() {}
    fun onRockerDown() {}
}
