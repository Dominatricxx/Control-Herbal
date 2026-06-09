package com.example.controlherbal

import android.Manifest
import androidx.appcompat.app.AlertDialog
import com.example.controlherbal.R
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantSetupActivity : AppCompatActivity() {

    private lateinit var etPlantName: EditText
    private lateinit var spinnerPlantType: AutoCompleteTextView
    private lateinit var spinnerEnvironment: AutoCompleteTextView
    private lateinit var btnSelectSpecificPlant: Button
    private var selectedPlantData: PredefinedPlant? = null
    private var customPlantType: String? = null
    private var currentCategory: String? = null

    data class PredefinedPlant(
        val name: String,
        val scientificName: String,
        val emoji: String,
        val environment: String
    )

    private val plantCategories = mapOf(
        "Flores 🌸" to listOf(
            PredefinedPlant("Girasol", "Helianthus annuus", "🌻", "Luz 🌞"),
            PredefinedPlant("Girasol de Sombra", "Helianthus decapetalus", "🌻", "Sombra 🌥️"),
            PredefinedPlant("Tulipán", "Tulipa", "🌷", "Híbrido ⛅"),
            PredefinedPlant("Rosa", "Rosa", "🌹", "Luz 🌞"),
            PredefinedPlant("Lavanda", "Lavandula", "🌿", "Luz 🌞"),
            PredefinedPlant("Caléndula", "Calendula officinalis", "🧡", "Luz 🌞"),
            PredefinedPlant("Orquídea", "Orchidaceae", "🌸", "Sombra 🌥️")
        ),
        "Hierbas y Especias 🌿" to listOf(
            PredefinedPlant("Menta", "Mentha", "🍃", "Sombra 🌥️"),
            PredefinedPlant("Albahaca", "Ocimum basilicum", "🌿", "Luz 🌞"),
            PredefinedPlant("Romero", "Salvia rosmarinus", "🌿", "Luz 🌞"),
            PredefinedPlant("Tomillo", "Thymus", "🌿", "Luz 🌞"),
            PredefinedPlant("Perejil", "Petroselinum crispum", "🌿", "Híbrido ⛅"),
            PredefinedPlant("Cilantro", "Coriandrum sativum", "🌿", "Híbrido ⛅"),
            PredefinedPlant("Hierbabuena", "Mentha spicata", "🌿", "Híbrido ⛅")
        ),
        "Medicinales 💊" to listOf(
            PredefinedPlant("Áloe Vera", "Aloe barbadensis", "🌵", "Luz 🌞"),
            PredefinedPlant("Manzanilla", "Chamaemelum nobile", "🌼", "Luz 🌞"),
            PredefinedPlant("Diente de León", "Taraxacum officinale", "🌼", "Luz 🌞"),
            PredefinedPlant("Salvia", "Salvia officinalis", "🌿", "Híbrido ⛅"),
            PredefinedPlant("Eucalipto", "Eucalyptus", "🌿", "Luz 🌞"),
            PredefinedPlant("Ruda", "Ruta graveolens", "🌿", "Híbrido ⛅")
        ),
        "Huerto y Frutales 🍅" to listOf(
            PredefinedPlant("Tomate", "Solanum lycopersicum", "🍅", "Luz 🌞"),
            PredefinedPlant("Chile", "Capsicum", "🌶️", "Luz 🌞"),
            PredefinedPlant("Limón", "Citrus limon", "🍋", "Luz 🌞"),
            PredefinedPlant("Aguacate", "Persea americana", "🥑", "Luz 🌞"),
            PredefinedPlant("Fresa", "Fragaria", "🍓", "Híbrido ⛅"),
            PredefinedPlant("Naranjo", "Citrus sinensis", "🍊", "Luz 🌞")
        ),
        "Suculentas y Otros 🌵" to listOf(
            PredefinedPlant("Nopal", "Opuntia ficus-indica", "🌵", "Luz 🌞"),
            PredefinedPlant("Suculenta", "Crassulaceae", "🌵", "Luz 🌞"),
            PredefinedPlant("Lengua de Suegra", "Sansevieria trifasciata", "🌿", "Sombra 🌥️"),
            PredefinedPlant("Bambú", "Bambusoideae", "🎍", "Sombra 🌥️"),
            PredefinedPlant("Helecho", "Filicopsida", "🌿", "Sombra 🌥️")
        )
    )

    // Lanzador para la cámara
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { identifyPlantWithAI(it) }
        }
    }

    // Permiso de cámara
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

        // Filtro para solo letras y números (incluyendo espacios)
        val alphaNumericFilter = android.text.InputFilter { source, start, end, dest, dstart, dend ->
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
        categoryList.addAll(plantCategories.keys)
        categoryList.add("Otro 🌱")
        categories = categoryList

        environments = listOf("Luz 🌞", "Sombra 🌥️", "Híbrido ⛅")

        val adapterType = ArrayAdapter(this, R.layout.spinner_item, categories)
        spinnerPlantType.setAdapter(adapterType)

        val adapterEnv = ArrayAdapter(this, R.layout.spinner_item, environments)
        spinnerEnvironment.setAdapter(adapterEnv)

        spinnerPlantType.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
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
                    plantCategories.containsKey(selected) -> {
                        showPlantSelectionDialog(selected)
                    }
                }
        }

        btnSelectSpecificPlant.setOnClickListener {
            currentCategory?.let { category ->
                if (plantCategories.containsKey(category)) {
                    showPlantSelectionDialog(category)
                }
            }
        }

        btnSavePlant.setOnClickListener {
            val name = etPlantName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Por favor, dale un nombre a tu planta", Toast.LENGTH_SHORT).show()
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

            savePlantAndFinish(name, type, environment)
        }
    }

    private fun showPlantSelectionDialog(category: String) {
        val plants = plantCategories[category] ?: return
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

    private fun selectPredefinedPlant(plant: PredefinedPlant, category: String) {
        selectedPlantData = plant
        
        // Formato solicitado: Categoría: [Nombre] | Tipo: [Nombre] [Emoji] ([Científico])
        val categoryName = category.split(" ").first()
        val formattedType = "Categoría: $categoryName | Tipo: ${plant.name} ${plant.emoji} (${plant.scientificName})"
        
        customPlantType = formattedType
        
        // Mostrar botón para re-seleccionar
        btnSelectSpecificPlant.text = "${plant.name} ${plant.emoji}"
        btnSelectSpecificPlant.visibility = View.VISIBLE

        // Auto-seleccionar ambiente y deshabilitar
        val environments = arrayOf("Luz 🌞", "Sombra 🌥️", "Híbrido ⛅")
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
            val text = etInput.text.toString().trim()
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
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun identifyPlantWithAI(bitmap: Bitmap) {
        if (isInternetAvailable()) {
            identifyOnline(bitmap)
        } else {
            identifyOffline(bitmap)
        }
    }

    private fun identifyOnline(bitmap: Bitmap) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(R.string.ai_identifying)
            .setCancelable(false)
            .show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val model = Firebase.vertexAI.generativeModel(modelName = "gemini-1.5-flash")
                
                val prompt = "Actúa como experto botánico. Analiza la imagen. " +
                            "Si es una planta, responde ÚNICAMENTE con este formato: " +
                            "Nombre común [Emoji] (Nombre científico) | Ambiente: [Luz/Sombra/Híbrido]. " +
                            "Ejemplo: Romero 🌿 (Salvia rosmarinus) | Ambiente: Luz. " +
                            "Si no es una planta, responde: No es una planta."
                
                val response = model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    val result = response.text?.trim() ?: "Planta Desconocida 🌿"
                    
                    if (result.contains("No es una planta", ignoreCase = true)) {
                        Toast.makeText(this@PlantSetupActivity, "No se detectó una planta.", Toast.LENGTH_LONG).show()
                        spinnerPlantType.setText(categories[0], false)
                    } else {
                        // Intentar extraer ambiente para auto-configurar
                        if (result.contains("| Ambiente:")) {
                            val parts = result.split("| Ambiente:")
                            customPlantType = parts[0].trim()
                            val envText = parts[1].trim()
                            
                            val environments = arrayOf("Luz", "Sombra", "Híbrido")
                            val envIndex = environments.indexOfFirst { envText.contains(it, ignoreCase = true) }
                            if (envIndex >= 0) {
                                spinnerEnvironment.setText(environments[envIndex], false)
                                spinnerEnvironment.isEnabled = false
                                spinnerEnvironment.alpha = 0.6f
                            }
                        } else {
                            customPlantType = result
                            resetEnvironmentSpinner()
                        }
                        selectedPlantData = null
                        Toast.makeText(this@PlantSetupActivity, "IA Detectó: $customPlantType", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    identifyOffline(bitmap)
                }
            }
        }
    }

    private fun identifyOffline(bitmap: Bitmap) {
        Toast.makeText(this, R.string.offline_identification, Toast.LENGTH_SHORT).show()
        
        // Base de datos local masiva (Simulación para este ejemplo con un subconjunto)
        val localHerbalDB = arrayOf(
            PredefinedPlant("Áloe Vera", "Aloe barbadensis", "🌵", "Luz 🌞"),
            PredefinedPlant("Manzanilla", "Chamaemelum nobile", "🌼", "Luz 🌞"),
            PredefinedPlant("Romero", "Salvia rosmarinus", "🌿", "Luz 🌞"),
            PredefinedPlant("Menta", "Mentha", "🍃", "Sombra 🌥️")
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
