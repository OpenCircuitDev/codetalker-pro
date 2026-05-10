package dev.opencircuit.codetalker.ui.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCT-32 Task A.4 — MarkupQuickCatalog invariants.
 *
 * The catalog drives the markup panel UI; tests guard against drift
 * between groups, form specs, and the daemon's accepted kinds.
 */
class MarkupKindResolutionTest {

    @Test
    fun `every form referenced in a group has a spec`() {
        MarkupQuickCatalog.GROUPS.forEach { group ->
            group.forms.forEach { formName ->
                val spec = MarkupQuickCatalog.FORMS[formName]
                assertNotNull("form $formName should have a spec", spec)
                assertEquals(formName, spec!!.name)
            }
        }
    }

    @Test
    fun `no group is empty`() {
        MarkupQuickCatalog.GROUPS.forEach { group ->
            assertTrue("group ${group.title} must contain at least one form", group.forms.isNotEmpty())
        }
    }

    @Test
    fun `every spec's first allowed kind is the safe default`() {
        // Convention: index 0 of allowedKinds is the conservative
        // option (skip / lowest verbosity), so when a session has no
        // overlay value we render that without speaking volume.
        MarkupQuickCatalog.FORMS.forEach { (_, spec) ->
            assertTrue(
                "spec ${spec.name} must have at least one allowed kind",
                spec.allowedKinds.isNotEmpty(),
            )
            val first = spec.allowedKinds.first()
            // Either explicit "skip" or the most-conservative kind.
            assertTrue(
                "spec ${spec.name} default '$first' should be conservative (skip/identifier_only/filename/count_only/summarize)",
                first in setOf("skip", "identifier_only", "filename", "count_only", "summarize"),
            )
        }
    }

    @Test
    fun `exactly six forms across three groups`() {
        assertEquals(3, MarkupQuickCatalog.GROUPS.size)
        val totalForms = MarkupQuickCatalog.GROUPS.sumOf { it.forms.size }
        assertEquals(6, totalForms)
        assertEquals(6, MarkupQuickCatalog.FORMS.size)
    }

    @Test
    fun `code_fence allowed kinds match daemon expectations`() {
        // narration_kinds.py recognises skip / describe / read for code fences.
        val spec = MarkupQuickCatalog.FORMS["code_fence"]!!
        assertEquals(listOf("skip", "describe", "read"), spec.allowedKinds)
    }
}
