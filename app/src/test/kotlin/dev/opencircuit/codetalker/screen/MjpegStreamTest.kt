package dev.opencircuit.codetalker.screen

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * CCT-31 Phase 9 — MJPEG parser unit tests.
 * No camera, no AR, no daemon — pure-bytes round trip.
 */
class MjpegStreamTest {

    private val s = MjpegStream()

    @Test
    fun `parseBoundary extracts boundary from multipart content-type`() {
        assertEquals("FrameBoundary", s.parseBoundary("multipart/x-mixed-replace; boundary=FrameBoundary"))
        assertEquals("--my-boundary--", s.parseBoundary("""multipart/x-mixed-replace; boundary="--my-boundary--""""))
    }

    @Test
    fun `parseBoundary returns null for non-multipart`() {
        assertNull(s.parseBoundary("image/jpeg"))
        assertNull(s.parseBoundary(""))
    }

    @Test
    fun `trimToJpeg slices SOI through EOI cleanly`() {
        // Garbage prefix + JPEG header + payload + EOI + garbage suffix.
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03, 0xFF.toByte(), 0xD9.toByte())
        val padded = byteArrayOf(0x00, 0x00) + jpeg + byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val trimmed = s.trimToJpeg(padded)
        assertArrayEquals(jpeg, trimmed)
    }

    @Test
    fun `trimToJpeg returns null if no JPEG markers`() {
        assertNull(s.trimToJpeg(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
    }

    @Test
    fun `parseMultipart yields each JPEG frame between boundaries`() {
        // A two-frame stream with boundary "B"
        val frame1 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x10, 0x11, 0xFF.toByte(), 0xD9.toByte())
        val frame2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x20, 0x21, 0x22, 0xFF.toByte(), 0xD9.toByte())
        val boundary = "B"
        val sep = "\r\n--$boundary\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray()
        val end = "\r\n--$boundary--\r\n".toByteArray()

        val streamBytes = sep + frame1 + sep + frame2 + end
        val collected = mutableListOf<ByteArray>()
        s.parseMultipart(ByteArrayInputStream(streamBytes), boundary) { f ->
            collected.add(f); true
        }
        assertEquals(2, collected.size)
        assertArrayEquals(frame1, collected[0])
        assertArrayEquals(frame2, collected[1])
    }

    @Test
    fun `parseMultipart stops when consumer returns false`() {
        val frame1 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x10, 0xFF.toByte(), 0xD9.toByte())
        val frame2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x20, 0xFF.toByte(), 0xD9.toByte())
        val sep = "\r\n--B\r\n\r\n".toByteArray()
        val streamBytes = sep + frame1 + sep + frame2 + sep
        val collected = mutableListOf<ByteArray>()
        s.parseMultipart(ByteArrayInputStream(streamBytes), "B") { f ->
            collected.add(f); false  // stop after first frame
        }
        assertEquals(1, collected.size)
    }
}
