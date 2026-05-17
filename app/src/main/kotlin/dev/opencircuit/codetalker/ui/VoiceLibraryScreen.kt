package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.PiperCatalogEntry
import dev.opencircuit.codetalker.viewmodel.VoiceLibraryViewModel
import kotlinx.coroutines.launch

/**
 * Voice Library — Piper catalog manager.
 *
 * Mirrors the webui's `Voices` tab. Two lists: installed (with test +
 * remove buttons) and available (with install button). Backed by four
 * daemon endpoints: catalog / install / uninstall / preview.
 *
 * Reachable from Preferences → "Voice library" entry (B.6 long-press
 * menu route), keeping it one tap behind the Sessions list so first-
 * launch users land on session monitoring, not voice management.
 */
@Composable
fun VoiceLibraryScreen(
    daemonClient: DaemonClient,
    onBack: () -> Unit,
) {
    val viewModel = remember(daemonClient) { VoiceLibraryViewModel(daemonClient) }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(daemonClient) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
            .padding(16.dp),
    ) {
        // Header row with back chip + refresh hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Text(
                "Voice library",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFFEDEEF3),
            )
            TextButton(onClick = { scope.launch { viewModel.refresh() } }) {
                Text("Refresh", color = Color(0xFF67E8F9))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Local TTS voices · install from rhasspy/piper-voices · test plays through the daemon's audio output",
            fontSize = 11.sp,
            color = Color(0xFF6B7280),
        )
        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(state.error ?: "", color = Color(0xFFFCA5A5), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF67E8F9))
            }
            return@Column
        }

        val installed = state.entries.filter { it.installed }
        val available = state.entries.filter { !it.installed }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SectionHeader("Installed (${installed.size})")
            }
            if (installed.isEmpty()) {
                item {
                    Text(
                        "No piper voices installed yet.",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(installed, key = { it.name }) { e ->
                VoiceRow(
                    entry = e,
                    busy = state.busy[e.name],
                    onTest = { scope.launch { viewModel.preview(e.name) } },
                    onInstall = { /* not shown for installed */ },
                    onRemove = { scope.launch { viewModel.uninstall(e.name) } },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                SectionHeader("Available (${available.size})")
            }
            if (available.isEmpty()) {
                item {
                    Text(
                        "All curated voices are installed.",
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(available, key = { it.name }) { e ->
                VoiceRow(
                    entry = e,
                    busy = state.busy[e.name],
                    onTest = { /* preview only for installed */ },
                    onInstall = { scope.launch { viewModel.install(e.name) } },
                    onRemove = { /* not shown for available */ },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    "Voice files download to ~/.claude/scripts/piper/voices on the daemon host. " +
                        "Larger voices (en_US-ryan-high) are 110 MB and can take a minute on slow connections.",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF94A3B8),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun VoiceRow(
    entry: PiperCatalogEntry,
    busy: VoiceLibraryViewModel.RowAction?,
    onTest: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0x80111827), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, fontSize = 13.sp, color = Color(0xFFE2E8F0))
            val meta = buildList {
                entry.lang?.let { add(it) }
                entry.gender?.let { add(it) }
                entry.quality?.let { add(it) }
                if (entry.sizeMb > 0) add("${entry.sizeMb} MB")
                if (!entry.curated) add("custom")
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, fontSize = 10.sp, color = Color(0xFF6B7280))
            }
        }
        Spacer(Modifier.size(8.dp))
        if (entry.installed) {
            OutlinedButton(
                onClick = onTest,
                enabled = busy == null,
            ) {
                Text(if (busy == VoiceLibraryViewModel.RowAction.PREVIEW) "…" else "▶ test")
            }
            Spacer(Modifier.size(6.dp))
            TextButton(
                onClick = onRemove,
                enabled = busy == null,
            ) {
                Text(
                    if (busy == VoiceLibraryViewModel.RowAction.UNINSTALL) "removing…" else "remove",
                    color = Color(0xFFFCA5A5),
                )
            }
        } else {
            OutlinedButton(
                onClick = onInstall,
                enabled = busy == null,
            ) {
                Text(
                    if (busy == VoiceLibraryViewModel.RowAction.INSTALL) "downloading…" else "⬇ install",
                    color = Color(0xFF67E8F9),
                )
            }
        }
    }
}
