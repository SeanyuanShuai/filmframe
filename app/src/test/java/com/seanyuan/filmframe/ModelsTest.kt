package com.seanyuan.filmframe

import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.TemplateAdjustments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun templateAdjustmentsDefaultsAreNeutral() {
        val d = TemplateAdjustments.Default
        assertEquals(1f, d.borderWidthMultiplier, 0f)
        assertEquals(1f, d.titleSizeMultiplier, 0f)
        assertTrue(d.showCaption)
    }

    @Test
    fun templateAdjustmentsCopyChangesOneField() {
        val a = TemplateAdjustments.Default.copy(borderWidthMultiplier = 1.3f)
        assertEquals(1.3f, a.borderWidthMultiplier, 0f)
        // copy leaves the rest untouched — equality with Default must NOT hold
        assertFalse(a == TemplateAdjustments.Default)
        assertEquals(TemplateAdjustments.Default.titleSizeMultiplier, a.titleSizeMultiplier, 0f)
    }

    @Test
    fun everyGroupHasNamedTemplatesAndUniqueIds() {
        val groups = FrameRenderer.groups
        assertTrue("expected several groups", groups.size >= 3)
        val allIds = mutableListOf<String>()
        for (g in groups) {
            assertTrue("group ${g.id} has a Chinese name", g.zh.isNotBlank())
            assertTrue("group ${g.id} has at least 2 templates", g.templates.size >= 2)
            for (t in g.templates) {
                assertTrue("template ${t.id} has a Chinese name", t.zhName.isNotBlank())
                allIds += t.id
            }
        }
        assertEquals("template ids must be unique", allIds.size, allIds.toSet().size)
    }

    @Test
    fun groupLookupsResolve() {
        val first = FrameRenderer.groups.first()
        assertEquals(first.id, FrameRenderer.groupById(first.id).id)
        val tpl = first.templates.first()
        assertEquals(first.id, FrameRenderer.groupOf(tpl.id).id)
        assertEquals(tpl.id, FrameRenderer.byId(tpl.id).id)
    }
}
