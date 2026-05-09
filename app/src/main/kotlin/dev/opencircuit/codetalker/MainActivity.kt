package dev.opencircuit.codetalker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.Pairing
import dev.opencircuit.codetalker.net.PairingFlow
import dev.opencircuit.codetalker.ui.PairingScreen
import dev.opencircuit.codetalker.ui.SessionListScreen

/**
 * CCT-31 — Codetalker AR Companion entry point.
 *
 * Phase 5c routes between PairingScreen (no token yet) and SessionListScreen
 * (token persisted). Phase 8 swaps SessionListScreen for the AR HUD root.
 */
class MainActivity : ComponentActivity() {
    private lateinit var pairingFlow: PairingFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingFlow = PairingFlow(this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B10)),
                    color = Color(0xFF0A0B10),
                ) {
                    CompanionRoot(pairingFlow)
                }
            }
        }
    }
}

@Composable
private fun CompanionRoot(pairingFlow: PairingFlow) {
    var pairing by remember { mutableStateOf<Pairing?>(pairingFlow.current()) }

    val current = pairing
    if (current == null) {
        PairingScreen(
            pairingFlow = pairingFlow,
            onPaired = { pairing = it },
        )
    } else {
        val client = remember(current) {
            DaemonClient(baseUrl = current.daemonUrl, pairingToken = current.pairingToken)
        }
        SessionListScreen(
            daemonClient = client,
            onUnpair = {
                pairingFlow.clear()
                pairing = null
            },
        )
    }
}
