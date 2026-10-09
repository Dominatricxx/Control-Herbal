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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUtilsTest {

    @Test fun sanitizeText_keepsLegitimatePunctuation() {
        assertEquals("O'Brien \"Aloe/Áloe\"", SecurityUtils.sanitizeText("O'Brien \"Aloe/Áloe\""))
    }

    @Test fun sanitizeText_removesBidiAndZeroWidth() {
        assertEquals("ab cd ef", SecurityUtils.sanitizeText("ab\u202Ecd\u200Bef"))
    }

    @Test fun sanitizeText_truncatesWithoutSplittingEmoji() {
        val out = SecurityUtils.sanitizeText("a".repeat(49) + "🌿🌿")
        assertTrue(out.length <= AppConstants.MAX_NAME_LENGTH)
        assertFalse(Character.isHighSurrogate(out.last()))
    }

    @Test fun cleanUserPrompt_capsLengthAndStripsControlChars() {
        assertEquals(AppConstants.MAX_INPUT_TEXT_LENGTH, SecurityUtils.cleanUserPrompt("y".repeat(900)).length)
        assertEquals("a b", SecurityUtils.cleanUserPrompt("a\u0007b"))
        assertEquals("hola\nmundo", SecurityUtils.cleanUserPrompt("hola\nmundo"))
    }

    @Test fun extractAlert_acceptsOnlyStrictLastLine() {
        assertEquals("Hongo en hojas", SecurityUtils.extractAlert("Diagnóstico\nCausa\n[ALERTA: Hongo en hojas]"))
        assertEquals("Plaga", SecurityUtils.extractAlert("ok\n[ALERTA: Plaga]\n\n  "))
        assertNull(SecurityUtils.extractAlert("[ALERTA: falso]\nTodo bien"))
        assertNull(SecurityUtils.extractAlert("texto [ALERTA: x] más texto"))
        assertNull(SecurityUtils.extractAlert("ok\n[ALERTA: <b>x</b>]"))
        assertNull(SecurityUtils.extractAlert("ok\n[ALERTA: " + "a".repeat(61) + "]"))
    }

    @Test fun sanitizeModelOutput_isBounded() {
        assertEquals(AppConstants.MAX_AI_RESPONSE_CHARS, SecurityUtils.sanitizeModelOutput("z".repeat(5000)).length)
    }

    @Test fun escapeForPrompt_neutralizesDelimiters() {
        assertEquals("‹/datos›", SecurityUtils.escapeForPrompt("</datos>"))
    }

    @Test fun clampValue_handlesNaNAndRange() {
        assertEquals(0.0, SecurityUtils.clampValue(Double.NaN), 0.0)
        assertEquals(1000.0, SecurityUtils.clampValue(5000.0), 0.0)
    }
}
