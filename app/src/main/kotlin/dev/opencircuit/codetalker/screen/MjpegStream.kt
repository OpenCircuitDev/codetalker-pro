package dev.opencircuit.codetalker.screen

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * CCT-31 Phase 9 — MJPEG ("multipart/x-mixed-replace") stream parser.
 *
 * The daemon's /api/companion/screen-frame/{kind} returns a single JPEG
 * today (the v1 path). This class is built so that when the daemon
 * upgrades to true MJPEG (multipart frames separated by a boundary),
 * the Android side reads each frame as it arrives and pushes it to a
 * frame consumer for AR rendering.
 *
 * Pure-Kotlin parser; testable without a CameraX surface, real screen,
 * or AR composition root.
 *
 * Frame format (per RFC 1521 multipart/x-mixed-replace):
 *
 *   --<boundary>
 *   Content-Type: image/jpeg
 *   Content-Length: <bytes>
 *   <CRLF>
 *   <JPEG bytes>
 *   --<boundary>
 *   ...
 *
 * The implementation is forgiving:
 *   - Missing Content-Length falls back to scanning for the next
 *     `--<boundary>` marker.
 *   - Bare JPEG (no boundary at all) is accepted as a single-frame
 *     stream (the v1 daemon serves this shape).
 *   - Boundary detection is case-sensitive; daemons that emit a custom
 *     boundary should announce it in the response Content-Type header.
 */
class MjpegStream(
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        const val SOI: Byte = 0xFF.toByte() // start of image
        const val SOI2: Byte = 0xD8.toByte()
        const val EOI: Byte = 0xFF.toByte() // end of image
        const val EOI2: Byte = 0xD9.toByte()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 0 == infinite
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Open the stream and yield each JPEG frame as a ByteArray to
     * [onFrame]. Returns when the stream ends or [onFrame] returns false.
     *
     * @param url The MJPEG endpoint URL (typically /api/companion/screen-frame/...).
     * @param headers Request headers (the AR companion always passes the pairing token).
     * @param onFrame Called with each decoded JPEG. Return false to stop.
     */
    fun openStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        onFrame: (jpegBytes: ByteArray) -> Boolean,
    ) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("MJPEG stream HTTP ${resp.code}")
            val contentType = resp.header("Content-Type") ?: ""
            val boundary = parseBoundary(contentType)
            val body = resp.body ?: throw IOException("empty MJPEG body")
            val input = body.byteStream()

            if (boundary == null) {
                // v1 daemon serves a single JPEG. Read the whole thing and emit once.
                val bytes = input.readBytes()
                if (bytes.isNotEmpty()) onFrame(bytes)
                return
            }
            try {
                parseMultipart(input, boundary, onFrame)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // stream ended; normal completion
            }
        }
    }

    /**
     * Pure-Kotlin frame extractor. Public for testability.
     */
    internal fun parseMultipart(
        input: InputStream,
        boundary: String,
        onFrame: (ByteArray) -> Boolean,
    ) {
        val boundaryBytes = ("--$boundary").toByteArray(Charsets.US_ASCII)
        val buffer = ByteArrayOutputStream()
        val ringSize = boundaryBytes.size
        val ring = ByteArray(ringSize)
        var ringPos = 0
        var inFrame = false

        while (true) {
            val b = input.read()
            if (b == -1) {
                if (inFrame && buffer.size() > 0) {
                    val frame = trimToJpeg(buffer.toByteArray())
                    if (frame != null) onFrame(frame)
                }
                return
            }
            val byte = b.toByte()
            ring[ringPos] = byte
            ringPos = (ringPos + 1) % ringSize

            if (inFrame) buffer.write(b)

            if (matchesBoundary(ring, ringPos, boundaryBytes)) {
                if (inFrame) {
                    val raw = buffer.toByteArray()
                    val frame = trimToJpeg(raw.copyOf(raw.size - boundaryBytes.size))
                    if (frame != null) {
                        if (!onFrame(frame)) return
                    }
                    buffer.reset()
                }
                inFrame = true
                buffer.reset()
            }
        }
    }

    /** Find a JPEG payload in [data] by SOI/EOI markers. Returns the
     *  trimmed JPEG, or null if the data isn't a valid JPEG frame. */
    internal fun trimToJpeg(data: ByteArray): ByteArray? {
        if (data.size < 4) return null
        var start = -1
        for (i in 0 until data.size - 1) {
            if (data[i] == SOI && data[i + 1] == SOI2) {
                start = i; break
            }
        }
        if (start < 0) return null
        var end = -1
        for (i in data.size - 2 downTo start + 2) {
            if (data[i] == EOI && data[i + 1] == EOI2) {
                end = i + 2; break
            }
        }
        if (end < 0) return null
        return data.copyOfRange(start, end)
    }

    private fun matchesBoundary(ring: ByteArray, headPos: Int, expected: ByteArray): Boolean {
        if (ring.size != expected.size) return false
        for (i in expected.indices) {
            val ringIdx = (headPos + i) % ring.size
            if (ring[ringIdx] != expected[i]) return false
        }
        return true
    }

    /**
     * Extract the boundary token from a multipart Content-Type header.
     * Returns null if the response is not multipart.
     */
    internal fun parseBoundary(contentType: String): String? {
        val ct = contentType.trim()
        if (!ct.contains("multipart/", ignoreCase = true)) return null
        val match = Regex("""boundary=("?)([^";\s]+)\1""").find(ct) ?: return null
        return match.groupValues[2]
    }
}
