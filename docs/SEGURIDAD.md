# Configuración segura de Control Herbal

Los cambios de código de este repositorio **no funcionan por sí solos**: dependen de que configures
Firebase como se indica abajo. Sin estos pasos, la app mostrará la pantalla de acceso pero las reglas
rechazarán toda lectura y escritura (que es precisamente el comportamiento deseado).

## 1. Modelo de identidades y roles

| Rol (`/roles/{uid}`) | Quién | Puede |
|---|---|---|
| `owner`  | Tú, en la app Android | Leer todo, escribir `config/tipoPlanta`, `control/riego`, `watering_history` |
| `device` | Cada ESP32 (cuenta propia) | Escribir `/sensor`, leer `config` y `control`, escribir `control/estado` |
| `viewer` | Reloj Wear OS y escritorio | Solo leer `/sensor` |

Los clientes no pueden leer ni escribir `/roles`: solo se editan desde la consola o con Admin SDK.

## 1.b Probar las reglas

```bash
cd firebase && npm install
npm run test:offline   # evaluador local, sin descargas
npm test               # emulador oficial de Firebase (recomendado antes de publicar las reglas)
```

## 2. Pasos en Firebase Console

1. **Authentication → Sign-in method**: habilita *Correo/contraseña* y **deshabilita *Anónimo***.
2. **Authentication → Users**: crea las cuentas (tú, cada ESP32, la cuenta de solo lectura) con
   contraseñas largas y únicas. Copia el **UID** de cada una.
3. **Realtime Database → Datos**: crea el nodo `roles` con una entrada por UID:
   ```
   roles
     <uid-de-tu-cuenta>:  "owner"
     <uid-del-esp32>:     "device"
     <uid-del-viewer>:    "viewer"
   ```
4. **Reglas**: publica `firebase/database.rules.json`
   (`cd firebase && cp .firebaserc.example .firebaserc` → edita el ID → `npx firebase deploy --only database`).
5. **App Check** (protege Firebase AI Logic y la base):
   - Registra la app Android con **Play Integrity** y añade la huella SHA-256 de tu firma de release.
   - **Firebase AI Logic → Aplicar (enforce)** App Check. Desde el 2-nov-2026 Firebase lo exigirá para usar AI Logic.
   - Realtime Database: déjalo en *Monitorizar* mientras uses el cliente REST de escritorio o el ESP32
     (ninguno de los dos puede atestiguar); la protección de la base la dan Auth + reglas.
   - Para desarrollo: ejecuta la app debug, copia el *debug secret* que imprime Logcat y regístralo
     en *App Check → Gestionar tokens de depuración*.
6. **Google Cloud Console → Credenciales**: restringe la API key de Android a tu `applicationId` + SHA-1/256,
   y limita las APIs habilitadas. Configura **presupuestos y alertas de facturación** y una cuota máxima
   para la API de Vertex AI / Gemini.

## 3. Variables y secretos (nunca en el repositorio)

| Componente | Cómo se configuran |
|---|---|
| ESP32 | `firmware/secrets.h` (copia de `secrets.h.example`; en `.gitignore`) |
| Reloj (Wear OS) | `~/.gradle/gradle.properties`: `herbal.viewerEmail=...` `herbal.viewerPassword=...` (o `HERBAL_VIEWER_EMAIL/PASSWORD`) |
| Escritorio | Variables de entorno `HERBAL_DB_URL`, `HERBAL_API_KEY`, `HERBAL_USER_EMAIL`, `HERBAL_USER_PASSWORD` |
| Entrenador ML | Mismas variables que el escritorio (cuenta `viewer`) |
| `google-services.json` | Descárgalo de tu proyecto; está en `.gitignore` |

> Todo lo que se embebe en un APK es extraíble. Por eso la cuenta del reloj es de **solo lectura** y
> revocable: si se filtra, desactívala en *Authentication* y genera otra.

## 4. Riego (cuando añadas relé/bomba)

El servidor (reglas) ya impone: solo `owner`, duración 1–60 s, `timestamp` del servidor, `uid` igual al
del emisor, `nonce` obligatorio y 30 s mínimos entre órdenes. **El firmware debe además**:

1. Leer `control/riego` solo si `activar == 1`.
2. Rechazar si el `nonce` ya fue procesado o si `timestamp` tiene más de ~60 s de antigüedad.
3. Limitar la duración a un máximo propio en el firmware y mantener un tope diario.
4. Publicar el resultado en `control/estado` (`nonce`, `resultado`, `segundos`, `ts`).
5. Tener un interruptor físico o *timeout* de hardware que corte la bomba si el firmware se cuelga.

## 5. Modelo TFLite

`ml/train_herbal_model.py` valida y filtra los datos, exige un mínimo de registros reales y rechaza
modelos con MAE alto. Genera `herbal_model.tflite` y `herbal_model.tflite.sha256`; **versiona ambos**.
La tarea Gradle `verifyModelIntegrity` falla la compilación si no coinciden, y `ModelLoader` descarta
en ejecución modelos con forma de tensores inesperada.

## 6. Verificación de dependencias

Ejecuta el workflow manual **"Generar gradle/verification-metadata.xml"** (Actions → Run workflow),
descarga el artefacto, revísalo y súbelo como `gradle/verification-metadata.xml`. En local sería:

```bash
./gradlew --write-verification-metadata sha256 :app:assembleDebug :wear:assembleDebug :desktop:compileKotlin
```
 Dependabot (`.github/dependabot.yml`) propondrá
actualizaciones semanales; CodeQL y gitleaks se ejecutan en cada PR.
