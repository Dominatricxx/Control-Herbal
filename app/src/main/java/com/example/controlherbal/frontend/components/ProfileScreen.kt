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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.frontend.activities.auth.ChangePasswordActivity
import com.example.controlherbal.frontend.activities.main.SplashActivity
import com.example.controlherbal.frontend.activities.privacy.PrivacyActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Option data item for profile menu cards using PNG tilesets.
 */
data class ProfileMenuItem(
    val title: String,
    val subtitle: String,
    val iconName: String,
    val onClick: () -> Unit
)

/**
 * Production-ready ProfileScreen matching the exact design mockup.
 * Displays user info card, menu cards using PNG tilesets, and an outlined "Cerrar sesión" button.
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
                iconName = "configuracion",
                onClick = {
                    context.startActivity(Intent(context, com.example.controlherbal.frontend.activities.plant.SettingsActivity::class.java))
                }
            ),
            ProfileMenuItem(
                title = "Accesibilidad",
                subtitle = "Colores, contraste y estados",
                iconName = "accesibilidad",
                onClick = {
                    context.startActivity(Intent(context, com.example.controlherbal.frontend.activities.plant.AccessibilityActivity::class.java))
                }
            ),
            ProfileMenuItem(
                title = "Privacidad e IA",
                subtitle = "Permisos de la IA y consentimie...",
                iconName = "privacidad",
                onClick = {
                    context.startActivity(Intent(context, PrivacyActivity::class.java))
                }
            ),
            ProfileMenuItem(
                title = "Términos y documentos",
                subtitle = "Aviso de privacidad, términos y ...",
                iconName = "terminos",
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

            // 2. USER PROFILE CARD ([Nombre del usuario]) with Blobatar avatar
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252527)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, com.example.controlherbal.frontend.activities.privacy.UserProfileActivity::class.java))
                    }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
}

/**
 * Individual Menu Option Card Component using PNG tilesets.
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
            // Left Circle Icon Container (#38383B) with PNG tileset icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38383B)),
                contentAlignment = Alignment.Center
            ) {
                HerbalImageTileset.TilesetIcon(
                    iconName = item.iconName,
                    contentDescription = item.title,
                    size = 24.dp
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
