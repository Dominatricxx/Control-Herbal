# Accesibilidad

Objetivo: WCAG 2.2 nivel AA en las pantallas propias, con comprobación automática en CI y un plan de pruebas manuales.

## 1. Texto alternativo

- Todo `ImageView`, `ImageButton` y botón flotante tiene `contentDescription` o está marcado como decorativo (`importantForAccessibility="no"`).
- Las descripciones están en `strings.xml` y describen la acción ("Enviar mensaje"), no el dibujo.
- La imagen enviada al asistente tiene su propia descripción. El IRH expone una descripción con su valor y estado.
- Los iconos del escritorio (Compose) son decorativos porque acompañan siempre un texto visible.
- Los encabezados de las pantallas nuevas están marcados (`accessibilityHeading`) para que TalkBack permita saltar entre ellos.
- `scripts/audit_a11y.py` falla el CI si falta una descripción.

## 2. Colores para distintas condiciones visuales

Menú, Privacidad y accesibilidad, sección de colores. Cuatro paletas para los estados Óptimo, Atención y Riesgo:

Estándar. Verde, ámbar oscuro y rojo. Contraste mínimo 4.7:1. Naranja y rojo quedan a poca distancia perceptual (ΔE 13.7); por eso el estado siempre lleva símbolo y texto.

Rojo-verde (protanopia y deuteranopia). Azul, ámbar oscuro y magenta. Distancia mínima ΔE 21.1 bajo ambas simulaciones.

Azul-amarillo (tritanopia). Verde, marrón y rosa intenso. Distancia mínima ΔE 18.9 bajo tritanopia.

Alto contraste. Azul marino, marrón oscuro y magenta oscuro. Contraste mínimo 7.3:1 (AAA). Con tanto contraste todos los colores son oscuros, así que la distancia entre ellos es menor (ΔE 6.8) y se apoya aún más en símbolo y texto.

Cómo se verificó: `scripts/audit_palettes.py` simula las tres condiciones (matrices de Machado, Oliveira y Fernandes, 2009), calcula distancias CIEDE2000 y contrastes WCAG, y lee los colores directamente de `StatusPalette.kt`. También cubre los siete colores de series de gráficos (contraste mínimo 3.27:1, distancia mínima ΔE 18.9 bajo todas las condiciones).

Principio aplicado: el color nunca es la única señal.
- Estados con símbolo y texto: ✔ Óptimo, ▲ Atención, ✖ Riesgo, ℹ Información.
- Series de gráficos con trazo distinto (continuo, rayas, puntos, marcadores) además del color. La segunda planta de una comparación usa el mismo color de métrica con trazo punteado.
- El widget y el IRH usan la paleta elegida.

## 3. Otras correcciones hechas

- Contraste de texto: 26 textos por debajo de 4.5:1 corregidos (por ejemplo `#757575`, `#9E9E9E`, `#E65100`, `#1E88E5`) y dos botones con texto blanco sobre fondo claro.
- Objetivos táctiles: botones de icono de 40 dp pasados a 48 dp; botones nuevos con `minHeight` de 48 dp.
- Reloj Wear OS: el texto estaba entre 5 y 9 sp; ahora mínimo 12 sp, y el botón mide 48 dp.
- Tamaños de texto siempre en `sp` (respetan el tamaño de fuente del sistema).
- Campos de texto con etiqueta visible (`hint`).
- Escritorio: gris y rojo nombrados de Compose y dos tonos de azul y naranja sustituidos por equivalentes con contraste suficiente.

## 4. Límites conocidos

- El auditor revisa layouts XML. Las pantallas de Compose del escritorio y los colores asignados en código se revisaron a mano; no hay prueba automática equivalente.
- El reloj usa la paleta estándar fija (no tiene selector).
- Las simulaciones de daltonismo son aproximaciones. No sustituyen pruebas con personas usuarias reales.
- No se probó con TalkBack, Switch Access ni tamaño de fuente máximo: no hubo dispositivo. Antes de publicar, repasa al menos: inicio de sesión, consentimiento, pantalla principal, asistente y Privacidad con TalkBack activo y fuente al 200 %.
- Sin modo oscuro propio.

## 5. Comandos

```bash
python scripts/audit_a11y.py        # layouts: descripciones, tamaños, contraste, objetivos táctiles
python scripts/audit_palettes.py    # paletas y colores de gráficos para daltonismo
```
