package com.example.controlherbal.common

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {

    @Test fun parse_headingsBulletsAndParagraphs() {
        val b = MarkdownLite.parse("# T\n\nHola **mundo**\ncontinúa\n\n## S\n- uno\n- dos\n\nfin")
        assertEquals(LegalBlock.Heading(1, "T"), b[0])
        assertEquals(LegalBlock.Paragraph("Hola mundo continúa"), b[1])
        assertEquals(LegalBlock.Heading(2, "S"), b[2])
        assertEquals(LegalBlock.Bullet("uno"), b[3])
        assertEquals(LegalBlock.Bullet("dos"), b[4])
        assertEquals(LegalBlock.Paragraph("fin"), b[5])
        assertEquals(6, b.size)
    }

    @Test fun parse_doesNotInterpretHtml() {
        assertEquals(LegalBlock.Paragraph("<b>x</b>"), MarkdownLite.parse("<b>x</b>")[0])
    }

    @Test fun placeholders_areDetectedOnce() {
        assertEquals(listOf("{{X_Y}}", "{{Z}}"), MarkdownLite.unresolvedPlaceholders("a {{X_Y}} b {{X_Y}} {{Z}}"))
        assertTrue(MarkdownLite.unresolvedPlaceholders("sin marcadores").isEmpty())
    }

    @Test fun declaredVersion_isExtracted() {
        assertEquals("2026.10", MarkdownLite.declaredVersion("# Aviso\n\nVersión 2026.10 · Vigente desde hoy"))
        assertNull(MarkdownLite.declaredVersion("sin versión"))
    }
}
