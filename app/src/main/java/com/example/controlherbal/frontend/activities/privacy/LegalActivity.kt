package com.example.controlherbal.frontend.activities.privacy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.controlherbal.common.accessibility.MarkdownLite
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.common.legal.LegalDocs
import com.example.controlherbal.frontend.components.LegalScreen
import com.example.controlherbal.frontend.theme.ControlHerbalTheme

/** Muestra un documento legal como texto plano accesible adaptado al tema oscuro botánico. */
class LegalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val doc = LegalDoc.fromName(intent.getStringExtra(EXTRA_DOC))
        val rawMarkdown = LegalDocs.load(this, doc)
        val parsedBlocks = MarkdownLite.parse(rawMarkdown)

        setContent {
            ControlHerbalTheme {
                LegalScreen(
                    title = doc.title,
                    blocks = parsedBlocks,
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_DOC = "doc"
        fun intent(context: Context, doc: LegalDoc): Intent =
            Intent(context, LegalActivity::class.java).putExtra(EXTRA_DOC, doc.name)
    }
}
