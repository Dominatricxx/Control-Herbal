package com.example.controlherbal.frontend.components

import android.app.Activity
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
import com.example.controlherbal.common.legal.AiConsent
import com.example.controlherbal.common.legal.LegalDoc
import com.example.controlherbal.frontend.activities.privacy.LegalActivity

/**
 * Production-ready PrivacyAiScreen matching the exact design mockup aesthetics.
 * Features top camera safe area, interactive AI Consent Switch with AiConsent integration,
 * and Consent Log navigation to legal markdown documents.
 */
@Composable
fun PrivacyAiScreen(
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isAiConsentGranted by remember { mutableStateOf(AiConsent.isGranted(context)) }

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
                    text = "Privacidad e IA",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    color = Color.White
                )
            }

            // 2. CARD 1: INTELIGENCIA ARTIFICIAL (AI Consent Switch)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Inteligencia artificial",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Enviar fotos y texto a la IA (Google)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Si lo desactivas, la app pedirá permiso de nuevo antes de usar la IA.",
                                fontSize = 13.sp,
                                color = Color(0xFFCCCCCC),
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Switch(
                                checked = isAiConsentGranted,
                                onCheckedChange = { newState ->
                                    if (newState) {
                                        activity?.let { act ->
                                            AiConsent.ensure(
                                                activity = act,
                                                onDenied = { isAiConsentGranted = false },
                                                onGranted = { isAiConsentGranted = true }
                                            )
                                        }
                                    } else {
                                        AiConsent.revoke(context)
                                        isAiConsentGranted = false
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF288B46),
                                    uncheckedThumbColor = Color(0xFFCCCCCC),
                                    uncheckedTrackColor = Color(0xFF38383B)
                                )
                            )
                            Text(
                                text = if (isAiConsentGranted) "Activado" else "Desactivado",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
            }

            // 3. CARD 2: REGISTRO DE CONSENTIMIENTOS (Navigates to Consent Markdown Document)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(LegalActivity.intent(context, LegalDoc.CONSENTS))
                    }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
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
                            contentDescription = "Registro de consentimientos",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Middle Column: Title & Subtitle
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Registro de consentimientos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Qué aceptaste y cuándo",
                            fontSize = 13.sp,
                            color = Color(0xFFCCCCCC)
                        )
                    }

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
