package com.example.controlherbal.common.legal

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.content.Context

/** Documentos legales empaquetados como assets (fuente única: carpeta /legal del repositorio). */
enum class LegalDoc(val assetName: String, val title: String) {
    PRIVACY("AVISO_DE_PRIVACIDAD.md", "Aviso de Privacidad"),
    TERMS("TERMINOS_DE_SERVICIO.md", "Términos de Servicio"),
    COOKIES("POLITICA_DE_COOKIES.md", "Política de Cookies"),
    CONSENTS("CONSENTIMIENTOS.md", "Registro de Consentimientos");

    companion object {
        fun fromName(name: String?): LegalDoc = values().firstOrNull { it.name == name } ?: PRIVACY
    }
}

object LegalDocs {
    /** Lee el documento desde assets; si falta devuelve un aviso en lugar de fallar. */
    fun load(context: Context, doc: LegalDoc): String =
        try {
            context.assets.open(doc.assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            SecureLogger.e("LegalDocs", "No se pudo leer ${doc.assetName}: ${e.javaClass.simpleName}")
            "# ${doc.title}\n\nNo se pudo cargar este documento. Consúltalo en el repositorio del proyecto."
        }
}
