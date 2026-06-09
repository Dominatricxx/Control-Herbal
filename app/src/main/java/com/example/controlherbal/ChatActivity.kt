package com.example.controlherbal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorDatabase
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val image: Bitmap? = null
)

class ChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: android.widget.EditText
    private lateinit var btnSend: android.widget.ImageButton
    private lateinit var btnCamera: android.widget.ImageButton
    private lateinit var pbLoading: android.widget.ProgressBar
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    
    private var currentPhotoPath: String? = null
    private var selectedImage: Bitmap? = null
    
    private val databaseLocal by lazy { SensorDatabase.getInstance(this) }
    private var currentPlant: Plant? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoPath != null) {
            val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
            selectedImage = bitmap
            val userMsg = ChatMessage("Analizando esta planta...", true, bitmap)
            addMessage(userMsg)
            sendMessageToAI(userMsg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvChat = findViewById(R.id.rvChat)
        etInput = findViewById(R.id.etChatInput)
        btnSend = findViewById(R.id.btnSend)
        btnCamera = findViewById(R.id.btnCamera)
        pbLoading = findViewById(R.id.pbLoading)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                val msg = ChatMessage(text, true)
                addMessage(msg)
                etInput.text.clear()
                sendMessageToAI(msg)
            }
        }

        btnCamera.setOnClickListener { openCamera() }

        lifecycleScope.launch {
            currentPlant = withContext(Dispatchers.IO) { databaseLocal.plantDao().getSelectedPlant() }
            addMessage(ChatMessage("¡Hola! Soy tu asistente Herbal AI. ¿En qué puedo ayudarte hoy con tu planta ${currentPlant?.name ?: ""}? Puedes enviarme fotos si notas algún problema.", false))
        }
    }

    private fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun openCamera() {
        val photoFile = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES))
        currentPhotoPath = photoFile.absolutePath
        val photoURI = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(photoURI)
    }

    private fun sendMessageToAI(userMsg: ChatMessage) {
        pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Migración al motor Gemini 2.0 Flash (Última versión disponible y funcional)
                val model = Firebase.vertexAI.generativeModel("gemini-2.0-flash-exp")
                
                val promptText = if (userMsg.image != null) {
                    "Actúa como un experto botánico. Analiza la imagen de esta planta (${currentPlant?.type ?: "desconocida"}). Identifica si tiene algún problema. Responde brevemente: 1. Diagnóstico, 2. Causa, 3. Recomendación. Si hay problema grave, termina con [ALERTA: Nombre]."
                } else {
                    "Asistente botánico para la planta: ${currentPlant?.name} (${currentPlant?.type}). Responde brevemente."
                }

                val response = if (userMsg.image != null) {
                    model.generateContent(content { image(userMsg.image!!); text(promptText) })
                } else {
                    model.generateContent(userMsg.text + "\nContexto: " + promptText)
                }

                val responseText = response.text ?: "No pude procesar tu solicitud."
                withContext(Dispatchers.Main) {
                    addMessage(ChatMessage(responseText, false))
                    pbLoading.visibility = View.GONE
                    if (responseText.contains("[ALERTA:")) {
                        val diagnosis = responseText.substringAfter("[ALERTA:").substringBefore("]").trim()
                        updatePlantHealth(diagnosis, responseText)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "Error de IA: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updatePlantHealth(diagnosis: String, fullResponse: String) {
        val plant = currentPlant ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedPlant = plant.copy(aiDiagnosis = diagnosis, aiRecommendation = fullResponse)
            databaseLocal.plantDao().update(updatedPlant)
            currentPlant = updatedPlant
            withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "Salud actualizada", Toast.LENGTH_SHORT).show() }
        }
    }

    inner class ChatAdapter(private val list: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
        inner class ChatViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tvMessageText)
            val ivImage: ImageView = v.findViewById(R.id.ivMessageImage)
            val card: androidx.cardview.widget.CardView = v.findViewById(R.id.cardMessage)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(v)
        }
        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val m = list[position]
            holder.tvText.text = m.text
            holder.ivImage.visibility = if (m.image != null) View.VISIBLE else View.GONE
            if (m.image != null) holder.ivImage.setImageBitmap(m.image)
            val params = holder.card.layoutParams as ViewGroup.MarginLayoutParams
            if (m.isUser) {
                holder.card.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                params.marginStart = 100; params.marginEnd = 0
            } else {
                holder.card.setCardBackgroundColor(android.graphics.Color.WHITE)
                params.marginStart = 0; params.marginEnd = 100
            }
            holder.card.layoutParams = params
        }
        override fun getItemCount() = list.size
    }
}
