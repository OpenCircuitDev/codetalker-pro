package dev.opencircuit.codetalker.ui.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.net.DaemonClient
import dev.opencircuit.codetalker.net.VoiceLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CCT-32 Task A.3 — voice dropdown.
 *
 * v1 model: only the engines we have voices for at runtime get rendered.
 * The daemon's /api/voices?engine=<name> is per-engine, so the picker
 * fans out to each engine and concatenates the results, prefixing the
 * engine name when there's ambiguity.
 *
 * Special case: voice models prefixed `char-` are character-cloned voices
 * (Phase 25c). These render with the persona color and a "Character voice"
 * suffix so users don't accidentally pick one without realising it's
 * cloned. Picking one still PUTs voice.engine + voice.model.
 */
@Composable
fun VoicePicker(
    currentEngine: String?,
    currentModel: String?,
    daemonClient: DaemonClient,
    modifier: Modifier = Modifier,
    onChange: (engine: String, model: String) -> Unit,
) {
    var voices by remember { mutableStateOf<List<VoiceLite>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            // CCT-32: probe each engine the daemon might have. Ignore
            // failures — different installs ship different engines.
            val combined = mutableListOf<VoiceLite>()
            for (engine in ENGINES_TO_PROBE) {
                try {
                    combined += withContext(Dispatchers.IO) { daemonClient.listVoices(engine) }
                } catch (_: Throwable) { /* engine not registered */ }
            }
            voices = combined
        } catch (e: Throwable) {
            loadError = e.message ?: "voices load failed"
        }
    }

    val currentLabel = when {
        currentModel == null -> "Default voice"
        currentModel.startsWith("char-") -> "Character voice (${currentModel.removePrefix("char-")})"
        else -> currentModel
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentLabel, modifier = Modifier.weight(1f))
                Text("▾", fontSize = 14.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (loadError != null) {
                DropdownMenuItem(
                    text = { Text("Could not load: $loadError", color = Color(0xFFFB7185)) },
                    onClick = { expanded = false },
                )
            } else if (voices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No voices available") },
                    onClick = { expanded = false },
                )
            } else {
                voices.forEach { v ->
                    val isCharacter = v.model.startsWith("char-")
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    if (isCharacter) "Character: ${v.model.removePrefix("char-")}"
                                    else v.displayName,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${v.engine} / ${v.model}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF8B91A0),
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onChange(v.engine, v.model)
                        },
                    )
                }
            }
        }
    }
}

internal val ENGINES_TO_PROBE = listOf("piper", "edge", "elevenlabs", "openai", "xtts")
