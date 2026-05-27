import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.platform.Font

@Composable
fun OpeningScreen(onNavigate: () -> Unit) {
    val raspbernFamily = FontFamily(
        Font("font/rasbern.otf", FontWeight.Normal)
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fondo
        Image(
            painter = painterResource("fondo_plantasia.png"),
            contentDescription = "Fondo de Pantalla",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Contenido Central
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Control Herbal",
                fontFamily = raspbernFamily,
                fontSize = 34.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Botón Verde con Flecha (Usando Box para control total del tamaño)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color(0xFF2E7D32), CircleShape)
                    .clickable { onNavigate() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Continuar",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            // Espacio extra al final para subir el contenido ligeramente
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
