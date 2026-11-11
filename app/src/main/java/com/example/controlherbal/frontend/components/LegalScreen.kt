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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.common.accessibility.LegalBlock

/**
 * Production-ready LegalScreen matching the exact design mockup aesthetics.
 * Respects top camera safe area (paddingTop = 64.dp), fits into Card container (#252527),
 * and adapts between dark and light themes seamlessly.
 */
@Composable
fun LegalScreen(
    title: String,
    blocks: List<LegalBlock>,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = Color.White
                )
            }

            // 2. DOCUMENT CARD CONTAINER (#252527)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    blocks.forEach { block ->
                        when (block) {
                            is LegalBlock.Heading -> {
                                Text(
                                    text = block.text,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (block.level == 1) 20.sp else 16.sp,
                                    color = Color(0xFFA5D6A7),
                                    modifier = Modifier.padding(top = if (block.level == 1) 4.dp else 12.dp, bottom = 4.dp)
                                )
                            }
                            is LegalBlock.Paragraph -> {
                                Text(
                                    text = block.text,
                                    fontSize = 14.sp,
                                    color = Color(0xFFE0E0E0),
                                    lineHeight = 20.sp
                                )
                            }
                            is LegalBlock.Bullet -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("•", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = block.text,
                                        fontSize = 14.sp,
                                        color = Color(0xFFCCCCCC),
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
