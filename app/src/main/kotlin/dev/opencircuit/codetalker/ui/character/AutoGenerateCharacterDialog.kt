package dev.opencircuit.codetalker.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import dev.opencircuit.codetalker.net.CharacterDraft
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.EMOTIVE_STATE_ORDER
import dev.opencircuit.codetalker.viewmodel.AutoGenerateCharacterViewModel
import kotlinx.coroutines.launch

/**
 * Auto-generate character — preview/edit/save flow.
 *
 * Mirrors the webui's CharacterDraftModal. Flow:
 *   1. open from "Auto-generate" button in SessionDetailScreen
 *   2. ViewModel fires generateCharacterDraft (preview=true on daemon)
 *   3. dialog renders editable form with all fields pre-filled
 *   4. user edits any field; tap Regenerate to ask LLM again (warns
 *      if any state textarea has content — would be lost)
 *   5. tap Save & Attach → createCharacter + attachCharacter
 *
 * Daemon side: relies on `?preview=true` mode of the
 * /api/sessions/{id}/generate-character endpoint, which returns the
 * draft WITHOUT writing it to the character store.
 */
@Composable
fun AutoGenerateCharacterDialog(
    daemonClient: DaemonClient,
    sessionId: String,
    sessionLabel: String,
    onDismiss: () -> Unit,
    onAttached: () -> Unit,
) {
    val viewModel = remember(daemonClient, sessionId) {
        AutoGenerateCharacterViewModel(daemonClient, sessionId)
    }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.generateInitial()
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("Regenerate?") },
            text = {
                Text(
                    "This will replace every field with a fresh LLM draft. " +
                        "Any edits you've made to the emotive states will be lost.",
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateConfirm = false
                    scope.launch { viewModel.regenerate() }
                }) { Text("Regenerate") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 600.dp)
                .background(Color(0xFF0A0B10), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Generated character — review & edit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                    )
                    Text(
                        "for session: $sessionLabel",
                        fontSize = 11.sp,
                        color = Color(0xFF67E8F9),
                    )
                }

                // Body
                if (state.draft == null && state.loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF67E8F9))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Designing character for \"$sessionLabel\"…",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                } else if (state.draft != null) {
                    DraftFormBody(
                        draft = state.draft!!,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        onChange = viewModel::updateDraft,
                    )
                }

                state.error?.let { err ->
                    Text(
                        err,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // Footer actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.saving,
                    ) { Text("Cancel") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val anyEdited = state.draft?.emotiveStates?.any {
                                it.value.isNotBlank()
                            } == true
                            if (anyEdited) {
                                showRegenerateConfirm = true
                            } else {
                                scope.launch { viewModel.regenerate() }
                            }
                        },
                        enabled = !state.saving && !state.regenerating,
                    ) {
                        Text(
                            if (state.regenerating) "Regenerating…" else "↻ Regenerate",
                            color = Color(0xFFC4B5FD),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.saveAndAttach { onAttached(); onDismiss() }
                            }
                        },
                        enabled = !state.saving &&
                            !state.regenerating &&
                            (state.draft?.displayName?.isNotBlank() == true),
                    ) {
                        Text(
                            if (state.saving) "Saving…" else "Save & attach",
                            color = Color(0xFF67E8F9),
                        )
                    }
                }
            }
        }
    }
}

private val PERSONAS = listOf("methodical", "warm", "technical", "plain", "sarcastic", "energetic")

@Composable
private fun DraftFormBody(
    draft: CharacterDraft,
    modifier: Modifier,
    onChange: (CharacterDraft) -> Unit,
) {
    var personaMenuOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        SectionHeader("Identity")
        OutlinedTextField(
            value = draft.displayName,
            onValueChange = { onChange(draft.copy(displayName = it)) },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = draft.id,
            onValueChange = { onChange(draft.copy(id = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-"))) },
            label = { Text("ID (kebab-case)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { personaMenuOpen = true }) {
                Text("Persona: ${draft.persona}")
            }
            DropdownMenu(
                expanded = personaMenuOpen,
                onDismissRequest = { personaMenuOpen = false },
            ) {
                PERSONAS.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p) },
                        onClick = {
                            onChange(draft.copy(persona = p))
                            personaMenuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = draft.voiceRef,
            onValueChange = { onChange(draft.copy(voiceRef = it)) },
            label = { Text("Voice ref") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader("Mesh prompt (waist-up talking-head)")
        OutlinedTextField(
            value = draft.meshPrompt ?: "",
            onValueChange = { onChange(draft.copy(meshPrompt = it.ifBlank { null })) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader("Emotive states")
        EMOTIVE_STATE_ORDER.forEach { s ->
            Text(
                s.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = draft.emotiveStates[s] ?: "",
                onValueChange = { v ->
                    onChange(draft.copy(emotiveStates = draft.emotiveStates + (s to v)))
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
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
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
