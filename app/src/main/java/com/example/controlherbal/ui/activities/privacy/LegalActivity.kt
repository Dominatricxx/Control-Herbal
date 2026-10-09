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

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.example.controlherbal.R
import com.example.controlherbal.common.accessibility.LegalBlock
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.common.legal.LegalDocs
import com.example.controlherbal.common.accessibility.MarkdownLite

/** Muestra un documento legal como texto plano accesible (encabezados marcados para TalkBack). */
class LegalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal)

        val doc = LegalDoc.fromName(intent.getStringExtra(EXTRA_DOC))
        findViewById<TextView>(R.id.tvLegalTitle).text = doc.title
        findViewById<ImageButton>(R.id.btnLegalBack).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.legalContainer)
        MarkdownLite.parse(LegalDocs.load(this, doc)).forEach { container.addView(viewFor(it)) }
    }

    private fun viewFor(block: LegalBlock): TextView {
        val tv = TextView(this)
        tv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tv.setTextColor(0xFF212121.toInt())
        tv.setLineSpacing(0f, 1.2f)
        tv.setTextIsSelectable(true)
        val dp = resources.displayMetrics.density
        when (block) {
            is LegalBlock.Heading -> {
                tv.text = block.text
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (block.level == 1) 22f else 18f)
                tv.setTypeface(tv.typeface, Typeface.BOLD)
                tv.setTextColor(0xFF1B5E20.toInt())
                tv.setPadding(0, (if (block.level == 1) 8 else 20).times(dp).toInt(), 0, (6 * dp).toInt())
                ViewCompat.setAccessibilityHeading(tv, true)
            }
            is LegalBlock.Paragraph -> {
                tv.text = block.text
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                tv.setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }
            is LegalBlock.Bullet -> {
                tv.text = "•  ${block.text}"
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                tv.setPadding((12 * dp).toInt(), (3 * dp).toInt(), 0, (3 * dp).toInt())
            }
        }
        return tv
    }

    companion object {
        private const val EXTRA_DOC = "doc"
        fun intent(context: Context, doc: LegalDoc): Intent =
            Intent(context, LegalActivity::class.java).putExtra(EXTRA_DOC, doc.name)
    }
}
