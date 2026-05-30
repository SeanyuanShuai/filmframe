package com.seanyuan.filmframe

import com.seanyuan.filmframe.frame.TemplateAdjustments
import com.seanyuan.filmframe.ui.TemplateLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun everyTemplateHasAReadableLabel() {
        val ids = listOf("classic", "bold", "solid", "minimal", "polaroid")
        for (id in ids) {
            val meta = TemplateLabels.byId[id]
            assertNotNull("missing label for $id", meta)
            assertTrue("blank zh label for $id", meta!!.zh.isNotBlank())
            assertTrue("blank en label for $id", meta.en.isNotBlank())
        }
    }
}
