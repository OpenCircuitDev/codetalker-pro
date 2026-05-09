package dev.opencircuit.codetalker.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * CCT-31 Phase 5 — REST + SSE wrapper for the codetalker daemon.
 *
 * Every request carries the X-CCT-Pairing-Token header. Tokens come from
 * the dashboard's "Pair AR Companion" QR generator and are persisted in
 * EncryptedSharedPreferences by [PairingFlow].
 *
 * Reconnection / error policy is centralized in [ConnectionGuard]; this
 * class throws on transport errors and lets the guard decide whether to
 * retry, back off, or notify the user. Keeping the policy out of the
 * wire layer makes both layers individually testable.
 */
class DaemonClient(
    private val baseUrl: String,
    private val pairingToken: String,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    companion object {
        const val HEADER_TOKEN = "X-CCT-Pairing-Token"
        private val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 0 == infinite for SSE
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun buildBase(path: String): Request.Builder =
        Request.Builder()
            .url("$baseUrl$path")
            .header(HEADER_TOKEN, pairingToken)

    /** GET /api/companion/sessions */
    fun listSessions(): List<SessionLite> {
        val req = buildBase("/api/companion/sessions").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("listSessions HTTP ${resp.code}")
            val body = resp.body?.string() ?: "[]"
            val arr = JSONArray(body)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SessionLite(
                    sessionId = o.getString("session_id"),
                    displayName = o.optString("display_name", o.getString("session_id").take(8)),
                    isLive = o.optBoolean("is_live", false),
                )
            }
        }
    }

    /** POST /api/companion/active-session { session_id } */
    fun setActiveSession(sessionId: String) {
        val body = JSONObject().put("session_id", sessionId).toString().toRequestBody(JSON)
        val req = buildBase("/api/companion/active-session").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("setActiveSession HTTP ${resp.code}")
        }
    }

    /** POST /api/companion/start-buddy { user_session_id } */
    fun startBuddy(userSessionId: String): String {
        val body = JSONObject().put("user_session_id", userSessionId).toString().toRequestBody(JSON)
        val req = buildBase("/api/companion/start-buddy").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("startBuddy HTTP ${resp.code}: ${resp.body?.string()}")
            val o = JSONObject(resp.body?.string() ?: "{}")
            return o.getString("buddy_id")
        }
    }

    /** POST /api/companion/inject { buddy_id, text } returns SSE EventSource */
    fun inject(buddyId: String, text: String, listener: EventSourceListener): EventSource {
        val body = JSONObject().put("buddy_id", buddyId).put("text", text).toString().toRequestBody(JSON)
        val req = buildBase("/api/companion/inject").post(body).build()
        val factory = EventSources.createFactory(httpClient)
        return factory.newEventSource(req, listener)
    }

    /** GET /api/companion/screen-frame/{kind} returns raw JPEG bytes or null on 503/404 */
    fun captureScreenFrame(kind: String = "fullscreen"): ByteArray? {
        val req = buildBase("/api/companion/screen-frame/$kind").build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code in listOf(404, 503)) return null
            if (!resp.isSuccessful) throw IOException("captureScreenFrame HTTP ${resp.code}")
            return resp.body?.bytes()
        }
    }

    /** Returns the URL to subscribe to for raw audio frames; the caller
     *  hands this to ExoPlayer/MediaSource. */
    fun audioStreamUrl(sessionId: String): String =
        "$baseUrl/api/companion/audio-stream/$sessionId"
}

data class SessionLite(
    val sessionId: String,
    val displayName: String,
    val isLive: Boolean,
)
