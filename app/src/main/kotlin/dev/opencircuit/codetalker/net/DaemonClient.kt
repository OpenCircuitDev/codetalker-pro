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
                val char = o.optJSONObject("attached_character")?.let { c ->
                    AttachedCharacter(
                        id = c.optString("id", ""),
                        displayName = c.optString("display_name", c.optString("id", "?")),
                        persona = c.optString("persona", "").ifBlank { null },
                        voiceRef = c.optString("voice_ref", "").ifBlank { null },
                        meshPath = c.optString("mesh_path", "").ifBlank { null },
                    )
                }
                SessionLite(
                    sessionId = o.getString("session_id"),
                    displayName = o.optString("display_name", o.getString("session_id").take(8)),
                    isLive = o.optBoolean("is_live", false),
                    attachedCharacter = char,
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

    // ------------------ CCT-32 Task A.1: full session/voice/character API ------------------

    /** GET /api/sessions/{session_id} — returns state + resolved_cfg merged into [SessionState]. */
    fun getSession(sessionId: String): SessionState {
        val req = buildBase("/api/sessions/$sessionId").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("getSession HTTP ${resp.code}")
            val body = resp.body?.string() ?: "{}"
            val root = JSONObject(body)
            val state = root.optJSONObject("state") ?: JSONObject()
            val cfg = root.optJSONObject("resolved_cfg") ?: JSONObject()

            val voiceObj = cfg.optJSONObject("voice")
            val liveObj = cfg.optJSONObject("live")
            val markupObj = cfg.optJSONObject("markup")
            val markup = mutableMapOf<String, MarkupTreatment>()
            if (markupObj != null) {
                val keys = markupObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = markupObj.optJSONObject(k) ?: continue
                    val kind = v.optString("kind", "")
                    if (kind.isNotBlank()) markup[k] = MarkupTreatment(kind)
                }
            }
            return SessionState(
                sessionId = state.optString("session_id", sessionId),
                cwd = state.optString("cwd", ""),
                attachedProfile = state.optString("attached_profile", "").ifBlank { null },
                attachedCharacterId = state.optString("attached_character", "").ifBlank { null },
                activeMode = cfg.optString("active_mode", ""),
                voiceEngine = voiceObj?.optString("engine")?.ifBlank { null },
                voiceModel = voiceObj?.optString("model")?.ifBlank { null },
                cadence = liveObj?.optString("cadence")?.ifBlank { null },
                enabled = cfg.optBoolean("enabled", true),
                markup = markup,
            )
        }
    }

    /**
     * PUT /api/sessions/{session_id}/overlay — partial overlay merge.
     * Pass a nested map; null values delete the keypath.
     */
    fun putOverlay(sessionId: String, overlay: Map<String, Any?>) {
        val body = mapToJson(overlay).toString().toRequestBody(JSON)
        val req = buildBase("/api/sessions/$sessionId/overlay").put(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("putOverlay HTTP ${resp.code}: ${resp.body?.string()}")
        }
    }

    /**
     * GET /api/voices?engine={engine}.
     *
     * The daemon may return either a flat list of strings (current) or
     * objects with `engine`/`model`/`display_name` (future). Both are
     * normalized into [VoiceLite].
     */
    fun listVoices(engine: String): List<VoiceLite> {
        val req = buildBase("/api/voices?engine=$engine").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("listVoices HTTP ${resp.code}")
            val body = resp.body?.string() ?: "[]"
            val arr = JSONArray(body)
            return List(arr.length()) { i ->
                val any = arr.opt(i)
                if (any is JSONObject) {
                    VoiceLite(
                        engine = any.optString("engine", engine),
                        model = any.optString("model", ""),
                        displayName = any.optString("display_name", any.optString("model", "")),
                    )
                } else {
                    val model = any?.toString() ?: ""
                    VoiceLite(engine = engine, model = model, displayName = model)
                }
            }
        }
    }

    /** GET /api/characters — full character library. */
    fun listCharacters(): List<CharacterLite> {
        val req = buildBase("/api/characters").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("listCharacters HTTP ${resp.code}")
            val body = resp.body?.string() ?: "[]"
            val arr = JSONArray(body)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CharacterLite(
                    id = o.optString("id", ""),
                    displayName = o.optString("display_name", o.optString("id", "?")),
                    persona = if (o.isNull("persona")) null else o.optString("persona", "").ifBlank { null },
                    voiceRef = o.optString("voice_ref", ""),
                    meshPath = if (o.isNull("mesh_path")) null else o.optString("mesh_path", "").ifBlank { null },
                )
            }
        }
    }

    /** POST /api/sessions/{session_id}/attach-character {"character_id": id} */
    fun attachCharacter(sessionId: String, characterId: String) {
        val body = JSONObject().put("character_id", characterId).toString().toRequestBody(JSON)
        val req = buildBase("/api/sessions/$sessionId/attach-character").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("attachCharacter HTTP ${resp.code}: ${resp.body?.string()}")
            }
        }
    }

    /** DELETE /api/sessions/{session_id}/character */
    fun detachCharacter(sessionId: String) {
        val req = buildBase("/api/sessions/$sessionId/character").delete().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("detachCharacter HTTP ${resp.code}")
        }
    }

    /** Recursively turn a nested Kotlin map / list into a JSONObject / JSONArray. */
    private fun mapToJson(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (k, v) -> put(k.toString(), mapToJson(v)) }
            }
            is List<*> -> JSONArray().apply {
                value.forEach { put(mapToJson(it)) }
            }
            else -> value
        }
    }
}

/**
 * CCT-32 Task A.1: full session state surface, merging the daemon's
 * `state` and `resolved_cfg` blocks into a single Kotlin record so the
 * UI doesn't have to know which side of the daemon's split each value
 * comes from.
 */
data class SessionState(
    val sessionId: String,
    val cwd: String,
    val attachedProfile: String?,
    val attachedCharacterId: String?,
    val activeMode: String,
    val voiceEngine: String?,
    val voiceModel: String?,
    val cadence: String?,
    val enabled: Boolean,
    val markup: Map<String, MarkupTreatment>,
)

data class MarkupTreatment(val kind: String)

data class VoiceLite(
    val engine: String,
    val model: String,
    val displayName: String,
)

data class CharacterLite(
    val id: String,
    val displayName: String,
    val persona: String?,
    val voiceRef: String,
    val meshPath: String?,
)

data class SessionLite(
    val sessionId: String,
    val displayName: String,
    val isLive: Boolean,
    /**
     * CCT-31: which character is attached to this session on the desktop.
     * When non-null, the session's TTS is rendered through this character's
     * voice (including custom-cloned voices), and the AR companion should
     * surface the character's name/persona/avatar to the listener.
     */
    val attachedCharacter: AttachedCharacter? = null,
)

/**
 * Mirror of the AttachedCharacter JSON shape returned by
 * /api/companion/sessions. Phase 25a/b/c characters from the desktop:
 * voice cloning lands in voiceRef, 3D mesh path lands in meshPath.
 */
data class AttachedCharacter(
    val id: String,
    val displayName: String,
    /** "methodical" | "warm" | "technical" | "plain" | "sarcastic" | "energetic" | null */
    val persona: String?,
    /** voice_ref the daemon uses for TTS — char-<id> for cloned voices,
     *  library voice id (e.g. en_US-amy-medium) for non-cloned. */
    val voiceRef: String?,
    /** Absolute path on the daemon host. AR companion can stream it back
     *  through a future /api/companion/mesh-frame endpoint when Phase 8
     *  (AR HUD) needs to render the avatar mesh. v1 just shows the
     *  character's display_name + persona color. */
    val meshPath: String?,
)
