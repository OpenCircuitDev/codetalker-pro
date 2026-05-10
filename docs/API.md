# API reference — DaemonClient

This document describes the daemon-side HTTP API the codetalker
companion calls. Every method on `DaemonClient.kt` maps 1:1 to an
endpoint, and is shown here with its wire shape, types, and example
JSON.

The API is **not** a public-internet endpoint. The daemon binds to
the user's LAN or Tailnet only. Authentication is the
`X-CCT-Pairing-Token` header on every request; the token is issued
by the desktop's "Pair AR Companion" QR flow.

---

## 1. Conventions

- All requests carry `X-CCT-Pairing-Token: <token>`.
- All bodies are `application/json` UTF-8.
- All timestamps are ISO 8601 with second precision.
- Status code semantics:
  - `200` — success.
  - `401` / `403` — token rejected. Re-pair.
  - `404` / `410` — session no longer live. App maps to
    `AppError.SessionOffline`.
  - `426` — daemon API version mismatch. App maps to
    `AppError.DaemonVersionMismatch`.
  - `503` — daemon shutting down or busy. App treats as transient.

The Kotlin types referenced below (`SessionLite`, `SessionState`,
`VoiceLite`, `CharacterLite`, `MarkupTreatment`) live in
`net/DaemonClient.kt`.

---

## 2. Health

### `GET /api/health`

Pings the daemon. No request body.

**200 response**

```json
{
  "ok": true,
  "version": "0.1.0",
  "uptime_s": 1234
}
```

**Kotlin**

```kotlin
fun getHealthOrThrow()
```

Throws `IOException("health HTTP <code>")` on non-200.

---

## 3. Sessions list

### `GET /api/companion/sessions`

Returns every Claude Code session the daemon is tracking, plus the
currently-attached character (if any).

**200 response**

```json
[
  {
    "session_id": "sid-abc",
    "display_name": "src/router.ts",
    "is_live": true,
    "attached_character": {
      "id": "aria",
      "display_name": "Aria",
      "persona": "warm",
      "voice_ref": "char-aria",
      "mesh_path": null
    }
  }
]
```

**Kotlin**

```kotlin
fun listSessions(): List<SessionLite>
```

`AttachedCharacter` is null when no character is attached.

---

## 4. Active session

### `POST /api/companion/active-session`

Sets the daemon's notion of which session the companion is mirroring
right now. Audio + SSE event streams pivot to this session.

**Request**

```json
{ "session_id": "sid-abc" }
```

**Kotlin**

```kotlin
fun setActiveSession(sessionId: String)
```

---

## 5. Session detail + overlay

### `GET /api/sessions/{session_id}`

Returns the full session state plus the resolved configuration overlay.

**200 response**

```json
{
  "state": {
    "session_id": "sid-abc",
    "cwd": "/Users/you/projects/codetalker",
    "attached_profile": null,
    "attached_character": "aria",
    "live_overlay": { /* opaque */ }
  },
  "resolved_cfg": {
    "active_mode": "direct",
    "voice": { "engine": "piper", "model": "char-aria" },
    "live": { "cadence": "normal" },
    "enabled": true,
    "markup": {
      "code_fence": { "kind": "describe" },
      "tool_output": { "kind": "describe" }
    }
  }
}
```

**Kotlin**

```kotlin
fun getSession(sessionId: String): SessionState
```

The two top-level blocks are merged into a single `SessionState`
record. Voice nullability is preserved (`voice_engine` and
`voice_model` may be null when no voice is wired).

### `PUT /api/sessions/{session_id}/overlay`

Partial overlay merge. Send a nested map; null values delete the
keypath. The daemon deep-merges the patch onto the live overlay.

**Request examples**

Change mode:

```json
{ "active_mode": "brief" }
```

Change voice:

```json
{ "voice": { "engine": "piper", "model": "en_US-amy-medium" } }
```

Change a markup treatment:

```json
{ "markup": { "code_fence": { "kind": "skip" } } }
```

Mute:

```json
{ "enabled": false }
```

**Kotlin**

```kotlin
fun putOverlay(sessionId: String, overlay: Map<String, Any?>)
```

---

## 6. Voices

### `GET /api/voices?engine={engine}`

Returns the daemon's voice library for the given engine. The daemon
may return either a flat list of strings (each is a voice model
identifier) or a list of objects; the companion handles both shapes.

**200 response (object form)**

```json
[
  { "engine": "piper", "model": "en_US-amy-medium", "display_name": "Amy" },
  { "engine": "piper", "model": "en_US-ryan-high",  "display_name": "Ryan" },
  { "engine": "piper", "model": "char-aria",       "display_name": "Aria (cloned)" }
]
```

**Kotlin**

```kotlin
fun listVoices(engine: String): List<VoiceLite>
```

---

## 7. Characters

### `GET /api/characters`

Returns the full character library. Each character points at a
`voice_ref` (either a library voice id like `en_US-amy-medium` or a
cloned-voice id like `char-aria`) and may include a 3D mesh path
for future avatar rendering.

**200 response**

```json
[
  {
    "id": "aria",
    "display_name": "Aria",
    "persona": "warm",
    "voice_ref": "char-aria",
    "mesh_path": null
  },
  {
    "id": "blake",
    "display_name": "Blake",
    "persona": "methodical",
    "voice_ref": "en_US-ryan-high",
    "mesh_path": "/Users/you/codetalker/meshes/blake.glb"
  }
]
```

**Kotlin**

```kotlin
fun listCharacters(): List<CharacterLite>
```

### `POST /api/sessions/{session_id}/attach-character`

Attaches a character to the session. The daemon updates
`state.attached_character` and the resolved voice.

**Request**

```json
{ "character_id": "aria" }
```

**Kotlin**

```kotlin
fun attachCharacter(sessionId: String, characterId: String)
```

### `DELETE /api/sessions/{session_id}/character`

Detaches the current character.

**Kotlin**

```kotlin
fun detachCharacter(sessionId: String)
```

---

## 8. Buddy session + inject

### `POST /api/companion/start-buddy`

Starts a "buddy" session — a server-side conversational thread
parented to a user session. The buddy_id is returned synchronously
and then used as the recipient of `inject` calls.

**Request**

```json
{ "user_session_id": "sid-abc" }
```

**200 response**

```json
{ "buddy_id": "buddy-xyz" }
```

**Kotlin**

```kotlin
fun startBuddy(userSessionId: String): String
```

### `POST /api/companion/inject`

Injects user-typed (or transcribed) text into a buddy session. The
response is **server-sent events** — the daemon streams the buddy's
reply line by line.

**Request**

```json
{ "buddy_id": "buddy-xyz", "text": "what test failed?" }
```

**200 response (SSE stream)**

```
event: caption
data: { "text": "Looking at..." }

event: caption
data: { "text": "Looking at the latest CI run, src/foo_test.ts line 42." }

event: status
data: { "phase": "complete" }
```

**Kotlin**

```kotlin
fun inject(
    buddyId: String,
    text: String,
    listener: EventSourceListener,
): EventSource
```

`EventSourceListener` is the standard okhttp-sse type. The companion
forwards `caption` events into `CompanionViewModel.captionText` for
HUD rendering.

---

## 9. Audio + screen-frame streaming

### `GET /api/companion/audio-stream/{session_id}`

The audio stream URL — handed to ExoPlayer's `MediaSource.Factory`.
Not a regular JSON endpoint; returns continuous audio bytes.

**Kotlin**

```kotlin
fun audioStreamUrl(sessionId: String): String
```

### `GET /api/companion/screen-frame/{kind}`

Returns a JPEG of the current desktop screen view. `kind` is one of:
`fullscreen` (the whole monitor), `dashboard` (the codetalker dashboard
itself), or `editor` (the active code editor).

**200 response:** raw JPEG bytes.
**404 / 503 response:** screen frame not available right now (returns
`null` from the Kotlin call).

**Kotlin**

```kotlin
fun captureScreenFrame(kind: String = "fullscreen"): ByteArray?
```

---

## 10. Error handling

`DaemonClient` throws `IOException` on every non-2xx response. The
exception message embeds the HTTP code so `AppErrors.fromThrowable`
(in `ui/errors/AppError.kt`) can map it to a catalog entry. See
`AppError.kt` for the full mapping table.

---

## 11. Integration example — wire a new picker

This is the canonical recipe for a new control surface (e.g., adding
a "speech volume" slider that talks to the daemon):

```kotlin
// 1. Read the current value out of SessionState (assuming the daemon
//    extends resolved_cfg.voice with a `volume` float).
val cur = state.voiceVolume ?: 1.0f

// 2. Render the slider.
Slider(
    value = cur,
    valueRange = 0f..1.5f,
    onValueChange = { newVolume ->
        scope.launch(Dispatchers.IO) {
            // 3. PATCH the overlay.
            daemonClient.putOverlay(
                sessionId,
                mapOf("voice" to mapOf("volume" to newVolume.toDouble())),
            )
            // 4. Refresh state from the daemon.
            state = daemonClient.getSession(sessionId)
        }
    },
)
```

The pattern is `putOverlay → getSession → state = ...`. Don't try to
mutate the local `SessionState` directly; the daemon is the source of
truth.

---

## 12. Versioning

The companion targets daemon API **v1**. The daemon responds with
`HTTP 426 Upgrade Required` if the API version doesn't match, and the
companion surfaces `AppError.DaemonVersionMismatch` at the UI layer.
Future API versions will live under `/api/v2/...` paths; v1 paths
continue to work.

The companion's API contract is stable for the lifespan of v0.1.x.
Breaking changes ship in v0.2.0 and bump the API version path.
