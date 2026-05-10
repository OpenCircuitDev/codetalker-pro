package dev.opencircuit.codetalker.ui.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task B.1 — verifies the permission catalog stays in sync with
 * the manifest strings the rationale screen will pass to the OS.
 */
class PermissionCatalogTest {

    @Test
    fun `CAMERA entry matches manifest constant`() {
        assertEquals(Manifest.permission.CAMERA, PermissionCatalog.CAMERA.manifest)
        assertTrue(PermissionCatalog.CAMERA.displayName.isNotBlank())
        assertTrue(PermissionCatalog.CAMERA.rationale.isNotBlank())
        assertTrue(PermissionCatalog.CAMERA.settingsHint.isNotBlank())
    }

    @Test
    fun `MICROPHONE entry matches manifest constant`() {
        assertEquals(Manifest.permission.RECORD_AUDIO, PermissionCatalog.MICROPHONE.manifest)
        assertTrue(PermissionCatalog.MICROPHONE.rationale.contains("microphone", ignoreCase = true)
                || PermissionCatalog.MICROPHONE.rationale.contains("voice", ignoreCase = true))
    }

    @Test
    fun `NOTIFICATIONS entry uses the API-33 string`() {
        assertEquals("android.permission.POST_NOTIFICATIONS", PermissionCatalog.NOTIFICATIONS.manifest)
    }

    @Test
    fun `forManifest returns the matching entry`() {
        assertNotNull(PermissionCatalog.forManifest(Manifest.permission.CAMERA))
        assertNotNull(PermissionCatalog.forManifest(Manifest.permission.RECORD_AUDIO))
        assertNotNull(PermissionCatalog.forManifest("android.permission.POST_NOTIFICATIONS"))
        assertNull(PermissionCatalog.forManifest("android.permission.SEND_SMS"))
    }

    @Test
    fun `required always includes camera and mic`() {
        val req = PermissionCatalog.required()
        assertTrue("camera in required", req.any { it.manifest == Manifest.permission.CAMERA })
        assertTrue("mic in required", req.any { it.manifest == Manifest.permission.RECORD_AUDIO })
    }
}
