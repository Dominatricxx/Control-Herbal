package com.example.controlherbal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Componente reutilizable para renderizar un avatar de usuario dinámico desde https://blobatar.dev/
 * utilizando Coil (SubcomposeAsyncImage) con forma circular y manejo robusto de estados (carga y error).
 */
@Composable
fun UserAvatar(
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null
) {
    val sanitizedSeed = seed.ifBlank { "default_user" }
    val avatarUrl = "https://blobatar.dev/avatar/$sanitizedSeed"

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(avatarUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription ?: "Avatar de $sanitizedSeed",
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.5f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Error al cargar avatar",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    )
}

@Preview(name = "User Avatar Preview", showBackground = true)
@Composable
fun UserAvatarPreview() {
    MaterialTheme {
        UserAvatar(seed = "herbal_architect_2025", size = 64.dp)
    }
}
