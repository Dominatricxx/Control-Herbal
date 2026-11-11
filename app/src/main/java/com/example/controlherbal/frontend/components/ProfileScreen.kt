package com.example.controlherbal.frontend.components

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.frontend.activities.auth.ChangePasswordActivity
import com.example.controlherbal.frontend.activities.main.SplashActivity
import com.example.controlherbal.frontend.activities.privacy.ConsentActivity
import com.example.controlherbal.frontend.activities.privacy.LegalActivity
import com.example.controlherbal.frontend.activities.privacy.PrivacyActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Option data item for profile menu cards.
 */
data class ProfileMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Production-ready ProfileScreen matching the exact design mockup.
 * Displays user info card, menu cards (Configuración, Accesibilidad, Privacidad e IA, Términos),
 * and an outlined "Cerrar sesión" button positioned near the bottom.
 */
@Composable
fun ProfileScreen(
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userDisplayName = remember {
        val name = AuthManager.displayName()
        val email = AuthManager.email()
        when {
            !name.isNullOrBlank() -> name
            !email.isNullOrBlank() -> email.substringBefore("@").replaceFirstChar { it.uppercase() }
            else -> "Usuario Herbal"
        }
    }

    var showAccessibilityDialog by remember { mutableStateOf(false) }

    val menuItems = remember(context) {
        listOf(
            ProfileMenuItem(
                title = "Configuración",
                subtitle = "Dispositivo, notificaciones y apa...",
                icon = Icons.Default.Tune,
                onClick = {
                    context.startActivity(Intent(context, com.example.controlherbal.frontend.activities.plant.SettingsActivity::class.java))
                }
            ),
            ProfileMenuItem(
                title = "Accesibilidad",
                subtitle = "Colores, contraste y estados",
                icon = Icons.Default.Visibility,
                onClick = {
                    context.startActivity(Intent(context, com.example.controlherbal.frontend.activities.plant.AccessibilityActivity::class.java))
                }
            ),
            ProfileMenuItem(
                title = "Privacidad e IA",
                subtitle = "Permisos de la IA y consentimie...",
                icon = Icons.Default.Lock,
                onClick = {
                    context.startActivity(Intent(context, PrivacyActivity::class.java))
                }
            ),
            ProfileMenuItem(
                title = "Términos y documentos",
                subtitle = "Aviso de privacidad, términos y ...",
                icon = Icons.Default.Description,
                onClick = {
                    context.startActivity(Intent(context, com.example.controlherbal.frontend.activities.privacy.TermsDocumentsActivity::class.java))
                }
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2F2F31))
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER: Title "Perfil"
            Text(
                text = "Perfil",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // 2. USER PROFILE CARD ([Nombre del usuario])
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // UserAvatar generated dynamically via Blobatar based on userDisplayName seed
                    UserAvatar(
                        seed = userDisplayName,
                        size = 56.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF555557),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = "[$userDisplayName]",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. MENU ITEM CARDS LIST
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                menuItems.forEach { item ->
                    ProfileMenuItemCard(item = item)
                }
            }

            // Spacer to push "Cerrar sesión" button near the bottom of the screen
            Spacer(modifier = Modifier.weight(1f))

            // 4. SIGN OUT BUTTON ("Cerrar sesión") positioned at the bottom
            OutlinedButton(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    activity?.let {
                        it.startActivity(
                            Intent(it, SplashActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        it.finish()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF252527)),
                border = BorderStroke(1.5.dp, Color(0xFF555557)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Cerrar sesión",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }

    // Accessibility Selection Dialog with PaletteMode options (including MONOCHROME)
    if (showAccessibilityDialog) {
        val currentPaletteMode = remember { com.example.controlherbal.common.accessibility.StatusPalette.mode(context) }
        var selectedMode by remember { mutableStateOf(currentPaletteMode) }

        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            containerColor = Color(0xFF252527),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Accesibilidad Visual",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Selecciona el modo de paleta visual adaptado:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    com.example.controlherbal.common.accessibility.PaletteMode.values().forEach { mode ->
                        val isSelected = mode == selectedMode
                        Surface(
                            color = if (isSelected) Color(0xFF288B46) else Color(0xFF38383B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMode = mode
                                    com.example.controlherbal.common.accessibility.StatusPalette.setMode(context, mode)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAccessibilityDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF288B46))
                ) {
                    Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Individual Menu Option Card Component
 */
@Composable
fun ProfileMenuItemCard(item: ProfileMenuItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
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
                    imageVector = item.icon,
                    contentDescription = item.title,
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
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = item.subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
