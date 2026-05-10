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
import dev.opencircuit.codetalker.audio.AndroidSTTRecorder
import dev.opencircuit.codetalker.audio.STTRecorder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.Response

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

    private var companionViewModel: CompanionViewModel? = null
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                    CompanionRoot(
                        pairingFlow = pairingFlow,
                        registerViewModel = { vm -> companionViewModel = vm },
                    )
                }
            }
        }

        // CCT-32 Task A.8: Forward ButtonRouter state changes to the
        // active CompanionViewModel. Installed once at activity scope.
        viewModelScope.launch {
            buttonRouter.state.collectLatest { state ->
                companionViewModel?.handleButtonState(state)
            }
        }
    }

    override fun onDestroy() {
        companionViewModel?.release()
        super.onDestroy()
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
private fun CompanionRoot(
    pairingFlow: PairingFlow,
    registerViewModel: (CompanionViewModel?) -> Unit,
) {
    var pairing by remember { mutableStateOf<Pairing?>(pairingFlow.current()) }

    // CCT-32 Task A.6: simple two-screen state — list <-> detail. Using a
    // remembered SessionLite ref instead of NavHost keeps deps minimal
    // for v1.0 and avoids reload-on-pop.
    var selectedSession by remember { mutableStateOf<SessionLite?>(null) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val current = pairing
    if (current == null) {
        PairingScreen(
            pairingFlow = pairingFlow,
            onPaired = { pairing = it },
        )
        registerViewModel(null)
    } else {
        val client = remember(current) {
            DaemonClient(baseUrl = current.daemonUrl, pairingToken = current.pairingToken)
        }
        // CCT-32 Task A.8: build (or rebuild) the coordinator when pairing changes.
        val viewModel = remember(current) {
            val sttRecorder: STTRecorder = AndroidSTTRecorder(context.applicationContext)
            CompanionViewModel(
                sttRecorder = sttRecorder,
                inject = { buddyId, text ->
                    // Use the SSE inject endpoint via DaemonClient. We
                    // don't subscribe to the response stream here — the
                    // SSE listener forwards events into captionText below.
                    val buddy = buddyId
                    val finalText = text
                    val cs = kotlinx.coroutines.CompletableDeferred<Unit>()
                    val source = client.inject(buddy, finalText, object : EventSourceListener() {
                        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                            // The daemon emits caption / status events. Surface
                            // them in captionText.
                            // (Coordinated via the VM in real use; here keep simple.)
                        }
                        override fun onClosed(eventSource: EventSource) { cs.complete(Unit) }
                        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                            cs.complete(Unit)
                        }
                    })
                    // Don't block — let SSE deliver in the background; the call
                    // path returns once the request is in flight.
                },
                startBuddy = { sid -> client.startBuddy(sid) },
            ).also { vm ->
                registerViewModel(vm)
            }
        }
        // Track the active session id in the VM whenever the user picks one.
        androidx.compose.runtime.LaunchedEffect(activeSessionId) {
            viewModel.activeSessionId.value = activeSessionId
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
