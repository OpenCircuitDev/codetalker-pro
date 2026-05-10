package dev.opencircuit.codetalker.net

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DaemonClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DaemonClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = DaemonClient(
            baseUrl = server.url("").toString().trimEnd('/'),
            pairingToken = "test-token-32-chars-XXXXXXXXXXXXXX",
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `every request includes pairing token header`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        client.listSessions()
        val recorded = server.takeRequest()
        assertEquals(
            "test-token-32-chars-XXXXXXXXXXXXXX",
            recorded.getHeader(DaemonClient.HEADER_TOKEN),
        )
    }

    @Test
    fun `listSessions parses display_name and falls back to session_id slice`() {
        val body = """
            [
              {"session_id":"abc-123","display_name":"My Session","is_live":true},
              {"session_id":"def-456-789-no-display"}
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val sessions = client.listSessions()
        assertEquals(2, sessions.size)
        assertEquals("My Session", sessions[0].displayName)
        assertTrue(sessions[0].isLive)
        // Fallback when display_name absent
        assertEquals("def-456-", sessions[1].displayName)
        assertEquals(false, sessions[1].isLive)
    }

    @Test
    fun `listSessions throws on non-200`() {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            client.listSessions()
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `setActiveSession posts JSON body with session_id`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        client.setActiveSession("sid-42")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("\"session_id\":\"sid-42\""))
    }

    @Test
    fun `startBuddy returns the buddy_id from response`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"buddy_id":"my-session","status":"ready"}""")
        )
        val id = client.startBuddy("my-session")
        assertEquals("my-session", id)
    }

    @Test
    fun `startBuddy throws on 400 with anthropic key error`() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"anthropic_api_key not set"}""")
        )
        try {
            client.startBuddy("sid")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("400"))
            assertTrue(e.message!!.contains("anthropic_api_key"))
        }
    }

    @Test
    fun `captureScreenFrame returns null on 503 graceful-degrade`() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(client.captureScreenFrame("fullscreen"))
    }

    @Test
    fun `captureScreenFrame returns bytes on 200`() {
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(okio.Buffer().write(fakeJpeg))
        )
        val bytes = client.captureScreenFrame("fullscreen")
        assertNotNull(bytes)
        assertEquals(4, bytes!!.size)
    }

    @Test
    fun `audioStreamUrl composes correctly`() {
        val url = client.audioStreamUrl("session-x")
        assertTrue(url.endsWith("/api/companion/audio-stream/session-x"))
    }

    @Test
    fun `listSessions parses attachedCharacter when daemon includes it`() {
        val body = """
            [
              {
                "session_id":"sid-1",
                "display_name":"My Session",
                "is_live":true,
                "attached_character":{
                  "id":"aria",
                  "display_name":"Aria",
                  "persona":"warm",
                  "voice_ref":"char-aria",
                  "mesh_path":"/some/path/aria.glb"
                }
              }
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val sessions = client.listSessions()
        val char = sessions[0].attachedCharacter
        assertNotNull(char)
        assertEquals("aria", char!!.id)
        assertEquals("Aria", char.displayName)
        assertEquals("warm", char.persona)
        assertEquals("char-aria", char.voiceRef)
        assertEquals("/some/path/aria.glb", char.meshPath)
    }

    @Test
    fun `listSessions tolerates attached_character null and missing key`() {
        val body = """
            [
              {"session_id":"a","display_name":"A","is_live":true,"attached_character":null},
              {"session_id":"b","display_name":"B","is_live":false}
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val sessions = client.listSessions()
        assertEquals(null, sessions[0].attachedCharacter)
        assertEquals(null, sessions[1].attachedCharacter)
    }

    // ---------- CCT-32 Task A.1: full session/voice/character endpoints ----------

    @Test
    fun `getSession returns full state with resolved cfg`() {
        val body = """
            {
              "state": {
                "session_id":"sid-1","cwd":"/tmp","attached_profile":null,
                "attached_character":"aria","live_overlay":{}
              },
              "resolved_cfg": {
                "active_mode":"direct",
                "voice":{"engine":"piper","model":"char-aria"},
                "live":{"cadence":"normal"},
                "enabled":true,
                "markup":{"code_fence":{"kind":"describe"}}
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val s = client.getSession("sid-1")
        assertEquals("sid-1", s.sessionId)
        assertEquals("/tmp", s.cwd)
        assertEquals("aria", s.attachedCharacterId)
        assertEquals("direct", s.activeMode)
        assertEquals("char-aria", s.voiceModel)
        assertEquals("piper", s.voiceEngine)
        assertEquals("normal", s.cadence)
        assertEquals(true, s.enabled)
        assertEquals("describe", s.markup["code_fence"]?.kind)
    }

    @Test
    fun `getSession tolerates missing nested fields`() {
        val body = """
            {
              "state":{"session_id":"sid-2","cwd":"","live_overlay":{}},
              "resolved_cfg":{"active_mode":"brief","enabled":false}
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val s = client.getSession("sid-2")
        assertEquals("brief", s.activeMode)
        assertEquals(false, s.enabled)
        assertNull(s.voiceModel)
        assertNull(s.cadence)
        assertTrue(s.markup.isEmpty())
    }

    @Test
    fun `getSession throws on 404`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"unknown session"}"""))
        try {
            client.getSession("missing")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun `putOverlay sends nested keypath PUT`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"resolved_cfg":{}}"""))
        client.putOverlay("sid-1", mapOf("active_mode" to "brief"))
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/sessions/sid-1/overlay"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"active_mode\":\"brief\""))
    }

    @Test
    fun `putOverlay sends nested map`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"resolved_cfg":{}}"""))
        client.putOverlay(
            "sid-1",
            mapOf("markup" to mapOf("code_fence" to mapOf("kind" to "skip"))),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"markup\""))
        assertTrue(body.contains("\"code_fence\""))
        assertTrue(body.contains("\"kind\":\"skip\""))
    }

    @Test
    fun `listVoices parses flat string list and prefixes engine`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["en_US-amy-medium","en_GB-jenny-medium"]"""))
        val voices = client.listVoices(engine = "piper")
        assertEquals(2, voices.size)
        assertEquals("en_US-amy-medium", voices[0].model)
        assertEquals("piper", voices[0].engine)
        // displayName falls back to model when daemon returns flat strings.
        assertEquals("en_US-amy-medium", voices[0].displayName)
    }

    @Test
    fun `listVoices parses object list when present`() {
        // Future-proofing: if daemon ever returns objects, we still parse.
        val body = """[{"engine":"piper","model":"en_US-amy-medium","display_name":"Amy"}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val voices = client.listVoices(engine = "piper")
        assertEquals("en_US-amy-medium", voices[0].model)
        assertEquals("Amy", voices[0].displayName)
    }

    @Test
    fun `listVoices sends engine query param`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        client.listVoices(engine = "piper")
        val r = server.takeRequest()
        assertTrue(r.path!!.contains("engine=piper"))
    }

    @Test
    fun `listCharacters returns full records`() {
        val body = """
            [
              {"id":"aria","display_name":"Aria","persona":"warm","voice_ref":"char-aria","mesh_path":null},
              {"id":"crow","display_name":"Dr Crow","persona":"methodical","voice_ref":"char-crow","mesh_path":"/x.glb"}
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val chars = client.listCharacters()
        assertEquals(2, chars.size)
        assertEquals("aria", chars[0].id)
        assertEquals("Aria", chars[0].displayName)
        assertEquals("warm", chars[0].persona)
        assertEquals("char-aria", chars[0].voiceRef)
        assertNull(chars[0].meshPath)
        assertEquals("/x.glb", chars[1].meshPath)
    }

    @Test
    fun `attachCharacter posts to attach endpoint with character_id`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        client.attachCharacter("sid-1", "aria")
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertTrue(r.path!!.endsWith("/api/sessions/sid-1/attach-character"))
        assertTrue(r.body.readUtf8().contains("\"character_id\":\"aria\""))
    }

    @Test
    fun `attachCharacter throws on 400`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad"}"""))
        try {
            client.attachCharacter("sid-1", "missing")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("400"))
        }
    }

    @Test
    fun `detachCharacter sends DELETE`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        client.detachCharacter("sid-1")
        val r = server.takeRequest()
        assertEquals("DELETE", r.method)
        assertTrue(r.path!!.endsWith("/api/sessions/sid-1/character"))
    }

    @Test
    fun `listSessions partial attached_character fields fall back gracefully`() {
        // A character with no persona / voice_ref / mesh_path (e.g. just-created
        // with a library voice) should still parse into an AttachedCharacter
        // with nulls for the optional fields.
        val body = """
            [
              {
                "session_id":"sid-x",
                "display_name":"X",
                "is_live":false,
                "attached_character":{
                  "id":"plain-char",
                  "display_name":"Plain Char",
                  "persona":null,
                  "voice_ref":"en_US-amy-medium",
                  "mesh_path":null
                }
              }
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val char = client.listSessions()[0].attachedCharacter!!
        assertEquals(null, char.persona)
        assertEquals("en_US-amy-medium", char.voiceRef)
        assertEquals(null, char.meshPath)
    }
}
