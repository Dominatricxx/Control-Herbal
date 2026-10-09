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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ProofOfWorkTest {

    /** Vector fijado con la implementación del servidor (firebase/functions/test/lib.test.js). */
    @Test fun matchesServerVector() = runBlocking {
        assertEquals("386", ProofOfWork.solve("00112233445566778899aabbccddeeff", 12))
    }

    @Test fun solutionHasRequiredLeadingZeroBits() = runBlocking {
        val salt = "ffeeddccbbaa99887766554433221100"
        val nonce = ProofOfWork.solve(salt, 16)
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt.$nonce".toByteArray())
        assertTrue(ProofOfWork.leadingZeroBits(digest) >= 16)
    }

    @Test fun leadingZeroBitsCountsLikeTheServer() {
        assertEquals(19, ProofOfWork.leadingZeroBits(byteArrayOf(0, 0, 0x10)))
        assertEquals(0, ProofOfWork.leadingZeroBits(byteArrayOf(0x80.toByte())))
        assertEquals(15, ProofOfWork.leadingZeroBits(byteArrayOf(0, 0x01)))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsAbsurdDifficulty() {
        runBlocking { ProofOfWork.solve("00112233445566778899aabbccddeeff", 60) }
    }
}
