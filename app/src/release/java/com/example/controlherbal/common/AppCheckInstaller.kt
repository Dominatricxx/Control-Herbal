package com.example.controlherbal.common

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.frontend.activities.main.*
import com.example.controlherbal.frontend.activities.auth.*
import com.example.controlherbal.frontend.activities.privacy.*
import com.example.controlherbal.frontend.activities.plant.*
import com.example.controlherbal.frontend.style.*

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Variante RELEASE: atestación real con Play Integrity. */
object AppCheckInstaller {
    fun install() {
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        appCheck.setTokenAutoRefreshEnabled(true)
    }
}
