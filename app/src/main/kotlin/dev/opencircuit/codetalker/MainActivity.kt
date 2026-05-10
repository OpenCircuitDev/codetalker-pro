package dev.opencircuit.codetalker

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.opencircuit.codetalker.input.ButtonRouter
import dev.opencircuit.codetalker.input.HardwareKeys
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.Pairing
import dev.opencircuit.codetalker.net.PairingFlow
import dev.opencircuit.codetalker.net.SessionLite
import dev.opencircuit.codetalker.service.CompanionForegroundService
import dev.opencircuit.codetalker.ui.PairingScreen
import dev.opencircuit.codetalker.ui.SessionDetailScreen
import dev.opencircuit.codetalker.ui.SessionListScreen
import dev.opencircuit.codetalker.ui.theme.CodetalkerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CCT-31 — Codetalker AR Companion entry point.
 *
 * Owns the long-lived singletons: PairingFlow, ButtonRouter, HardwareKeys.
 * dispatchKeyEvent is overridden to feed Beam Pro side button + volume
 * rocker events into HardwareKeys → ButtonRouter.
 *
 * Phase 8 will swap SessionListScreen for the AR HUD root; Compose UI
 * here remains as the before-glasses config surface.
 */
class MainActivity : ComponentActivity() {
    private lateinit var pairingFlow: PairingFlow
    private val buttonRouter = ButtonRouter()
    private val hardwareKeys = HardwareKeys(onInput = { input ->
        buttonRouter.handle(input)
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingFlow = PairingFlow(this)

        // CCT-31 Phase 10a: keep audio + SSE alive when the app backgrounds.
        // Only start the service if the user has paired — no point holding a
        // foreground notification when there's no daemon to talk to yet.
        if (pairingFlow.current() != null) {
            CompanionForegroundService.start(this)
        }

        setContent {
            CodetalkerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B10)),
                    color = Color(0xFF0A0B10),
                ) {
                    CompanionRoot(pairingFlow)
                }
            }
        }
    }

    /**
     * Beam Pro side button + volume rocker capture. HardwareKeys returns
     * true when it consumes an event so the system doesn't ring the
     * volume audio or trigger media playback alongside the AR app.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hardwareKeys.handle(event)) return true
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
@Composable
private fun CompanionRoot(pairingFlow: PairingFlow) {
    var pairing by remember { mutableStateOf<Pairing?>(pairingFlow.current()) }

    // CCT-32 Task A.6: simple two-screen state — list <-> detail. Using a
    // remembered SessionLite ref instead of NavHost keeps deps minimal
    // for v1.0 and avoids reload-on-pop.
    var selectedSession by remember { mutableStateOf<SessionLite?>(null) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }

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
        val selected = selectedSession
        if (selected != null) {
            SessionDetailScreen(
                session = selected,
                daemonClient = client,
                isActive = activeSessionId == selected.sessionId,
                onSetActive = {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            client.setActiveSession(selected.sessionId)
                        } catch (_: Throwable) { /* surfaced via reload */ }
                    }
                    activeSessionId = selected.sessionId
                },
                onBack = { selectedSession = null },
            )
        } else {
            SessionListScreen(
                daemonClient = client,
                activeSessionId = activeSessionId,
                onSelect = { selectedSession = it },
                onUnpair = {
                    pairingFlow.clear()
                    pairing = null
                    selectedSession = null
                    activeSessionId = null
                },
            )
        }
    }
}
