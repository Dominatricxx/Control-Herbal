#!/usr/bin/env python3
"""Auditoría automática de privacidad, rastreo y SDKs de terceros.

Hace cumplir en CI lo que prometen los documentos de /legal:
  1. Sin SDKs de analítica, publicidad, atribución, reporte de fallos ni mensajería de terceros.
  2. Solo permisos de Android de la lista permitida; ID de publicidad eliminado; analítica desactivada.
  3. Sin WebView, cookies, almacenamiento web ni APIs de identificadores de dispositivo o ubicación.
  4. Solo se contactan dominios permitidos desde el código (ver ALLOWED_HOSTS).
  5. Documentos legales presentes, con la misma versión que AppConstants.LEGAL_VERSION.
  6. Copias de seguridad desactivadas y sin tráfico en claro.

Uso:
  python scripts/audit_privacy.py            # falla (código 1) ante cualquier incumplimiento
  python scripts/audit_privacy.py --strict   # además falla si quedan marcadores {{...}} en /legal (publicación)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRICT = "--strict" in sys.argv
errors, warnings = [], []

def err(msg): errors.append(msg)
def warn(msg): warnings.append(msg)

def read(p):
    return Path(p).read_text(encoding="utf-8", errors="ignore")

def files(patterns, skip=("node_modules", "/build/", "/.git/", "/.gradle/", "/androidTest/", "/src/test/")):
    out = []
    for pat in patterns:
        for f in ROOT.glob(pat):
            if f.is_file() and not any(s in str(f) for s in skip):
                out.append(f)
    return sorted(set(out))

# ------------------------------------------------------------------ 1. SDKs prohibidos
FORBIDDEN_DEPS = {
    "firebase-analytics": "analítica de Google", "play-services-measurement": "analítica de Google",
    "crashlytics": "reporte de fallos", "firebase-perf": "monitoreo de rendimiento",
    "play-services-ads": "publicidad", "admob": "publicidad", "facebook": "SDK de Meta",
    "appsflyer": "atribución", "adjust": "atribución", "branch.io": "atribución", "mixpanel": "analítica",
    "amplitude": "analítica", "segment": "analítica", "sentry": "reporte de fallos", "bugsnag": "reporte de fallos",
    "onesignal": "notificaciones push de terceros", "firebase-messaging": "mensajería (token por dispositivo)",
    "play-services-location": "ubicación", "play-services-maps": "mapas/ubicación", "play-services-auth": "cuentas de Google",
    "com.google.android.gms:play-services-ads-identifier": "identificador de publicidad",
    "firebase-dynamic-links": "enlaces con seguimiento", "firebase-inappmessaging": "mensajes in-app con seguimiento",
    "firebase-config": "Remote Config (envía ID de instalación; revisar antes de añadir)",
    "datadog": "analítica", "posthog": "analítica", "matomo": "analítica", "clarity": "grabación de sesiones",
}
for f in files(["**/build.gradle.kts", "**/*.kts", "gradle/libs.versions.toml", "ml/requirements.txt", "firebase/package.json"]):
    text = read(f).lower()
    for dep, why in FORBIDDEN_DEPS.items():
        if re.search(r'(?<![\w-])' + re.escape(dep.lower()), text):
            err(f"SDK no permitido '{dep}' ({why}) en {f.relative_to(ROOT)}")

# ------------------------------------------------------------------ 2. Permisos y manifest
ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.POST_NOTIFICATIONS", "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.SCHEDULE_EXACT_ALARM",
}
REQUIRED_META = [
    ("firebase_analytics_collection_deactivated", "true"),
    ("google_analytics_adid_collection_enabled", "false"),
    ("google_analytics_ssaid_collection_enabled", "false"),
]
for mf in files(["app/src/main/AndroidManifest.xml", "wear/src/main/AndroidManifest.xml"]):
    text = read(mf)
    rel = mf.relative_to(ROOT)
    for m in re.finditer(r'<uses-permission[^>]*android:name="([^"]+)"([^>]*)/?>', text):
        name, rest = m.group(1), m.group(2)
        if name == "com.google.android.gms.permission.AD_ID":
            if 'tools:node="remove"' not in m.group(0):
                err(f"{rel}: AD_ID debe declararse con tools:node=\"remove\"")
        elif name not in ALLOWED_PERMISSIONS:
            err(f"{rel}: permiso no permitido {name} (añádelo a ALLOWED_PERMISSIONS solo si es imprescindible y actualiza el Aviso de Privacidad)")
    for key, val in REQUIRED_META:
        if not re.search(rf'android:name="{key}"\s+android:value="{val}"', text):
            err(f"{rel}: falta <meta-data {key}={val}>")
    if 'android:allowBackup="false"' not in text:
        err(f"{rel}: android:allowBackup debe ser false")
    if "usesCleartextTraffic=\"true\"" in text:
        err(f"{rel}: tráfico en claro habilitado")

# ------------------------------------------------------------------ 3. Patrones de rastreo en el código
CODE_PATTERNS = {
    r"\bWebView\b": "WebView (cookies/rastreo web)", r"\bCookieManager\b": "CookieManager",
    r"document\.cookie": "cookies web", r"\blocalStorage\b|\bsessionStorage\b": "almacenamiento web",
    r"AdvertisingIdClient": "ID de publicidad", r"Settings\.Secure\.ANDROID_ID|ANDROID_ID": "ANDROID_ID",
    r"TelephonyManager|getDeviceId|getImei|getSubscriberId": "identificadores de teléfono",
    r"LocationManager|FusedLocationProvider|requestLocationUpdates": "ubicación",
    r"READ_CONTACTS|ContactsContract": "contactos", r"MediaRecorder|AudioRecord": "micrófono",
    r"InstallReferrer": "atribución de instalación", r"FirebaseAnalytics|FirebaseCrashlytics": "analítica/fallos",
}
for f in files(["app/src/**/*.kt", "wear/src/**/*.kt", "desktop/src/**/*.kt", "common/src/**/*.kt"]):
    text = read(f)
    for pat, why in CODE_PATTERNS.items():
        if re.search(pat, text):
            err(f"{f.relative_to(ROOT)}: uso de {why}")

# ------------------------------------------------------------------ 4. Dominios contactados desde el código
ALLOWED_HOSTS = (
    "firebaseio.com", "googleapis.com", "firebase.google.com", "google.com", "gstatic.com",
    "schemas.android.com", "apache.org", "w3.org", "example.com", "example.invalid",
    "securetoken.googleapis.com", "identitytoolkit.googleapis.com", "pki.goog", "github.com",
    "espressif.github.io", "developer.android.com", "docs.gradle.org", "kotlinlang.org",
    "firebase-settings.crashlytics.com",  # solo aparece si lo trae un SDK; el patrón de dependencias lo prohíbe
)
URL = re.compile(r"https?://([A-Za-z0-9._\-]+)")
for f in files(["app/src/**/*.kt", "wear/src/**/*.kt", "desktop/src/**/*.kt", "ml/*.py", "firmware/*.ino", "firmware/*.h",
                "app/src/main/res/xml/*.xml"]):
    text = read(f)
    for m in URL.finditer(text):
        host = m.group(1).lower().rstrip(".")
        if not any(host == h or host.endswith("." + h) for h in ALLOWED_HOSTS):
            err(f"{f.relative_to(ROOT)}: dominio no permitido '{host}' (si es legítimo, añádelo a ALLOWED_HOSTS y documéntalo en docs/AUDITORIA_PRIVACIDAD_TERCEROS.md)")
        if m.group(0).startswith("http://") and "localhost" not in host and "schemas.android" not in host \
                and "w3.org" not in host and "apache.org" not in host:
            err(f"{f.relative_to(ROOT)}: URL sin cifrar {m.group(0)}")

# ------------------------------------------------------------------ 5. Documentos legales
REQUIRED_DOCS = ["AVISO_DE_PRIVACIDAD.md", "TERMINOS_DE_SERVICIO.md", "POLITICA_DE_COOKIES.md", "CONSENTIMIENTOS.md"]
consts = read(ROOT / "app/src/main/java/com/example/controlherbal/common/AppConstants.kt")
mv = re.search(r'LEGAL_VERSION\s*=\s*"([^"]+)"', consts)
app_version = mv.group(1) if mv else None
if not app_version:
    err("No se encontró LEGAL_VERSION en AppConstants.kt")
pending = []
for name in REQUIRED_DOCS:
    p = ROOT / "legal" / name
    if not p.exists():
        err(f"Falta el documento legal legal/{name}")
        continue
    text = read(p)
    vm = re.search(r"Versi[oó]n\s+([0-9][0-9A-Za-z.\-]*)", text)
    if not vm or vm.group(1) != app_version:
        err(f"legal/{name}: versión '{vm.group(1) if vm else None}' != LEGAL_VERSION '{app_version}'")
    pending += [f"{name}: {x}" for x in sorted(set(re.findall(r"\{\{[A-Z_]+\}\}", text)))]
if pending:
    (err if STRICT else warn)(f"{len(pending)} marcador(es) legal(es) sin rellenar (obligatorio antes de publicar): " + "; ".join(pending))

# Los textos legales no deben prometer lo que el código no hace: palabras clave de coherencia
privacy = read(ROOT / "legal/AVISO_DE_PRIVACIDAD.md") if (ROOT / "legal/AVISO_DE_PRIVACIDAD.md").exists() else ""
for must in ("Firebase Authentication", "Firebase AI Logic", "Secretaría Anticorrupción y Buen Gobierno", "ARCO"):
    if must not in privacy:
        err(f"AVISO_DE_PRIVACIDAD.md debería mencionar '{must}'")

# ------------------------------------------------------------------ Resultado
for w in warnings: print("AVISO:", w)
for e in errors: print("ERROR:", e)
print(f"\nPrivacidad/rastreo: {len(errors)} error(es), {len(warnings)} aviso(s)")
sys.exit(1 if errors else 0)
