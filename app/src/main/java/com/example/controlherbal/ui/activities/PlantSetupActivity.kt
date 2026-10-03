package com.example.controlherbal.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.controlherbal.R
import com.example.controlherbal.ai.AiGateway
import com.example.controlherbal.common.AiConsent
import com.example.controlherbal.common.PlantCategoriesCatalog
import com.example.controlherbal.common.PredefinedPlantInfo
import com.example.controlherbal.common.SecureLogger
import com.example.controlherbal.common.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantSetupActivity : AppCompatActivity() {

    private lateinit var etPlantName: EditText
    private lateinit var spinnerPlantType: AutoCompleteTextView
    private lateinit var spinnerEnvironment: AutoCompleteTextView
    private lateinit var btnSelectSpecificPlant: Button
    private var selectedPlantData: PredefinedPlantInfo? = null
    private var customPlantType: String? = null
    private var currentCategory: String? = null

    companion object {
        private const val TAG = "PlantSetupActivity"
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { identifyPlantWithAI(it) }
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private var categories: List<String> = emptyList()
    private var environments: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_setup)

        etPlantName = findViewById(R.id.etPlantName)
        spinnerPlantType = findViewById(R.id.spinnerPlantType)
        spinnerEnvironment = findViewById(R.id.spinnerEnvironment)
        btnSelectSpecificPlant = findViewById(R.id.btnSelectSpecificPlant)
        val btnSavePlant = findViewById<Button>(R.id.btnSavePlant)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        val alphaNumericFilter = InputFilter { source, start, end, _, _, _ ->
            for (i in start until end) {
                val char = source[i]
                if (!Character.isLetterOrDigit(char) && char != ' ') {
                    return@InputFilter ""
                }
            }
            null
        }
        etPlantName.filters = arrayOf(alphaNumericFilter)

        val categoryList = mutableListOf("Seleccionar Categoría...")
        categoryList.addAll(PlantCategoriesCatalog.categoriesMap.keys)
        categoryList.add("Otro 🌱")
        categories = categoryList

        environments = listOf("Luz 🌞", "Sombra 🌥️", "Híbrido ⛅")

        val adapterType = ArrayAdapter(this, R.layout.spinner_item, categories)
        spinnerPlantType.setAdapter(adapterType)

        val adapterEnv = ArrayAdapter(this, R.layout.spinner_item, environments)
        spinnerEnvironment.setAdapter(adapterEnv)

        spinnerPlantType.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = categories[position]
            currentCategory = selected
            when {
                selected == "Seleccionar Categoría..." -> {
                    selectedPlantData = null
                    customPlantType = null
                    btnSelectSpecificPlant.visibility = View.GONE
                    resetEnvironmentSpinner()
                }
                selected == "Otro 🌱" -> {
                    btnSelectSpecificPlant.visibility = View.GONE
                    showOtherOptionsDialog()
                }
                PlantCategoriesCatalog.categoriesMap.containsKey(selected) -> {
                    showPlantSelectionDialog(selected)
                }
            }
        }

        btnSelectSpecificPlant.setOnClickListener {
            currentCategory?.let { category ->
                if (PlantCategoriesCatalog.categoriesMap.containsKey(category)) {
                    showPlantSelectionDialog(category)
                }
            }
        }

        btnSavePlant.setOnClickListener {
            val rawName = etPlantName.text.toString().trim()
            val name = SecurityUtils.sanitizeText(rawName)
            if (name.isEmpty()) {
                Toast.makeText(this, "Por favor, dale un nombre válido a tu planta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = when {
                customPlantType != null -> customPlantType!!
                selectedPlantData != null -> "${selectedPlantData!!.name} ${selectedPlantData!!.emoji} (${selectedPlantData!!.scientificName})"
                else -> {
                    Toast.makeText(this, "Por favor, selecciona un tipo de planta", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            
            val environment = spinnerEnvironment.text.toString()

            savePlantAndFinish(name, SecurityUtils.sanitizeText(type), environment)
        }
    }

    private fun showPlantSelectionDialog(category: String) {
        val plants = PlantCategoriesCatalog.categoriesMap[category] ?: return
        val plantNames = plants.map { "${it.name} ${it.emoji}" }

        val dialogView = layoutInflater.inflate(R.layout.dialog_rounded_list, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = "Selecciona ${category.split(" ").first()}"
        
        val listView = dialogView.findViewById<ListView>(R.id.dialogListView)
        val adapter = ArrayAdapter(this, R.layout.item_plant_selection, R.id.tvItemPlantName, plantNames)
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        listView.setOnItemClickListener { _, _, which, _ ->
            val plant = plants[which]
            selectPredefinedPlant(plant, category)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun selectPredefinedPlant(plant: PredefinedPlantInfo, category: String) {
        selectedPlantData = plant
        
        val categoryName = category.split(" ").first()
        val formattedType = "Categoría: $categoryName | Tipo: ${plant.name} ${plant.emoji} (${plant.scientificName})"
        
        customPlantType = formattedType
        
        btnSelectSpecificPlant.text = "${plant.name} ${plant.emoji}"
        btnSelectSpecificPlant.visibility = View.VISIBLE

        val envIndex = environments.indexOf(plant.environment)
        if (envIndex >= 0) {
            spinnerEnvironment.setText(environments[envIndex], false)
        }
        spinnerEnvironment.isEnabled = false
        spinnerEnvironment.alpha = 0.6f
    }

    private fun resetEnvironmentSpinner() {
        spinnerEnvironment.isEnabled = true
        spinnerEnvironment.alpha = 1.0f
        if (environments.isNotEmpty()) {
            spinnerEnvironment.setText(environments[0], false)
        }
    }

    private fun showOtherOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_options, null)
        val btnOption1 = dialogView.findViewById<Button>(R.id.btnOption1)
        val btnOption2 = dialogView.findViewById<Button>(R.id.btnOption2)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOption1.setOnClickListener {
            showManualInputDialog()
            dialog.dismiss()
        }
        btnOption2.setOnClickListener {
            checkCameraPermissionAndOpen()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showManualInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etInput = dialogView.findViewById<EditText>(R.id.etInput)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnOk = dialogView.findViewById<Button>(R.id.btnOk)
        
        tvTitle.text = getString(R.string.manual_registration)
        etInput.hint = getString(R.string.manual_input_hint)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOk.setOnClickListener {
            val text = SecurityUtils.sanitizeText(etInput.text.toString().trim())
            if (text.isNotEmpty()) {
                customPlantType = "$text 🌿"
                selectedPlantData = null
                resetEnvironmentSpinner()
                Toast.makeText(this, "Tipo configurado: $text", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        btnCancel.setOnClickListener {
            if (categories.isNotEmpty()) {
                spinnerPlantType.setText(categories[0], false)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error al abrir la cámara: ${e.message}")
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun identifyPlantWithAI(bitmap: Bitmap) {
        if (isInternetAvailable()) {
            AiConsent.ensure(this) { identifyOnline(bitmap) }
        } else {
            identifyOffline()
        }
    }

    private fun identifyOnline(bitmap: Bitmap) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(R.string.ai_identifying)
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val aiText = AiGateway.identify(bitmap)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    val result = aiText.ifBlank { "Planta Desconocida 🌿" }
                    
                    if (result.contains("No es una planta", ignoreCase = true)) {
                        Toast.makeText(this@PlantSetupActivity, "No se detectó una planta.", Toast.LENGTH_LONG).show()
                        spinnerPlantType.setText(categories[0], false)
                    } else {
                        if (result.contains("| Ambiente:")) {
                            val parts = result.split("| Ambiente:")
                            customPlantType = SecurityUtils.sanitizeText(parts[0], 80)
                            val envText = parts[1].trim()
                            
                            val envIndex = environments.indexOfFirst { envText.contains(it.split(" ").first(), ignoreCase = true) }
                            if (envIndex >= 0) {
                                spinnerEnvironment.setText(environments[envIndex], false)
                                spinnerEnvironment.isEnabled = false
                                spinnerEnvironment.alpha = 0.6f
                            }
                        } else {
                            customPlantType = SecurityUtils.sanitizeText(result, 80)
                            resetEnvironmentSpinner()
                        }
                        selectedPlantData = null
                        Toast.makeText(this@PlantSetupActivity, "IA Detectó: $customPlantType", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error identificación online: ${e.javaClass.simpleName}")
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    identifyOffline()
                }
            }
        }
    }

    private fun identifyOffline() {
        Toast.makeText(this, R.string.offline_identification, Toast.LENGTH_SHORT).show()
        val localHerbalDB = listOf(
            PredefinedPlantInfo("Áloe Vera", "Aloe barbadensis", "🌵", "Luz 🌞"),
            PredefinedPlantInfo("Manzanilla", "Chamaemelum nobile", "🌼", "Luz 🌞"),
            PredefinedPlantInfo("Romero", "Salvia rosmarinus", "🌿", "Luz 🌞"),
            PredefinedPlantInfo("Menta", "Mentha", "🍃", "Sombra 🌥️")
        )
        val plant = localHerbalDB.random() 
        selectPredefinedPlant(plant, "Local 🏠")
        selectedPlantData = null
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun savePlantAndFinish(name: String, type: String, environment: String) {
        val db = SensorDatabase.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            db.plantDao().deselectAll()
            val plant = Plant(name = name, type = type, environment = environment, isSelected = true)
            db.plantDao().insert(plant)

            withContext(Dispatchers.Main) {
                val intent = Intent(this@PlantSetupActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}
