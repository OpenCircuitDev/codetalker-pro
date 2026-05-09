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
}
