package dev.opencircuit.codetalker.ui.permissions

import android.Manifest
import android.os.Build

/**
 * CCT-32 Task B.1 — pure-data permission catalog.
 *
 * Centralises the user-visible rationale for every dangerous permission
 * the AR companion asks for. Keeps the manifest names + rationale strings
 * + recovery copy in one place so the rationale screen, onboarding, and
 * error banner can share the same source of truth.
 *
 * Pure Kotlin — no Android Context — so it unit-tests under JVM without
 * Robolectric.
 */
data class PermissionInfo(
    /** Manifest constant, e.g. `android.permission.CAMERA`. */
    val manifest: String,
    /** Human-readable name for headers. */
    val displayName: String,
    /** Why we need it (1–2 sentences). */
    val rationale: String,
    /** Hint shown when the user has permanently denied (Don't Ask Again). */
    val settingsHint: String,
)

object PermissionCatalog {

    val CAMERA = PermissionInfo(
        manifest = Manifest.permission.CAMERA,
        displayName = "Camera",
        rationale = "Codetalker needs the camera to scan the pairing QR code shown on your desktop dashboard. Frames are decoded on-device and never leave the glasses.",
        settingsHint = "Open Settings → Apps → Codetalker → Permissions and turn on Camera, then return.",
    )

    val MICROPHONE = PermissionInfo(
        manifest = Manifest.permission.RECORD_AUDIO,
        displayName = "Microphone",
        rationale = "The side button captures your voice so Claude can hear what you say. Audio is transcribed on-device by Android speech recognition before being sent to the daemon over your private network.",
        settingsHint = "Open Settings → Apps → Codetalker → Permissions and turn on Microphone, then return.",
    )

    /**
     * POST_NOTIFICATIONS only exists on Android 13+ (API 33). Older
     * devices implicitly grant notifications, so the rationale screen
     * skips this entry on lower API levels.
     */
    val NOTIFICATIONS = PermissionInfo(
        manifest = "android.permission.POST_NOTIFICATIONS",
        displayName = "Notifications",
        rationale = "Codetalker shows an ongoing notification while it's playing narration so Android keeps the audio stream alive when the screen is off.",
        settingsHint = "Open Settings → Apps → Codetalker → Notifications and allow them, then return.",
    )

    /** All permissions required for full functionality on this device. */
    fun required(): List<PermissionInfo> {
        val list = mutableListOf(CAMERA, MICROPHONE)
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(NOTIFICATIONS)
        }
        return list
    }

    fun forManifest(manifest: String): PermissionInfo? = when (manifest) {
        CAMERA.manifest -> CAMERA
        MICROPHONE.manifest -> MICROPHONE
        NOTIFICATIONS.manifest -> NOTIFICATIONS
        else -> null
    }
}
