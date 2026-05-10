package dev.opencircuit.codetalker.ui.pickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task A.3 — pickers catalog sanity.
 *
 * Compose UI tests would need androidTest; these JVM tests cover the
 * catalogs to guarantee the daemon-recognised values stay in sync as we
 * iterate.
 */
class PickerCatalogTest {

    @Test
    fun `mode picker exposes the four daemon-recognised modes`() {
        val values = MODE_OPTIONS.map { it.first }
        assertEquals(listOf("brief", "direct", "live", "trigger"), values)
    }

    @Test
    fun `mode labels are human readable`() {
        MODE_OPTIONS.forEach { (_, label) ->
            assertTrue("label not blank", label.isNotBlank())
            // labels are Title-cased, not raw daemon keys
            assertTrue("label '$label' should differ from a raw lowercase key",
                label.first().isUpperCase() || label.contains(" "))
        }
    }

    @Test
    fun `cadence picker covers slow normal fast`() {
        val values = CADENCE_OPTIONS.map { it.first }
        assertEquals(listOf("slow", "normal", "fast"), values)
    }

    @Test
    fun `engines list to probe is non-empty and contains piper`() {
        assertTrue("probe list must include piper", ENGINES_TO_PROBE.contains("piper"))
        assertTrue("probe list non-empty", ENGINES_TO_PROBE.isNotEmpty())
    }
}
