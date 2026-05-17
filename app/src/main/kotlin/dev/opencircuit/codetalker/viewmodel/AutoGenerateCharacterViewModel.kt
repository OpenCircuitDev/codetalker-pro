package dev.opencircuit.codetalker.viewmodel

import dev.opencircuit.codetalker.net.CharacterDraft
import dev.opencircuit.codetalker.net.DaemonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * State machine for the auto-generate character flow. Wraps:
 *   - generateCharacterDraft(sessionId)        — daemon LLM, no save
 *   - createCharacter(draft)                   — POST /api/characters
 *   - attachCharacter(sessionId, characterId)  — attach + persist
 *
 * The UI binds to [uiState]; calls into [generateInitial], [regenerate],
 * [updateDraft], and [saveAndAttach]. Errors land in `error`; the
 * dialog renders them inline.
 */
class AutoGenerateCharacterViewModel(
    private val daemonClient: DaemonClient,
    private val sessionId: String,
) {
    data class UiState(
        val loading: Boolean = true,
        val regenerating: Boolean = false,
        val saving: Boolean = false,
        val draft: CharacterDraft? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    /** Fire the daemon's LLM the first time the dialog opens. */
    suspend fun generateInitial() {
        if (_state.value.draft != null) return  // already populated
        try {
            val draft = withContext(Dispatchers.IO) {
                daemonClient.generateCharacterDraft(sessionId)
            }
            _state.update { it.copy(loading = false, draft = draft, error = null) }
        } catch (e: Throwable) {
            _state.update { it.copy(loading = false, error = "Generate failed: ${e.message}") }
        }
    }

    /** Re-fire the LLM. UI confirms-with-warning when user has edits. */
    suspend fun regenerate() {
        _state.update { it.copy(regenerating = true, error = null) }
        try {
            val draft = withContext(Dispatchers.IO) {
                daemonClient.generateCharacterDraft(sessionId)
            }
            _state.update { it.copy(regenerating = false, draft = draft) }
        } catch (e: Throwable) {
            _state.update {
                it.copy(regenerating = false, error = "Regenerate failed: ${e.message}")
            }
        }
    }

    fun updateDraft(next: CharacterDraft) {
        _state.update { it.copy(draft = next) }
    }

    /** Commit: POST /api/characters → POST attach-character. Calls
     *  [onSuccess] only after both succeed. */
    suspend fun saveAndAttach(onSuccess: () -> Unit) {
        val draft = _state.value.draft ?: return
        if (draft.displayName.isBlank() || draft.id.isBlank()) {
            _state.update { it.copy(error = "Display name and ID are required.") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        try {
            withContext(Dispatchers.IO) {
                daemonClient.createCharacter(draft)
                daemonClient.attachCharacter(sessionId, draft.id)
            }
            _state.update { it.copy(saving = false) }
            onSuccess()
        } catch (e: Throwable) {
            _state.update { it.copy(saving = false, error = "Save failed: ${e.message}") }
        }
    }
}
