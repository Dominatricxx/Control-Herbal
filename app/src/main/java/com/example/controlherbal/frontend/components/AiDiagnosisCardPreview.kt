package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AiDiagnosisCardContent(
    isDark: Boolean = false
) {
    val bg = if (isDark) Color(0xFF2B2B2D) else Color(0xFFFFF3E0)
    val textColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF212121)
    val bodyColor = if (isDark) Color(0xFFC4C4CC) else Color(0xFF424242)
    val accentColor = Color(0xFFE65100)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Diagnóstico Herbal AI",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Suelo seco detectado",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "La humedad del suelo ha descendido al 14%. Se recomienda regar en las próximas horas para evitar estrés hídrico.",
                color = bodyColor,
                fontSize = 14.sp
            )
            TextButton(
                onClick = {},
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Cerrar aviso",
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(name = "AiDiagnosisCardContent", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun AiDiagnosisCardPreviewLight() {
    AiDiagnosisCardContent(isDark = false)
}
