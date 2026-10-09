package com.example.controlherbal.common.security

import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import com.nulabinc.zxcvbn.Zxcvbn
import java.text.Normalizer

/**
 * Política de contraseñas. Misma especificación que firebase/scripts/lib/passwordPolicy.mjs
 * (el servidor la aplica al crear cuentas; Firebase Auth aplica además longitud y clases de caracteres).
 *
 * Reglas duras: >= 12 y <= 128 caracteres; mayúscula, minúscula, número y símbolo (ASCII; el símbolo debe ser
 * uno de los que acepta Firebase Auth); no contiene correo/nombre; sin 4+ caracteres iguales seguidos; sin
 * secuencias de 5+ (abcde, 12345, qwert…); su "núcleo" normalizado no es una contraseña común
 * (P@ssw0rd!2024 -> password). Además zxcvbn debe puntuar >= 3. La comprobación contra HaveIBeenPwned
 * es aparte ([PwnedPasswords]) porque necesita red.
 *
 * Clase sin dependencias de Android: se prueba con JUnit local.
 */
class PasswordPolicy(
    private val commonCores: Set<String>,
    private val scorer: (password: String, userInputs: List<String>) -> Int = ZXCVBN_SCORER,
) {

    enum class Rule { LENGTH, MAX_LENGTH, UPPER, LOWER, DIGIT, SYMBOL, PERSONAL, REPEAT, SEQUENCE, COMMON, WEAK, PWNED }

    data class Result(val failures: Set<Rule>, val score: Int) {
        val ok: Boolean get() = failures.isEmpty()

        /** Nivel 0..4 para el medidor: se limita a 1 si falla alguna regla dura. */
        val level: Int get() = if (failures.any { it != Rule.WEAK }) minOf(score, 1) else score
    }

    fun evaluate(password: String, userInputs: List<String> = emptyList()): Result {
        val failures = linkedSetOf<Rule>()
        val len = password.codePointCount(0, password.length)
        if (len < MIN_LENGTH) failures += Rule.LENGTH
        if (len > MAX_LENGTH) failures += Rule.MAX_LENGTH
        if (password.none { it in 'A'..'Z' }) failures += Rule.UPPER
        if (password.none { it in 'a'..'z' }) failures += Rule.LOWER
        if (password.none { it in '0'..'9' }) failures += Rule.DIGIT
        if (password.none { it in SYMBOLS }) failures += Rule.SYMBOL
        if (containsPersonal(password, userInputs)) failures += Rule.PERSONAL
        if (hasRepeat(password)) failures += Rule.REPEAT
        if (hasSequence(password)) failures += Rule.SEQUENCE
        if (cores(password).any { it.isNotEmpty() && it in commonCores }) failures += Rule.COMMON

        val score = scorer(password.take(256), userInputs.map { it.lowercase() })
        if (score < MIN_SCORE) failures += Rule.WEAK
        return Result(failures, score)
    }

    companion object {
        const val MIN_LENGTH = 12
        const val MAX_LENGTH = 128
        const val MIN_SCORE = 3

        /** Conjunto de caracteres especiales que Firebase Auth reconoce como "no alfanuméricos". */
        const val SYMBOLS = "^\$*.[]{}()?\"!@#%&/\\,><':;|_~`+=-"

        private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "abcdefghijklmnopqrstuvwxyz")
        private val LEET = mapOf('@' to 'a', '0' to 'o', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '$' to 's', '!' to 'i', '+' to 't')
        private val COMBINING_MARKS = Regex("\\p{M}+")
        private val TOKEN_SPLIT = Regex("[^a-z0-9ñáéíóúü]+", RegexOption.IGNORE_CASE)

        // Zxcvbn() carga sus diccionarios al construirse (cientos de ms): una sola instancia, perezosa.
        private val zxcvbn: Zxcvbn by lazy { Zxcvbn() }
        val ZXCVBN_SCORER: (String, List<String>) -> Int = { pw, inputs -> zxcvbn.measure(pw, inputs).score }

        /** Precarga zxcvbn en segundo plano para que el primer análisis en pantalla sea instantáneo. */
        fun warmUp() { zxcvbn }

        /** Interpreta el contenido de assets/common_passwords.txt (una palabra por línea, # = comentario). */
        fun parseCores(text: String): Set<String> =
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

        /**
         * Núcleos normalizados: minúsculas, sin acentos, leet->letras y solo a-z. Se generan variantes
         * (recortando o no los bordes no alfabéticos; "1" como i o l): un "!" o un "2024" al final son
         * decoración, pero un "@" al principio puede ser una "a".
         */
        internal fun cores(password: String): Set<String> {
            val base = Normalizer.normalize(password, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()
            val out = HashSet<String>()
            for (trimStart in listOf(false, true)) for (trimEnd in listOf(false, true)) for (one in listOf('i', 'l')) {
                var s = base
                if (trimStart) s = s.dropWhile { it !in 'a'..'z' }
                if (trimEnd) s = s.dropLastWhile { it !in 'a'..'z' }
                val sb = StringBuilder()
                for (ch in s) {
                    val m = if (ch == '1') one else (LEET[ch] ?: ch)
                    if (m in 'a'..'z') sb.append(m)
                }
                if (sb.isNotEmpty()) out += sb.toString()
            }
            return out
        }

        internal fun hasSequence(password: String, len: Int = 5): Boolean {
            val lower = password.lowercase()
            for (row in ROWS) for (r in listOf(row, row.reversed())) {
                for (i in 0..(r.length - len)) if (lower.contains(r.substring(i, i + len))) return true
            }
            return false
        }

        internal fun hasRepeat(password: String, n: Int = 4): Boolean {
            val cps = password.codePoints().toArray()
            var run = 1
            for (i in 1 until cps.size) {
                run = if (cps[i] == cps[i - 1]) run + 1 else 1
                if (run >= n) return true
            }
            return false
        }

        internal fun containsPersonal(password: String, userInputs: List<String>): Boolean {
            val lower = password.lowercase()
            for (raw in userInputs) for (tok in raw.lowercase().split(TOKEN_SPLIT)) {
                if (tok.length >= 4 && lower.contains(tok)) return true
            }
            return false
        }
    }
}
