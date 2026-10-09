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
import org.junit.Test

class PwnedPasswordsTest {
    @Test fun sha1MatchesKnownVector() {
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", PwnedPasswords.sha1Upper("password"))
    }

    @Test fun parsesRangeResponse() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2"
        assertEquals(3861493, PwnedPasswords.parseRange(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
        assertEquals(0, PwnedPasswords.parseRange(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"))
    }
}
