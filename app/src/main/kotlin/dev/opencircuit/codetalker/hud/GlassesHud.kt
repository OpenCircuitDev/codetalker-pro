package dev.opencircuit.codetalker.hud

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Phase 7 (2026-05-16) — AR/XR caption HUD for XREAL One Pro glasses.
 *
 * Android's Presentation API renders Compose content on a secondary
 * display. When the user plugs in the XREAL glasses (Display 6 on
 * the Beam Pro), this class opens a Presentation that shows daemon
 * narration as subtitle-style captions in the user's field of view.
 *
 * Scope: caption rendering only. Avatar/character mesh render is a
 * future task (Phase 7.1) — the schema already carries character
 * data, but Filament/SceneView integration is out of scope for this
 * milestone.
 *
 * Lifecycle:
 *   1. MainActivity creates one [GlassesHudController] per app
 *      process (or per pairing) and wires it to the
 *      DisplayManager.DisplayListener.
 *   2. When an external display attaches, controller.show() opens
 *      this Presentation.
 *   3. When the display detaches, controller.hide() dismisses it.
 *   4. The Presentation subscribes to /api/narration-stream SSE
 *      directly (no token required — narration stream is daemon-
 *      host-bound, same model the SessionDetail Feed panel uses).
 */
class GlassesHud(
    outerContext: Context,
    display: Display,
    private val daemonBaseUrl: String,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateOwner: SavedStateRegistryOwner,
    private val compositionContext: CompositionContext,
) : Presentation(outerContext, display) {

    private var sseSource: EventSource? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // 0 = infinite, required for SSE
        .build()

    /** Newest narration text. Empty = no recent narration, caption
     *  area renders blank. */
    private var captionText by mutableStateOf("")
    /** Voice tag for sub-caption ("OCR-Web:", "BPF-Web:", etc.). */
    private var captionTag by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compose-on-Presentation requires that the ContentView's view
        // tree carries a LifecycleOwner + SavedStateRegistryOwner +
        // CompositionContext so AbstractComposeView can render.
        val view = ComposeView(context).apply {
            setParentCompositionContext(compositionContext)
            setContent {
                GlassesCaptionLayout(
                    captionText = captionText,
                    captionTag = captionTag,
                )
            }
        }
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(savedStateOwner)
        setContentView(view)
        // Always-on dim for HUD readability without overpowering the
        // surrounding world view.
        window?.attributes = window?.attributes?.apply {
            flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }

        connectNarrationStream()
    }

    override fun onStop() {
        super.onStop()
        sseSource?.cancel()
        sseSource = null
    }

    private fun connectNarrationStream() {
        if (sseSource != null) return
        val req = Request.Builder()
            .url("$daemonBaseUrl/api/narration-stream")
            .get()
            .build()
        val factory = EventSources.createFactory(httpClient)
        sseSource = factory.newEventSource(req, object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                val payload = try { JSONObject(data) } catch (_: Exception) { return }
                val status = payload.optString("status", "")
                // Only "speaking" events carry user-meaningful text; queued/
                // skipped/done are state-machine transitions. Future:
                // surface "skipped" with a fade-out cue for diagnostic UX.
                if (status != "speaking") return
                val text = payload.optString("text", "").ifBlank { return }
                val voice = payload.optString("voice", "").ifBlank { "" }
                captionText = text
                captionTag = voice
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                sseSource = null
                // No retry here — controller restarts the HUD if the
                // display is still attached.
            }
        })
    }
}


/**
 * Caption layout for the HUD. Bottom-third of the display reserved
 * for caption text so the user's central field of view stays clear
 * for the world behind the glasses.
 */
@Composable
private fun GlassesCaptionLayout(
    captionText: String,
    captionTag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        if (captionText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x80000000)) // 50% black scrim for legibility
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (captionTag.isNotBlank()) {
                    Text(
                        text = captionTag,
                        color = Color(0xFFFFD166),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                    )
                }
                Text(
                    text = captionText,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}


/**
 * Returns the first non-default display the system knows about, if
 * any. The XREAL One Pro shows up as a virtual display on the Beam
 * Pro at index 6 in the user's hardware kit; on other devices the
 * exact index varies, so we don't hard-code it — any non-default
 * display is treated as the HUD target.
 */
fun findExternalDisplay(context: Context): Display? {
    return try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    } catch (_: Throwable) {
        null
    }
}
