package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencircuit.codetalker.CompanionViewModel

/**
 * CCT-32 v0.1.0 polish — SessionDetail Chat tab.
 *
 * Text-input alternative to the front-button hold-to-talk STT path. Sends
 * typed messages to the buddy via CompanionViewModel.injectText() and
 * displays the conversation history along with the live caption when the
 * buddy is generating a reply.
 */
data class ChatMessage(val role: String, val text: String)

@Composable
fun BuddyChatPanel(
    viewModel: CompanionViewModel?,
) {
    val history = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    val captionText by (viewModel?.captionText?.collectAsState() ?: remember { mutableStateOf("") }.let { state -> state })
    val lastFinal by (viewModel?.lastFinalText?.collectAsState() ?: remember { mutableStateOf<String?>(null) }.let { state -> state })

    // Each new final_text from the buddy is a new buddy turn — push it into
    // history so the conversation persists in this UI even after the live
    // caption clears.
    LaunchedEffect(lastFinal) {
        val text = lastFinal
        if (!text.isNullOrBlank()) {
            val tail = history.lastOrNull()
            if (tail?.role != "buddy" || tail.text != text) {
                history.add(ChatMessage("buddy", text))
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        if (history.isEmpty() && captionText.isBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Type to chat with the buddy, or press-and-hold the front button to talk.",
                    color = Color(0xFF8B91A0),
                    fontSize = 12.sp,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(history) { msg -> ChatBubble(msg) }
        }
        // Live caption while the buddy is streaming a reply.
        val captionVisible = captionText.isNotBlank() &&
            (history.lastOrNull()?.text != captionText)
        if (captionVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(8.dp),
            ) {
                Text(
                    captionText,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…", fontSize = 12.sp) },
                singleLine = false,
                maxLines = 3,
            )
            Spacer(Modifier.width(6.dp))
            Button(
                enabled = input.isNotBlank() && viewModel != null,
                onClick = {
                    val text = input.trim()
                    if (text.isBlank()) return@Button
                    history.add(ChatMessage("user", text))
                    viewModel?.injectText(text)
                    input = ""
                },
            ) { Text("Send", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val bg = if (isUser) Color(0xFF1E40AF) else Color(0xFF374151)
    val fg = if (isUser) Color(0xFFE0E7FF) else Color(0xFFE5E7EB)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(msg.text, color = fg, fontSize = 12.sp)
        }
    }
}
