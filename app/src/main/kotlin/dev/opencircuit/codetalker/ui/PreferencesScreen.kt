package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.opencircuit.codetalker.net.DaemonClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.prefs.AppPreferences
import kotlinx.coroutines.launch

/**
 * CCT-32 Task B.4 — preferences surface.
 *
 * v1 holds the "Start on device boot" opt-in. Phase B.6 adds the
 * Diagnostics entry point (long-press menu); for now this screen is the
 * minimum needed to flip B.4 on.
 */
@Composable
fun PreferencesScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
    daemonClient: DaemonClient? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onOpenVoiceLibrary: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val startOnBoot by appPreferences.startOnBoot.collectAsState(initial = false)
    // CCT-32 Task G.2 — opt-in crash reporting toggle. ConsentFlow asks
    // once at first launch; this surfaces the setting forever after so
    // the user can flip it.
    val crashReportingEnabled by appPreferences.crashReportingEnabled.collectAsState(initial = false)

    // 2026-05-16 -- Master narration toggle. Mirrors the value in
    // ~/.claude/scripts/tts_config.yaml `enabled` -- when off, every
    // hook-driven TTS path is silent across the entire fleet. Single
    // place to surface + toggle so a stale `enabled: false` never
    // again causes 5 days of silent audio. Persists to the YAML so
    // daemon restart honors it.
    var masterEnabled by remember { mutableStateOf<Boolean?>(null) }
    var masterToggling by remember { mutableStateOf(false) }
    // Phase 4 (2026-05-16) — push-based refresh via /api/events SSE.
    // Subscribe to MasterConfigChanged so a flip from the webui (or
    // the MCP tts_mute/tts_unmute tools) propagates here within ~50ms
    // without the previous 5s polling burden.
    val daemonEvents = dev.opencircuit.codetalker.net.LocalDaemonEvents.current
    LaunchedEffect(daemonClient, daemonEvents) {
        if (daemonClient != null) {
            // Initial fetch on entry so the toggle reflects current state.
            try {
                masterEnabled = withContext(Dispatchers.IO) { daemonClient.getMasterEnabled() }
            } catch (_: Throwable) { /* keep null until first success */ }
            // Listen for pushed updates.
            daemonEvents?.events?.collect { ev ->
                if (ev.eventType == "MasterConfigChanged" && !masterToggling) {
                    try {
                        masterEnabled = withContext(Dispatchers.IO) { daemonClient.getMasterEnabled() }
                    } catch (_: Throwable) { /* keep last value */ }
                }
            }
        }
    }
    // Safety-net polling for transient SSE drops. Bumped 5s → 30s now
    // that the push channel handles hot deltas. Phase 6 final cleanup
    // will remove this once SSE has soaked in production.
    LaunchedEffect(daemonClient) {
        if (daemonClient != null) {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                if (!masterToggling) {
                    try {
                        masterEnabled = withContext(Dispatchers.IO) { daemonClient.getMasterEnabled() }
                    } catch (_: Throwable) { /* keep last value */ }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(16.dp))

        // 2026-05-16 -- master narration switch (mirrors tts_config.yaml
        // enabled). Single tap mutes the whole fleet without diving
        // into per-session settings; restoring brings everything back.
        if (daemonClient != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text(
                        "Master narration",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        when (masterEnabled) {
                            null -> "Checking daemon..."
                            true -> "Narration is ON for all sessions. Toggle off to silence the entire fleet."
                            false -> "Narration is OFF. No TTS plays from any session. Toggle on to resume."
                        },
                        fontSize = 12.sp,
                        color = if (masterEnabled == false) Color(0xFFFB923C) else Color(0xFF8B91A0),
                    )
                }
                Switch(
                    checked = masterEnabled == true,
                    enabled = !masterToggling && masterEnabled != null,
                    onCheckedChange = { newValue ->
                        masterToggling = true
                        scope.launch {
                            try {
                                val applied = withContext(Dispatchers.IO) {
                                    daemonClient.setMasterEnabled(newValue)
                                }
                                masterEnabled = applied
                            } catch (_: Throwable) {
                                // Re-read from daemon to recover the truth.
                                try {
                                    masterEnabled = withContext(Dispatchers.IO) {
                                        daemonClient.getMasterEnabled()
                                    }
                                } catch (_: Throwable) {}
                            } finally {
                                masterToggling = false
                            }
                        }
                    },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Text("Start on device boot", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "When the glasses power on, automatically reconnect to the daemon if you've already paired.",
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = startOnBoot,
                onCheckedChange = { newValue ->
                    scope.launch { appPreferences.setStartOnBoot(newValue) }
                },
            )
        }

        // CCT-32 Task G.2 — crash-report toggle (opt-in only).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Text(
                    "Send anonymous crash reports",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Help improve stability by sharing stack traces and device model only. No audio, transcripts, or session text are ever sent.",
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = crashReportingEnabled,
                onCheckedChange = { newValue ->
                    scope.launch { appPreferences.setCrashReportingEnabled(newValue) }
                },
            )
        }

        if (onOpenVoiceLibrary != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenVoiceLibrary,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Voice library") }
        }
        if (onOpenDiagnostics != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Diagnostics") }
        }
        if (onOpenAbout != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("About this app") }
        }
    }
}
