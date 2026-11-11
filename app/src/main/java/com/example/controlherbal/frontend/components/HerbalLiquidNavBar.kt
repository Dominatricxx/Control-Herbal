package com.example.controlherbal.frontend.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Barra de navegación con gota líquida para Control Herbal usando PNG tilesets.
 */

data class HerbalNavItem(val iconName: String, val descripcion: String)

@Immutable
data class HerbalNavColors(
    val superficie: Color,
    val borde: Color,
    val gota: Color,
    val pestana: Color,
    val iconoActivo: Color,
    val iconoInactivo: Color,
) {
    companion object {
        val Oscuro = HerbalNavColors(
            superficie = Color(0xFF252527), borde = Color(0x38FFFFFF),
            gota = Color(0xFF288B46), pestana = Color(0xFF458C4F),
            iconoActivo = Color.White, iconoInactivo = Color(0xFFCCCCCC),
        )
        val Claro = HerbalNavColors(
            superficie = Color.White, borde = Color(0x2E3C3C43),
            gota = Color(0xFF288B46), pestana = Color(0xFF458C4F),
            iconoActivo = Color.White, iconoInactivo = Color(0xFF3A3A3C),
        )
    }
}

// ───────────── Constantes de geometría (dp) ─────────────
private const val SLOTS_TOTALES = 5
private const val SLOT_CENTRAL = 2
private val SLOT_DE_PESTANA = intArrayOf(0, 1, 3, 4)

private const val ALTO_BARRA = 64f
private const val CY = 32f
private const val BASE = 26f
private const val MITAD_TAB = 28f
private const val ALTO_TAB = 48f
private const val RADIO_TAB = 20f
private const val HUECO = 2f
private const val SUELO = 62f
private const val CY_U = (ALTO_TAB + HUECO + SUELO) / 2f
private const val RY_U = (SUELO - ALTO_TAB - HUECO) / 2f
private const val RX_U = 14f
private const val D_FUERA = 66f
private const val D_DENTRO = 30f
private const val LARGO_BAJO_TAB = 34f
private const val GRAVEDAD = 1500f
private const val Y_ATERRIZAJE = 24f

@Composable
fun HerbalLiquidNavBar(
    items: List<HerbalNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    agregarSeleccionado: Boolean,
    onAgregarSeleccionadoChange: (Boolean) -> Unit,
    onNuevaPlanta: () -> Unit,
    onEditarPlanta: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HerbalNavColors = if (isSystemInDarkTheme()) HerbalNavColors.Oscuro else HerbalNavColors.Claro,
    addDescription: String = "Agregar o editar planta",
) {
    require(items.size == 4) { "Se necesitan exactamente 4 pestañas: Inicio, Plantas, Calendario y Perfil" }

    val forma = RoundedCornerShape(32.dp)
    val contexto = LocalContext.current
    val densidad = LocalDensity.current.density
    val sinAnimaciones = remember {
        Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scope = rememberCoroutineScope()
    val alCambiarAgregar by rememberUpdatedState(onAgregarSeleccionadoChange)

    var anchoPx by remember { mutableFloatStateOf(0f) }
    val anchoDp = anchoPx / densidad
    val geom = remember(anchoDp) { Geometria(anchoDp) }
    val geomActual by rememberUpdatedState(geom)

    val liquido = remember { Liquido() }
    var inicializado by remember { mutableStateOf(false) }
    var ultimoPlus by remember { mutableStateOf(agregarSeleccionado) }
    var menuAbierto by remember { mutableStateOf(false) }
    var cuadro by remember { mutableIntStateOf(0) }
    var impulso by remember { mutableIntStateOf(0) }
    var vuelo by remember { mutableStateOf<Vuelo?>(null) }
    var tVuelo by remember { mutableFloatStateOf(0f) }
    val rebote = remember { Animatable(1f) }

    val plusActivo = agregarSeleccionado

    LaunchedEffect(selectedIndex, agregarSeleccionado, anchoDp) {
        val veniaDelPlus = ultimoPlus
        ultimoPlus = agregarSeleccionado
        if (anchoDp <= 0f || agregarSeleccionado) return@LaunchedEffect
        val destino = anchoDp * (SLOT_DE_PESTANA[selectedIndex.coerceIn(0, 3)] + 0.5f) / SLOTS_TOTALES
        if (!inicializado || sinAnimaciones) {
            liquido.colocar(destino)
            inicializado = true
            cuadro++
            return@LaunchedEffect
        }
        if (veniaDelPlus) {
            liquido.colocar(anchoDp / 2f)
            liquido.aparicion = 0.2f
        }
        liquido.objetivo = destino
        impulso++
    }

    LaunchedEffect(impulso) {
        if (impulso == 0) return@LaunchedEffect
        var ultimo = withFrameNanos { it }
        var acumulado = 0f
        while (true) {
            val ahora = withFrameNanos { it }
            val dt = ((ahora - ultimo) / 1_000_000_000f).coerceAtMost(0.05f)
            ultimo = ahora
            val v = vuelo
            if (v != null) {
                tVuelo += dt
                if (tVuelo >= v.duracion) {
                    vuelo = null
                    tVuelo = 0f
                    scope.launch {
                        rebote.snapTo(0.82f)
                        rebote.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
                    }
                    alCambiarAgregar(true)
                    menuAbierto = true
                    cuadro++
                    break
                }
            } else {
                acumulado += dt * 60f
                while (acumulado >= 1f) {
                    liquido.paso(geomActual)
                    acumulado -= 1f
                }
                if (liquido.enReposo()) {
                    liquido.x = liquido.objetivo
                    liquido.xc = liquido.objetivo
                    liquido.aparicion = 1f
                    cuadro++
                    break
                }
            }
            cuadro++
        }
    }

    fun despegar() {
        if (plusActivo || vuelo != null || anchoDp <= 0f) return
        val nuevo = if (sinAnimaciones) null else crearVuelo(liquido.x, anchoDp / 2f)
        if (nuevo == null) {
            alCambiarAgregar(true)
            menuAbierto = true
            return
        }
        tVuelo = 0f
        vuelo = nuevo
        impulso++
    }

    fun cerrarMenu(volver: Boolean) {
        menuAbierto = false
        if (volver) alCambiarAgregar(false)
    }

    Box(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(ALTO_BARRA.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(12.dp, forma, clip = false)
                .clip(forma)
                .background(colors.superficie)
                .border(1.dp, colors.borde, forma)
                .onSizeChanged { anchoPx = it.width.toFloat() }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (cuadro < 0) return@Canvas
                if (anchoDp <= 0f || plusActivo || vuelo != null) return@Canvas
                scale(densidad, Offset.Zero) {
                    val sw = anchoDp / SLOTS_TOTALES
                    val mx = liquido.x
                    val tx = liquido.xc
                    val d = abs(mx - tx)
                    val estiramiento = (abs(liquido.objetivo - mx) / sw * 0.18f).coerceAtMost(0.4f)
                    val rx0 = BASE * (1f + estiramiento)
                    val ry0 = BASE * (1f - estiramiento * 0.55f)
                    val rCola = 18f * (0.6f + (d / (sw * 2.5f)).coerceAtMost(0.3f))
                    val f = liquido.aparicion

                    fun pintar(o: Gota) = drawOval(
                        color = colors.gota,
                        topLeft = Offset(o.x - o.rx * f, o.y - o.ry * f),
                        size = Size(o.rx * 2f * f, o.ry * 2f * f),
                    )

                    if (d > 1f) {
                        val n = max(8, ceil(d / 3f).toInt())
                        val grosor = (1f - d / (sw * 2.5f)).coerceIn(0.22f, 1f)
                        for (i in 0..n) {
                            val u = i.toFloat() / n
                            val r = (rCola + (ry0 - rCola) * u) * (1f - (1f - grosor) * 4f * u * (1f - u))
                            pintar(geom.gota(tx + (mx - tx) * u, r, r))
                        }
                    }
                    pintar(geom.gota(tx, rCola, rCola))
                    pintar(geom.gota(mx, rx0, ry0))
                }
            }

            Row(Modifier.fillMaxSize()) {
                for (slot in 0 until SLOTS_TOTALES) {
                    if (slot == SLOT_CENTRAL) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val i = SLOT_DE_PESTANA.indexOf(slot)
                    val item = items[i]
                    val activo = i == selectedIndex && vuelo == null
                    val escala by animateFloatAsState(
                        targetValue = if (activo) 1.15f else 1f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                        label = "escala",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = activo,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (vuelo == null) {
                                        menuAbierto = false
                                        if (agregarSeleccionado) alCambiarAgregar(false)
                                        onSelect(i)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        HerbalImageTileset.TilesetIcon(
                            iconName = item.iconName,
                            contentDescription = item.descripcion,
                            size = 28.dp,
                            modifier = Modifier.scale(escala)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = (MITAD_TAB * 2f).dp, height = ALTO_TAB.dp)
                    .scale(rebote.value)
                    .clip(RoundedCornerShape(bottomStart = RADIO_TAB.dp, bottomEnd = RADIO_TAB.dp))
                    .background(colors.pestana)
                    .clickable(onClickLabel = addDescription, role = Role.Button, onClick = {
                        when {
                            vuelo != null -> Unit
                            agregarSeleccionado && menuAbierto -> cerrarMenu(true)
                            agregarSeleccionado -> {
                                menuAbierto = true
                                scope.launch {
                                    rebote.snapTo(0.82f)
                                    rebote.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
                                }
                            }
                            else -> despegar()
                        }
                    })
                    .semantics { contentDescription = addDescription },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    HerbalImageTileset.TilesetIcon(
                        iconName = "agregar",
                        contentDescription = "Agregar",
                        size = 28.dp
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            if (tVuelo < 0f) return@Canvas
            val f = vuelo ?: return@Canvas
            scale(densidad, Offset.Zero) {
                val t = min(tVuelo, f.duracion)
                val x = f.x0 + f.s * f.vx * t
                val y = CY - f.vy * t + 0.5f * GRAVEDAD * t * t
                val vxv = f.s * f.vx
                val vyv = -f.vy + GRAVEDAD * t
                val sp = min(1f, hypot(vxv, vyv) / 420f)
                val u = t / f.duracion
                val r = when {
                    u < 0.15f -> BASE + (16f - BASE) * u / 0.15f
                    u > 0.8f -> 16f + (12.5f - 16f) * (u - 0.8f) / 0.2f
                    else -> 16f
                }
                val angulo = (atan2(vyv, vxv) * 180.0 / PI).toFloat()
                rotate(angulo, Offset(x, y)) {
                    drawOval(
                        color = colors.gota,
                        topLeft = Offset(x - r * (1f + 0.3f * sp), y - r * (1f - 0.15f * sp)),
                        size = Size(r * 2f * (1f + 0.3f * sp), r * 2f * (1f - 0.15f * sp)),
                    )
                }
            }
        }

        if (menuAbierto) {
            Popup(
                popupPositionProvider = remember(densidad) { PosicionMenu((4f * densidad).toInt()) },
                onDismissRequest = { cerrarMenu(true) },
                properties = PopupProperties(focusable = true),
            ) {
                MenuAgregar(
                    colors = colors,
                    onNueva = { cerrarMenu(false); onNuevaPlanta() },
                    onEditar = { cerrarMenu(false); onEditarPlanta() },
                )
            }
        }
    }
}

@Composable
private fun MenuAgregar(colors: HerbalNavColors, onNueva: () -> Unit, onEditar: () -> Unit) {
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progreso.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
    }
    val forma = RoundedCornerShape(26.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            val p = progreso.value
            transformOrigin = TransformOrigin(0.5f, 1f)
            translationY = (1f - p) * 34.dp.toPx()
            scaleX = 0.35f + 0.65f * p
            scaleY = 0.2f + 0.8f * p
            alpha = min(1f, p * 2f)
        },
    ) {
        Column(
            Modifier
                .width(224.dp)
                .shadow(12.dp, forma)
                .clip(forma)
                .background(colors.superficie)
                .border(1.dp, colors.borde, forma)
                .padding(8.dp)
        ) {
            FilaMenu("Nueva planta", colors, editar = false, onClick = onNueva)
            FilaMenu("Editar planta", colors, editar = true, onClick = onEditar)
        }
        Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
            val pico = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(pico, colors.superficie)
        }
    }
}

@Composable
private fun FilaMenu(texto: String, colors: HerbalNavColors, editar: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            HerbalImageTileset.TilesetIcon(
                iconName = if (!editar) "agregar" else "plantas",
                contentDescription = texto,
                size = 24.dp
            )
        }
        Text(
            text = texto,
            color = colors.iconoInactivo,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private class PosicionMenu(private val separacionPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        val y = max(0, anchorBounds.top - popupContentSize.height - separacionPx)
        return IntOffset(x, y)
    }
}

private class Liquido {
    var x = 0f
    var xc = 0f
    var v = 0f
    var vc = 0f
    var objetivo = 0f
    var aparicion = 1f

    fun colocar(px: Float) {
        x = px; xc = px; v = 0f; vc = 0f; objetivo = px; aparicion = 1f
    }

    fun paso(g: Geometria) {
        v = (v + (objetivo - x) * 0.11f) * 0.69f
        x += v
        vc = (vc + (x - xc) * 0.07f) * 0.76f
        xc += vc
        val lim = g.limite(x, xc)
        if (abs(x - xc) > lim) {
            xc = x - sign(x - xc) * lim
            vc = v
        }
        aparicion = min(1f, aparicion + 0.08f)
    }

    fun enReposo() = abs(v) + abs(vc) + abs(objetivo - x) + abs(x - xc) < 0.05f
}

private class Gota(val x: Float, val y: Float, val rx: Float, val ry: Float)

private class Geometria(private val ancho: Float) {
    private val centro = ancho / 2f

    fun fondo(x: Float): Float {
        val a = abs(x - centro)
        if (a >= MITAD_TAB) return -1f
        val plano = MITAD_TAB - RADIO_TAB
        if (a <= plano) return ALTO_TAB
        val d = a - plano
        return ALTO_TAB - RADIO_TAB + sqrt(RADIO_TAB * RADIO_TAB - d * d)
    }

    private fun compD(d: Float): Float {
        val c = ((D_FUERA - d) / (D_FUERA - D_DENTRO)).coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun comp(x: Float) = compD(abs(x - centro))

    fun limite(x: Float, xc: Float): Float {
        val lo = min(x, xc)
        val hi = max(x, xc)
        val ds = if (centro in lo..hi) 0f else min(abs(lo - centro), abs(hi - centro))
        val sw = ancho / SLOTS_TOTALES
        return sw * 3f + (LARGO_BAJO_TAB - sw * 3f) * compD(ds)
    }

    fun gota(x: Float, rx0: Float, ry0: Float): Gota {
        val c = comp(x)
        val rx = rx0 + (min(rx0, RX_U) - rx0) * c
        val ry = ry0 + (min(ry0, RY_U) - ry0) * c
        val cy = CY + (CY_U - CY) * c
        val bt = cy - ry
        val bb = cy + ry

        fun tope(bot: Float): Float {
            var top = bt
            var k = -0.99f
            while (k <= 0.991f) {
                val e = fondo(x + rx * k)
                if (e > 0f) {
                    val s = sqrt(1f - k * k)
                    val req = (e + HUECO - bot * (1f - s) / 2f) / ((1f + s) / 2f)
                    if (req > top) top = req
                }
                k += 0.11f
            }
            return top
        }

        var top = tope(bb)
        val den = ALTO_TAB + HUECO - bt
        val q = if (den > 0.01f) ((top - bt) / den).coerceIn(0f, 1f) else 0f
        val bot = bb + (SUELO - bb) * q
        top = min(tope(bot), bot - 5f)
        return Gota(x, (top + bot) / 2f, rx, (bot - top) / 2f)
    }
}

private class Vuelo(val x0: Float, val s: Float, val vx: Float, val vy: Float, val duracion: Float)

private fun crearVuelo(x0: Float, centro: Float): Vuelo? {
    val dx = centro - x0
    val dist = abs(dx)
    if (dist < 8f) return null
    val th = (72.0 - 17.0 * ((dist - 70f) / 80f).coerceIn(0f, 1f)) * PI / 180.0
    val tn = tan(th).toFloat()
    val cs = cos(th).toFloat()
    val sn = sin(th).toFloat()
    val v = sqrt(GRAVEDAD * dist * dist / (2f * cs * cs * max(10f, dist * tn + (Y_ATERRIZAJE - CY))))
    return Vuelo(x0, sign(dx), v * cs, v * sn, dist / (v * cs))
}
