package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import dev.opencircuit.codetalker.CompanionViewModel
import dev.opencircuit.codetalker.audio.SttAudibleCue
import dev.opencircuit.codetalker.input.ButtonState
import dev.opencircuit.codetalker.input.CompanionButtonHandler
import dev.opencircuit.codetalker.net.AttachedCharacter
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.SessionLite
import dev.opencircuit.codetalker.net.SessionState
import dev.opencircuit.codetalker.ui.character.CharacterAttachRow
import dev.opencircuit.codetalker.ui.character.CharacterChip
import dev.opencircuit.codetalker.ui.errors.AppError
import dev.opencircuit.codetalker.ui.errors.AppErrors
import dev.opencircuit.codetalker.ui.errors.ErrorBanner
import dev.opencircuit.codetalker.ui.markup.MarkupQuickPanel
import dev.opencircuit.codetalker.ui.pickers.CadencePicker
import dev.opencircuit.codetalker.ui.pickers.ModePicker
import dev.opencircuit.codetalker.ui.pickers.MutedToggle
import dev.opencircuit.codetalker.ui.pickers.VoicePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CCT-32 Task A.2 — per-session control surface.
 *
 * Owns the read-modify-loop for a single session: load the daemon's
 * resolved_cfg, render pickers + toggles + markup panel + character row,
 * and on every change PUT a partial overlay back to the daemon and
 * refresh state from the response.
 *
 * Pickers, MarkupQuickPanel, and CharacterAttachRow are wired in tasks
 * A.3-A.5; this scaffold loads state and renders the header/back/active
 * toggle so subsequent tasks slot in without churn.
 */
@Composable
fun SessionDetailScreen(
    session: SessionLite,
    daemonClient: DaemonClient,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onBack: () -> Unit,
    companionViewModel: CompanionViewModel? = null,
    setScreenButtonHandler: (CompanionButtonHandler?) -> Unit = {},
) {
    var state by remember(session.sessionId) { mutableStateOf<SessionState?>(null) }
    var loadError by remember(session.sessionId) { mutableStateOf<AppError?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                val s = withContext(Dispatchers.IO) { daemonClient.getSession(session.sessionId) }
                state = s
                loadError = null
            } catch (e: Throwable) {
                loadError = AppErrors.fromThrowable(e)
            }
        }
    }

    LaunchedEffect(session.sessionId) { refresh() }

    // 2026-05-16 -- periodic refresh so mode/mute/voice/cadence changes
    // made from the Sessions list (or webui, or another phone) propagate
    // into the detail panel within 3 seconds. Previously the detail
    // panel loaded once on entry and never refreshed, so brief/live
    // toggles in the row list looked like they didn't sync.
    LaunchedEffect(session.sessionId) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            try {
                val s = withContext(Dispatchers.IO) { daemonClient.getSession(session.sessionId) }
                state = s
            } catch (_: Throwable) {
                // Keep last successful snapshot; transient blips are not user-facing.
            }
        }
    }

    // CCT-32 v0.1.0 polish — wire hardware buttons for in-session use:
    //   Click       → toggle mute via overlay PUT
    //   RockerUp    → switch session to live mode
    //   RockerDown  → switch session to brief mode
    //   LongPress   → start STT (hold to talk)
    //   HoldEnd     → stop STT + dispatch to buddy → reply audio (Phase 2)
    DisposableEffect(session.sessionId, state, companionViewModel) {
        val handler = object : CompanionButtonHandler {
            override fun onClick() {
                val currentlyEnabled = state?.enabled ?: session.enabled
                scope.launch(Dispatchers.IO) {
                    try {
                        daemonClient.setMuted(session.sessionId, muted = currentlyEnabled)
                        refresh()
                    } catch (e: Throwable) {
                        loadError = AppErrors.fromThrowable(e)
                    }
                }
            }
            override fun onRockerUp() {
                scope.launch(Dispatchers.IO) {
                    try {
                        daemonClient.setActiveMode(session.sessionId, "live")
                        refresh()
                    } catch (e: Throwable) {
                        loadError = AppErrors.fromThrowable(e)
                    }
                }
            }
            override fun onRockerDown() {
                scope.launch(Dispatchers.IO) {
                    try {
                        daemonClient.setActiveMode(session.sessionId, "brief")
                        refresh()
                    } catch (e: Throwable) {
                        loadError = AppErrors.fromThrowable(e)
                    }
                }
            }
            override fun onLongPress() {
                // Vol-DOWN hold → Buddy STT (intermediate LLM agent).
                SttAudibleCue.playStart()
                companionViewModel?.sttMode?.value =
                    dev.opencircuit.codetalker.SttMode.BUDDY
                companionViewModel?.activeSessionId?.value = session.sessionId
                companionViewModel?.handleButtonState(ButtonState.Listening)
            }
            override fun onHoldEnd() {
                SttAudibleCue.playStop()
                companionViewModel?.handleButtonState(ButtonState.DispatchListening)
            }
            // 2026-05-12 — vol-UP hold → direct-STT into the active CC
            // session (no Buddy intermediate). Same recording mechanics,
            // different dispatch endpoint, set via sttMode.
            override fun onRockerUpLongPress() {
                SttAudibleCue.playStart()
                companionViewModel?.sttMode?.value =
                    dev.opencircuit.codetalker.SttMode.DIRECT_CC
                companionViewModel?.activeSessionId?.value = session.sessionId
                companionViewModel?.handleButtonState(ButtonState.Listening)
            }
            override fun onRockerUpHoldEnd() {
                SttAudibleCue.playStop()
                companionViewModel?.handleButtonState(ButtonState.DispatchListening)
            }
        }
        setScreenButtonHandler(handler)
        onDispose { setScreenButtonHandler(null) }
    }

    fun applyOverlay(overlay: Map<String, Any?>) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { daemonClient.putOverlay(session.sessionId, overlay) }
                val s = withContext(Dispatchers.IO) { daemonClient.getSession(session.sessionId) }
                state = s
            } catch (e: Throwable) {
                loadError = AppErrors.fromThrowable(e)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        // Header — back + display name + persona chip if a character is attached.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.isLive) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34D399))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(session.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(session.sessionId.take(12), fontSize = 11.sp, color = Color(0xFF8B91A0))
            }
            session.attachedCharacter?.let { c ->
                CharacterChip(character = c)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Make Active toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        if (isActive) "Active session" else "Make active",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Sends this session's audio + transcript to the AR companion.",
                        fontSize = 11.sp,
                        color = Color(0xFF8B91A0),
                    )
                }
                Button(onClick = onSetActive, enabled = !isActive) {
                    Text(if (isActive) "Active" else "Set active")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        loadError?.let { err ->
            ErrorBanner(
                error = err,
                onAction = { e ->
                    when (e.recovery) {
                        AppError.Recovery.Retry -> { loadError = null; refresh() }
                        AppError.Recovery.RePair -> onBack()  // SessionList handles unpair flow.
                        AppError.Recovery.OpenSettings -> { /* no-op from detail */ }
                        AppError.Recovery.None -> { /* no-op */ }
                    }
                },
                onDismiss = { loadError = null },
            )
            Spacer(Modifier.height(8.dp))
        }

        val s = state
        if (s == null) {
            Text("Loading session details…", color = Color(0xFF8B91A0), fontSize = 13.sp)
            return@Column
        }

        // ----- Display name + workspace group -----
        SectionHeader("Name & workspace")
        WorkspaceFields(
            initialDisplayName = session.displayName,
            initialWorkspaceGroup = session.workspaceGroup,
            onSaveDisplayName = { newName -> applyOverlay(mapOf("display_name" to newName)) },
            onSaveWorkspaceGroup = { newGroup -> applyOverlay(mapOf("workspace_group" to newGroup)) },
        )
        Spacer(Modifier.height(16.dp))

        // ----- Mode -----
        SectionHeader("Speaking mode")
        ModePicker(current = s.activeMode) { newMode ->
            applyOverlay(mapOf("active_mode" to newMode))
        }
        Spacer(Modifier.height(8.dp))
        // v0.1.0 unification — auto-switch toggle with optimistic local
        // state so the user sees the flip immediately. The parent's
        // session.autoModeEnabled is a stale SessionLite snapshot, so
        // without `var localAuto by remember(session.autoModeEnabled)` the
        // switch would visually freeze even when the daemon write succeeded.
        var localAuto by remember(session.autoModeEnabled) { mutableStateOf(session.autoModeEnabled) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = localAuto,
                onCheckedChange = {
                    localAuto = it
                    applyOverlay(mapOf("auto_mode_enabled" to it))
                },
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Auto-switch by activity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Briefs background work, goes live when you interact.",
                    fontSize = 11.sp,
                    color = Color(0xFF8B91A0),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // ----- Audio destination -----
        SectionHeader("Audio destination")
        DestinationPicker(
            current = session.audioOutputs,
            onChange = { newList ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            daemonClient.setAudioOutputs(session.sessionId, newList)
                        }
                        refresh()
                    } catch (e: Throwable) {
                        loadError = AppErrors.fromThrowable(e)
                    }
                }
            },
        )
        Spacer(Modifier.height(16.dp))

        // ----- Voice -----
        SectionHeader("Voice")
        VoicePicker(
            currentEngine = s.voiceEngine,
            currentModel = s.voiceModel,
            daemonClient = daemonClient,
        ) { engine, model ->
            val voicePatch: Map<String, Any?> = mapOf("engine" to engine, "model" to model)
            applyOverlay(mapOf("voice" to voicePatch))
        }
        Spacer(Modifier.height(16.dp))

        // ----- Cadence -----
        SectionHeader("Cadence (live mode)")
        CadencePicker(current = s.cadence) { newCadence ->
            val livePatch: Map<String, Any?> = mapOf("cadence" to newCadence)
            applyOverlay(mapOf("live" to livePatch))
        }
        Spacer(Modifier.height(16.dp))

        // ----- Muted toggle -----
        MutedToggle(enabled = s.enabled) { newEnabled ->
            applyOverlay(mapOf("enabled" to newEnabled))
        }
        Spacer(Modifier.height(16.dp))

        // ----- Character attach row -----
        SectionHeader("Character")
        CharacterAttachRow(
            attachedCharacterId = s.attachedCharacterId,
            attachedDisplay = session.attachedCharacter,
            daemonClient = daemonClient,
            onAttach = { charId ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            daemonClient.attachCharacter(session.sessionId, charId)
                        }
                        refresh()
                    } catch (e: Throwable) {
                        loadError = AppErrors.fromThrowable(e)
                    }
                }
            },
            onDetach = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { daemonClient.detachCharacter(session.sessionId) }
                        refresh()
                    } catch (e: Throwable) {
                        loadError = AppErrors.fromThrowable(e)
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        // v1.0 — auto-generate character via LLM, preview-then-edit flow.
        // Dialog opens with all fields editable; Save & Attach commits.
        var showingAutoGen by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showingAutoGen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("✨ Auto-generate character from session", fontSize = 12.sp)
        }
        if (showingAutoGen) {
            dev.opencircuit.codetalker.ui.character.AutoGenerateCharacterDialog(
                daemonClient = daemonClient,
                sessionId = session.sessionId,
                sessionLabel = session.displayName,
                onDismiss = { showingAutoGen = false },
                onAttached = {
                    refresh()
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        // ----- Markup quick panel -----
        SectionHeader("Markup treatments")
        MarkupQuickPanel(current = s.markup) { formName, kind ->
            val kindPatch: Map<String, Any?> = mapOf("kind" to kind)
            val formPatch: Map<String, Any?> = mapOf(formName to kindPatch)
            applyOverlay(mapOf("markup" to formPatch))
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** v0.1.0 unification — three independent audio sinks (Desktop, Phone
 *  speakers, Glasses USB-Audio). Each chip toggles membership in the
 *  list. Empty list = silent everywhere. `current == null` means the
 *  session inherits the fleet default — we render that as all three
 *  selected (since the default of defaults is "play everywhere") but
 *  the first toggle commits an explicit override. The "Reset to default"
 *  text button below clears the override (sends null) so the session
 *  re-inherits the fleet default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationPicker(
    current: List<String>?,
    onChange: (List<String>?) -> Unit,
) {
    // v0.1.0 unification — optimistic local state. The parent's `current`
    // prop is a stale snapshot of SessionLite from list-row click time; it
    // doesn't re-bind to subsequent SessionListScreen polls, so without
    // this local mirror every chip tap would appear visually frozen even
    // though the daemon accepted the write. `remember(current)` re-seeds
    // local on parent updates (e.g., when the user backs out and re-enters
    // the detail, or a future poll-driven refresh kicks in).
    var local by remember(current) { mutableStateOf(current) }
    val isOverride = local != null
    val effective: Set<String> = local?.toSet() ?: setOf("desktop", "phone", "glasses")
    val sinks = listOf(
        "desktop" to "Desktop",
        "phone" to "Phone",
        "glasses" to "Glasses",
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sinks.forEach { (key, label) ->
                val selected = key in effective
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = effective.toMutableSet().apply {
                            if (selected) remove(key) else add(key)
                        }
                        val nextList = next.toList().sorted()
                        local = nextList            // optimistic UI update
                        onChange(nextList)          // fire-and-forget daemon write
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF334155),
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isOverride) "Per-session override" else "Inheriting fleet default",
                fontSize = 10.sp,
                color = Color(0xFF8B91A0),
            )
            if (isOverride) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { local = null; onChange(null) }) {
                    Text("Reset to default", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceFields(
    initialDisplayName: String,
    initialWorkspaceGroup: String?,
    onSaveDisplayName: (String) -> Unit,
    onSaveWorkspaceGroup: (String) -> Unit,
) {
    // v0.1.0 unification — track the LAST SAVED value locally so the Save
    // button correctly disables after a successful write. Without this,
    // the button compares against `initialDisplayName` which is a stale
    // SessionLite snapshot that never refreshes — so users see the Save
    // button stuck "enabled" even after they've tapped it.
    var displayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var workspaceGroup by remember(initialWorkspaceGroup) {
        mutableStateOf(initialWorkspaceGroup.orEmpty())
    }
    var savedDisplayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var savedWorkspaceGroup by remember(initialWorkspaceGroup) {
        mutableStateOf(initialWorkspaceGroup.orEmpty())
    }
    val suggestions = dev.opencircuit.codetalker.net.SUGGESTED_WORKSPACE_GROUPS

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Session name", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = displayName.trim()
                    savedDisplayName = trimmed       // optimistic: button disables now
                    onSaveDisplayName(trimmed)
                },
                enabled = displayName.trim() != savedDisplayName,
            ) { Text("Save", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = workspaceGroup,
                onValueChange = { workspaceGroup = it },
                label = { Text("Workspace group", fontSize = 11.sp) },
                placeholder = { Text("e.g. OCRacing / Clients / OCDev", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = workspaceGroup.trim()
                    savedWorkspaceGroup = trimmed
                    onSaveWorkspaceGroup(trimmed)
                },
                enabled = workspaceGroup.trim() != savedWorkspaceGroup,
            ) { Text("Save", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            suggestions.forEach { name ->
                androidx.compose.material3.AssistChip(
                    onClick = { workspaceGroup = name },
                    label = { Text(name, fontSize = 10.sp) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        color = Color(0xFF8B91A0),
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = Color(0xFF1E2230))
    Spacer(Modifier.height(8.dp))
}
