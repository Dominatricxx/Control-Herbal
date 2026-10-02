package com.example.controlherbal.ui.activities

import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.controlherbal.R
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun MainActivity.showPlantInfo() {
    currentPlant?.let { plant ->
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_options, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = "Planta: ${plant.name}\nEspecie: ${plant.type}\nEntorno: ${plant.environment}"

        AlertDialog.Builder(this)
            .setTitle("Detalles de la Planta")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}

fun MainActivity.clearAiDiagnosis() {
    currentPlant?.let { plant ->
        val activity = this
        ioScope.launch {
            databaseLocal.plantDao().update(plant.copy(aiDiagnosis = null, aiRecommendation = null))
            currentPlant = databaseLocal.plantDao().getSelectedPlant()
            withContext(Dispatchers.Main) {
                cardAiDiagnosis.visibility = View.GONE
                Toast.makeText(activity, "Diagnóstico limpiado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun MainActivity.showDataControlDialog() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_data_control, null)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()

    dialogView.findViewById<View>(R.id.btnImportFirebase)?.setOnClickListener {
        dialog.dismiss()
        importDataFromFirebase()
    }
    dialogView.findViewById<View>(R.id.btnResetLocal)?.setOnClickListener {
        dialog.dismiss()
        showResetDataDialog()
    }
    dialogView.findViewById<View>(R.id.btnCancelControl)?.setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}

fun MainActivity.importDataFromFirebase() {
    val plant = currentPlant ?: return
    val activity = this
    val progressDialog = AlertDialog.Builder(activity).setMessage("Importando registros...").setCancelable(false).show()
    databaseFirebase.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            ioScope.launch {
                try {
                    if (snapshot.exists()) {
                        val readingsToInsert = mutableListOf<SensorReading>()
                        fun processNode(data: DataSnapshot) {
                            val temp = (data.child("temp").value as? Number)?.toDouble() ?: return
                            val hum = (data.child("hum").value as? Number)?.toDouble() ?: 0.0
                            val luzRaw = (data.child("luz_raw").value as? Number)?.toDouble() ?: (data.child("luz").value as? Number)?.toDouble() ?: 0.0
                            val luz = PredictiveTheorem.filtrarSensibilidadLuz(luzRaw)
                            val soil = (data.child("soil").value as? Number)?.toDouble() ?: 0.0
                            val irh = (data.child("irh").value as? Number)?.toDouble() ?: 0.0
                            val seq = (data.child("seq").value as? Number)?.toDouble() ?: 0.0
                            val somb = (data.child("somb").value as? Number)?.toDouble() ?: 0.0
                            val action = data.child("acc").value as? String ?: ""
                            val timestamp = (data.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
                            readingsToInsert.add(SensorReading(timestamp, plant.id, temp, hum, luz, soil, irh, seq, somb, action))
                        }
                        if (snapshot.hasChild("history")) snapshot.child("history").children.forEach { processNode(it) } else processNode(snapshot)
                        if (readingsToInsert.isNotEmpty()) {
                            readingsToInsert.forEach { databaseLocal.sensorDao().insert(it) }
                            withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(activity, "Importados ${readingsToInsert.size} ✅", Toast.LENGTH_LONG).show(); loadDataAndDrawChart() }
                        } else withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(activity, "Sin datos válidos", Toast.LENGTH_SHORT).show() }
                    } else withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(activity, "Sin datos en Firebase", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) { withContext(Dispatchers.Main) { progressDialog.dismiss(); Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
            }
        }
        override fun onCancelled(error: DatabaseError) { progressDialog.dismiss(); Toast.makeText(activity, "Error de conexión", Toast.LENGTH_SHORT).show() }
    })
}

fun MainActivity.showResetDataDialog() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_options, null)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .create()

    dialogView.findViewById<View>(R.id.btnResetAll)?.setOnClickListener {
        dialog.dismiss()
        resetData(0, 0, 0, true)
    }
    dialogView.findViewById<View>(R.id.btnReset24h)?.setOnClickListener {
        dialog.dismiss()
        val oneDayAgo = System.currentTimeMillis() - 86400000
        resetData(1, oneDayAgo, System.currentTimeMillis(), false)
    }
    dialogView.findViewById<View>(R.id.btnCancelReset)?.setOnClickListener {
        dialog.dismiss()
    }
    dialog.show()
}

fun MainActivity.showEditNameDialog() {
    currentPlant?.let { plant ->
        val activity = this
        val input = EditText(this).apply {
            setText(plant.name)
            setSelection(plant.name.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Editar Nombre de Planta")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    ioScope.launch {
                        databaseLocal.plantDao().update(plant.copy(name = newName))
                        currentPlant = databaseLocal.plantDao().getSelectedPlant()
                        withContext(Dispatchers.Main) {
                            tvPlantNameAndEmoji?.text = "🌱 $newName"
                            Toast.makeText(activity, "Nombre actualizado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

fun MainActivity.showDeletePlantDialog() {
    currentPlant?.let { plant ->
        AlertDialog.Builder(this)
            .setTitle("Eliminar Planta")
            .setMessage("¿Estás seguro de eliminar a '${plant.name}'? Se borrarán sus registros asociados.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteCurrentPlant()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

fun MainActivity.deleteCurrentPlant() {
    currentPlant?.let { plant ->
        val activity = this
        ioScope.launch {
            databaseLocal.sensorDao().deleteAllByPlantId(plant.id)
            databaseLocal.plantDao().delete(plant)
            val remainingPlants = databaseLocal.plantDao().getAll()
            withContext(Dispatchers.Main) {
                if (remainingPlants.isNotEmpty()) {
                    val nextPlant = remainingPlants.first()
                    databaseLocal.plantDao().deselectAll()
                    databaseLocal.plantDao().update(nextPlant.copy(isSelected = true))
                    currentPlant = nextPlant
                    recreate()
                } else {
                    val intent = Intent(activity, PlantSetupActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }
    }
}

fun MainActivity.resetData(type: Int, startTime: Long, endTime: Long, clearAll: Boolean) {
    currentPlant?.let { plant ->
        val activity = this
        ioScope.launch {
            if (clearAll) {
                databaseLocal.sensorDao().deleteAllByPlantId(plant.id)
            } else {
                databaseLocal.sensorDao().deleteReadingsBetween(plant.id, startTime, endTime)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "Datos restablecidos correctamente", Toast.LENGTH_SHORT).show()
                loadDataAndDrawChart()
            }
        }
    }
}
