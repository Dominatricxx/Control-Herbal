package com.example.controlherbal.ui.activities.privacy

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.R
import com.example.controlherbal.common.legal.ConsentManager
import com.example.controlherbal.common.legal.LegalDoc

/**
 * Primera pantalla de la app: aceptación del Aviso de Privacidad y los Términos de Servicio.
 *  - La casilla empieza SIN marcar y "Continuar" permanece desactivado hasta que la persona la marca.
 *  - Los documentos completos están a un toque.
 *  - Si no acepta, la app se cierra: no se procesa ningún dato.
 */
class ConsentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consent)

        val cb = findViewById<CheckBox>(R.id.cbAccept)
        val btnContinue = findViewById<Button>(R.id.btnConsentContinue)

        cb.setOnCheckedChangeListener { _, checked -> btnContinue.isEnabled = checked }

        findViewById<Button>(R.id.btnReadPrivacy).setOnClickListener { open(LegalDoc.PRIVACY) }
        findViewById<Button>(R.id.btnReadTerms).setOnClickListener { open(LegalDoc.TERMS) }
        findViewById<Button>(R.id.btnReadCookies).setOnClickListener { open(LegalDoc.COOKIES) }

        btnContinue.setOnClickListener {
            if (!cb.isChecked) return@setOnClickListener
            ConsentManager.accept(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnConsentDecline).setOnClickListener { finishAffinity() }
    }

    private fun open(doc: LegalDoc) = startActivity(LegalActivity.intent(this, doc))
}
