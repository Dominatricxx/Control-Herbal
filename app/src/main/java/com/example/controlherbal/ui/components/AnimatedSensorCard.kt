package com.example.controlherbal.ui.components

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.common.utils.*
import com.example.controlherbal.ui.activities.main.*
import com.example.controlherbal.ui.activities.auth.*
import com.example.controlherbal.ui.activities.privacy.*
import com.example.controlherbal.ui.activities.plant.*
import com.example.controlherbal.ui.style.*

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

/**
 * ImageLoader optimizado con GifDecoder/ImageDecoderDecoder para GIFs locales en Jetpack Compose.
 */
@Composable
fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

/**
 * Componente de tarjeta de monitoreo de sensor con animación GIF expresiva (Material Design 3).
 *
 * @param title Título del indicador (ej: Temperatura, Humedad).
 * @param value Valor actual formateado (ej: 24.5 °C).
 * @param modifier Modifier opcional para personalización.
 * @param statusText Estado descriptivo o recomendación (ej: Óptimo, Riego necesario).
 * @param gifResId Recurso GIF (ej: R.raw.temp_animated).
 * @param fallbackIcon Emoticono o icono de reserva si el GIF está cargando o ausente.
 * @param containerColor Color de contenedor tonal M3.
 * @param contentColor Color de texto y contenido sobre el contenedor tonal.
 */
@Composable
fun AnimatedSensorCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    gifResId: Any? = null,
    fallbackIcon: String = "",
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val imageLoader = rememberGifImageLoader()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Badge circular con el GIF animado expresivo
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (gifResId != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(gifResId)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(30.dp)
                                .padding(2.dp)
                        )
                    } else {
                        Text(
                            text = fallbackIcon,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Valor numérico destacado
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            // Estado o badge tonal inferior
            if (!statusText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.15f),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
