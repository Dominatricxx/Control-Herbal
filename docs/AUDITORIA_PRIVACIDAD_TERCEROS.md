# Auditoría de analítica, rastreo y servicios de terceros

Fecha de la auditoría: octubre de 2026 · Alcance: apps Android, Wear OS y escritorio, firmware ESP32, entrenador de ML, Docker/Mosquitto, CI y README.

## 1. Resultado en una frase

El proyecto **no incluye analítica, publicidad, atribución, reporte de fallos, mensajería push ni mapas/ubicación**. Todos los datos que salen del dispositivo van a **un solo proveedor (Google/Firebase)**, y los de IA solo después de un consentimiento expreso. Este resultado lo hace cumplir `scripts/audit_privacy.py` en cada integración continua.

## 2. Qué se verificó y cómo

- Dependencias: búsqueda de 28 familias de SDK de rastreo en todos los `*.kts`, el catálogo de versiones, `requirements.txt` y `package.json`.
- Permisos: lista blanca de 8 permisos; `AD_ID` eliminado con `tools:node="remove"`; sin `CAMERA`, ubicación, contactos, micrófono, almacenamiento ni `WIFI_STATE`.
- Código: ausencia de `WebView`, `CookieManager`, `localStorage`, ID de publicidad, `ANDROID_ID`, identificadores de teléfono, ubicación e instalación-referrer.
- Dominios: todo `http(s)://` del código fuente pertenece a Google/Firebase u hospedajes técnicos conocidos.
- Documentos legales: presentes, con versión igual a `LEGAL_VERSION`, y sin promesas que el código incumpla.
- Pruebas de mutación: se inyectaron a propósito un permiso de ubicación, el SDK `firebase-analytics`, un dominio de rastreo, la reactivación de la analítica y una versión legal desalineada. El auditor detectó las cinco.

## 3. Inventario de servicios que reciben datos

Firebase Authentication. Recibe correo y contraseña (que solo ve Firebase), IP e identificadores de instalación. Es necesario. No tiene exclusión voluntaria: sin cuenta no hay acceso.

Firebase Realtime Database. Recibe lecturas de sensores, `tipoPlanta` (número 1 a 3), órdenes de riego (duración, hora, UID, código aleatorio), historial de riego y roles. No recibe el nombre de la planta. Es necesario.

Firebase App Check y Google Play Integrity. Reciben señales de integridad del dispositivo y de la app. Es necesario para proteger el servicio. En compilaciones debug se usa un proveedor de depuración que no existe en release.

Firebase AI Logic y API de Gemini (Agent Platform). Reciben fotos reducidas (sin EXIF), el texto del asistente y el tipo de planta. Es opcional, con consentimiento previo y revocable. Las políticas de retención son de Google: https://firebase.google.com/docs/ai-logic/data-governance

Google Play Services base (transitivo). Lo traen las bibliotecas de Firebase. Puede intercambiar datos técnicos con Google. No se pudo inspeccionar su tráfico desde este entorno (ver sección 6).

Servicios que solo funcionan en el dispositivo y no envían datos: Room, WorkManager, TensorFlow Lite, MPAndroidChart, Glance, Compose y AndroidX.

## 4. Herramientas externas fuera de la app

GitHub Actions. Ejecuta CI. Todas las acciones se fijan por SHA y el token tiene permisos mínimos. Reciben el código del repositorio, no datos de usuarios.

JitPack. Se consulta en tiempo de compilación solo para `MPAndroidChart`, limitado al grupo `com.github.PhilJay`. Ve la IP de quien compila.

README en GitHub. Carga imágenes decorativas de `capsule-render.vercel.app`, `skillicons.dev` e `img.shields.io`. GitHub las sirve a través de su proxy de imágenes, pero son tres terceros que se pueden caer o cambiar. Recomendación: descargarlas a `assets/` si se quiere independencia total.

Docker Hub (`python`, `eclipse-mosquitto`). Imágenes base con descarga en compilación.

## 5. Hallazgos corregidos en esta revisión

- Eliminado `org.json:json` del escritorio (licencia "JSON License", ver `docs/LICENCIAS.md`); sustituido por Gson.
- Eliminadas dependencias sin uso: `play-services-wearable` (app y reloj), `androidx.wear`, `percentlayout`, `legacy-support-v4` y `recyclerview` en el reloj. Menos SDK propietarios y menos superficie.
- Quitados los permisos `CAMERA` (la foto la toma la app de cámara del sistema), `VIBRATE` y `ACCESS_WIFI_STATE`, que no se usaban o no eran necesarios. Se eliminó el flujo de solicitud del permiso de cámara.
- Analítica de Firebase, ID de publicidad, SSAID y Crashlytics desactivados de forma explícita, y `AD_ID` eliminado, por si un SDK transitivo los reintroduce.
- El nombre que la persona da a su planta dejó de enviarse a la IA (solo el tipo). Se añadió una advertencia en el campo del nombre.
- Consentimiento de IA reforzado: texto específico, revocable en la app y con aviso de seguridad sobre plantas tóxicas.

## 6. Lo que esta auditoría NO puede garantizar

- No se pudo resolver el árbol de dependencias transitivas (sin acceso a Google Maven). Falta comprobar con `./gradlew :app:dependencies` que no entra ningún SDK de la lista prohibida. El CI de `verify.yml` lo compila, y `scripts/audit_privacy.py` revisa los archivos declarados, no los transitivos.
- No se capturó tráfico de red real. Recomendado antes de publicar: instalar el APK release en un dispositivo de pruebas y observar las conexiones con mitmproxy o el Network Inspector, verificando que solo aparecen dominios de Google.
- Los textos de Google (retención, ubicación de servidores) cambian; revísalos al publicar.
- La declaración de **Seguridad de los datos** de Google Play Console debe rellenarse con base en esta auditoría (ver `docs/LEGAL.md`).

## 7. Cómo repetir la auditoría

```bash
python scripts/audit_privacy.py            # reglas de privacidad y rastreo
python scripts/audit_privacy.py --strict   # además exige rellenar los marcadores legales (publicación)
./gradlew :app:dependencies --configuration releaseRuntimeClasspath   # revisar SDK transitivos
```
