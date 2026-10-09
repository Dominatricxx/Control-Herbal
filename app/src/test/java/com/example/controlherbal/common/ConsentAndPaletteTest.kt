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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentAndPaletteTest {

    @Test fun consent_onlyCurrentVersionIsAccepted() {
        assertTrue(ConsentManager.isAcceptedVersion(AppConstants.LEGAL_VERSION))
        assertFalse(ConsentManager.isAcceptedVersion(null))
        assertFalse(ConsentManager.isAcceptedVersion("1999.01"))
    }

    @Test fun paletteMode_unknownIdFallsBackToStandard() {
        assertEquals(PaletteMode.STANDARD, PaletteMode.fromId(null))
        assertEquals(PaletteMode.STANDARD, PaletteMode.fromId("inexistente"))
        PaletteMode.values().forEach { assertEquals(it, PaletteMode.fromId(it.id)) }
    }

    @Test fun everyModeHasThreeDistinctStatusColors() {
        PaletteMode.values().forEach { mode ->
            val c = StatusPalette.colors(mode)
            assertEquals(3, setOf(c.ok, c.warn, c.critical).size)
        }
    }

    @Test fun statusIsNeverOnlyColor() {
        StatusLevel.values().forEach {
            assertTrue(it.symbol.isNotBlank())
            assertTrue(it.describe().contains(it.label))
        }
        assertEquals("✖ Riesgo: IRH alto", StatusLevel.CRITICAL.describe("IRH alto"))
    }

    @Test fun forLevel_matchesFields() {
        val c = StatusPalette.colors(PaletteMode.RED_GREEN)
        assertEquals(c.ok, c.forLevel(StatusLevel.OK))
        assertEquals(c.critical, c.forLevel(StatusLevel.CRITICAL))
        assertNotEquals(c.warn, c.forLevel(StatusLevel.CRITICAL))
    }
}
