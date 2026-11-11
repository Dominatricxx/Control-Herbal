package com.example.controlherbal.frontend.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.controlherbal.R

/**
 * Tileset Loader utility that maps icon keys to the exact user-provided raster PNG icon assets
 * stored in res/drawable (tile_humedad, tile_sol, tile_suelo, tile_temperatura,
 * tile_irh, tile_inicio, tile_plantas, tile_agregar, tile_calendario, tile_perfil).
 */
object HerbalImageTileset {

    @DrawableRes
    fun getDrawableId(iconName: String): Int {
        return when (iconName.lowercase()) {
            "humedad", "water" -> R.drawable.tile_humedad
            "sol", "sun" -> R.drawable.tile_sol
            "suelo", "plant" -> R.drawable.tile_suelo
            "temperatura", "temp" -> R.drawable.tile_temperatura
            "irh", "shield" -> R.drawable.tile_irh
            "inicio", "home" -> R.drawable.tile_inicio
            "plantas" -> R.drawable.tile_plantas
            "agregar", "add" -> R.drawable.tile_agregar
            "calendario", "calendar" -> R.drawable.tile_calendario
            "perfil", "user" -> R.drawable.tile_perfil
            else -> R.drawable.tile_sol
        }
    }

    @Composable
    fun getPainter(iconName: String): Painter {
        return painterResource(id = getDrawableId(iconName))
    }

    @Composable
    fun TilesetIcon(
        iconName: String,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        size: Dp = 32.dp
    ) {
        val drawableId = getDrawableId(iconName)
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    }
}
