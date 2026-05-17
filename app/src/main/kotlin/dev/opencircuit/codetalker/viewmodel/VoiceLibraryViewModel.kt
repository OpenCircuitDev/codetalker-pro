package dev.opencircuit.codetalker.viewmodel

import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.PiperCatalogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Voice library state machine. Wraps four daemon calls:
 *   - getPiperCatalog        → list with installed flags
 *   - installPiperVoice      → download
 *   - uninstallPiperVoice    → delete
 *   - previewPiperVoice      → synthesize + (daemon plays on its host)
 *
 * Tracks per-row busy state (a row can be installing, removing, or
 * previewing at a time) so the UI can disable buttons independently
 * without blocking other rows.
 *
 * All calls fire on Dispatchers.IO and update state on the same scope.
 * The UI collects `uiState` and re-renders on each change.
 */
class VoiceLibraryViewModel(
    private val daemonClient: DaemonClient,
) {
    enum class RowAction { INSTALL, UNINSTALL, PREVIEW }

    data class UiState(
        val loading: Boolean = true,
        val entries: List<PiperCatalogEntry> = emptyList(),
        val busy: Map<String, RowAction> = emptyMap(),
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    suspend fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val entries = daemonClient.getPiperCatalog()
            _state.update { it.copy(loading = false, entries = entries) }
        } catch (e: Throwable) {
            _state.update {
                it.copy(loading = false, error = "Failed to load catalog: ${e.message}")
            }
        }
    }

    suspend fun install(name: String) = withBusy(name, RowAction.INSTALL) {
        daemonClient.installPiperVoice(name)
        refresh()
    }

    suspend fun uninstall(name: String) = withBusy(name, RowAction.UNINSTALL) {
        daemonClient.uninstallPiperVoice(name)
        refresh()
    }

    suspend fun preview(name: String) = withBusy(name, RowAction.PREVIEW) {
        // Daemon-side playback is the canonical behavior (matches webui).
        // We receive WAV bytes here but ignore them — Android-side
        // playback would be a future enhancement (e.g. for users who
        // want to preview on the phone speaker before installing).
        daemonClient.previewPiperVoice(name)
    }

    /** Wrap a single daemon call with per-row busy tracking + error
     *  capture. Refresh after success so the list reflects the new
     *  installed-state immediately. */
    private suspend inline fun withBusy(
        name: String,
        action: RowAction,
        crossinline block: suspend () -> Unit,
    ) {
        _state.update { it.copy(busy = it.busy + (name to action), error = null) }
        try {
            block()
        } catch (e: Throwable) {
            _state.update { it.copy(error = "$action failed for $name: ${e.message}") }
        } finally {
            _state.update { it.copy(busy = it.busy - name) }
        }
    }

    /** Launch a daemon call from a suspending context the UI already has
     *  (e.g. rememberCoroutineScope) without exposing the scope. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}
