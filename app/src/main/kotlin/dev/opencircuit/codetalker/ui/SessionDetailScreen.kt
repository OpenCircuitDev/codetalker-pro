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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import dev.opencircuit.codetalker.net.AttachedCharacter
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.SessionLite
import dev.opencircuit.codetalker.net.SessionState
import dev.opencircuit.codetalker.ui.character.CharacterAttachRow
import dev.opencircuit.codetalker.ui.character.CharacterChip
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
) {
    var state by remember(session.sessionId) { mutableStateOf<SessionState?>(null) }
    var loadError by remember(session.sessionId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                val s = withContext(Dispatchers.IO) { daemonClient.getSession(session.sessionId) }
                state = s
                loadError = null
            } catch (e: Throwable) {
                loadError = e.message ?: "load failed"
            }
        }
    }

    LaunchedEffect(session.sessionId) { refresh() }

    fun applyOverlay(overlay: Map<String, Any?>) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { daemonClient.putOverlay(session.sessionId, overlay) }
                val s = withContext(Dispatchers.IO) { daemonClient.getSession(session.sessionId) }
                state = s
            } catch (e: Throwable) {
                loadError = e.message ?: "overlay failed"
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

        loadError?.let {
            Text(
                "Could not load: $it",
                color = Color(0xFFFB7185),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
        }

        val s = state
        if (s == null) {
            Text("Loading session details…", color = Color(0xFF8B91A0), fontSize = 13.sp)
            return@Column
        }

        // ----- Mode -----
        SectionHeader("Speaking mode")
        ModePicker(current = s.activeMode) { newMode ->
            applyOverlay(mapOf("active_mode" to newMode))
        }
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
                        loadError = e.message ?: "attach failed"
                    }
                }
            },
            onDetach = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { daemonClient.detachCharacter(session.sessionId) }
                        refresh()
                    } catch (e: Throwable) {
                        loadError = e.message ?: "detach failed"
                    }
                }
            },
        )
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
