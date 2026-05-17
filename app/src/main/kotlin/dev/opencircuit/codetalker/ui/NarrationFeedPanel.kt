package dev.opencircuit.codetalker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * CCT-32 v0.1.0 polish — SessionDetail Feed tab.
 *
 * Subscribes to the daemon's `/api/narration-stream` SSE (unauthenticated by
 * design — narration is daemon-host-bound, not companion-token-bound) and
 * renders the events for this specific session_id in newest-first order.
 *
 * Same source the webui consumes via `useNarrationStream`; the only thing
 * different here is filter-by-session at the client and a Compose-friendly
 * render.
 */
data class NarrationEvent(
    val id: String,
    val kind: String,
    val text: String,
    val tsMs: Long,
    val sessionId: String?,
)

@Composable
fun NarrationFeedPanel(
    daemonBaseUrl: String,
    sessionId: String,
    maxEvents: Int = 50,
) {
    val events = remember(sessionId) { mutableStateListOf<NarrationEvent>() }
    val listState = rememberLazyListState()

    DisposableEffect(daemonBaseUrl, sessionId) {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("${daemonBaseUrl.trimEnd('/')}/api/narration-stream")
            .header("Accept", "text/event-stream")
            .build()
        val source = EventSources.createFactory(client).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val o = JSONObject(data)
                    val sid = if (o.has("session_id") && !o.isNull("session_id")) o.getString("session_id") else null
                    if (sid != null && sid != sessionId) return
                    val ev = NarrationEvent(
                        id = id ?: "${System.currentTimeMillis()}-${events.size}",
                        kind = o.optString("kind", "system"),
                        text = o.optString("text", ""),
                        tsMs = (o.optDouble("ts", System.currentTimeMillis() / 1000.0) * 1000).toLong(),
                        sessionId = sid,
                    )
                    // Insert at index 0 (newest first), trim tail.
                    events.add(0, ev)
                    while (events.size > maxEvents) events.removeAt(events.size - 1)
                } catch (_: Throwable) {
                    // ignore malformed events
                }
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // EventSource will be retried by the screen-level recompose loop;
                // here we just stop appending until reconnection.
            }
        })
        onDispose { source.cancel() }
    }

    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Waiting for narration events from this session…",
                color = Color(0xFF8B91A0),
                fontSize = 12.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(events, key = { it.id }) { ev ->
            EventRow(ev)
        }
    }
}

@Composable
private fun EventRow(ev: NarrationEvent) {
    val (chipBg, chipFg) = kindColors(ev.kind)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(chipBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                ev.kind.uppercase(),
                color = chipFg,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            ev.text.ifBlank { "—" },
            color = Color(0xFFE5E7EB),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
    }
}

private fun kindColors(kind: String): Pair<Color, Color> = when (kind.lowercase()) {
    "speak" -> Color(0xFF065F46) to Color(0xFFA7F3D0)
    "tool" -> Color(0xFF155E75) to Color(0xFFA5F3FC)
    "subagent" -> Color(0xFF5B21B6) to Color(0xFFDDD6FE)
    "error" -> Color(0xFF9F1239) to Color(0xFFFECDD3)
    "system" -> Color(0xFF374151) to Color(0xFFD1D5DB)
    else -> Color(0xFF334155) to Color(0xFFCBD5E1)
}
