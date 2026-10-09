#!/usr/bin/env node
// Endurece la configuración de Firebase Auth / Identity Platform de TODO el proyecto.
// Es la única capa que Google aplica en SU servidor (los clientes no pueden saltársela).
//
//   node scripts/harden-auth.mjs --project <ID>                       # simulación: muestra qué cambiaría
//   node scripts/harden-auth.mjs --project <ID> --apply               # aplica
//   opciones: --recaptcha-enforce   reCAPTCHA Enterprise en modo ENFORCE (por defecto AUDIT; ver nota)
//             --force-upgrade       obliga a cambiar contraseñas antiguas que no cumplan la política
//
// Requisitos: proyecto con Identity Platform (actualización gratuita hasta 50 000 MAU; hace falta para
// TOTP), credenciales de administrador (GOOGLE_APPLICATION_CREDENTIALS o `gcloud auth application-default login`).
//
// Qué configura:
//   1. MFA TOTP habilitado.
//   2. Política de contraseñas en el servidor: 12-128, mayúscula, minúscula, número y símbolo (ENFORCE).
//   3. Protección contra enumeración de correos.
//   4. reCAPTCHA Enterprise para correo/contraseña (AUDIT o ENFORCE).
//   5. Registro de peticiones de Auth en Cloud Logging (para auditar intentos fallidos).
//   6. (Opcional, --appcheck-enforce) App Check OBLIGATORIO: Google rechaza las peticiones que no vengan de la app
//      genuina. Es lo más cercano a "limitar el endpoint de Google": un script externo ya no puede probar
//      contraseñas ni leer/escribir la base. Ver advertencias sobre clientes que dejarían de funcionar.
//
// NOTA App Check ENFORCED (--appcheck-enforce identitytoolkit,database --yes):
//   identitytoolkit  protege el login/reset de Google, pero DEJAN DE FUNCIONAR los clientes que inician sesión por REST
//                    sin atestación (Desktop, ml/train_herbal_model.py) y la verificación de contraseña que hace la
//                    función redeemBackupCode (llamada servidor a servidor): se pierde la recuperación por códigos.
//   database         protege lectura/escritura de la base, pero DEJAN DE FUNCIONAR el firmware ESP32, Desktop y ML.
//                    La app Android sí funciona (Play Integrity).
//   Antes de imponerlo, mide: Consola > App Check > APIs muestra cuántas peticiones están verificadas. Impón solo
//   cuando las no verificadas sean 0 (o solo ataques).
//
// NOTA reCAPTCHA ENFORCE: bloquea bots en el login nativo, pero la Cloud Function redeemBackupCode
// verifica la contraseña por REST y esa llamada no lleva token de reCAPTCHA, así que quedaría rechazada
// (la función lo detecta y responde "recuperación no disponible"). Elige: ENFORCE (sin recuperación por
// códigos hasta integrar el token) o AUDIT (por defecto) + límites propios + bloqueo de Google.
//
// NOTA forceUpgradeOnSignin: las cuentas del ESP32 y del reloj guardan su contraseña en el dispositivo;
// si no cumplen la política, forzar la mejora las dejaría sin acceso. Cámbialas antes (provision-user.mjs --generate).

import { parseArgs } from "node:util";

const { values: a } = parseArgs({
  options: {
    project: { type: "string" },
    apply: { type: "boolean", default: false },
    "recaptcha-enforce": { type: "boolean", default: false },
    "force-upgrade": { type: "boolean", default: false },
    "appcheck-enforce": { type: "string" },
    yes: { type: "boolean", default: false },
  },
});
if (!a.project) { console.error("Falta --project <ID>"); process.exit(2); }

const APPCHECK_SERVICES = {
  identitytoolkit: "identitytoolkit.googleapis.com",
  database: "firebasedatabase.googleapis.com",
};
const appCheckList = (a["appcheck-enforce"] ?? "").split(",").map((x) => x.trim()).filter(Boolean);
for (const svc of appCheckList) {
  if (!(svc in APPCHECK_SERVICES)) { console.error(`--appcheck-enforce: servicio desconocido "${svc}" (usa: ${Object.keys(APPCHECK_SERVICES).join(", ")})`); process.exit(2); }
}
if (appCheckList.length && a.apply && !a.yes) {
  console.error("App Check ENFORCED puede dejar sin acceso a Desktop/ML/ESP32 (lee la nota de la cabecera). Repite con --yes para confirmarlo.");
  process.exit(2);
}

const sections = {
  "MFA TOTP": {
    multiFactorConfig: {
      state: "ENABLED",
      providerConfigs: [{ state: "ENABLED", totpProviderConfig: { adjacentIntervals: 1 } }],
    },
  },
  "Política de contraseñas": {
    passwordPolicyConfig: {
      enforcementState: "ENFORCE",
      forceUpgradeOnSignin: a["force-upgrade"],
      constraints: {
        minPasswordLength: 12,
        maxPasswordLength: 128,
        containsLowercaseCharacter: true,
        containsUppercaseCharacter: true,
        containsNumericCharacter: true,
        containsNonAlphanumericCharacter: true,
      },
    },
  },
  "Anti-enumeración de correos": { emailPrivacyConfig: { enableImprovedEmailPrivacy: true } },
  "reCAPTCHA Enterprise": {
    recaptchaConfig: {
      emailPasswordEnforcementState: a["recaptcha-enforce"] ? "ENFORCE" : "AUDIT",
      useAccountDefender: true,
    },
  },
};

console.log(`Proyecto: ${a.project}   Modo: ${a.apply ? "APLICAR" : "SIMULACIÓN (usa --apply)"}\n`);
for (const [name, cfg] of Object.entries(sections)) console.log(`# ${name}\n${JSON.stringify(cfg, null, 2)}\n`);
console.log("# Registro de peticiones de Auth\n{ monitoring: { requestLogging: { enabled: true } } }\n");
if (appCheckList.length) console.log(`# App Check ENFORCED en: ${appCheckList.join(", ")}  (lee las advertencias de la cabecera del script)\n`);
if (!a.apply) process.exit(0);

// Solo se necesita firebase-admin al aplicar: la simulación funciona sin `npm install`.
const { initializeApp, applicationDefault } = await import("firebase-admin/app");
const { getAuth } = await import("firebase-admin/auth");
const app = initializeApp({ credential: applicationDefault(), projectId: a.project });
const mgr = getAuth(app).projectConfigManager();
let failed = 0;

for (const [name, cfg] of Object.entries(sections)) {
  try { await mgr.updateProjectConfig(cfg); console.log(`OK    ${name}`); }
  catch (e) { failed++; console.error(`ERROR ${name}: ${e.code ?? ""} ${e.message}`); }
}

try {
  const { access_token: token } = await applicationDefault().getAccessToken();
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/admin/v2/projects/${encodeURIComponent(a.project)}/config?updateMask=monitoring.requestLogging`,
    {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", "X-Goog-User-Project": a.project },
      body: JSON.stringify({ monitoring: { requestLogging: { enabled: true } } }),
    },
  );
  if (!res.ok) throw new Error(`HTTP ${res.status} ${await res.text()}`);
  console.log("OK    Registro de peticiones de Auth");
} catch (e) { failed++; console.error(`ERROR Registro de peticiones: ${e.message}`); }

for (const svc of appCheckList) {
  try {
    const { access_token: token } = await applicationDefault().getAccessToken();
    const res = await fetch(
      `https://firebaseappcheck.googleapis.com/v1/projects/${encodeURIComponent(a.project)}/services/${APPCHECK_SERVICES[svc]}?updateMask=enforcementMode`,
      {
        method: "PATCH",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", "X-Goog-User-Project": a.project },
        body: JSON.stringify({ enforcementMode: "ENFORCED" }),
      },
    );
    if (!res.ok) throw new Error(`HTTP ${res.status} ${await res.text()}`);
    console.log(`OK    App Check ENFORCED en ${svc}`);
  } catch (e) { failed++; console.error(`ERROR App Check (${svc}): ${e.message}`); }
}

console.log(failed ? `\n${failed} sección(es) fallaron: revisa permisos / Identity Platform y compruébalo en la consola.` : "\nListo. Verifica el resultado en Consola → Authentication → Settings.");
process.exit(failed ? 1 : 0);
