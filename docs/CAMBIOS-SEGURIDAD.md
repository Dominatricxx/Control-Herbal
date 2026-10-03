# Registro de cambios de seguridad (hallazgo → corrección)

## Alto
| # | Hallazgo | Corrección |
|---|---|---|
| 1 | Firebase sin autenticación | Firebase Auth (correo/contraseña) en app, reloj, escritorio, ML y ESP32; reglas por rol en `firebase/database.rules.json` con validación de tipos/rangos, campos desconocidos rechazados y límite de frecuencia; tests en `firebase/test/`. |
| 2 | Riego sin autorización ni límites | Solo rol `owner`; duración 1–60 s; `timestamp` del servidor; `uid` = emisor; `nonce` obligatorio; 30 s entre órdenes (reglas). Cliente: cooldown de 60 s y manejo de rechazo. Requisitos para el firmware futuro en `docs/SEGURIDAD.md §4`. |
| 3 | Gemini sin App Check | `firebase-vertexai` beta → `firebase-ai` (BoM); App Check (Play Integrity en release, debug solo en debug); `generativeai` eliminada; consentimiento previo (`AiConsent`); fotos reducidas, sin EXIF y borradas tras usarse (cache interno); tiempo máximo y salida acotada. |
| 4 | Envenenamiento del modelo | Lectura autenticada; validación de rango + filtro IQR + mínimo de registros reales (sin datos sintéticos) + umbral de MAE; `.sha256` verificado en compilación (`verifyModelIntegrity`); `ModelLoader` valida tamaño y forma de tensores y la salida. Se corrigió además la incoherencia entradas del entrenador (3) vs. app (5). |

## Medio
| Hallazgo | Corrección |
|---|---|
| Credenciales en firmware | `secrets.h` (+ `.example`, en `.gitignore`); el firmware no compila sin él. |
| ESP32 identidad compartida / TLS / tasa | Cuenta propia por dispositivo; `FIREBASE_ROOT_CA` obligatorio; librería mobizt `Firebase_ESP_Client`; envío cada 5 s. |
| `network_security_config` | Eliminadas las excepciones de texto claro. Wear: configuración HTTPS explícita. |
| Wear OS | `allowBackup=false`, reglas de extracción, minify + shrink resources, ProGuard propio, login de solo lectura. |
| `SecurityUtils` | Eliminada la lista de frases prohibidas; ahora normalización Unicode, caracteres de control/bidi, límites, delimitado estructural en el prompt (instrucción de sistema), y `extractAlert` con formato estricto en la última línea. Ya no borra `/` ni comillas de los nombres. |
| Cadena de suministro | Room 2.8.5 estable; `firebase-vertexai` y `generativeai` fuera; versión de Firebase única vía BoM; JitPack limitado al grupo de MPAndroidChart; repositorio `jetbrains.space` eliminado; `proguard-rules.pro` sin `-keep` de `firebase.**`/`ai.**`; Dependabot. |
| CI y Docker | `permissions: contents: read`, acciones fijadas por SHA, `persist-credentials: false`, validación del wrapper de Gradle, CodeQL, gitleaks, tests de reglas, contenedor `read_only`/`cap_drop`/`no-new-privileges`. Mosquitto: sin acceso anónimo, contraseña obligatoria, puertos en `127.0.0.1`. |

## Bajo
Escritorio: persistencia con escape de campos, escritura atómica y permisos 0600; `FirebaseService` con
tiempos de espera, tope de respuesta, solo HTTPS y validación; sin `printStackTrace`. Repo: eliminados
`.idea/`, `app/AndroidManifest.xml` vacío y `app/release/`; `.gitignore` ampliado. Android: fotos fuera de
almacenamiento externo. Widgets: no se modificaron (heredan de `GlanceAppWidgetReceiver`); queda pendiente revisar su comportamiento ante intents externos.

## Segunda pasada (pendientes resueltos)
- **Modelo de IA vigente.** `gemini-2.5-flash` se retira en octubre de 2026 y `vertexAI()` es sintaxis heredada: ahora `gemini-3.5-flash` + `GenerativeBackend.agentPlatform()` + BoM 34.19.0, y `maxOutputTokens` 1024 (los modelos con razonamiento cuentan ahí sus tokens de pensamiento). `firebase-database-ktx` (versión fija 21.0.0, ya fuera del BoM) → `firebase-database` por BoM.
- **Reglas de Firebase.** Evaluador local (`firebase/test-offline/`, `npm run test:offline`): 19 escenarios, todos OK, y probado por mutación (6 reglas rotas a propósito, las 6 detectadas). Se corrigió además `npm test`, que usaba `node --test test/` (falla en Node 22) → globs.
- **Widgets.** Revisados: los receivers deben seguir exportados y no leen datos del Intent (un broadcast falso solo redibuja desde la base local). Se eliminó `runBlocking` dentro de la composición (bloqueaba el hilo principal).
- **Verificación automatizada en CI** (`.github/workflows/verify.yml`): compila `:wear`, `:app` release con R8 y `:desktop`, ejecuta pruebas unitarias, compila el firmware ESP32 con `arduino/compile-sketches` y ejecuta las reglas. `gradle-verification-metadata.yml` (manual) genera `verification-metadata.xml` como artefacto.
- **Comprobado localmente con stubs:** `SecurityUtils`, `PlantStorage`, `ModelLoader`, `ImageUtils`, `AuthManager`, limpieza de datos del entrenador y coherencia del catálogo de versiones.

## Lo que sigue sin poder verificarse aquí (lo cubre CI o depende de ti)
- Primera ejecución real de `verify.yml`: si falla la compilación de `:app` por la API de Firebase AI Logic o por el firmware, el log dirá exactamente dónde.
- `npm test` contra el emulador oficial de Firebase (el evaluador local es una aproximación; ver su cabecera).
- `gradle/verification-metadata.xml`: ejecuta el workflow manual y haz commit del artefacto.
- Fuera del código: App Check en modo *enforce* para AI Logic (Firebase lo exigirá desde el 2-nov-2026), restricción de la API key en Google Cloud, creación de usuarios y `/roles`, y **rotación de claves** si alguna estuvo en un commit público.
