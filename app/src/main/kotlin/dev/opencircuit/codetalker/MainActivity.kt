package dev.opencircuit.codetalker

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import dev.opencircuit.codetalker.net.DaemonEvents
import dev.opencircuit.codetalker.net.LocalDaemonEvents
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
import dev.opencircuit.codetalker.telemetry.ConsentDialog
import dev.opencircuit.codetalker.telemetry.ConsentFlow
import dev.opencircuit.codetalker.telemetry.CrashReporter
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
    // CCT-32 v0.1.0 polish — per-screen button handler. When non-null, raw
    // ButtonInput events are dispatched here BEFORE falling through to the
    // legacy ButtonRouter (so existing in-glasses voice flow stays intact
    // when no screen has claimed the buttons).
    @Volatile
    private var screenButtonHandler: dev.opencircuit.codetalker.input.CompanionButtonHandler? = null

    private val hardwareKeys = HardwareKeys(onInput = { input ->
        val handler = screenButtonHandler
        if (handler != null) {
            when (input) {
                is dev.opencircuit.codetalker.input.ButtonInput.Click -> handler.onClick()
                is dev.opencircuit.codetalker.input.ButtonInput.LongPress -> handler.onLongPress()
                is dev.opencircuit.codetalker.input.ButtonInput.HoldEnd -> handler.onHoldEnd()
                // 2026-05-12 — vol-UP long-press routes to direct-STT
                // (transcript types into the active CC session via daemon
                // SendKeys); vol-DOWN long-press stays on Buddy STT.
                is dev.opencircuit.codetalker.input.ButtonInput.LongPressUp ->
                    handler.onRockerUpLongPress()
                is dev.opencircuit.codetalker.input.ButtonInput.HoldEndUp ->
                    handler.onRockerUpHoldEnd()
                is dev.opencircuit.codetalker.input.ButtonInput.RockerUp -> handler.onRockerUp()
                is dev.opencircuit.codetalker.input.ButtonInput.RockerDown -> handler.onRockerDown()
                is dev.opencircuit.codetalker.input.ButtonInput.Silence -> { /* not part of screen API */ }
            }
        } else {
            buttonRouter.handle(input)
        }
    })

    /** Screens call this on composition (and pass null on dispose) to claim
     *  the hardware buttons. Idempotent; last writer wins. */
    fun setScreenButtonHandler(h: dev.opencircuit.codetalker.input.CompanionButtonHandler?) {
        screenButtonHandler = h
    }

    // CCT-32 Task B.5: lifecycle hardening — screen on/off pause hooks
    // and network-change SSE reconnect tick.
    private val screenStateObserver = dev.opencircuit.codetalker.service.ScreenStateObserver()
    private var networkStateObserver: dev.opencircuit.codetalker.service.NetworkStateObserver? = null

    private var companionViewModel: CompanionViewModel? = null
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        // CCT-32 Task C.2 — installSplashScreen() must be called BEFORE
        // super.onCreate so the splash window is owned by the SplashScreen
        // compat library and dissolves cleanly into our Compose root.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pairingFlow = PairingFlow(this)
        appPreferences = AppPreferences.forContext(this)
        screenStateObserver.register(applicationContext)
        val cm = applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        networkStateObserver = dev.opencircuit.codetalker.service.NetworkStateObserver(cm).also { it.register() }

        // CCT-32 Task G.1 — initialize Sentry only if the user has opted
        // in. The init guard inside CrashReporter additionally checks the
        // build-config DSN; with empty DSN the SDK never starts even when
        // the toggle is on. Re-runs on every onCreate are no-ops thanks
        // to the idempotent guard.
        viewModelScope.launch {
            appPreferences.crashReportingEnabled.collectLatest { enabled ->
                if (enabled) {
                    CrashReporter.init(this@MainActivity, enabled = true)
                }
            }
        }

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
                        setScreenButtonHandler = ::setScreenButtonHandler,
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

@OptIn(
    kotlinx.coroutines.DelicateCoroutinesApi::class,
    androidx.media3.common.util.UnstableApi::class,
)
@Composable
private fun CompanionRoot(
    pairingFlow: PairingFlow,
    appPreferences: AppPreferences,
    registerViewModel: (CompanionViewModel?) -> Unit,
    setScreenButtonHandler: (dev.opencircuit.codetalker.input.CompanionButtonHandler?) -> Unit,
) {
    var pairing by remember { mutableStateOf<Pairing?>(pairingFlow.current()) }

    // CCT-32 Task A.6: simple two-screen state — list <-> detail. Using a
    // remembered SessionLite ref instead of NavHost keeps deps minimal
    // for v1.0 and avoids reload-on-pop.
    var selectedSession by remember { mutableStateOf<SessionLite?>(null) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    // 2026-05-11 — multi-active. The Set is the source of truth; the single
    // [activeSessionId] above is kept in sync as "primary" (first member by
    // lex order) for legacy callers. UI toggles via [toggleActive] below.
    var activeSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // CCT-32 Task B.4 / B.6 / C.5 — secondary screens reachable via long-press menu.
    var showingPreferences by remember { mutableStateOf(false) }
    var showingDiagnostics by remember { mutableStateOf(false) }
    var showingAbout by remember { mutableStateOf(false) }
    // v1.0 — Voice library (Piper catalog manager) + CodeTalkerChat
    // multi-session gallery. Both reachable from the long-press menu
    // entries added in PreferencesScreen + SessionListScreen.
    var showingVoiceLibrary by remember { mutableStateOf(false) }
    var showingCTC by remember { mutableStateOf(false) }

    // CCT-32 Task B.5: restore active session id from DataStore on launch
    // so process death doesn't drop the user into "no active session."
    // 2026-05-11 — restore the full SET (multi-active). Legacy single-slot
    // [persistedActiveId] kept as fallback for old installs.
    val persistedActiveId by appPreferences.activeSessionId.collectAsState(initial = null)
    val persistedActiveIds by appPreferences.activeSessionIds.collectAsState(initial = emptySet())
    androidx.compose.runtime.LaunchedEffect(persistedActiveIds, persistedActiveId) {
        if (activeSessionIds.isEmpty()) {
            when {
                persistedActiveIds.isNotEmpty() -> {
                    activeSessionIds = persistedActiveIds
                    activeSessionId = persistedActiveIds.sorted().firstOrNull()
                }
                persistedActiveId != null -> {
                    activeSessionIds = setOf(persistedActiveId!!)
                    activeSessionId = persistedActiveId
                }
            }
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

    // CCT-32 Task G.2 — first-launch consent dialog. Shows once after
    // onboarding when crash-reporting consent has not yet been asked.
    val consentFlow = remember(appPreferences) { ConsentFlow(appPreferences) }
    val showConsent by consentFlow.shouldShowConsent.collectAsState(initial = false)
    if (showConsent) {
        ConsentDialog(
            onAccept = {
                kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                    consentFlow.recordConsent(enabled = true)
                }
            },
            onDecline = {
                kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                    consentFlow.recordDecline()
                }
            },
        )
        // Don't return — the rest of the UI renders behind the dialog.
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
        // Phase 4 (2026-05-16) — open the /api/events SSE stream once
        // per pairing and shut it down when this composable leaves.
        // UI screens collect from daemonEvents.events to refresh
        // their state on push instead of waiting for the polling tick.
        val daemonEvents = remember(client) { DaemonEvents(client) }
        androidx.compose.runtime.DisposableEffect(daemonEvents) {
            daemonEvents.connect()
            onDispose { daemonEvents.close() }
        }
        // Phase 7 (2026-05-16) — AR HUD on Display 6 (XREAL One Pro).
        // Watches for an external display; when one attaches, opens a
        // Presentation that renders subtitle-style captions fed by the
        // daemon's /api/narration-stream SSE. When the display
        // detaches (glasses unplugged), dismisses the Presentation.
        // The host ComponentActivity acts as BOTH LifecycleOwner and
        // SavedStateRegistryOwner since AbstractComposeView attached
        // to the Presentation's content view requires both.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val compositionContext = androidx.compose.runtime.rememberCompositionContext()
        val glassesHud = remember(current) {
            val hostActivity = ctx as androidx.activity.ComponentActivity
            dev.opencircuit.codetalker.hud.GlassesHudController(
                appContext = hostActivity.applicationContext,
                daemonBaseUrl = current.daemonUrl,
                lifecycleOwner = hostActivity,
                savedStateOwner = hostActivity,
                compositionContext = compositionContext,
            )
        }
        androidx.compose.runtime.DisposableEffect(glassesHud) {
            glassesHud.start()
            onDispose { glassesHud.close() }
        }
        // Expose via CompositionLocal so deeply-nested screens can
        // collect without prop-drilling.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDaemonEvents provides daemonEvents,
            dev.opencircuit.codetalker.hud.LocalGlassesHudController provides glassesHud,
        ) {
        // 2026-05-11 — hydrate the local active set from the daemon on first
        // composition with this pairing. The daemon is the source of truth
        // for which sessions ANY paired device has activated; this lets
        // process-death recovery + pairing-restored launches pick up the
        // full N-member set instead of just the locally-persisted one.
        //
        // 2026-05-16 — bidirectional reconciliation. Previously this only
        // pulled (daemon → phone). On a fresh daemon restart (or after a
        // companion-active-sessions wipe), the daemon's set is empty but
        // the phone's DataStore still has yesterday's IDs. Result: phone
        // thinks session X is "Active", daemon's audio worker sees
        // companion_active_session="" and Strategy C drops every WAV →
        // total silence. The fix: when daemon's set is empty but phone
        // has restored IDs from DataStore, INTERSECT those with the
        // daemon's known sessions (dropping dead sids that no longer
        // exist) and PUSH the survivors back to the daemon so its audio
        // routing has a target. DataStore is updated to the cleaned set
        // so the stale entries don't keep coming back next launch.
        // 2026-05-16 -- Active-set reconciliation. Both the daemon's
        // companion-active list AND the phone's DataStore restored list
        // can carry stale sids across restarts. Always validate against
        // CURRENT recent activity (sessions touched in the last 60 min,
        // matching the SessionListScreen Active filter). Drop the rest
        // so the audio-subscriber set is clean and the phone doesn't
        // poll long-dead sessions.
        androidx.compose.runtime.LaunchedEffect(current) {
            try {
                val now = System.currentTimeMillis() / 1000.0
                val recencyWindowSec = 60 * 60.0
                val recentSids: Set<String> = try {
                    withContext(Dispatchers.IO) {
                        client.listSessions()
                            .filter { s ->
                                s.isLive ||
                                    (s.lastModified > 0.0 && (now - s.lastModified) < recencyWindowSec)
                            }
                            .map { it.sessionId }
                            .toSet()
                    }
                } catch (_: Throwable) { emptySet() }

                val remoteSet = withContext(Dispatchers.IO) { client.getActiveSessions() }
                val localSet = if (persistedActiveIds.isNotEmpty()) {
                    persistedActiveIds
                } else if (persistedActiveId != null) {
                    setOf(persistedActiveId!!)
                } else emptySet()

                // Union both sources, then intersect with recent. The
                // union preserves any sid either side knows about; the
                // intersection drops anything older than 60 minutes.
                val union = remoteSet + localSet
                val cleaned = if (recentSids.isNotEmpty()) union.intersect(recentSids) else emptySet()

                // For sids the daemon currently has active but should NOT
                // (stale, outside recency window), explicitly deactivate
                // so the daemon's companion_active_sessions also stays
                // clean across restarts.
                val toDeactivate = remoteSet - cleaned
                for (sid in toDeactivate) {
                    try { withContext(Dispatchers.IO) { client.toggleActiveSession(sid, false) } } catch (_: Throwable) {}
                }
                // For sids in the cleaned set that the daemon isn't yet
                // tracking, push them up so audio routing has them.
                val toActivate = cleaned - remoteSet
                for (sid in toActivate) {
                    try { withContext(Dispatchers.IO) { client.toggleActiveSession(sid, true) } } catch (_: Throwable) {}
                }

                activeSessionIds = cleaned
                activeSessionId = cleaned.sorted().firstOrNull()
                appPreferences.setActiveSessionIds(cleaned)
            } catch (_: Throwable) {
                // Daemon unreachable on first try is fine -- DataStore
                // fallback already populated the local set from
                // persistedActiveIds.
            }
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
                // 2026-05-12 — direct-STT delivery: POST transcript to
                // /api/companion/direct-stt, daemon SendKeys-types into
                // the OS-foreground CC window. Fire-and-forget on the
                // viewModel side; reply auto-narrates via the existing
                // hook pipeline.
                directStt = { sid, text -> client.postDirectStt(sid, text) },
            ).also { vm ->
                registerViewModel(vm)
            }
        }
        // Track the active session id in the VM whenever the user picks one.
        androidx.compose.runtime.LaunchedEffect(activeSessionId) {
            viewModel.activeSessionId.value = activeSessionId
        }
        // CCT-32 v0.1.0 polish — instantiate TTSPlayer and start streaming
        // the active session's audio. Previously the class existed but was
        // never invoked, so the user only heard narration on the desktop.
        val ttsPlayer = remember(current) {
            dev.opencircuit.codetalker.audio.TTSPlayer(
                context = context.applicationContext,
                daemonClient = client,
                pairingToken = current.pairingToken,
            )
        }
        // 2026-05-11 — multi-active. Tell TTSPlayer the full set; it manages
        // N parallel pollers and sequential playback through one ExoPlayer.
        // Compose re-runs this whenever the set changes (any toggle).
        androidx.compose.runtime.DisposableEffect(activeSessionIds) {
            ttsPlayer.setActiveSessions(activeSessionIds)
            onDispose {
                ttsPlayer.stop()
            }
        }
        if (showingDiagnostics) {
            dev.opencircuit.codetalker.ui.DiagnosticsScreen(
                daemonClient = client,
                appPreferences = appPreferences,
                onBack = { showingDiagnostics = false },
            )
            return@CompositionLocalProvider
        }
        if (showingAbout) {
            dev.opencircuit.codetalker.ui.AboutScreen(
                onBack = { showingAbout = false },
            )
            return@CompositionLocalProvider
        }
        if (showingVoiceLibrary) {
            dev.opencircuit.codetalker.ui.VoiceLibraryScreen(
                daemonClient = client,
                onBack = { showingVoiceLibrary = false },
            )
            return@CompositionLocalProvider
        }
        if (showingCTC) {
            dev.opencircuit.codetalker.ui.CTCGalleryScreen(
                daemonClient = client,
                activeSessionId = activeSessionId,
                onBack = { showingCTC = false },
                onSelectSession = { sid ->
                    // CTC keeps a single-spotlight "focused" session — when
                    // the user taps a tile we toggle that sid into the active
                    // set rather than replacing it, so other active sessions
                    // keep streaming.
                    val nowActive = sid !in activeSessionIds
                    activeSessionIds = if (nowActive) activeSessionIds + sid
                                       else activeSessionIds - sid
                    activeSessionId = activeSessionIds.sorted().firstOrNull()
                    GlobalScope.launch(Dispatchers.IO) {
                        try { client.toggleActiveSession(sid, nowActive) } catch (_: Throwable) {}
                        appPreferences.setActiveSessionIds(activeSessionIds)
                    }
                },
            )
            return@CompositionLocalProvider
        }
        if (showingPreferences) {
            PreferencesScreen(
                appPreferences = appPreferences,
                onBack = { showingPreferences = false },
                daemonClient = client,
                onOpenDiagnostics = {
                    showingPreferences = false
                    showingDiagnostics = true
                },
                onOpenAbout = {
                    showingPreferences = false
                    showingAbout = true
                },
                onOpenVoiceLibrary = {
                    showingPreferences = false
                    showingVoiceLibrary = true
                },
            )
            return@CompositionLocalProvider
        }
        val selected = selectedSession
        if (selected != null) {
            SessionDetailScreen(
                session = selected,
                daemonClient = client,
                isActive = selected.sessionId in activeSessionIds,
                onSetActive = {
                    // Multi-active toggle: add/remove this sid from the set
                    // and tell the daemon to mirror the change. Other members
                    // are preserved.
                    val sid = selected.sessionId
                    val nowActive = sid !in activeSessionIds
                    activeSessionIds = if (nowActive) activeSessionIds + sid
                                       else activeSessionIds - sid
                    activeSessionId = activeSessionIds.sorted().firstOrNull()
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            client.toggleActiveSession(sid, nowActive)
                        } catch (_: Throwable) { /* surfaced via reload */ }
                        appPreferences.setActiveSessionIds(activeSessionIds)
                    }
                },
                onBack = { selectedSession = null },
                companionViewModel = viewModel,
                setScreenButtonHandler = setScreenButtonHandler,
            )
        } else {
            SessionListScreen(
                daemonClient = client,
                activeSessionId = activeSessionId,
                activeSessionIds = activeSessionIds,
                appPreferences = appPreferences,
                onSelect = { selectedSession = it },
                onSetActive = { session ->
                    val sid = session.sessionId
                    val nowActive = sid !in activeSessionIds
                    activeSessionIds = if (nowActive) activeSessionIds + sid
                                       else activeSessionIds - sid
                    activeSessionId = activeSessionIds.sorted().firstOrNull()
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            client.toggleActiveSession(sid, nowActive)
                        } catch (_: Throwable) { /* surfaced via reload */ }
                        appPreferences.setActiveSessionIds(activeSessionIds)
                    }
                },
                onUnpair = {
                    pairingFlow.clear()
                    pairing = null
                    selectedSession = null
                    activeSessionId = null
                    activeSessionIds = emptySet()
                    GlobalScope.launch(Dispatchers.IO) {
                        appPreferences.setActiveSessionIds(emptySet())
                    }
                },
                onLongPressMenu = { showingPreferences = true },
                onOpenCTC = { showingCTC = true },
                // 2026-05-11 Tier-B — bulk auto-subscribe to sessions the
                // daemon flagged as audio_misaligned. Collapses the
                // dual-state UX (audio_outputs + active_session_ids) into
                // one: configure phone audio for a session and the phone
                // subscribes automatically. Persists to AppPreferences so
                // the change survives app restart.
                onAutoSubscribeMissing = { missingIds ->
                    if (missingIds.isNotEmpty()) {
                        val newActive = activeSessionIds + missingIds
                        activeSessionIds = newActive
                        activeSessionId = newActive.sorted().firstOrNull()
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                missingIds.forEach { sid ->
                                    client.toggleActiveSession(sid, true)
                                }
                            } catch (_: Throwable) { /* surfaced via reload */ }
                            appPreferences.setActiveSessionIds(newActive)
                        }
                    }
                },
                setScreenButtonHandler = setScreenButtonHandler,
            )
        }
        }
    }
}
