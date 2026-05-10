package dev.opencircuit.codetalker.ui.markup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.opencircuit.codetalker.net.MarkupTreatment

/**
 * CCT-32 Task A.4 — markup quick panel.
 *
 * Six markup forms, three categories. Each form has a per-spec list of
 * allowed kinds — the daemon's narration pipeline (`narration_kinds`)
 * is the source of truth for which kinds make sense for which forms.
 *
 * Pure data catalog so tests don't need Compose.
 */
object MarkupQuickCatalog {
    data class Group(val title: String, val hint: String, val forms: List<String>)
    data class FormSpec(val name: String, val label: String, val allowedKinds: List<String>)

    val GROUPS = listOf(
        Group(
            title = "Listen density",
            hint = "Biggest verbosity dials.",
            forms = listOf("code_fence", "tool_output"),
        ),
        Group(
            title = "Inline detail",
            hint = "How aggressively short references get read.",
            forms = listOf("inline_code", "file_path"),
        ),
        Group(
            title = "Structural pauses",
            hint = "How multi-line structures get summarised.",
            forms = listOf("todo_update", "plan_block"),
        ),
    )

    val FORMS = mapOf(
        "code_fence" to FormSpec(
            "code_fence", "Code blocks", listOf("skip", "describe", "read"),
        ),
        "tool_output" to FormSpec(
            "tool_output", "Tool output", listOf("skip", "describe", "read"),
        ),
        "inline_code" to FormSpec(
            "inline_code", "Inline `code`", listOf("skip", "identifier_only", "read"),
        ),
        "file_path" to FormSpec(
            "file_path", "File paths", listOf("skip", "filename", "describe", "read"),
        ),
        "todo_update" to FormSpec(
            "todo_update", "Todo updates", listOf("skip", "count_only", "itemize", "read"),
        ),
        "plan_block" to FormSpec(
            "plan_block", "Plan blocks", listOf("skip", "summarize", "read"),
        ),
    )
}

@Composable
fun MarkupQuickPanel(
    current: Map<String, MarkupTreatment>,
    modifier: Modifier = Modifier,
    onChange: (formName: String, kind: String) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MarkupQuickCatalog.GROUPS.forEach { group ->
            Text(
                group.title.uppercase(),
                fontSize = 11.sp,
                color = Color(0xFFA8AEC0),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                group.hint,
                fontSize = 11.sp,
                color = Color(0xFF8B91A0),
            )
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    group.forms.forEachIndexed { idx, formName ->
                        val spec = MarkupQuickCatalog.FORMS[formName]!!
                        val kind = current[formName]?.kind ?: spec.allowedKinds.first()
                        MarkupRow(spec = spec, kind = kind, onChange = { newKind ->
                            onChange(formName, newKind)
                        })
                        if (idx < group.forms.lastIndex) Spacer(Modifier.height(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MarkupRow(
    spec: MarkupQuickCatalog.FormSpec,
    kind: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(spec.label, fontSize = 13.sp)
        Box {
            Text(
                kind,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2D3142))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                spec.allowedKinds.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(k) },
                        onClick = {
                            expanded = false
                            onChange(k)
                        },
                    )
                }
            }
        }
    }
}
