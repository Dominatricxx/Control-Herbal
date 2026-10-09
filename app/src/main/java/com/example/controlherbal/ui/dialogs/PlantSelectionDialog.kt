package com.example.controlherbal.ui.dialogs

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

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.controlherbal.R
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant

/**
 * PlantSelectionDialog: Encapsula el diálogo de selección de plantas activas en el sistema.
 */
object PlantSelectionDialog {

    fun show(
        activity: Activity,
        plants: List<Plant>,
        onPlantSelected: (Plant) -> Unit
    ) {
        val builder = AlertDialog.Builder(activity)
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_rounded_list, null)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val listView = dialogView.findViewById<ListView>(R.id.dialogListView)
        val adapter = object : ArrayAdapter<Plant>(activity, R.layout.item_plant_selection, R.id.tvItemPlantName, plants) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: activity.layoutInflater.inflate(R.layout.item_plant_selection, parent, false)
                val plant = getItem(position) ?: return row

                val tvName = row.findViewById<TextView>(R.id.tvItemPlantName)
                val tvDetails = row.findViewById<TextView>(R.id.tvItemPlantDetails)
                val tvScientific = row.findViewById<TextView>(R.id.tvItemPlantScientific)

                tvName.text = SecurityUtils.sanitizeText(plant.name)

                val fullType = plant.type
                val category = if (fullType.contains("Categoría:")) fullType.substringAfter("Categoría:").substringBefore("|").trim() else ""
                val typePart = if (fullType.contains("Tipo:")) fullType.substringAfter("Tipo:").substringBefore("(").trim() else fullType.substringBefore("(")
                val scientific = if (fullType.contains("(")) fullType.substringAfter("(").substringBefore(")") else ""

                tvDetails.text = "${if (category.isNotEmpty()) "$category | " else ""}$typePart | ${plant.environment}"
                tvScientific.text = if (scientific.isNotEmpty()) "($scientific)" else ""
                tvScientific.visibility = if (scientific.isNotEmpty()) View.VISIBLE else View.GONE

                return row
            }
        }

        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            onPlantSelected(plants[position])
            dialog.dismiss()
        }

        dialog.show()
    }
}
