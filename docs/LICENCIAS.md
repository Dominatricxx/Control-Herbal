# Verificación de licencias, copyright y marcas

Esta verificación es técnica, no asesoría legal. Para distribución comercial consulta a una persona abogada de propiedad intelectual.

## 1. Hallazgos que requieren tu decisión

### 1.1 El repositorio no tiene archivo LICENSE
Sin licencia, el código queda con todos los derechos reservados: nadie más puede legalmente usarlo, modificarlo ni redistribuirlo, aunque el repositorio sea público (GitHub solo concede ver y bifurcar). No añadí un LICENSE por mi cuenta porque elegir una licencia es una decisión del titular del copyright.

Recomendación: **Apache-2.0** (compatible con todas las dependencias, incluye concesión de patentes y cláusula de marcas) o **MIT** si prefieres simplicidad. Si no quieres permitir reutilización, déjalo como está, pero indícalo en el README. Para añadir Apache-2.0:

```bash
curl -o LICENSE https://www.apache.org/licenses/LICENSE-2.0.txt
# y agrega en el README:  Licencia: Apache-2.0 © {{AÑO}} {{TITULAR}}
```

Los textos de `/legal` (aviso, términos, cookies) son plantillas del proyecto; si los publicas con la licencia del código, cualquiera podrá reutilizarlos. Está bien, pero cada operador debe sustituir sus propios datos.

### 1.2 Imágenes sin procedencia documentada
No encontré metadatos de autoría en ninguna imagen. Documenta de dónde proviene cada una (hecha por ti, encargo, generada con IA, banco de imágenes con licencia, etc.) y conserva el comprobante:

- Ilustración principal (planta a tinta y acuarela con un personaje sentado): aparece en `assets/Ícono Control Herbal.png` (versión con marco circular y hierba) y en una versión recortada que se usa como icono de la app (`ic_launcher*` en `app` y `wear`), como `app/src/main/ic_launcher-playstore.png`, `desktop/.../logo_desktop.png` y `desktop/.../fondo_plantasia.png` (estos tres son copias idénticas del mismo archivo). Toda la identidad visual depende de esta obra, así que es lo más importante de documentar.
- `assets/*.svg`: `architecture`, `structure`, `title` y el gráfico de lenguajes. El último lo genera un workflow propio; los demás se supone propios.
- `capturas/` y `docs/screenshots/`: capturas de pantalla propias. Revisadas: no muestran correos ni datos de cuenta. Muestran un apodo de planta ("Roberto") y la barra de estado.

Si el icono se generó con IA, revisa los términos de la herramienta (uso comercial, atribución) y que no imite a un artista concreto. Si lo hizo otra persona, necesitas su cesión o licencia por escrito.

## 2. Dependencias y licencias (resumen)

El inventario completo está en `THIRD_PARTY_NOTICES.md`. Resumen:

- Todas las bibliotecas de la app, el reloj y el escritorio son **Apache-2.0**, salvo JUnit (EPL-1.0, solo pruebas) y los `play-services-*` transitivos (Android SDK License, propietaria pero de redistribución permitida).
- El servicio de Firebase y la API de Gemini no se rigen por la licencia del SDK sino por los **términos de Google** (Firebase ToS, Google APIs ToS, Gemini API Additional Terms). Cumplirlos es obligación de quien opera el proyecto.
- Firmware: el núcleo `arduino-esp32` es LGPL-2.1. Para un proyecto de código abierto no plantea problema; si distribuyes binarios del firmware, ofrece el código fuente y los medios para recompilar y reflashear.
- Python y Node (solo herramientas de desarrollo): BSD, MIT y Apache-2.0.

Ninguna dependencia usa licencias copyleft fuertes (GPL/AGPL) en el producto distribuido.

## 3. Problema de licencia corregido

`org.json:json` (escritorio) se distribuye bajo la "JSON License", que añade la cláusula "el software debe usarse para el Bien, no para el Mal". Esa cláusula la hace no libre según la FSF y la OSI, y es incompatible con Apache-2.0. Se sustituyó por **Gson (Apache-2.0)**. En Android, `org.json` viene en la plataforma (Apache-2.0) y no se distribuye con la app.

## 4. Marcas registradas

Las siguientes marcas pertenecen a sus titulares y se mencionan solo de forma descriptiva (uso nominativo), sin sugerir afiliación. Los Términos de Servicio incluyen el aviso correspondiente.

- Google LLC: Android, Wear OS, Google, Google Play, Firebase, Gemini, TensorFlow.
- JetBrains: Kotlin.
- Arduino SA: Arduino. Espressif Systems: ESP32.
- Docker Inc.: Docker. GitHub Inc.: GitHub. Python Software Foundation: Python.

Reglas prácticas que se cumplen hoy y deben mantenerse:
- No se usa el robot de Android, ni logotipos de Google, Firebase o Gemini como parte del icono ni de la interfaz de la app.
- No se afirma ni se sugiere que Google, Espressif u otros respalden el proyecto.
- Si añades insignias "Powered by" o logos en el README, sigue la guía de marca de cada empresa. Las insignias de `skillicons.dev` muestran logos de terceros; el uso descriptivo es habitual, pero conviene revisarlas si el proyecto se comercializa.

### El nombre "Control Herbal"
No pude comprobar registros de marca. Antes de comercializar o publicar en tiendas, haz una búsqueda de anterioridades en el Instituto Mexicano de la Propiedad Industrial (MarcaNet) y, si vas a distribuir fuera de México, en EUIPO y USPTO. "Herbal" es un término descriptivo y de uso común, por lo que la protección de la parte genérica es limitada. "Plantasia" solo aparece como nombre interno de un archivo (`fondo_plantasia.png`), nunca como texto visible; se recomienda renombrarlo porque coincide con un álbum musical conocido.

## 5. Cómo mantener esto al día

1. Para el inventario exacto de dependencias transitivas con licencia, genera un reporte en tu equipo (por ejemplo con el plugin `com.github.jk1.dependency-license-report`) y compáralo con `THIRD_PARTY_NOTICES.md`.
2. Revisa las licencias al aceptar cada actualización de Dependabot que añada una dependencia nueva.
3. Rechaza dependencias con licencia GPL/AGPL, "JSON License", "Commons Clause", "BUSL" o sin licencia declarada.
