package com.example.controlherbal.data.sync

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

import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * FirebaseSensorSyncManager: Aisla los listeners en tiempo real con Firebase Realtime Database.
 */
class FirebaseSensorSyncManager(
    private val onReadingReceived: (
        temp: Double, hum: Double, luz: Double, soil: Double,
        analysisResult: PredictiveTheorem.AnalysisResult
    ) -> Unit,
    private val onDisconnected: () -> Unit
) {

    companion object {
        private const val TAG = "FirebaseSensorSyncManager"
    }

    private var firebaseListener: ValueEventListener? = null

    fun startListening() {
        val ref = FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
            .getReference(AppConstants.FIREBASE_SENSOR_NODE)

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val temp = snapshot.child("temperatura").getValue(Double::class.java) ?: 0.0
                    val hum = snapshot.child("humedad").getValue(Double::class.java) ?: 0.0
                    val luz = snapshot.child("luz").getValue(Double::class.java) ?: 0.0
                    val soil = snapshot.child("humedadSuelo").getValue(Double::class.java)
                        ?: snapshot.child("soilMoisture").getValue(Double::class.java)
                        ?: snapshot.child("suelo").getValue(Double::class.java) ?: 0.0

                    val result = PredictiveTheorem.analyze(temp, hum, luz, soil)
                    onReadingReceived(temp, hum, luz, soil, result)
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Error procesando datos de Firebase: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                SecureLogger.e(TAG, "Listener Firebase cancelado: ${error.message}")
                onDisconnected()
            }
        }

        ref.addValueEventListener(firebaseListener!!)
    }

    fun stopListening() {
        firebaseListener?.let {
            FirebaseDatabase.getInstance(AppConstants.FIREBASE_DATABASE_URL)
                .getReference(AppConstants.FIREBASE_SENSOR_NODE)
                .removeEventListener(it)
            firebaseListener = null
        }
    }
}
