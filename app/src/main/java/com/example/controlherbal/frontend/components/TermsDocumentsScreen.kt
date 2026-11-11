package com.example.controlherbal.frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.common.legal.ConsentManager
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.frontend.activities.privacy.LegalActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-ready TermsDocumentsScreen matching the exact design mockup aesthetics.
 * Displays accepted documents status card, and navigation cards for Privacy Notice, Terms of Service, and Cookies Policy.
 */
@Composable
fun TermsDocumentsScreen(
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val acceptedVersion = remember(context) {
        ConsentManager.acceptedVersion(context) ?: "2026.11"
    }

    val acceptedDateText = remember(context) {
        val millis = ConsentManager.acceptedAt(context)
        if (millis > 0) {
            SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(Date(millis))
        } else {
            "Fecha de aceptación registrada"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2F2F31))
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER WITH RESPECTED CAMERA SAFE AREA (top = 64.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38383B))
                        .clickable { onBackClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Términos y documentos",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Color.White
                )
            }

            // 2. CARD 1: DOCUMENTOS ACEPTADOS
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Documentos aceptados",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    Text(
                        text = "Versión [$acceptedVersion] · [$acceptedDateText]",
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }

            // 3. DOCUMENT NAVIGATION CARDS
            val docsList = listOf(
                Pair("Aviso de privacidad", LegalDoc.PRIVACY),
                Pair("Términos de servicio", LegalDoc.TERMS),
                Pair("Política de cookies", LegalDoc.COOKIES)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                docsList.forEach { (title, doc) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(LegalActivity.intent(context, doc))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Left Circle Icon Container (#38383B)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38383B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = title,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Middle Title
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )

                            // Right Chevron Arrow
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Abrir",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
