package dev.opencircuit.codetalker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import dev.opencircuit.codetalker.SttMode
import dev.opencircuit.codetalker.SttUiPhase
import dev.opencircuit.codetalker.input.CompanionButtonHandler
import dev.opencircuit.codetalker.net.DaemonClient
import kotlinx.coroutines.flow.StateFlow
import dev.opencircuit.codetalker.net.SessionLite
import dev.opencircuit.codetalker.prefs.AppPreferences
import dev.opencircuit.codetalker.ui.character.CharacterChip
import dev.opencircuit.codetalker.ui.errors.AppError
import dev.opencircuit.codetalker.ui.errors.AppErrors
import dev.opencircuit.codetalker.ui.errors.ErrorBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CCT-31 Phase 5c + CCT-32 v0.1.0 polish — paired-state landing screen.
 *
 * v0.1.0 polish brings Pro Sessions list to dashboard parity: filter pills
 * (Live / All / Dormant / Active), sticky project_slug group headers with
 * collapse, speaking pulse for recently-active rows, inline mute + brief/live
 * mode pick, and a Make-active chip that sets the companion-active session
 * without entering SessionDetail. Detail is preserved for the full picker
 * set (voice, cadence, markup, character).
 *
 * Voice cloning is implicit: when a desktop-bound character carries a cloned
 * voice_ref, that character's CharacterChip surfaces here, and once the row
 * is Active the audio stream the AR companion plays IS that cloned voice.
 *
 * Phase 8 swaps this Compose screen for the AR HUD; this surface stays as
 * the before-glasses config layer.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    daemonClient: DaemonClient,
    activeSessionId: String?,
    /** 2026-05-11 — full multi-active set. Treat any membership as "active"
     *  for the filter + chip; the legacy [activeSessionId] is now derived. */
    activeSessionIds: Set<String> = activeSessionId?.let { setOf(it) } ?: emptySet(),
    appPreferences: AppPreferences,
    onSelect: (SessionLite) -> Unit,
    onSetActive: (SessionLite) -> Unit,
    onUnpair: () -> Unit,
    onLongPressMenu: () -> Unit = {},
    /** v1.0 — open the CodeTalkerChat multi-session gallery. Null = hide
     *  the entry chip (graceful in older callers). */
    onOpenCTC: (() -> Unit)? = null,
    /** 2026-05-11 Tier-B — invoked once per session-list entry with the set
     *  of SIDs that the daemon flagged as `audio_misaligned` AND have
     *  'phone' in `audio_outputs`. Caller bulk-adds them to the
     *  active-session set so the phone auto-subscribes to their
     *  audio-stream. Collapses the dual-state UX trap (audio_outputs +
     *  active_session_ids) into one. Fires at most once per screen visit
     *  to respect user-driven de-activations. */
    onAutoSubscribeMissing: (Set<String>) -> Unit = {},
    setScreenButtonHandler: (CompanionButtonHandler?) -> Unit = {},
    /** 2026-05-17 — per-card voice input. First call starts the recorder
     *  pinned to the given session_id + mode; second call (via [onHoldEnd])
     *  stops + dispatches the transcript.
     *
     *  2026-05-18 — UX semantics changed: click-toggle, not press-and-hold.
     *  The button names stayed the same (onHoldStart/onHoldEnd) for
     *  back-compat with the wiring in MainActivity. Behavior: tap the
     *  button to start recording; tap again to stop + send. Only one
     *  button can be recording at a time; tapping a different button
     *  while one is recording stops the current and starts the new one.
     *
     *  Null defaults keep older callers + previews working. */
    onHoldStart: ((sessionId: String, mode: SttMode) -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
    /** 2026-05-17 — live STT caption flow. Partial transcript streams
     *  while a recording is active; the active button shows it in real
     *  time so the user has feedback that the recognizer is hearing them.
     *  After release/stop, holds the final transcript or error briefly. */
    captionFlow: StateFlow<String>? = null,
    /** 2026-05-21 — dictation phase. When REVIEW, the row owning the
     *  active recording renders the Send / Re-record / Cancel button row
     *  instead of the regular VoiceToggleButton. Null = legacy behavior
     *  (dispatch immediately on stop, no review). */
    sttPhaseFlow: StateFlow<SttUiPhase>? = null,
    /** 2026-05-21 — elapsed milliseconds inside the current RECORDING
     *  phase. Used to render a countdown like "12s / 30s". Null = no
     *  countdown rendered. */
    recordingElapsedFlow: StateFlow<Long>? = null,
    /** 2026-05-21 — user pressed Send in the review row. */
    onConfirmSend: (() -> Unit)? = null,
    /** 2026-05-21 — user pressed Re-record in the review row. */
    onDiscardAndReRecord: (() -> Unit)? = null,
    /** 2026-05-21 — user pressed Cancel in the review row. */
    onCancelDictation: (() -> Unit)? = null,
) {
    val liveCaption by (captionFlow?.collectAsState(initial = "") ?: remember { mutableStateOf("") })
    val sttPhase by (sttPhaseFlow?.collectAsState(initial = SttUiPhase.IDLE)
        ?: remember { mutableStateOf(SttUiPhase.IDLE) })
    val recordingElapsedMs by (recordingElapsedFlow?.collectAsState(initial = 0L)
        ?: remember { mutableStateOf(0L) })
    // 2026-05-18 — click-toggle recording state. Tracks WHICH (sessionId,
    // mode) pair is currently recording so:
    //   (a) the right card button shows the "recording" visual,
    //   (b) clicking a different button cleanly stops the current
    //       recording before starting the new one (only one recording
    //       can run at a time on the recognizer anyway),
    //   (c) clicking the SAME button stops + dispatches the transcript.
    // Held at screen level (not inside each button) so only one button
    // can show "recording" at any moment.
    var activeRecording by remember { mutableStateOf<Pair<String, SttMode>?>(null) }
    var sessions by remember { mutableStateOf<List<SessionLite>>(emptyList()) }
    // 2026-05-11 Tier-B — auto-subscribe state. Tracks SIDs we've already
    // considered for auto-subscription this screen visit so we don't
    // re-add sessions the user has explicitly de-activated.
    val autoSubEvaluatedSids = remember { mutableSetOf<String>() }
    var loadError by remember { mutableStateOf<AppError?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var highlightedSessionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val filter by appPreferences.sessionFilter.collectAsState(initial = "live")
    val collapsedGroups by appPreferences.sessionCollapsedGroups.collectAsState(initial = emptySet())

    // 2026-05-21 — clear the screen-level activeRecording tracker when the
    // VM returns to IDLE (after Send succeeds, after Cancel, or after a
    // recording errored out). Without this, the row stays visually
    // "owned" by the previous recording even though the VM has cleared
    // its state — and the user can't start a new recording on a different
    // session because the toggle logic still treats activeRecording as set.
    LaunchedEffect(sttPhase) {
        if (sttPhase == SttUiPhase.IDLE) {
            activeRecording = null
        }
    }

    // Reload on demand (after a mutating action) and on first composition.
    LaunchedEffect(reloadKey) {
        try {
            val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
            sessions = list
            loadError = null
            maybeAutoSubscribe(list, activeSessionIds, autoSubEvaluatedSids, onAutoSubscribeMissing)
        } catch (e: Throwable) {
            loadError = AppErrors.fromThrowable(e)
        }
    }

    // Phase 4 (2026-05-16) — push-based refresh via the daemon's SSE
    // /api/events channel. Subscribes to SessionChanged + LifecycleChanged
    // and triggers an immediate reload when either arrives. Replaces the
    // need for a tight polling loop for hot deltas; cross-device sync
    // latency drops from ~3s to ~50ms.
    val daemonEvents = dev.opencircuit.codetalker.net.LocalDaemonEvents.current
    LaunchedEffect(daemonEvents) {
        if (daemonEvents != null) {
            daemonEvents.events.collect { ev ->
                if (ev.eventType == "SessionChanged" || ev.eventType == "LifecycleChanged") {
                    try {
                        val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
                        sessions = list
                        maybeAutoSubscribe(list, activeSessionIds, autoSubEvaluatedSids, onAutoSubscribeMissing)
                    } catch (_: Throwable) {
                        // keep last successful snapshot
                    }
                }
            }
        }
    }

    // Safety-net polling — fires every 30s in case SSE drops silently
    // (NAT timeout, network blip). When SSE is healthy this is wasted
    // work for delta detection but it also catches transient drops in
    // the speaking pulse / dormant transition. Phase 6 final cleanup
    // will remove this once SSE has soaked.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            try {
                val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
                sessions = list
                maybeAutoSubscribe(list, activeSessionIds, autoSubEvaluatedSids, onAutoSubscribeMissing)
            } catch (_: Throwable) {
                // Keep last successful snapshot; transient blips are not user-facing.
            }
        }
    }

    // 2026-05-16 — restructured "Active" filter to match the user's
    // mental model: a session is Active iff the user has touched it OR
    // Claude Code has been writing to its transcript within the last
    // 30 minutes. The previous filter required BOTH `isLive` (5-minute
    // transcript window) AND a narrow 30-second interaction window,
    // which hid sessions the user clearly perceived as active.
    //
    // New semantics:
    //   isActive = isSpeaking                                  // TTS now
    //              OR isCompanionActive                         // phone marked
    //              OR sessionId in activeSessionIds             // local choice
    //              OR isLive                                    // 5-min transcript / hook
    //              OR last_modified within 30 minutes           // recent transcript activity
    //              OR last_user_interaction_at within 30 min    // recent prompt
    //
    // The 30-minute window matches the user's "session I'm working in"
    // intuition without including sessions touched yesterday. The
    // transcript-mtime branch is the new bit -- it catches sessions
    // where Claude was working but the live badge expired (e.g., the
    // user asked a question 25 min ago and CC has been writing tool
    // output in the background since, but the last batched flush was
    // 8 min ago so is_live is false).
    val now = System.currentTimeMillis() / 1000.0
    // 2026-05-16 -- Active filter is now PURELY about recent activity.
    // The previous `sessionId in activeSessionIds` branch caused stale
    // audio-subscription entries (from DataStore) to remain visible as
    // "Active" even after the user hadn't touched the session in 30+
    // days. The user's mental model: Active = sessions I'm working in
    // OR sessions doing something right now. The audio-subscription
    // set (activeSessionIds) is a separate concept and should not
    // pollute this filter.
    //
    // 60-min window matches "this work session" without bleeding into
    // yesterday. Outside the window -> use the Live filter (5-min) or
    // Muted filter.
    val RECENT_ACTIVITY_WINDOW_SEC = 60 * 60.0
    fun isActiveNow(s: dev.opencircuit.codetalker.net.SessionLite): Boolean {
        if (s.isSpeaking) return true            // TTS in flight
        if (s.isCompanionActive) return true      // companion has it active
        if (s.isLive) return true                 // 5-min transcript/hook window
        val tsMod = s.lastModified
        if (tsMod > 0.0 && (now - tsMod) in -2.0..RECENT_ACTIVITY_WINDOW_SEC) return true
        val last = s.lastUserInteractionAt
        if (last <= 0.0) return false
        val age = now - last
        return age >= -2 && age < RECENT_ACTIVITY_WINDOW_SEC
    }

    // 2026-05-16 -- filter model rebuilt to match the user's mental
    // model. Every filter is a SUBSET of Active (recently-touched
    // sessions); the secondary pills narrow to a specific speaking
    // mode or to muted sessions. So a session must be recently active
    // AND in the chosen speaking mode (or muted) to appear.
    //
    //   active (default) -- recently active (any mode/mute state)
    //   live             -- recently active AND active_mode == "live"
    //   brief            -- recently active AND active_mode == "brief"
    //   teacher          -- recently active AND active_mode == "teacher" (2026-05-17)
    //   muted            -- recently active AND enabled == false
    //
    // 2026-05-17 — added "teacher" filter. ModePicker has long offered
    // Teacher as a peer of Live/Brief/Trigger, but the Sessions list had
    // no pill for it. Result: setting a session to Teacher mode dropped
    // it out of every visible filter (Live and Brief both exclude it,
    // Active sweeps too broad). User reported "no sessions in list"
    // after switching to Teacher — this closes that trap.
    val filtered = remember(sessions, filter, activeSessionIds, now) {
        sessions.filter { s ->
            if (!isActiveNow(s)) return@filter false
            when (filter) {
                "active" -> true
                "live" -> (s.activeMode ?: "").equals("live", ignoreCase = true)
                "brief" -> (s.activeMode ?: "").equals("brief", ignoreCase = true)
                "teacher" -> (s.activeMode ?: "").equals("teacher", ignoreCase = true)
                "muted" -> !s.enabled
                else -> true
            }
        }
    }

    // v0.1.0 polish — mirror Claude Code's per-project organization.
    // Priority order for the workspace grouping key:
    //  1. projectDir (the Claude Code project dir name, ALWAYS distinct
    //     per workspace and persisted in the catalog even for dormant
    //     sessions) → humanize by extracting the trailing segments
    //  2. cwd leaf (live sessions only)
    //  3. projectSlug (codetalker's older derived name — collapses)
    fun humanizeProjectDir(dir: String): String {
        // Claude Code project dir form: "C--Users-brand-Dropbox-...-Workspace"
        // Take the trailing 2–3 segments to produce a readable workspace label.
        // Examples:
        //   "C--Users-brand-Documents-Unreal-Projects-BlueprintForge-Workbench"
        //     -> "BlueprintForge / Workbench"
        //   "c--Users-brand-Dropbox-OCR-Open-Circuit-Unreal-Games-OpenCircuitRacing"
        //     -> "OpenCircuitRacing"
        //   "C--Users-brand-Dropbox-OCR-Open-Circuit-codetalker"
        //     -> "codetalker"
        val parts = dir.split('-').filter { it.isNotBlank() && it != "C" && it != "c" }
        // Heuristic: keep the last 1–2 segments unless the last looks like a
        // generic word ("Workbench", "Workspace", "Web", "Clean") in which
        // case include the prior segment for context.
        val genericTails = setOf(
            "Workbench", "Workspace", "Web", "Clean", "Forge", "Dev",
            "App", "Server", "Client", "Repo", "src", "project",
        )
        if (parts.size >= 2 && parts.last() in genericTails) {
            return parts.takeLast(2).joinToString(" / ")
        }
        return parts.lastOrNull().orEmpty().ifBlank { dir }
    }

    fun workspaceLabel(s: SessionLite): String {
        // v0.1.0 polish — user-defined workspace_group takes top priority,
        // matches the user's mental model (OCRacing / Clients / OCDev /
        // BlueprintForge / Ungrouped). Falls back to auto-derived label
        // until the user assigns.
        val wg = s.workspaceGroup
        if (!wg.isNullOrBlank()) return wg
        val pd = s.projectDir
        if (!pd.isNullOrBlank()) return humanizeProjectDir(pd)
        val c = s.cwd
        if (!c.isNullOrBlank()) {
            val sep = c.lastIndexOfAny(charArrayOf('/', '\\'))
            val leaf = if (sep >= 0 && sep < c.length - 1) c.substring(sep + 1) else c
            if (leaf.isNotBlank()) return leaf
        }
        if (!s.projectSlug.isNullOrBlank()) return s.projectSlug
        return "Ungrouped"
    }

    val grouped = remember(filtered) {
        filtered.groupBy { workspaceLabel(it) }
            .toSortedMap(compareBy { it.lowercase() })
    }

    // 2026-05-16 -- counts now share the SAME predicate the filter
    // applies (isActiveNow + per-pill state slicing). Previously the
    // counts used an outdated AND-gate (`isLive && isActiveNow`) AND
    // had no "brief" key, so the pill numbers lied about the list
    // (e.g., "Active * 1" while two rows were visible).
    val counts = remember(sessions, activeSessionIds, now) {
        val active = sessions.filter { isActiveNow(it) }
        mapOf(
            "active" to active.size,
            "live" to active.count { (it.activeMode ?: "").equals("live", ignoreCase = true) },
            "brief" to active.count { (it.activeMode ?: "").equals("brief", ignoreCase = true) },
            "teacher" to active.count { (it.activeMode ?: "").equals("teacher", ignoreCase = true) },
            "muted" to active.count { !it.enabled },
        )
    }

    // Build the visible-row order matching what the user actually sees in
    // the LazyColumn so rocker navigation moves through the same sequence.
    val visibleRowOrder = remember(filtered, collapsedGroups) {
        buildList {
            grouped.forEach { (group, rows) ->
                if (group !in collapsedGroups) addAll(rows.map { it.sessionId })
            }
        }
    }

    // Initialize / clamp the highlighted index when the visible set changes.
    LaunchedEffect(visibleRowOrder) {
        val current = highlightedSessionId
        if (current == null || current !in visibleRowOrder) {
            highlightedSessionId = activeSessionId?.takeIf { it in visibleRowOrder }
                ?: visibleRowOrder.firstOrNull()
        }
    }

    // Hardware-button handler:
    //   RockerUp/Down → move highlight cursor
    //   Click          → select the highlighted session (make active + open detail)
    DisposableEffect(visibleRowOrder, highlightedSessionId, sessions) {
        val handler = object : CompanionButtonHandler {
            override fun onRockerUp() {
                val idx = visibleRowOrder.indexOf(highlightedSessionId)
                if (idx > 0) highlightedSessionId = visibleRowOrder[idx - 1]
            }
            override fun onRockerDown() {
                val idx = visibleRowOrder.indexOf(highlightedSessionId)
                if (idx >= 0 && idx < visibleRowOrder.size - 1) {
                    highlightedSessionId = visibleRowOrder[idx + 1]
                } else if (idx < 0 && visibleRowOrder.isNotEmpty()) {
                    highlightedSessionId = visibleRowOrder.first()
                }
            }
            override fun onClick() {
                val sid = highlightedSessionId ?: return
                val session = sessions.firstOrNull { it.sessionId == sid } ?: return
                onSetActive(session)
                onSelect(session)
            }
        }
        setScreenButtonHandler(handler)
        onDispose { setScreenButtonHandler(null) }
    }

    // 2026-05-16 -- master narration state. Persistent-polled (5s) so a
    // flip from the webui or another device propagates here without a
    // screen re-entry. UX parity with the webui's top-bar pill: master
    // is the single highest-stakes toggle in the system; one-tap access.
    var masterEnabled by remember { mutableStateOf<Boolean?>(null) }
    var masterToggling by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val v = withContext(Dispatchers.IO) { daemonClient.getMasterEnabled() }
                masterEnabled = v
            } catch (_: Throwable) { /* keep last value, retry next tick */ }
            delay(5_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Sessions",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onLongPressMenu,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 2026-05-16 -- Master narration pill, parity with webui
                // GlobalStatusBar. One tap mutes the fleet without
                // diving into Preferences. Stays visible at the top of
                // every Sessions list render so the silence-by-master
                // failure mode is one glance away.
                val masterLabel = when (masterEnabled) {
                    true -> "Narration ON"
                    false -> "Narration OFF"
                    null -> "Narration..."
                }
                val masterColor = when (masterEnabled) {
                    true -> Color(0xFF34D399)
                    false -> Color(0xFFFB923C)
                    null -> Color(0xFF94A3B8)
                }
                Surface(
                    color = masterColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(end = 8.dp).clickable(enabled = !masterToggling && masterEnabled != null) {
                        val current = masterEnabled ?: return@clickable
                        masterToggling = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val applied = daemonClient.setMasterEnabled(!current)
                                masterEnabled = applied
                            } catch (_: Throwable) {
                                try { masterEnabled = daemonClient.getMasterEnabled() } catch (_: Throwable) {}
                            } finally {
                                masterToggling = false
                            }
                        }
                    },
                ) {
                    Text(
                        masterLabel,
                        color = masterColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                OutlinedButton(onClick = onUnpair) { Text("Unpair") }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilterChipsRow(
                current = filter,
                counts = counts,
                onChange = { newFilter ->
                    scope.launch { appPreferences.setSessionFilter(newFilter) }
                },
            )
            if (onOpenCTC != null) {
                OutlinedButton(onClick = onOpenCTC) {
                    Text("Chat ▦", fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        loadError?.let { err ->
            ErrorBanner(
                error = err,
                onAction = { e ->
                    when (e.recovery) {
                        AppError.Recovery.Retry -> { loadError = null; reloadKey++ }
                        AppError.Recovery.RePair -> onUnpair()
                        else -> { /* no-op */ }
                    }
                },
                onDismiss = { loadError = null },
            )
            return@Column
        }

        if (sessions.isEmpty()) {
            Text(
                "No sessions yet — start a Claude Code instance and pair it with codetalker.",
                fontSize = 13.sp,
            )
            return@Column
        }
        if (filtered.isEmpty()) {
            // 2026-05-17 — when the current pill hides everything,
            // surface counts in OTHER pills so the user knows where
            // their sessions went. Common trap: change a session's
            // mode to Teacher → it drops out of Live AND Brief filters
            // → user stares at an empty screen with no hint that their
            // sessions exist under the "Teacher" pill. Each non-empty
            // peer filter is offered as a one-tap escape.
            val activeCount = counts["active"] ?: 0
            val peers = listOf("active", "live", "brief", "teacher", "muted")
                .filter { it != filter }
                .mapNotNull { key ->
                    val n = counts[key] ?: 0
                    if (n > 0) key to n else null
                }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "No sessions match the “${filter}” filter.",
                    fontSize = 13.sp,
                    color = Color(0xFF8B91A0),
                )
                if (peers.isNotEmpty()) {
                    Text(
                        "Sessions in other filters:",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                    peers.forEach { (key, n) ->
                        val label = when (key) {
                            "active" -> "Active"
                            "live" -> "Live"
                            "brief" -> "Brief"
                            "teacher" -> "Teacher"
                            "muted" -> "Muted"
                            else -> key
                        }
                        Text(
                            "Switch to $label ($n)",
                            fontSize = 13.sp,
                            color = Color(0xFF6EA8FE),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                scope.launch { appPreferences.setSessionFilter(key) }
                            },
                        )
                    }
                } else if (activeCount == 0) {
                    Text(
                        "No sessions have been active in the last hour. Open a Claude Code window or trigger a hook to wake one up.",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
            }
            return@Column
        }

        LazyColumn {
            grouped.forEach { (group, rows) ->
                val expanded = group !in collapsedGroups
                item(key = "header-$group") {
                    GroupHeader(
                        title = group,
                        liveCount = rows.count { it.isLive },
                        totalCount = rows.size,
                        expanded = expanded,
                        onToggle = {
                            val next = if (expanded) collapsedGroups + group else collapsedGroups - group
                            scope.launch { appPreferences.setSessionCollapsedGroups(next) }
                        },
                    )
                }
                if (expanded) {
                    items(rows, key = { it.sessionId }) { session ->
                        val isActive = session.isCompanionActive ||
                            session.sessionId in activeSessionIds
                        val isHighlighted = session.sessionId == highlightedSessionId
                        SessionRow(
                            session = session,
                            isActive = isActive,
                            isHighlighted = isHighlighted,
                            onClick = {
                                highlightedSessionId = session.sessionId
                                onSelect(session)
                            },
                            onSetActive = { onSetActive(session) },
                            onToggleMute = {
                                val currentlyMuted = !session.enabled
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        // setMuted's contract: true to mute, false to unmute.
                                        daemonClient.setMuted(session.sessionId, muted = !currentlyMuted)
                                        reloadKey++
                                    } catch (e: Throwable) {
                                        loadError = AppErrors.fromThrowable(e)
                                    }
                                }
                            },
                            onSetMode = { mode ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        daemonClient.setActiveMode(session.sessionId, mode)
                                        reloadKey++
                                    } catch (e: Throwable) {
                                        loadError = AppErrors.fromThrowable(e)
                                    }
                                }
                            },
                            onToggleAuto = {
                                val next = !session.autoModeEnabled
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        daemonClient.putOverlay(
                                            session.sessionId,
                                            mapOf("auto_mode_enabled" to next),
                                        )
                                        reloadKey++
                                    } catch (e: Throwable) {
                                        loadError = AppErrors.fromThrowable(e)
                                    }
                                }
                            },
                            // 2026-05-18 — click-toggle voice input. Each
                            // button click either starts recording (if
                            // nothing is recording, or a different button
                            // is) or stops + dispatches (if THIS button is
                            // the active one). Captures session_id at
                            // bind-time so the dispatch always targets the
                            // right session even after scroll.
                            onVoiceToggle = if (onHoldStart != null && onHoldEnd != null) {
                                { mode ->
                                    val thisKey = session.sessionId to mode
                                    val current = activeRecording
                                    if (current == thisKey) {
                                        // Tapped the active button — stop + dispatch
                                        onHoldEnd()
                                        activeRecording = null
                                    } else {
                                        // Stop any other active recording first,
                                        // then start this one.
                                        if (current != null) {
                                            onHoldEnd()
                                        }
                                        onHoldStart(session.sessionId, mode)
                                        activeRecording = thisKey
                                    }
                                }
                            } else null,
                            activeRecordingMode = activeRecording
                                ?.takeIf { it.first == session.sessionId }
                                ?.second,
                            // 2026-05-18 — only the row that owns the active
                            // recording sees the live caption stream. Passing
                            // it unconditionally to every row caused all cards
                            // to grow when ANY session was recording, which
                            // shifted the LazyColumn's content position and
                            // felt like an unexpected scroll-upward to the
                            // user. Now only the active row reflows.
                            liveCaption = if (activeRecording?.first == session.sessionId) liveCaption else "",
                            // 2026-05-21 — REVIEW state plumbing. Only the
                            // row that owns the recording sees the phase +
                            // elapsed values; other rows get IDLE/0 so they
                            // render their normal buttons.
                            sttPhase = if (activeRecording?.first == session.sessionId)
                                sttPhase else SttUiPhase.IDLE,
                            recordingElapsedMs = if (activeRecording?.first == session.sessionId)
                                recordingElapsedMs else 0L,
                            onConfirmSend = onConfirmSend,
                            onDiscardAndReRecord = onDiscardAndReRecord,
                            onCancelDictation = {
                                onCancelDictation?.invoke()
                                activeRecording = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    current: String,
    counts: Map<String, Int>,
    onChange: (String) -> Unit,
) {
    // 2026-05-17 — five pills (added Teacher) overflow narrow-screen
    // width, which caused some chip labels to wrap to two lines and
    // gave the chip-row an unpredictable height. Switched to a
    // horizontally-scrollable LazyRow so chips keep a fixed height
    // regardless of how many are added.
    val pills = listOf(
        "active" to "Active",
        "live" to "Live",
        "brief" to "Brief",
        "teacher" to "Teacher",
        "muted" to "Muted",
    )
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(pills) { (key, label) ->
            val count = counts[key] ?: 0
            FilterChip(
                selected = current == key,
                onClick = { onChange(key) },
                label = { Text("$label · $count", fontSize = 12.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    liveCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▾" else "▸", fontSize = 14.sp, color = Color(0xFFB8BCC4))
            Spacer(Modifier.width(6.dp))
            Text(
                title.uppercase(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color(0xFFE5E7EB),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$liveCount live · $totalCount total",
                fontSize = 11.sp,
                color = Color(0xFF8B91A0),
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1E2230))
    }
}

@Composable
private fun SessionRow(
    session: SessionLite,
    isActive: Boolean,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onSetActive: () -> Unit,
    onToggleMute: () -> Unit,
    onSetMode: (String) -> Unit,
    /** 2026-05-11 — toggle auto-mode from the row's `↻ auto` indicator.
     *  Previously the indicator was a static label; now it's interactive
     *  so users don't have to dive into SessionDetail to turn it off. */
    onToggleAuto: () -> Unit = {},
    /** 2026-05-18 — click-toggle voice input. When non-null, the row
     *  renders Buddy + Dictate buttons at the bottom. The callback fires
     *  ONCE per click with the chosen [SttMode]; the screen-level state
     *  decides whether that click starts or stops a recording (passed
     *  in via [activeRecordingMode]). Null hides the row. */
    onVoiceToggle: ((SttMode) -> Unit)? = null,
    /** 2026-05-18 — when non-null, indicates THIS row currently has a
     *  recording active in the given mode. Used to show the recording
     *  visual on the right button. Null means no recording is active
     *  on this row (the recording may be on another row, or none). */
    activeRecordingMode: SttMode? = null,
    /** 2026-05-18 — live STT caption surface. While a voice recording
     *  is active anywhere on the screen, the partial transcript streams
     *  here. After stop, holds the final transcript / error message
     *  briefly. */
    liveCaption: String = "",
    /** 2026-05-21 — dictation phase. When this row owns the active
     *  recording and phase==REVIEW, the voice-toggle area renders the
     *  Send / Re-record / Cancel button row instead of the regular
     *  buttons. IDLE / RECORDING / DISPATCHING render the existing UI. */
    sttPhase: SttUiPhase = SttUiPhase.IDLE,
    /** 2026-05-21 — elapsed milliseconds inside the current RECORDING
     *  phase. The active row renders this as "8s / 30s" so the user
     *  knows how much time is left before the auto-stop fires. */
    recordingElapsedMs: Long = 0L,
    /** 2026-05-21 — Send button handler in REVIEW. */
    onConfirmSend: (() -> Unit)? = null,
    /** 2026-05-21 — Re-record button handler in REVIEW. */
    onDiscardAndReRecord: (() -> Unit)? = null,
    /** 2026-05-21 — Cancel button handler in REVIEW. */
    onCancelDictation: (() -> Unit)? = null,
) {
    val nowSec = System.currentTimeMillis() / 1000.0
    val isRecentlyActive = session.isLive && (nowSec - session.lastHookAt < 10.0)
    // v0.1.0 unification — strong pulse when daemon says is_speaking=true,
    // softer ambient pulse on recency, steady green on active companion
    // session. Matches the webui flash semantics.
    val isSpeaking = session.isSpeaking

    val transition = rememberInfiniteTransition(label = "row-${session.sessionId}")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    val muted = !session.enabled
    val baseBorderColor = when {
        isActive -> Color(0xFF34D399)
        isSpeaking -> Color(0xFFFB923C).copy(alpha = pulse)
        isRecentlyActive -> Color(0xFFFB923C).copy(alpha = (pulse * 0.7f))
        session.isLive -> Color(0xFF34D399).copy(alpha = 0.5f)
        else -> Color(0xFF3F3F46)
    }
    // CCT-32 v0.1.0 polish — when the user is navigating via the volume
    // rocker, the highlighted row gets a brighter outline so it's obvious
    // which session a hardware-button "select" will act on.
    val borderColor = if (isHighlighted) Color(0xFF60A5FA) else baseBorderColor
    val borderWidth = if (isHighlighted) 3.dp else 2.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 2026-05-16 -- single glyph encodes speaking / mode /
                // liveness / muted state. See SessionStateGlyph for the
                // visual key (solid/hollow/pinprick + color + slash).
                SessionStateGlyph(
                    mode = session.activeMode,
                    speaking = isSpeaking,
                    live = session.isLive,
                    recent = isRecentlyActive,
                    muted = muted,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = if (muted) Color(0xFFB8BCC4) else Color(0xFFE5E7EB),
                    )
                    Text(
                        session.sessionId.take(12),
                        fontSize = 11.sp,
                        color = Color(0xFF8B91A0),
                    )
                }
                ActiveChip(active = isActive, onSetActive = onSetActive)
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MuteButton(muted = muted, onClick = onToggleMute)
                Spacer(Modifier.width(8.dp))
                ModeQuickPick(current = session.activeMode, onChange = onSetMode)
                if (session.autoModeEnabled) {
                    Spacer(Modifier.width(6.dp))
                    // 2026-05-11 — tap to disable auto-mode without opening
                    // SessionDetail. Padding gives a tap target ≥48dp w/h.
                    Text(
                        "↻ auto",
                        fontSize = 10.sp,
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onToggleAuto)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                if (session.audioMisaligned) {
                    Spacer(Modifier.width(6.dp))
                    // 2026-05-11 Tier-A.3 — audio routing misalignment
                    // badge. Daemon flagged audio_outputs for this session
                    // includes phone/glasses but no audio-stream subscriber
                    // is registered. Tapping the row opens the detail screen,
                    // which auto-registers the subscription (or the row's
                    // 'Make active' button does the same). Amber color +
                    // muted-speaker glyph makes the misalignment obvious.
                    Text(
                        "📵 not receiving",
                        fontSize = 10.sp,
                        color = Color(0xFFFB923C),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(0xFF2D1F15), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            session.attachedCharacter?.let { c ->
                Spacer(Modifier.height(8.dp))
                CharacterChip(character = c)
            }

            // 2026-05-18 — click-toggle voice input row.
            // 2026-05-21 — extended with REVIEW state. When this row owns
            // the active recording AND phase is REVIEW, we render a
            // captioned text + Send / Re-record / Cancel row instead of
            // the regular Buddy / Dictate buttons. This gives the user a
            // chance to read what STT heard before it goes to the daemon
            // (fixes the "tax prep rabbit hole" failure mode where the
            // recognizer mishears a key word and Claude charges off in
            // the wrong direction).
            if (onVoiceToggle != null) {
                Spacer(Modifier.height(10.dp))
                val rowOwnsRecording = activeRecordingMode != null
                val inReview = rowOwnsRecording && sttPhase == SttUiPhase.REVIEW
                val inDispatch = rowOwnsRecording && sttPhase == SttUiPhase.DISPATCHING
                if (inReview || inDispatch) {
                    // Caption box — shows the final transcript awaiting Send.
                    Surface(
                        color = Color(0xFF1A2030),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color(0xFF374151),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            liveCaption.ifBlank { "(empty transcript)" },
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Send — primary, green.
                        ReviewActionButton(
                            modifier = Modifier.weight(1f),
                            label = if (inDispatch) "Sending…" else "✓ Send",
                            accentColor = Color(0xFF34D399),
                            enabled = inReview && onConfirmSend != null,
                            onClick = { onConfirmSend?.invoke() },
                        )
                        // Re-record — secondary, orange.
                        ReviewActionButton(
                            modifier = Modifier.weight(1f),
                            label = "↻ Re-record",
                            accentColor = Color(0xFFFB923C),
                            enabled = inReview && onDiscardAndReRecord != null,
                            onClick = { onDiscardAndReRecord?.invoke() },
                        )
                        // Cancel — tertiary, neutral.
                        ReviewActionButton(
                            modifier = Modifier.weight(1f),
                            label = "✕ Cancel",
                            accentColor = Color(0xFF94A3B8),
                            enabled = inReview && onCancelDictation != null,
                            onClick = { onCancelDictation?.invoke() },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 2026-05-21 — countdown label while recording. Shows
                        // "12s / 30s" so the user knows how much time is left
                        // before the auto-finalize fires.
                        val countdownText = if (rowOwnsRecording && sttPhase == SttUiPhase.RECORDING) {
                            val sec = (recordingElapsedMs / 1000L).coerceAtLeast(0)
                            "● ${sec}s / 30s"
                        } else "● Listening…"
                        VoiceToggleButton(
                            modifier = Modifier.weight(1f),
                            label = "🎙 Buddy",
                            recordingLabel = countdownText,
                            accentColor = Color(0xFF60A5FA),
                            isRecording = activeRecordingMode == SttMode.BUDDY,
                            onClick = { onVoiceToggle(SttMode.BUDDY) },
                            liveCaption = if (activeRecordingMode == SttMode.BUDDY) liveCaption else "",
                        )
                        VoiceToggleButton(
                            modifier = Modifier.weight(1f),
                            label = "⌨ Dictate",
                            recordingLabel = countdownText,
                            accentColor = Color(0xFFFB923C),
                            isRecording = activeRecordingMode == SttMode.DIRECT_CC,
                            onClick = { onVoiceToggle(SttMode.DIRECT_CC) },
                            liveCaption = if (activeRecordingMode == SttMode.DIRECT_CC) liveCaption else "",
                        )
                    }
                    // Status line for errors or final caption (only when
                    // not in review — review has its own caption box).
                    if (liveCaption.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        val isError = liveCaption.startsWith("[")
                        Text(
                            liveCaption,
                            fontSize = 11.sp,
                            color = if (isError) Color(0xFFFB7185) else Color(0xFF94A3B8),
                            fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2026-05-18 — click-toggle voice button. First click starts a recording
 * pinned to this button's STT route; second click (or click of any other
 * voice button) stops the recording and dispatches the transcript. The
 * [isRecording] flag controls the visual state — when true, the button
 * shows a darker fill, the recording-state label, and (if non-blank) the
 * live partial transcript. ~48dp tall, thumb-friendly.
 *
 * Replaces the press-and-hold gesture used earlier — user explicitly
 * preferred a tap-to-toggle interaction (less ambiguous when the
 * recognizer auto-stops on silence, more reliable on devices where the
 * Compose pointer-event stream can drop a release).
 */
@Composable
private fun VoiceToggleButton(
    modifier: Modifier = Modifier,
    label: String,
    recordingLabel: String,
    accentColor: Color,
    isRecording: Boolean,
    onClick: () -> Unit,
    /** 2026-05-18 — live partial transcript while THIS button's
     *  recording is active. Caller passes empty string when the
     *  recording is for a different button so we always show the
     *  static label here. */
    liveCaption: String = "",
) {
    val bg = if (isRecording) accentColor.copy(alpha = 0.45f) else accentColor.copy(alpha = 0.15f)
    val borderAlpha = if (isRecording) 1.0f else 0.6f
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            // 2026-05-18 — use a pointerInput tap detector instead of
            // Modifier.clickable. clickable bubbles up: the parent Card's
            // own clickable was firing too, navigating into SessionDetail
            // on the same press. detectTapGestures consumes the pointer
            // events so the Card's click handler never sees them. Also
            // avoids the focus-request side effect that LazyColumn was
            // honoring as "scroll the focused item into view", which
            // contributed to the perceived screen-jump.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isRecording) 2.dp else 1.dp,
            accentColor.copy(alpha = borderAlpha),
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
        ) {
            // While recording and we have a real (non-error) partial
            // transcript, show it so the user can SEE the recognizer is
            // picking them up. Otherwise fall back to the recording or
            // idle label.
            val displayText = when {
                isRecording && liveCaption.isNotBlank() && !liveCaption.startsWith("[") -> liveCaption
                isRecording -> recordingLabel
                else -> label
            }
            Text(
                displayText,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
    }
}

/**
 * 2026-05-21 — pill-style action button used in the dictation REVIEW row.
 * Similar visual language to [VoiceToggleButton] but rendered with a solid
 * outlined treatment so Send / Re-record / Cancel read as decisive
 * commit-style actions rather than the toggle metaphor of the voice
 * buttons. Disabled state dims the surface so DISPATCHING (Send already
 * tapped, request in flight) is visually distinct from REVIEW.
 */
@Composable
private fun ReviewActionButton(
    modifier: Modifier = Modifier,
    label: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1.0f else 0.4f
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        color = accentColor.copy(alpha = 0.12f * alpha),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp, accentColor.copy(alpha = 0.8f * alpha),
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
        ) {
            Text(
                label,
                color = accentColor.copy(alpha = alpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}


@Composable
private fun ActiveChip(active: Boolean, onSetActive: () -> Unit) {
    if (active) {
        // 2026-05-11 — was a non-interactive Surface; promoted to a button so
        // the user can deactivate a session by tapping its green chip,
        // symmetric with the "Make active" outlined button below.
        Surface(
            color = Color(0xFF34D399),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.clickable(onClick = onSetActive),
        ) {
            Text(
                "ACTIVE",
                color = Color(0xFF052E25),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    } else {
        OutlinedButton(
            onClick = onSetActive,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 30.dp),
        ) {
            Text("Make active", fontSize = 11.sp)
        }
    }
}

@Composable
private fun MuteButton(muted: Boolean, onClick: () -> Unit) {
    val label = if (muted) "Unmute" else "Mute"
    val bg = if (muted) Color(0xFF9F1239).copy(alpha = 0.3f) else Color(0xFF1E2230)
    val fg = if (muted) Color(0xFFFCA5A5) else Color(0xFFD1D5DB)
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ModeQuickPick(current: String?, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E2230)),
    ) {
        listOf("brief", "live").forEach { mode ->
            val selected = current?.lowercase() == mode
            val bg = if (selected) Color(0xFF334155) else Color.Transparent
            val fg = if (selected) Color(0xFFFFFFFF) else Color(0xFF94A3B8)
            Text(
                mode,
                color = fg,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .background(bg)
                    .clickable { onChange(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 2026-05-11 Tier-B — auto-subscribe helper. Given a fresh session list,
 * the current active set, and an evaluated-SIDs tracker, compute which
 * sessions should be auto-added to the active set and invoke the
 * callback exactly once for the new batch.
 *
 * Rules:
 * - Daemon says `audioMisaligned == true` (configured for companion sink,
 *   no live subscriber yet)
 * - `audioOutputs` contains "phone" (this Android device represents the
 *   phone sink; glasses auto-subscribe is a future refinement gated on
 *   USB-Audio detection)
 * - Not already in `activeSessionIds` (don't re-add)
 * - Not yet evaluated this screen visit (respects user-driven removals;
 *   user toggles `Make active` off → we mark the SID evaluated, so we
 *   won't auto-re-add it on the next 3s poll)
 *
 * The evaluated set is per-screen-visit (lives in `remember`); leaving
 * and re-entering the session list re-evaluates, which is the natural
 * UX expectation for "I just opened the app, give me the audio I asked
 * for via audio_outputs".
 */
private fun maybeAutoSubscribe(
    sessions: List<SessionLite>,
    activeSessionIds: Set<String>,
    autoSubEvaluatedSids: MutableSet<String>,
    onAutoSubscribeMissing: (Set<String>) -> Unit,
) {
    // 2026-05-16 -- gate auto-subscribe on the same 60-min recency
    // window the Active filter uses. Previously this auto-added every
    // session ever configured with audio_outputs=[phone] (including
    // dormant ones from 30+ days ago) the moment they showed
    // misaligned=true, re-polluting activeSessionIds across launches
    // even when the user had explicitly cleaned them out. Now we only
    // auto-add sessions that are genuinely active right now.
    val nowSec = System.currentTimeMillis() / 1000.0
    val recencyWindowSec = 60 * 60.0
    val candidates = sessions.filter {
        it.audioMisaligned &&
            it.audioOutputs?.contains("phone") == true &&
            (it.isLive ||
                (it.lastModified > 0.0 && (nowSec - it.lastModified) < recencyWindowSec))
    }
    android.util.Log.w(
        "AutoSubscribe",
        "scan: sessions=${sessions.size} misaligned-phone=${candidates.size} " +
            "active=${activeSessionIds.size} evaluated=${autoSubEvaluatedSids.size}",
    )
    if (candidates.isNotEmpty()) {
        android.util.Log.w(
            "AutoSubscribe",
            "candidates: ${candidates.joinToString { it.sessionId.take(12) + '(' + it.displayName + ')' }}",
        )
    }
    val toSubscribe = candidates
        .map { it.sessionId }
        .filter { it !in activeSessionIds && it !in autoSubEvaluatedSids }
        .toSet()
    // Mark every misaligned SID as evaluated even when not auto-adding,
    // so a user de-activation doesn't get clobbered by the next poll.
    candidates.forEach { autoSubEvaluatedSids.add(it.sessionId) }
    if (toSubscribe.isNotEmpty()) {
        android.util.Log.w("AutoSubscribe", "calling onAutoSubscribeMissing for $toSubscribe")
        onAutoSubscribeMissing(toSubscribe)
    } else {
        android.util.Log.w("AutoSubscribe", "nothing to subscribe (toSubscribe empty)")
    }
}

/**
 * 2026-05-16 -- state-aware session-row indicator. Encodes four pieces
 * of state in one visual glyph:
 *
 *   - speaking (TTS in flight)              : pulsing solid ring around a filled core
 *   - muted (enabled == false)              : grey with a diagonal slash overlay
 *   - mode (live / brief / direct)          : color (green / blue / amber)
 *   - liveness (within 5-min / within 60-min / dormant)
 *                                            : fill style (solid / hollow / pinprick)
 *
 * The user said "we can tell activity by the icon that is in the
 * session list, it changes depending on its state" -- this glyph
 * collapses the row's primary state into a 14dp circle that updates
 * at the same 3s poll cadence as the rest of the list.
 */
@Composable
private fun SessionStateGlyph(
    mode: String?,
    speaking: Boolean,
    live: Boolean,
    recent: Boolean,
    muted: Boolean,
) {
    val modeColor = when ((mode ?: "").lowercase()) {
        "live" -> Color(0xFF34D399)   // green
        "brief" -> Color(0xFF60A5FA)  // blue
        "direct" -> Color(0xFFFB923C) // amber
        else -> Color(0xFF94A3B8)
    }
    val coreColor = when {
        muted -> Color(0xFF52525B)
        speaking -> modeColor
        live -> modeColor
        recent -> modeColor.copy(alpha = 0.55f)
        else -> Color(0xFF3F3F46)
    }
    val pulse by rememberInfiniteTransition(label = "glyph").animateFloat(
        initialValue = if (speaking) 0.55f else 1f,
        targetValue = if (speaking) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glyph-pulse",
    )
    Box(modifier = Modifier.size(14.dp)) {
        // Outer ring -- speaking pulses, recent has a thin ring, dormant nothing.
        if (speaking) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(modeColor.copy(alpha = pulse * 0.4f)),
            )
        }
        // Core. Solid for live/speaking, hollow ring for recent-only,
        // tiny pinprick for fully dormant.
        when {
            speaking || live -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(coreColor),
                )
            }
            recent -> {
                // Hollow ring: outer mode color, inner card-background.
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(modeColor.copy(alpha = 0.45f)),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color(0xFF111418)),
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(coreColor),
                )
            }
        }
        // Muted overlay -- diagonal slash. Drawn last so it sits on top.
        if (muted) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawLine(
                    color = Color(0xFFE5E7EB),
                    start = Offset(size.width * 0.15f, size.height * 0.85f),
                    end = Offset(size.width * 0.85f, size.height * 0.15f),
                    strokeWidth = 2.5f,
                )
            }
        }
    }
}
