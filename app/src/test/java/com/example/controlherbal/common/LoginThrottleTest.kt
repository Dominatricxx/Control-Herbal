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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mismos casos que firebase/functions/test/lib.test.js (limitador por cuenta). */
class LoginThrottleTest {

    private class MemStore : LoginThrottle.Store {
        val m = HashMap<String, Long>()
        override fun get(key: String) = m[key] ?: 0L
        override fun putAll(values: Map<String, Long>) { m.putAll(values) }
        override fun clear() = m.clear()
    }

    private var now = 10_000_000L
    private val store = MemStore()
    private val t = LoginThrottle(store) { now }

    @Test fun noBackoffUntilThirdFailure() {
        assertTrue(t.recordFailure().allowed)
        assertTrue(t.recordFailure().allowed)
        val d = t.recordFailure()
        assertFalse(d.allowed)
        assertEquals(5_000L, d.retryAfterMs)
        assertEquals(LoginThrottle.Reason.BACKOFF, d.reason)
    }

    @Test fun backoffDoubles() {
        repeat(3) { t.recordFailure() }
        now += 5_000
        assertEquals(10_000L, t.recordFailure().retryAfterMs)
    }

    @Test fun lockoutAtFifthFailureDoublesAndCaps() {
        repeat(5) { t.recordFailure() }
        var d = t.check()
        assertEquals(LoginThrottle.Reason.LOCKED, d.reason)
        assertEquals(15 * 60 * 1000L, d.retryAfterMs)
        now += 15 * 60 * 1000L
        repeat(5) { t.recordFailure() }
        assertEquals(30 * 60 * 1000L, t.check().retryAfterMs)
        repeat(12) { now += 24 * 3600 * 1000L; repeat(5) { t.recordFailure() } }
        d = t.check()
        assertEquals(24 * 3600 * 1000L, d.retryAfterMs)
    }

    @Test fun lockoutSurvivesRestart() {
        repeat(5) { t.recordFailure() }
        // "Reiniciar la app" = nueva instancia sobre el mismo almacén persistente.
        assertFalse(LoginThrottle(store) { now }.check().allowed)
    }

    @Test fun successResetsEverything() {
        repeat(5) { t.recordFailure() }
        now += 15 * 60 * 1000L
        t.recordSuccess()
        assertTrue(t.check().allowed)
        assertEquals(0L, t.consecutiveFailures)
    }

    @Test fun accountTagIsStableAndDoesNotLeakEmail() {
        val a = LoginThrottle.accountTag(" Ana@Example.com ")
        assertEquals(a, LoginThrottle.accountTag("ana@example.com"))
        assertFalse(a.contains("ana"))
        assertEquals(8, a.length)
    }
}
