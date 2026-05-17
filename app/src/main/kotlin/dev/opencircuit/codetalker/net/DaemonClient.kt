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

    /** GET /api/health — returns daemon health + narration-gate status.
     *  Throws on non-2xx. 2026-05-16 -- now also surfaces
     *  `narrationEnabled` / `narrationWarning` so the Pro app can
     *  render a visible banner when tts_config.yaml's master switch
     *  is off (otherwise the user hears no hook-driven TTS and has
     *  no signal that the cause is a single line in a YAML file). */
    data class DaemonHealth(
        val ok: Boolean,
        val narrationEnabled: Boolean,
        val narrationWarning: String?,
    )

    /** GET /api/master-enabled -- read the global narration master switch. */
    fun getMasterEnabled(): Boolean {
        val req = buildBase("/api/master-enabled").get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("getMasterEnabled HTTP ${resp.code}")
            val o = JSONObject(resp.body?.string() ?: "{}")
            return o.optBoolean("enabled", true)
        }
    }

    /** PUT /api/master-enabled {enabled: bool} -- toggle the master switch.
     *  Persists to tts_config.yaml so daemon restart honors it. */
    fun setMasterEnabled(enabled: Boolean): Boolean {
        val body = JSONObject().put("enabled", enabled).toString().toRequestBody(JSON)
        val req = buildBase("/api/master-enabled").put(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("setMasterEnabled HTTP ${resp.code}")
            val o = JSONObject(resp.body?.string() ?: "{}")
            return o.optBoolean("enabled", enabled)
        }
    }

    fun getHealthOrThrow(): DaemonHealth {
        val req = buildBase("/api/health").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("health HTTP ${resp.code}")
            val o = JSONObject(resp.body?.string() ?: "{}")
            return DaemonHealth(
                ok = o.optBoolean("ok", true),
                narrationEnabled = o.optBoolean("narration_enabled", true),
                narrationWarning = if (o.has("narration_warning") && !o.isNull("narration_warning"))
                    o.optString("narration_warning", "").ifBlank { null }
                else null,
            )
        }
    }

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
                    projectSlug = o.optString("project_slug", "").ifBlank { null },
                    enabled = o.optBoolean("enabled", true),
                    activeMode = o.optString("active_mode", "").ifBlank { null },
                    lastHookAt = o.optDouble("last_hook_at", 0.0),
                    lastModified = o.optDouble("last_modified", 0.0),
                    isCompanionActive = o.optBoolean("is_companion_active", false),
                    cwd = o.optString("cwd", "").ifBlank { null },
                    projectDir = o.optString("project_dir", "").ifBlank { null },
                    workspaceGroup = if (o.has("workspace_group") && !o.isNull("workspace_group"))
                        o.optString("workspace_group", "").ifBlank { null }
                    else null,
                    isSpeaking = o.optBoolean("is_speaking", false),
                    autoModeEnabled = o.optBoolean("auto_mode_enabled", false),
                    audioOutputs = if (o.has("audio_outputs") && !o.isNull("audio_outputs")) {
                        val arr = o.optJSONArray("audio_outputs")
                        if (arr != null) List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
                        else null
                    } else null,
                    // 2026-05-11 Tier-A.2 — daemon-computed warning flag.
                    // True iff audio_outputs contains a companion sink
                    // (phone/glasses) but the audio_hub has no subscriber
                    // for that SID. UI uses this to render a "configured
                    // but not receiving" badge so the dual-state alignment
                    // trap is visible. See feedback/dual_state_audio_routing.md.
                    audioMisaligned = o.optBoolean("audio_misaligned", false),
                    pinned = o.optBoolean("pinned", false),
                    cadence = o.optString("cadence", "").ifBlank { null },
                    lastUserInteractionAt = o.optDouble("last_user_interaction_at", 0.0),
                )
            }
        }
    }

    /** CCT-32 v0.1.0 — inline mute toggle from Sessions list (no detail nav). */
    fun setMuted(sessionId: String, muted: Boolean) {
        putOverlay(sessionId, mapOf("enabled" to !muted))
    }

    /** CCT-32 v0.1.0 — inline brief/live quick pick from Sessions list. */
    fun setActiveMode(sessionId: String, mode: String) {
        putOverlay(sessionId, mapOf("active_mode" to mode))
    }

    /** v0.1.0 polish — assign a session to a user-defined workspace group.
     *  Passing empty string clears the assignment (session falls under
     *  "Ungrouped" on the Sessions list). */
    fun setWorkspaceGroup(sessionId: String, group: String) {
        putOverlay(sessionId, mapOf("workspace_group" to group))
    }

    /** v0.1.0 polish — rename a session (writes to persistent.display_name
     *  so Pro app + webui both pick it up immediately). */
    fun setDisplayName(sessionId: String, displayName: String) {
        putOverlay(sessionId, mapOf("display_name" to displayName))
    }

    /** v0.1.0 unification — multi-select audio outputs.
     *  Values from {"desktop","phone","glasses"}. Pass `null` to clear the
     *  per-session override (the fleet default then applies); pass `[]`
     *  to explicitly silence everywhere. */
    fun setAudioOutputs(sessionId: String, outputs: List<String>?) {
        putOverlay(sessionId, mapOf("audio_outputs" to outputs))
    }

    /** v0.1.0 unification — opt-in to auto-switch live/brief mode based on
     *  recent user interaction. */
    fun setAutoMode(sessionId: String, enabled: Boolean) {
        putOverlay(sessionId, mapOf("auto_mode_enabled" to enabled))
    }

    /** v0.1.0 unification — pin a session to the top of its workspace
     *  group. Persisted in the daemon overlay so the pin syncs across
     *  webui + Pro Android. Pass `false` to unpin. */
    fun setPinned(sessionId: String, pinned: Boolean) {
        putOverlay(sessionId, mapOf("pinned" to pinned))
    }

    /**
     * POST /api/companion/active-session { session_id }
     *
     * Legacy single-slot setter — replaces the daemon's active set with just
     * this one sid. Kept for the few call sites that still want exclusive
     * activation. Prefer [toggleActiveSession] for the multi-active flow.
     */
    fun setActiveSession(sessionId: String) {
        val body = JSONObject().put("session_id", sessionId).toString().toRequestBody(JSON)
        val req = buildBase("/api/companion/active-session").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("setActiveSession HTTP ${resp.code}")
        }
    }

    /**
     * POST /api/companion/active-session { session_id, active: bool }
     *
     * 2026-05-11 — multi-active toggle. `active=true` adds the sid to the
     * daemon's set; `false` removes it. Other members are unaffected.
     */
    fun toggleActiveSession(sessionId: String, active: Boolean) {
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("active", active)
            .toString()
            .toRequestBody(JSON)
        val req = buildBase("/api/companion/active-session").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("toggleActiveSession HTTP ${resp.code}")
        }
    }

    /** GET /api/companion/active-sessions — read the daemon's full active set. */
    fun getActiveSessions(): Set<String> {
        val req = buildBase("/api/companion/active-sessions").get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("getActiveSessions HTTP ${resp.code}")
            val o = JSONObject(resp.body?.string() ?: "{}")
            val arr = o.optJSONArray("active_session_ids") ?: return emptySet()
            return buildSet {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
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

    /**
     * Phase 4 (2026-05-16) — GET /api/events as a long-lived SSE stream.
     *
     * One stream per app process. The daemon pushes SessionChanged,
     * MasterConfigChanged, LifecycleChanged, AudioJobStateChanged,
     * SubscriptionChanged, and CharacterPoseChanged events; callers
     * filter via `topics`. Pass null/empty to receive everything.
     *
     * Wire format: standard SSE.
     *   event: SessionChanged
     *   data: { "event_type": "...", "at": 1234.5, "session_id": "...", ... }
     *
     * The listener's `onEvent(eventSource, id, type, data)` fires per
     * event. Use `type` to dispatch, `data` is the JSON-serialized
     * Event payload. Auto-reconnect is OkHttp's default for SSE; the
     * stream stays open until the caller cancels.
     *
     * Replaces (well, augments — current screens still poll as a
     * safety net) the 3-5s polling loops in SessionListScreen,
     * PreferencesScreen, and CTCGalleryScreen.
     */
    fun openDaemonEventStream(
        topics: List<String>? = null,
        listener: EventSourceListener,
    ): EventSource {
        val path = if (topics.isNullOrEmpty()) {
            "/api/events"
        } else {
            "/api/events?topics=" + topics.joinToString(",")
        }
        val req = buildBase(path).get().build()
        val factory = EventSources.createFactory(httpClient)
        return factory.newEventSource(req, listener)
    }

    /**
     * 2026-05-12 — POST /api/companion/direct-stt { session_id, text }.
     *
     * Direct-STT bypasses the Buddy intermediate LLM. Daemon types the
     * transcript into the OS-foreground window (user keeps Claude Code
     * focused) via Windows SendKeys, followed by Enter. Synchronous
     * fire-and-forget; reply auto-narrates via the existing hook → audio
     * pipeline once Claude Code processes the typed prompt.
     *
     * Returns the daemon's response body for debugging (or throws on
     * non-2xx).
     */
    fun postDirectStt(sessionId: String, text: String): String {
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("text", text)
            .toString().toRequestBody(JSON)
        val req = buildBase("/api/companion/direct-stt").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            val bodyText = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw java.io.IOException("direct-stt HTTP ${resp.code}: $bodyText")
            }
            return bodyText
        }
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

    // ─── Piper voice manager (v1.x) ──────────────────────────────────────
    // Mirrors the webui's Voice Library tab. Backed by 4 daemon endpoints
    // added this release: catalog (curated 12 entries with installed flag),
    // install (downloads .onnx + .onnx.json from huggingface), uninstall
    // (deletes the two files), preview (synthesizes a sample + returns WAV
    // bytes; daemon also plays through its own audio_hub but on Android we
    // play through AudioTrack ourselves from the response body).

    /** GET /api/piper/catalog — curated 12-entry piper voice catalog. */
    fun getPiperCatalog(): List<PiperCatalogEntry> {
        val req = buildBase("/api/piper/catalog").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("getPiperCatalog HTTP ${resp.code}")
            val body = resp.body?.string() ?: "[]"
            val arr = JSONArray(body)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                PiperCatalogEntry(
                    name = o.getString("name"),
                    lang = o.optString("lang", "").ifBlank { null },
                    speaker = o.optString("speaker", "").ifBlank { null },
                    gender = o.optString("gender", "").ifBlank { null },
                    quality = o.optString("quality", "").ifBlank { null },
                    sizeMb = o.optInt("size_mb", 0),
                    installed = o.optBoolean("installed", false),
                    curated = o.optBoolean("curated", true),
                )
            }
        }
    }

    /** POST /api/piper/install body={"name": "..."} — downloads voice
     *  files to the daemon host's piper voices dir. Can take 10-60s for
     *  larger voices (en_US-ryan-high is 110 MB). */
    fun installPiperVoice(name: String) {
        val body = JSONObject().put("name", name).toString().toRequestBody(JSON)
        val req = buildBase("/api/piper/install").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.take(200) ?: ""
                throw IOException("installPiperVoice HTTP ${resp.code}: $msg")
            }
        }
    }

    /** DELETE /api/piper/voices/{name} — removes voice files from disk. */
    fun uninstallPiperVoice(name: String) {
        val req = buildBase("/api/piper/voices/$name").delete().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw IOException("uninstallPiperVoice HTTP ${resp.code}")
            }
        }
    }

    /** POST /api/piper/preview/{name} — synthesizes a short sample and
     *  returns the WAV bytes. The daemon ALSO plays this on its own
     *  desktop audio, so we just return the bytes here for the Android
     *  side to play through AudioTrack if it wants a "phone-side preview"
     *  experience. */
    fun previewPiperVoice(name: String): ByteArray {
        val empty = ByteArray(0).toRequestBody(JSON)
        val req = buildBase("/api/piper/preview/$name").post(empty).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("previewPiperVoice HTTP ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    // ─── Auto-generate character (v1.x) ──────────────────────────────────
    // Two-stage flow mirroring the webui:
    //   1. generateCharacterDraft(sid)  → daemon LLM, no save
    //   2. createCharacter(...) + attachCharacter(...) → commit edits
    // The webui calls these directly as separate steps; we mirror that
    // here so the UI can show a fully editable preview before commit.

    /** POST /api/sessions/{id}/generate-character?preview=true — daemon
     *  fires the configured LLM (openrouter Haiku by default) with a
     *  prompt seeded by the session's display_name + project_dir and
     *  returns a CharacterDraft. Nothing is saved or attached — the
     *  caller decides via createCharacter + attachCharacter. */
    fun generateCharacterDraft(sessionId: String): CharacterDraft {
        val empty = ByteArray(0).toRequestBody(JSON)
        val req = buildBase("/api/sessions/$sessionId/generate-character?preview=true").post(empty).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.take(200) ?: ""
                throw IOException("generateCharacterDraft HTTP ${resp.code}: $msg")
            }
            val o = JSONObject(resp.body?.string() ?: "{}")
            val draft = o.optJSONObject("draft") ?: o  // tolerate either shape
            val states = mutableMapOf<String, String>()
            draft.optJSONObject("emotive_states")?.keys()?.forEach { k ->
                val v = draft.getJSONObject("emotive_states").optString(k, "")
                if (v.isNotBlank()) states[k] = v
            }
            return CharacterDraft(
                id = draft.optString("id", ""),
                displayName = draft.optString("display_name", ""),
                voiceRef = draft.optString("voice_ref", ""),
                persona = draft.optString("persona", "technical"),
                meshPrompt = draft.optString("mesh_prompt", "").ifBlank { null },
                emotiveStates = states,
            )
        }
    }

    /** POST /api/characters — create a new character. Used by the
     *  auto-generate dialog's Save & Attach step after the user has
     *  reviewed/edited the LLM draft. Daemon returns the saved record. */
    fun createCharacter(draft: CharacterDraft) {
        val statesJson = JSONObject().apply {
            draft.emotiveStates.forEach { (k, v) -> put(k, v) }
        }
        val body = JSONObject().apply {
            put("id", draft.id)
            put("display_name", draft.displayName)
            put("voice_ref", draft.voiceRef)
            put("persona", draft.persona)
            if (draft.meshPrompt != null) put("mesh_prompt", draft.meshPrompt)
            put("emotive_states", statesJson)
        }.toString().toRequestBody(JSON)
        val req = buildBase("/api/characters").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.take(200) ?: ""
                throw IOException("createCharacter HTTP ${resp.code}: $msg")
            }
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
    /** CCT-32 v0.1.0 polish — fields the Pro Sessions list redesign uses. */
    val projectSlug: String? = null,
    val enabled: Boolean = true,
    val activeMode: String? = null,
    val lastHookAt: Double = 0.0,
    /**
     * 2026-05-16 -- catalog's transcript mtime in epoch seconds. The
     * phone's "Active" filter uses this with a 30-minute window so
     * sessions the user has been working in (but that may have idled
     * past the 5-minute is_live threshold) still appear. 0.0 when the
     * daemon has no transcript record for this sid.
     */
    val lastModified: Double = 0.0,
    val isCompanionActive: Boolean = false,
    /**
     * v0.1.0 polish — Claude Code's working directory for this session.
     * Used to group sessions by workspace (cwd leaf) so multiple distinct
     * projects don't collapse under a shared `project_slug`.
     */
    val cwd: String? = null,
    /**
     * v0.1.0 polish — Claude Code's project directory name (e.g.
     * "C--Users-brand-Documents-Unreal-Projects-BlueprintForge-Workbench").
     * Survives session eviction (catalog stores transcript_path whose
     * parent IS this dir); fallback grouping key when workspaceGroup unset.
     */
    val projectDir: String? = null,
    /**
     * v0.1.0 polish — user-defined workspace group, persisted via PUT
     * /api/sessions/{id}/overlay {"workspace_group": "OCRacing"}. When set,
     * this is the PRIMARY grouping key on the Sessions list; null sessions
     * fall under "Ungrouped".
     */
    val workspaceGroup: String? = null,
    /** v0.1.0 unification — true while the daemon is actively synthesizing
     *  TTS for this session; drives the strong-pulse flash on Sessions
     *  rows on both the Pro Android and the webui. */
    val isSpeaking: Boolean = false,
    /** v0.1.0 unification — opt-in auto-switch between live/brief
     *  active_mode based on user-interaction recency. */
    val autoModeEnabled: Boolean = false,
    /** v0.1.0 unification — multi-select audio outputs.
     *  Values from {"desktop","phone","glasses"}; null = follow fleet default. */
    val audioOutputs: List<String>? = null,
    /** 2026-05-11 Tier-A.2 — daemon-computed alignment warning. True iff
     *  audio_outputs contains a companion sink (phone/glasses) but the
     *  audio_hub has no live subscriber for that SID. Rendered as a
     *  "configured but not receiving" badge so the dual-state trap
     *  surfaces visually. */
    val audioMisaligned: Boolean = false,
    /** v0.1.0 unification — pinned to the top of its workspace_group.
     *  Persisted in the daemon overlay so it syncs across surfaces. */
    val pinned: Boolean = false,
    /** v0.1.0 unification — live cadence selection (periodic / hybrid /
     *  significant_only / per_tool_call / per_cluster). Applies only
     *  when active_mode == "live". */
    val cadence: String? = null,
    /** v0.1.0 unification — epoch seconds of last UserPromptSubmit on
     *  this session. Drives the "Active" filter chip (sessions with
     *  activity in the last 30s) on both webui and Android. 0.0 = no
     *  interaction recorded since daemon start. */
    val lastUserInteractionAt: Double = 0.0,
)

/** Suggested workspace group names — used as autocomplete hints in the
 *  SessionDetail workspace-group field. Keep in sync with whatever the
 *  user labels in production. */
val SUGGESTED_WORKSPACE_GROUPS: List<String> = listOf(
    "OCRacing", "Clients", "OCDev", "BlueprintForge", "Ungrouped",
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

/**
 * One row of the curated piper voice catalog (12 entries this release).
 * `curated=false` entries surface user-installed voices that aren't in
 * the daemon's curated list (e.g. dropped into the voices dir manually).
 */
data class PiperCatalogEntry(
    val name: String,
    val lang: String?,
    val speaker: String?,
    val gender: String?,
    val quality: String?,
    val sizeMb: Int,
    val installed: Boolean,
    val curated: Boolean = true,
)

/**
 * Editable draft from the auto-generate flow. The daemon returns one of
 * these in response to `POST /api/sessions/{id}/generate-character?preview=true`;
 * the UI lets the user tweak any field, then commits with createCharacter()
 * + attachCharacter().
 */
data class CharacterDraft(
    val id: String,
    val displayName: String,
    val voiceRef: String,
    val persona: String,
    val meshPrompt: String?,
    val emotiveStates: Map<String, String>,
)

/** Canonical order for the 10 emotive states. The wizard / preview UI
 *  iterates this list to render the per-state textareas in a stable
 *  order matching the webui's EmotiveStatesEditor. */
val EMOTIVE_STATE_ORDER: List<String> = listOf(
    "idle", "listening", "speaking", "researching", "working",
    "questioning", "thinking", "confirming", "concluding", "alerted",
)
