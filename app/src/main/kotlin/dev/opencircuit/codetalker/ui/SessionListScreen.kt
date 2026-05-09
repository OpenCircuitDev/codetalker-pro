package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.SessionLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CCT-31 Phase 5c — paired-state landing screen.
 *
 * Phase 5/6/7 stub. Lists sessions reachable through the daemon and lets
 * the user pick an active one. Phase 8 swaps this UI for the AR HUD +
 * floating menu rendered via Nebula SDK; this Compose screen will remain
 * as the "before-glasses" config surface.
 */
@Composable
fun SessionListScreen(
    daemonClient: DaemonClient,
    onUnpair: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<SessionLite>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var activeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val list = withContext(Dispatchers.IO) { daemonClient.listSessions() }
            sessions = list
        } catch (e: Throwable) {
            loadError = e.message ?: "load failed"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(0.dp).weight(0f, false),
        ) {
            Text("Sessions", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onUnpair) { Text("Unpair") }
        }
        Spacer(Modifier.height(8.dp))

        loadError?.let {
            Text("Could not load: $it", fontSize = 13.sp)
            return@Column
        }
        if (sessions.isEmpty()) {
            Text("No sessions yet — start a Claude Code instance.", fontSize = 13.sp)
            return@Column
        }

        LazyColumn {
            items(sessions, key = { it.sessionId }) { s ->
                Card(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(s.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(s.sessionId.take(12), fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            activeId = s.sessionId
                            // Phase 6 will wire button-router and inject;
                            // for now we just acknowledge the selection.
                        }) {
                            Text(if (activeId == s.sessionId) "Active" else "Set active")
                        }
                    }
                }
            }
        }
    }
}
