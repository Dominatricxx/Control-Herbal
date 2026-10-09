# Auditoría del sistema de autenticación y cambios aplicados

## 0. Lo primero: cómo es este sistema (y por qué algunos puntos no se aplican "al pie de la letra")

Control Herbal **no es una aplicación web con backend propio**. Es una app nativa Android (+ Wear, Desktop, firmware ESP32) cuya identidad la gestiona **Firebase Auth** (correo/contraseña) y cuya autorización la imponen las **reglas de Realtime Database**. Por tanto:

| Concepto del encargo | Realidad en este proyecto |
|---|---|
| `localStorage`, cookies `httpOnly/SameSite` | No existen (no hay navegador). Equivalente: dónde guarda la app la credencial. |
| Rutas `/login`, `/signup`, `/forgot-password`, `/reset-password`, `/verify-otp` | No existen como rutas nuestras: las aloja Google (Identity Toolkit). No hay registro público (las cuentas las crea el administrador). |
| Middleware en rutas admin | Equivalente: reglas de la base de datos (`firebase/database.rules.json`), que ya eran server-side. |

Se ha hecho lo que **sí** tiene efecto real en este modelo y se explica abajo qué no es posible y por qué.

---

## 1. Resultado por punto

### 1. Token de sesión en `localStorage` → ✅ ya conforme (verificado) + endurecido
- No hay `localStorage` ni almacenamiento propio de tokens. Firebase Auth guarda su credencial en el almacenamiento privado de la app; `allowBackup=false` y `data_extraction_rules.xml` la excluyen de copias de seguridad. Búsqueda en `app/`, `wear/`, `desktop/`: ningún token se escribe en preferencias ni en logs.
- Desktop/ML: el token vive solo en memoria.
- **Cambio:** las nuevas guardas de bloqueo (`login_guard`, etc.) también quedan fuera de las copias de seguridad y no contienen datos personales (solo contadores).
- Cookies httpOnly/Secure/SameSite=Strict: **no aplicable** (no hay web).

### 2. Comprobaciones de admin solo en el cliente → ✅ ninguna existía; ahora la barrera del servidor es más fuerte
- No hay ningún `if (isAdmin)` en el cliente: `/roles` tiene `.read: false`, por lo que la app ni siquiera puede saber su rol. El rol `owner` solo se decide en `database.rules.json`.
- **Cambio:** las reglas del rol `owner` (lectura de `sensor/config/control/watering_history`, escritura de `config/tipoPlanta` y `control/riego`) exigen ahora además `auth.token.firebase.sign_in_second_factor === 'totp'` y `auth.token.email_verified === true`. Un cliente modificado o un atacante con solo la contraseña **no** puede operar.
- Nuevo nodo `/_server` cerrado a todos los clientes.
- Tests: 25 casos offline (+6 nuevos) y los equivalentes para el emulador oficial. Se comprobó por mutación que quitar el 2FA de una regla hace fallar los tests.

### 3. Sin 2FA / OTP → ✅ implementado (TOTP + códigos de respaldo + obligatorio)
- **TOTP** con Firebase Auth MFA (verificado por Google, no por la app): enrolamiento con QR / clave manual / abrir en la app autenticadora (`MfaEnrollActivity`), verificación de correo previa, y petición del código en cada inicio de sesión.
- **Obligatorio**: la app no deja pasar sin 2FA (Splash/Main/Login redirigen al enrolamiento) y **las reglas del servidor lo exigen** para `owner`.
- **Caducidad del OTP**: la define el estándar TOTP (ventana de 30 s ± 1 intervalo, configurado en `harden-auth.mjs`); **limitación de intentos**: la de Google + bloqueo persistente en la app.
- **Códigos de respaldo** (Firebase no los ofrece): Cloud Functions `generateBackupCodes`, `backupCodesStatus`, `redeemBackupCode` (`firebase/functions/`). 10 códigos de ~50 bits, solo se guarda su HMAC-SHA256 con *pepper* secreto, un solo uso, canje atómico. Canjear exige correo + contraseña + código, elimina el factor perdido, **revoca todas las sesiones** y obliga a enrolar uno nuevo.
- Las cuentas de `device` (ESP32) y `viewer` (reloj/desktop) no son interactivas y no usan 2FA; no pueden escribir riego ni leer `/roles`.

### 4. Sin rate limiting en login → ✅ cubierto donde el servidor es nuestro; mitigado donde es de Google (ver §3)
- **Bloqueo persistente en la app** (`LoginThrottle`, sobrevive a cerrar la app): retroceso exponencial desde el 3.er fallo (5 s, 10 s… 5 min), bloqueo al 5.º (15 min, se duplica, tope 24 h). Aplica a contraseña, código TOTP, contraseña actual al cambiarla y solicitudes de restablecimiento.
- **Registro de todos los fallos** (`SecureLogger`, con huella SHA-256 truncada del correo; nunca correo ni clave) y, en el servidor, registro de peticiones de Auth en Cloud Logging (`harden-auth.mjs`).
- **Endpoint propio `redeemBackupCode`** (el único que controlamos): App Check obligatorio, 5 intentos/15 min por IP, retroceso exponencial y bloqueo por cuenta (aunque cambie de IP), respuesta genérica, registro de fallos sin datos personales.
- **CAPTCHA tras 3 fallos (autoalojado):** a partir del 3.er fallo de una cuenta, el servidor exige un **reto de prueba de trabajo** firmado (HMAC), atado a la cuenta, de un solo uso y con caducidad de 2 min; la dificultad sube con cada fallo (18, 20, 22 bits). La app lo resuelve sola y reintenta. Pedir el reto no consume intentos. Ver límites en §3.
- **Alertas por correo** (`scripts/setup-alerts.sh`): aviso inmediato si alguien restablece el 2FA con un código de respaldo y aviso si hay ≥ 10 fallos/bloqueos/retos en 10 min.
- **Anti-enumeración de correos**, límites propios de Google en login/reset y **App Check obligatorio opcional** sobre el login y la base (`harden-auth.mjs --appcheck-enforce`, con advertencias).
- **reCAPTCHA Enterprise** configurable (`--recaptcha-enforce`), por defecto en `AUDIT`.

### 5. Sin comprobación de fortaleza de contraseña → ✅ implementado en cliente y servidor
- **Cliente** (`ChangePasswordActivity`): medidor en tiempo real con **zxcvbn** (`com.nulab-inc:zxcvbn`) + lista de requisitos en vivo.
- **Reglas** (idénticas en Kotlin y Node): ≥ 12 y ≤ 128 caracteres, mayúscula, minúscula, número y símbolo, sin correo/nombre, sin repeticiones ni secuencias (`12345`, `qwerty`), sin contraseña común tras normalizar (`P@ssw0rd!2024` → `password`) y zxcvbn ≥ 3. Rechaza `password123`, `12345678`, `Password123!`, etc. (comprobado).
- **HaveIBeenPwned** con k-anonimato (solo salen 5 caracteres del SHA-1) en la app y en el alta de cuentas.
- **Servidor**: política de contraseñas de Firebase Auth en modo `ENFORCE` (longitud y clases) vía `harden-auth.mjs`; alta de cuentas con política completa (zxcvbn + HIBP) en `provision-user.mjs`.
- Nuevas pantallas: cambio de contraseña (con reautenticación + 2FA) y "¿Olvidaste tu contraseña?" (mensaje idéntico exista o no la cuenta).

---

## 2. Hallazgos adicionales corregidos (fuera de los 5 puntos)
| Gravedad | Hallazgo | Acción |
|---|---|---|
| **Crítica** | `telemetry_bridge.py` incluía un **token de InfluxDB real** como valor por defecto | Eliminado; el script exige `INFLUX_TOKEN` por entorno. **Debes rotar ese token** (sigue en el historial de git). |
| Alta | `docker-compose.yml`: contraseña admin de InfluxDB hardcodeada y débil (`controlherbal2026`), Grafana con admin por defecto | Ahora se exigen por `.env` (`.env.example`, `.env` en `.gitignore`); **cambia la contraseña de InfluxDB** si ya se desplegó. |
| Media | Puertos de MQTT anónimo, InfluxDB y Grafana publicados en 0.0.0.0 | Enlazados a `127.0.0.1` (para exponer: `deploy/` con TLS+ACL). |

---

## 3. Límites que siguen existiendo (y qué se hizo en su lugar)
1. **CAPTCHA:** el reto de prueba de trabajo encarece cada intento automatizado, pero **no distingue personas de bots** (un atacante con GPU lo resuelve más rápido que un móvil). Para eso está reCAPTCHA Enterprise (`--recaptcha-enforce`). **Compromiso:** en `ENFORCE`, `redeemBackupCode` no puede verificar la contraseña por REST (esa llamada no lleva token), por eso queda en `AUDIT` por defecto.
2. **Login de Google (Identity Toolkit):** no podemos interponer un proxy ni contar IPs. Lo más cercano es **App Check ENFORCED** (`--appcheck-enforce identitytoolkit`): Google rechaza peticiones que no vengan de la app genuina. Coste: dejan de funcionar Desktop, `ml/` y el canje de códigos de respaldo (verifica la contraseña por REST). Por eso **no se activa solo**: exige `--yes` y conviene medir antes en Consola → App Check. Mientras tanto, el 2FA obligatorio hace que la contraseña sola no dé acceso a nada. El bloqueo de la app es una defensa de interfaz (se evita borrando los datos de la app).
3. **Lista "top 10k":** sin acceso a red aquí no pude traer una lista verificada. `assets/common_passwords.txt` trae 271 *núcleos* y ahora hay una herramienta reproducible para ampliarlo: `npm run fetch:passwords` descarga SecLists 10k-most-common, la normaliza igual que la política, imprime el SHA-256 de la fuente y se niega a procesar fuentes sospechosamente cortas. **Ejecútala una vez con red.** Mientras tanto la cobertura amplia la dan zxcvbn y HaveIBeenPwned.
4. **Aviso por correo al restablecer 2FA:** resuelto con alertas de Cloud Monitoring (punto 4), no con un correo transaccional al propio usuario (eso requeriría un servicio de correo).
5. **Token con segundo factor tras reiniciar sesión:** la app comprueba que el token de la sesión incluye `sign_in_second_factor`; si no, cierra la sesión y lo explica, en lugar de dejar fallos de permisos silenciosos.

---

## 4. Orden de despliegue (importante: el orden evita que te quedes sin acceso)
1. Consola Firebase → pasar a **Identity Platform** (gratis hasta 50 000 MAU) y activar **MFA → TOTP**.
2. `cd firebase && npm install && node scripts/harden-auth.mjs --project <ID>` (simulación) y luego con `--apply`.
3. Funciones: `firebase functions:secrets:set BACKUP_CODE_PEPPER` (también deriva la clave de los retos) (≥ 32 caracteres aleatorios) → `firebase deploy --only functions` (te pedirá `HERBAL_API_KEY`, la Web API key). Registrar la app en **App Check** (Play Integrity) — las funciones lo exigen.
4. Publicar la **nueva versión de la app**; el owner inicia sesión, verifica su correo y **enrola el TOTP**, vuelve a entrar y guarda sus 10 códigos de respaldo.
5. **Solo entonces** desplegar las reglas: `firebase deploy --only database`. Si se hace antes, el owner pierde acceso a los datos hasta enrolar el 2FA (sigue pudiendo enrolarlo: no depende de la base de datos).
6. `npm run fetch:passwords` (con red) y `bash scripts/setup-alerts.sh <ID> <correo>`.
7. Rotar el token y la contraseña de InfluxDB, y cambiar contraseñas débiles de cuentas de dispositivo (`provision-user.mjs --generate`).

## 5. Verificación realizada y sus límites (honestidad)
- ✅ **79 tests pasan** aquí: reglas (25), política de contraseñas, HIBP y lista común (19), funciones de 2FA y reto de trabajo (35). Quitar el reto o el 2FA de las reglas hace fallar los tests (comprobado por mutación).
- ✅ Sintaxis, recursos XML y referencias `R.*` de la app comprobados mecánicamente.
- ⚠️ **El código Kotlin no se ha compilado ni ejecutado** (sin Gradle ni red en este entorno), igual que `harden-auth.mjs`/`provision-user.mjs` (necesitan `npm install` y credenciales), el test del **emulador de reglas** y el despliegue de funciones. Los tests de Kotlin (`PasswordPolicyTest`, `LoginThrottleTest`, `PwnedPasswordsTest`, `ProofOfWorkTest`) reflejan los casos de Node ya probados y los ejecutará `./gradlew :app:testDebugUnitTest` en CI. Es probable que haya algún ajuste menor de compilación o de API (TOTP, zxcvbn4j) en la primera build: revísala antes de publicar.
- ⚠️ Los nombres de campos de `harden-auth.mjs` (Admin SDK `projectConfigManager`) están escritos de memoria de la documentación: el script informa por sección qué falla; contrasta con la consola.
- ⚠️ Supuesto no verificado: que tras volver a iniciar sesión con TOTP el token incluye `sign_in_second_factor` (así lo documenta Firebase); por eso el flujo cierra sesión tras enrolar en vez de reutilizar la sesión.
