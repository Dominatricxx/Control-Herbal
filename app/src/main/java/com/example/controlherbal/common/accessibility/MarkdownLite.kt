package com.example.controlherbal.common.accessibility

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

/** Bloques de un documento legal ya interpretado. */
sealed class LegalBlock {
    data class Heading(val level: Int, val text: String) : LegalBlock()
    data class Paragraph(val text: String) : LegalBlock()
    data class Bullet(val text: String) : LegalBlock()
}

/**
 * Intérprete mínimo de Markdown para los documentos de /legal: títulos (#, ##), viñetas (- ) y párrafos.
 * Los documentos legales evitan tablas, imágenes y enlaces a propósito, así que no se necesita más.
 * No ejecuta ni interpreta HTML: todo se muestra como texto plano.
 */
object MarkdownLite {

    fun parse(markdown: String): List<LegalBlock> {
        val blocks = mutableListOf<LegalBlock>()
        val paragraph = StringBuilder()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += LegalBlock.Paragraph(clean(paragraph.toString()))
                paragraph.setLength(0)
            }
        }

        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("## ") -> { flush(); blocks += LegalBlock.Heading(2, clean(line.drop(3))) }
                line.startsWith("# ") -> { flush(); blocks += LegalBlock.Heading(1, clean(line.drop(2))) }
                line.startsWith("- ") -> { flush(); blocks += LegalBlock.Bullet(clean(line.drop(2))) }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        flush()
        return blocks
    }

    /** Quita marcas de énfasis simples; el texto restante se muestra tal cual. */
    fun clean(text: String): String = text.replace("**", "").replace("`", "").trim()

    /** Marcadores {{...}} que aún no se han rellenado. */
    fun unresolvedPlaceholders(markdown: String): List<String> =
        Regex("\\{\\{[A-Z_]+}}").findAll(markdown).map { it.value }.distinct().toList()

    /** Extrae "Versión X" de la primera líneas del documento, para comprobar coherencia con LEGAL_VERSION. */
    fun declaredVersion(markdown: String): String? =
        Regex("Versi[oó]n\\s+([0-9][0-9A-Za-z.\\-]*)").find(markdown)?.groupValues?.get(1)
}
