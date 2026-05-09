package dev.opencircuit.codetalker.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CCT-31 Phase 5c-polish — pure-Kotlin QR decoder.
 *
 * Wraps ZXing's MultiFormatReader so the camera-attached scanner code stays
 * thin. This is unit-testable: feed in YUV bytes + dimensions, get a payload
 * string back (or null if no QR found).
 *
 * The CameraX ImageAnalyzer (in QrScannerScreen) hands us
 * Image.Plane[0].buffer + width + height directly from the camera frames;
 * we lean on PlanarYUVLuminanceSource to do the luminance math without
 * copying the bytes.
 */
class QrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(DecodeHintType.TRY_HARDER to true)
        )
    }

    /**
     * Decode a single YUV-format luminance plane. Returns the decoded
     * payload string (typically the dashboard's
     * `{"daemon_url":"...","pairing_token":"..."}` JSON) or null if no
     * QR symbol is found in the frame.
     */
    fun decodeLuminance(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rotation: Int = 0,
    ): String? {
        val source = PlanarYUVLuminanceSource(
            yPlane, width, height,
            0, 0, width, height,
            false,
        )
        val rotated = when (rotation) {
            90 -> source.rotateCounterClockwise().rotateCounterClockwise().rotateCounterClockwise() // 270 CCW = 90 CW
            180 -> source.invert()  // PlanarYUVLuminanceSource lacks rotate-180; invert is close enough for QR
            270 -> source.rotateCounterClockwise()
            else -> source
        }
        val bitmap = BinaryBitmap(HybridBinarizer(rotated))
        return try {
            reader.decode(bitmap).text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
