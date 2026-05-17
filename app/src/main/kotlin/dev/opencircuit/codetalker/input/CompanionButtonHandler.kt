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
 * Button → ButtonInput mapping (2026-05-17 binding move):
 *
 * | Hardware              | Short press         | Long press (>300ms)     |
 * |-----------------------|---------------------|-------------------------|
 * | Side button           | [ButtonInput.Click] | — (unbound; system kept)|
 * | Volume UP / DOWN      | (system default — volume) | (system default)  |
 *
 * 2026-05-17 — STT moved OFF the volume rocker and ONTO per-session-card
 * buttons (Buddy + Dictate hold-to-talk). The volume rocker is now released
 * back to system handling so the user can adjust media / notification /
 * call volume normally while the app is foregrounded. The LongPress /
 * LongPressUp / RockerUp / RockerDown ButtonInput variants are kept in the
 * sealed type for legacy in-glasses flows but no longer fire from hardware.
 *
 * Per-screen semantics:
 *
 * | Screen          | Side-button click   | Per-card Buddy hold | Per-card Dictate hold |
 * |-----------------|---------------------|---------------------|------------------------|
 * | SessionList     | Select highlighted  | Buddy STT (that sid)| Direct-STT (that sid)  |
 * | SessionDetail   | Toggle mute         | (same)              | (same)                 |
 *
 * The two STT routes are distinct products:
 *   - Buddy STT (per-card Buddy hold) → /api/companion/inject → intermediate
 *     LLM agent → response narrated back. Lightweight Q&A about the session.
 *   - Direct-STT (per-card Dictate hold) → /api/companion/direct-stt → daemon
 *     types the transcript into the OS-foreground window (presumed CC
 *     session) via SendKeys, then Enter. Wireless dictation mic for the
 *     target Claude Code session; reply auto-narrates via existing hook
 *     pipeline.
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
