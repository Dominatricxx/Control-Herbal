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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Solucionador del reto de prueba de trabajo del servidor (firebase/functions/lib/pow.js).
 * Hay que encontrar un `nonce` tal que SHA-256("<salt>.<nonce>") empiece por >= `bits` bits a cero.
 * Se resuelve fuera del hilo principal y es cancelable.
 */
object ProofOfWork {

    private const val MAX_BITS = 24            // el servidor nunca pide más de 22: protege de un reto absurdo
    private const val MAX_ITERATIONS = 1L shl 30

    suspend fun solve(salt: String, bits: Int): String = withContext(Dispatchers.Default) {
        require(bits in 1..MAX_BITS) { "dificultad fuera de rango" }
        val md = MessageDigest.getInstance("SHA-256")
        val prefix = "$salt.".toByteArray(Charsets.UTF_8)
        var nonce = 0L
        var found: String? = null
        while (found == null) {
            if ((nonce and 0xFFFL) == 0L) ensureActive()
            check(nonce < MAX_ITERATIONS) { "sin solución en el límite" }
            md.reset()
            md.update(prefix)
            md.update(nonce.toString().toByteArray(Charsets.US_ASCII))
            if (leadingZeroBits(md.digest()) >= bits) found = nonce.toString()
            nonce++
        }
        found
    }

    internal fun leadingZeroBits(bytes: ByteArray): Int {
        var n = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0) { n += 8; continue }
            n += Integer.numberOfLeadingZeros(v) - 24
            break
        }
        return n
    }
}
