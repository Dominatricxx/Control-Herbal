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

import com.example.controlherbal.common.security.PasswordPolicy.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mismos casos que firebase/scripts/test/passwordPolicy.test.mjs (la especificación es única). */
class PasswordPolicyTest {

    private val cores: Set<String> = PasswordPolicy.parseCores(
        listOf("src/main/assets/common_passwords.txt", "app/src/main/assets/common_passwords.txt")
            .map(::File).first { it.exists() }.readText(),
    )

    // Puntuador fijo para probar solo las reglas duras; zxcvbn real se prueba aparte.
    private fun policy(score: Int = 4) = PasswordPolicy(cores) { _, _ -> score }
    private fun fails(pw: String, inputs: List<String> = emptyList()) = policy().evaluate(pw, inputs).failures

    @Test fun typicalPasswordsAreRejected() {
        for (pw in listOf("password123", "12345678", "qwerty123456", "Password123!", "P@ssw0rd!2024", "Admin1234567!", "Welcome2024!!", "ILoveYou123!!")) {
            assertTrue(pw, fails(pw).isNotEmpty())
        }
    }

    @Test fun lengthLimits() {
        assertTrue(Rule.LENGTH in fails("Ab1!xyzQ9m"))
        assertTrue(Rule.MAX_LENGTH in fails("Ab1!" + "x".repeat(130)))
    }

    @Test fun requiresAllCharacterClasses() {
        val base = "Zq7!mKp2#vXw"
        assertTrue(fails(base).isEmpty())
        assertTrue(Rule.UPPER in fails(base.lowercase()))
        assertTrue(Rule.LOWER in fails(base.uppercase()))
        assertTrue(Rule.DIGIT in fails("Zqpmkpxvxwab!"))
        assertTrue(Rule.SYMBOL in fails("Zq7mKp2vXwab"))
    }

    @Test fun spaceAndUnsupportedSymbolsDoNotCountAsSymbol() {
        assertTrue(Rule.SYMBOL in fails("Zq7 mKp2 vXwab"))
        assertTrue(Rule.SYMBOL in fails("Zq7€mKp2€vXwab"))
    }

    @Test fun commonCoreIsDetectedAfterNormalisation() {
        assertTrue("password" in PasswordPolicy.cores("P@ssw0rd!2024"))
        assertTrue(Rule.COMMON in fails("Contraseña#2024"))
        assertTrue(Rule.COMMON in fails("C0ntr@señ@"))
        assertTrue(Rule.COMMON in fails("ControlHerbal#2026"))
    }

    @Test fun longPassphraseWithOneCommonWordIsAllowed() {
        assertTrue(fails("Dragonfly-Orchid-Tractor-92").isEmpty())
    }

    @Test fun sequencesAndRepeats() {
        assertTrue(Rule.SEQUENCE in fails("Xk!12345Tmqrz"))
        assertTrue(Rule.SEQUENCE in fails("Xk!qwertyTmqz9"))
        assertTrue(Rule.SEQUENCE in fails("Xk!9876543Tmqz"))
        assertTrue(Rule.REPEAT in fails("Xk!aaaaTmqz9wp"))
    }

    @Test fun personalDataIsRejected() {
        assertTrue(Rule.PERSONAL in fails("Maria#Lopez-7391xz", listOf("maria.lopez@example.com")))
        assertFalse(Rule.PERSONAL in fails("Zq7!mKp2#vXw", listOf("maria.lopez@example.com")))
    }

    @Test fun zxcvbnThresholdAndMeterCap() {
        val low = policy(2).evaluate("Zq7!mKp2#vXw")
        assertTrue(Rule.WEAK in low.failures && !low.ok)
        assertEquals(4, policy(4).evaluate("Zq7!mKp2#vXw").level)
        assertEquals(1, policy(4).evaluate("Abc!1xyz").level) // regla dura rota => medidor limitado a 1
    }

    @Test fun realZxcvbnRejectsPredictablePassword() {
        val real = PasswordPolicy(cores)
        assertFalse(real.evaluate("Password123!").ok)
        assertTrue(real.evaluate("Zq7!mKp2#vXw-Orchid").score >= PasswordPolicy.MIN_SCORE)
    }
}
