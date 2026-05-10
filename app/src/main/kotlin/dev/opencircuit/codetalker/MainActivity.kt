package dev.opencircuit.codetalker

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import dev.opencircuit.codetalker.prefs.AppPreferences
import dev.opencircuit.codetalker.service.CompanionForegroundService
import dev.opencircuit.codetalker.ui.OnboardingScreen
import dev.opencircuit.codetalker.ui.PairingScreen
import dev.opencircuit.codetalker.ui.PreferencesScreen
import dev.opencircuit.codetalker.ui.SessionDetailScreen
import dev.opencircuit.codetalker.ui.SessionListScreen
import dev.opencircuit.codetalker.ui.permissions.PermissionCatalog
import dev.opencircuit.codetalker.ui.permissions.PermissionGate
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
    lateinit var appPreferences: AppPreferences
        private set
    private val buttonRouter = ButtonRouter()
    private val hardwareKeys = HardwareKeys(onInput = { input ->
        buttonRouter.handle(input)
    })

    // CCT-32 Task B.5: lifecycle hardening — screen on/off pause hooks
    // and network-change SSE reconnect tick.
    private val screenStateObserver = dev.opencircuit.codetalker.service.ScreenStateObserver()
    private var networkStateObserver: dev.opencircuit.codetalker.service.NetworkStateObserver? = null

    private var companionViewModel: CompanionViewModel? = null
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingFlow = PairingFlow(this)
        appPreferences = AppPreferences.forContext(this)
        screenStateObserver.register(applicationContext)
        val cm = applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        networkStateObserver = dev.opencircuit.codetalker.service.NetworkStateObserver(cm).also { it.register() }

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
                        appPreferences = appPreferences,
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

        // CCT-32 Task B.5: forward screen on/off transitions to the
        // foreground service so TTS pauses while the screen is off and
        // resumes when the user looks again. The service holds the
        // ExoPlayer; activity is just the conduit.
        viewModelScope.launch {
            screenStateObserver.screenOn.collectLatest { on ->
                if (on) {
                    CompanionForegroundService.notifyResume(this@MainActivity)
                } else {
                    CompanionForegroundService.notifyPause(this@MainActivity)
                }
            }
        }
        // Network-change reconnect tick — bumps every time we get back
        // a usable network. The view model can opt-in to re-trigger SSE
        // by collecting on the `online` flow externally; for v1 we just
        // log here.
        viewModelScope.launch {
            networkStateObserver?.reconnectTick?.collectLatest { tick ->
                if (tick > 0) {
                    CompanionForegroundService.notifyReconnect(this@MainActivity)
                }
            }
        }
    }

    override fun onDestroy() {
        companionViewModel?.release()
        screenStateObserver.unregister(applicationContext)
        networkStateObserver?.unregister()
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
    appPreferences: AppPreferences,
    registerViewModel: (CompanionViewModel?) -> Unit,
) {
    var pairing by remember { mutableStateOf<Pairing?>(pairingFlow.current()) }

    // CCT-32 Task A.6: simple two-screen state — list <-> detail. Using a
    // remembered SessionLite ref instead of NavHost keeps deps minimal
    // for v1.0 and avoids reload-on-pop.
    var selectedSession by remember { mutableStateOf<SessionLite?>(null) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    // CCT-32 Task B.4 / B.6 — secondary screens reachable via long-press menu.
    var showingPreferences by remember { mutableStateOf(false) }
    var showingDiagnostics by remember { mutableStateOf(false) }

    // CCT-32 Task B.5: restore active session id from DataStore on launch
    // so process death doesn't drop the user into "no active session."
    val persistedActiveId by appPreferences.activeSessionId.collectAsState(initial = null)
    androidx.compose.runtime.LaunchedEffect(persistedActiveId) {
        if (activeSessionId == null && persistedActiveId != null) {
            activeSessionId = persistedActiveId
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // CCT-32 Task B.2: onboarding gate. Persisted in DataStore — once the
    // user finishes the tour, every relaunch goes straight to the pairing
    // / session list. Already-paired users with a pre-existing token also
    // skip onboarding (the QR they scanned acted as their first launch).
    val onboardingComplete by appPreferences.onboardingComplete.collectAsState(
        initial = pairingFlow.current() != null,
    )
    var onboardingDismissed by remember { mutableStateOf(false) }
    val onboardingDone = onboardingComplete || onboardingDismissed
    if (!onboardingDone) {
        OnboardingScreen(
            onComplete = {
                onboardingDismissed = true
                kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                    appPreferences.setOnboardingComplete(true)
                }
            },
        )
        registerViewModel(null)
        return
    }

    // CCT-32 Task B.1: permission rationale gate. Run before any screen
    // that needs camera (QR), mic (STT), or notifications (foreground
    // service). The gate self-skips already-granted entries; user can
    // also opt to "Skip for now" which routes to the pairing flow.
    var permissionsAcked by remember {
        mutableStateOf(
            PermissionCatalog.required().all { p ->
                androidx.core.content.ContextCompat.checkSelfPermission(context, p.manifest) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            },
        )
    }
    if (!permissionsAcked) {
        PermissionGate(
            permissions = PermissionCatalog.required(),
            onAllGranted = { permissionsAcked = true },
            onSkip = { permissionsAcked = true },
        )
        registerViewModel(null)
        return
    }

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
        if (showingDiagnostics) {
            dev.opencircuit.codetalker.ui.DiagnosticsScreen(
                daemonClient = client,
                appPreferences = appPreferences,
                onBack = { showingDiagnostics = false },
            )
            return
        }
        if (showingPreferences) {
            PreferencesScreen(
                appPreferences = appPreferences,
                onBack = { showingPreferences = false },
                onOpenDiagnostics = {
                    showingPreferences = false
                    showingDiagnostics = true
                },
            )
            return
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
                    // CCT-32 Task B.5: persist for process-death recovery.
                    GlobalScope.launch(Dispatchers.IO) {
                        appPreferences.setActiveSessionId(selected.sessionId)
                    }
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
                    GlobalScope.launch(Dispatchers.IO) {
                        appPreferences.setActiveSessionId(null)
                    }
                },
                onLongPressMenu = { showingPreferences = true },
            )
        }
    }
}
