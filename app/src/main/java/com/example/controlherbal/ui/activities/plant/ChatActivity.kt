package com.example.controlherbal.ui.activities.plant

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.style.*

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controlherbal.R
import com.example.controlherbal.ai.AiGateway
import com.example.controlherbal.common.legal.AiConsent
import com.example.controlherbal.common.utils.AppConstants
import com.example.controlherbal.common.utils.ImageUtils
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.common.security.SecurityUtils
import com.example.controlherbal.data.database.Plant
import com.example.controlherbal.data.database.SensorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.TimeoutCancellationException

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val image: Bitmap? = null
)

class ChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnCamera: ImageButton
    private lateinit var pbLoading: ProgressBar
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    
    private var currentPhotoPath: String? = null
    private var selectedImage: Bitmap? = null
    
    private val databaseLocal by lazy { SensorDatabase.getInstance(this) }
    private var currentPlant: Plant? = null

    companion object {
        private const val TAG = "ChatActivity"
        private const val CAMERA_DIR = "camera"
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = currentPhotoPath
        currentPhotoPath = null
        if (success && path != null) {
            // Se reduce la imagen (memoria, datos enviados, sin EXIF) y se borra el archivo original.
            val bitmap = ImageUtils.decodeScaled(path)
            ImageUtils.deleteQuietly(path)
            if (bitmap == null) {
                Toast.makeText(this, "No se pudo leer la foto", Toast.LENGTH_SHORT).show()
            } else {
                selectedImage = bitmap
                val userMsg = ChatMessage("Analizando esta planta...", true, bitmap)
                addMessage(userMsg)
                sendMessageToAI(userMsg)
            }
        } else {
            ImageUtils.deleteQuietly(path)
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

        ImageUtils.purgeCameraCache(File(cacheDir, CAMERA_DIR), maxAgeMs = 0L)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                val cleanedText = SecurityUtils.cleanUserPrompt(text)
                if (cleanedText.isEmpty()) return@setOnClickListener
                val msg = ChatMessage(cleanedText, true)
                addMessage(msg)
                etInput.text.clear()
                sendMessageToAI(msg)
            }
        }

        btnCamera.setOnClickListener { openCamera() }

        lifecycleScope.launch {
            currentPlant = withContext(Dispatchers.IO) { databaseLocal.plantDao().getSelectedPlant() }
            val plantName = SecurityUtils.sanitizeText(currentPlant?.name ?: "")
            addMessage(ChatMessage("¡Hola! Soy tu asistente Herbal AI. ¿En qué puedo ayudarte hoy con tu planta $plantName? Puedes enviarme fotos si notas algún problema.", false))
        }
    }

    private fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun openCamera() {
        AiConsent.ensure(this) {
            try {
                // Almacenamiento interno de la app (cacheDir): no accesible por otras apps.
                val dir = File(cacheDir, CAMERA_DIR).apply { mkdirs() }
                val photoFile = File.createTempFile("IMG_", ".jpg", dir)
                currentPhotoPath = photoFile.absolutePath
                val photoURI = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
                takePictureLauncher.launch(photoURI)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error al abrir la cámara: ${e.javaClass.simpleName}")
                Toast.makeText(this, "Error al abrir la cámara", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessageToAI(userMsg: ChatMessage) {
        if (pbLoading.visibility == View.VISIBLE) return   // una petición a la vez
        AiConsent.ensure(this) { runAiRequest(userMsg) }
    }

    private fun runAiRequest(userMsg: ChatMessage) {
        pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val plantType = currentPlant?.type ?: "desconocida"

                val responseText = withContext(Dispatchers.IO) {
                    if (userMsg.image != null) AiGateway.diagnose(userMsg.image, plantType)
                    else AiGateway.chat(userMsg.text, plantType)
                }.ifEmpty { "No pude procesar tu solicitud." }

                // La respuesta del modelo es entrada NO confiable: solo se acepta una etiqueta
                // con formato estricto en la última línea; el resto se muestra como texto plano.
                val alert = SecurityUtils.extractAlert(responseText)

                addMessage(ChatMessage(responseText, false))
                pbLoading.visibility = View.GONE
                if (alert != null) updatePlantHealth(alert, responseText)
            } catch (e: TimeoutCancellationException) {
                SecureLogger.e(TAG, "Tiempo de espera agotado en la IA")
                pbLoading.visibility = View.GONE
                Toast.makeText(this@ChatActivity, "El servicio de IA tardó demasiado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error de IA: ${e.javaClass.simpleName}")
                pbLoading.visibility = View.GONE
                Toast.makeText(this@ChatActivity, "Error de conexión con el servicio de IA", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePlantHealth(diagnosis: String, fullResponse: String) {
        val plant = currentPlant ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedPlant = plant.copy(
                aiDiagnosis = SecurityUtils.sanitizeText(diagnosis, 60),
                aiRecommendation = SecurityUtils.sanitizeModelOutput(fullResponse)
            )
            databaseLocal.plantDao().update(updatedPlant)
            currentPlant = updatedPlant
            withContext(Dispatchers.Main) { Toast.makeText(this@ChatActivity, "Salud actualizada", Toast.LENGTH_SHORT).show() }
        }
    }

    inner class ChatAdapter(private val list: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
        inner class ChatViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tvMessageText)
            val ivImage: ImageView = v.findViewById(R.id.ivMessageImage)
            val card: CardView = v.findViewById(R.id.cardMessage)
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
                holder.card.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                params.marginStart = 100; params.marginEnd = 0
            } else {
                holder.card.setCardBackgroundColor(Color.WHITE)
                params.marginStart = 0; params.marginEnd = 100
            }
            holder.card.layoutParams = params
        }
        override fun getItemCount() = list.size
    }
}
