"use strict";
/**
 * Control Herbal · códigos de respaldo de 2FA (Cloud Functions 2.ª gen, Node 22)
 *
 *  generateBackupCodes   (requiere sesión con 2FA TOTP)  -> genera 10 códigos de un solo uso, mostrados UNA vez.
 *  backupCodesStatus     (requiere sesión con 2FA TOTP)  -> cuántos códigos quedan.
 *  redeemBackupCode      (sin sesión: el usuario perdió su autenticador)
 *        verifica correo + contraseña + código, consume el código, elimina el segundo factor perdido
 *        y revoca todas las sesiones. Tras esto la cuenta solo entra con contraseña y la app obliga a
 *        enrolar un TOTP nuevo (y las reglas de la base siguen negando al owner sin 2FA).
 *
 * Defensas de redeemBackupCode (todas en el servidor):
 *   - App Check obligatorio.
 *   - Reto de prueba de trabajo (CAPTCHA autoalojado, lib/pow.js) desde el 3.er fallo de la cuenta.
 *   - 5 intentos / 15 min por IP.
 *   - Por cuenta: retroceso exponencial desde el 3.er fallo y bloqueo al 5.º (15 min, se duplica, tope 24 h).
 *   - Respuesta genérica ("Datos incorrectos") sin distinguir contraseña/código/usuario.
 *   - Todos los fallos y bloqueos se registran (Cloud Logging) con hashes, nunca correo ni IP en claro.
 *
 * Secretos / parámetros:
 *   firebase functions:secrets:set BACKUP_CODE_PEPPER      (>= 32 caracteres aleatorios)
 *   HERBAL_API_KEY                                          (Web API key del proyecto; se pide al desplegar)
 */

const crypto = require("node:crypto");
const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineString, defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const bc = require("./lib/backupCodes");
const lim = require("./lib/limiter");
const pow = require("./lib/pow");

admin.initializeApp();
const db = admin.database();

const WEB_API_KEY = defineString("HERBAL_API_KEY");
const PEPPER = defineSecret("BACKUP_CODE_PEPPER");

const BASE = { region: "us-central1", enforceAppCheck: true, maxInstances: 5, timeoutSeconds: 30 };
const GEN_LIMIT = { max: 3, windowMs: 60 * 60 * 1000 }; // 3 generaciones por hora y usuario

const sha = (s) => crypto.createHash("sha256").update(String(s)).digest("hex").slice(0, 32);

function clientIp(req) {
  const xff = req.rawRequest?.headers?.["x-forwarded-for"];
  const first = typeof xff === "string" ? xff.split(",")[0].trim() : "";
  return first || req.rawRequest?.ip || "unknown";
}

/** Transacción atómica. compute(cur) -> { decision, next }; next === undefined aborta sin escribir. */
async function atomic(path, compute) {
  let decision;
  await db.ref(path).transaction((cur) => {
    const r = compute(cur);
    decision = r.decision;
    return r.next;
  });
  return decision;
}

const tooMany = (ms) =>
  new HttpsError("resource-exhausted", "Demasiados intentos. Inténtalo más tarde.", {
    retryAfterSeconds: Math.max(1, Math.ceil(ms / 1000)),
  });

function requireTotpSession(req) {
  if (!req.auth) throw new HttpsError("unauthenticated", "Inicia sesión.");
  if (req.auth.token?.firebase?.sign_in_second_factor !== "totp") {
    throw new HttpsError("permission-denied", "Se requiere una sesión con verificación en dos pasos.");
  }
  return req.auth.uid;
}

/** Comprueba correo+contraseña con Identity Toolkit. Un usuario con MFA recibe 200 + mfaPendingCredential. */
async function verifyFirstFactor(email, password) {
  let res;
  try {
    res = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${encodeURIComponent(WEB_API_KEY.value())}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, returnSecureToken: true }),
        signal: AbortSignal.timeout(8000),
      },
    );
  } catch {
    throw new HttpsError("unavailable", "Servicio no disponible. Inténtalo de nuevo.");
  }
  if (res.ok) return true;
  let msg = "";
  try { msg = (await res.json())?.error?.message || ""; } catch { /* sin cuerpo */ }
  if (msg.startsWith("TOO_MANY_ATTEMPTS")) throw new HttpsError("resource-exhausted", "Demasiados intentos. Espera unos minutos.");
  if (msg.includes("RECAPTCHA")) {
    logger.error("reCAPTCHA en modo ENFORCE impide verificar la contraseña por REST; usa AUDIT o integra el token.");
    throw new HttpsError("failed-precondition", "Recuperación no disponible por configuración del servidor.");
  }
  return false;
}

exports.generateBackupCodes = onCall({ ...BASE, secrets: [PEPPER] }, async (req) => {
  const uid = requireTotpSession(req);
  const now = Date.now();

  const gate = await atomic(`_server/rl/gen/${uid}`, (cur) => {
    const d = lim.ipAttempt(cur, now, GEN_LIMIT);
    return { decision: d, next: d.allowed ? d.next : undefined };
  });
  if (!gate.allowed) throw tooMany(gate.retryAfterMs);

  const codes = bc.generateCodes();
  const pepper = PEPPER.value();
  const hashes = {};
  for (const c of codes) hashes[bc.hashCode(c, pepper)] = true;
  await db.ref(`_server/backup/${uid}`).set({ createdAt: now, hashes }); // sustituye el juego anterior

  logger.info("backup_codes_generated", { uidHash: sha(uid) });
  return { codes: codes.map(bc.pretty), count: codes.length };
});

exports.backupCodesStatus = onCall({ ...BASE }, async (req) => {
  const uid = requireTotpSession(req);
  const snap = await db.ref(`_server/backup/${uid}`).get();
  const v = snap.val();
  return { remaining: v?.hashes ? Object.keys(v.hashes).length : 0, createdAt: v?.createdAt ?? null };
});

exports.redeemBackupCode = onCall({ ...BASE, secrets: [PEPPER] }, async (req) => {
  const now = Date.now();
  const { email, password, code } = req.data || {};
  if (
    typeof email !== "string" || email.length > 254 || !email.includes("@") ||
    typeof password !== "string" || password.length === 0 || password.length > 128
  ) {
    throw new HttpsError("invalid-argument", "Datos no válidos.");
  }
  const mail = email.trim().toLowerCase();
  const ip = clientIp(req);
  const ipKey = `_server/rl/ip/${sha(ip)}`;
  const acctKey = `_server/rl/acct/${sha(mail)}`;
  const logCtx = { acctHash: sha(mail), ipHash: sha(ip) };

  // 1) Bloqueo / retroceso por cuenta (se aplica también a correos inexistentes: no revela si existen).
  const acctState = (await db.ref(acctKey).get()).val();
  const acctGate = lim.acctCheck(acctState, now);
  if (!acctGate.allowed) {
    logger.warn("backup_redeem_blocked", { ...logCtx, reason: "account", retryAfterMs: acctGate.retryAfterMs });
    throw tooMany(acctGate.retryAfterMs);
  }

  // 2) "CAPTCHA" a partir del 3.er fallo: reto de prueba de trabajo firmado, atado a la cuenta, caducable y de
  //    un solo uso. Si falta o no vale, se responde con un reto nuevo (la app lo resuelve y reintenta); esa
  //    respuesta no consume intentos de IP ni de cuenta, porque aún no se ha mirado ninguna credencial.
  const needBits = pow.bitsForFails(acctState?.fails || 0);
  if (needBits > 0) {
    const powKey = pow.deriveKey(PEPPER.value());
    const v = pow.verify(powKey, logCtx.acctHash, req.data.challenge, String(req.data.nonce ?? ""), needBits, now);
    let fresh = false;
    if (v.ok) {
      // Anti-repetición: cada reto resuelto vale una sola vez.
      fresh = await atomic(`_server/pow/${v.salt}`, (cur) => (cur ? { decision: false, next: undefined } : { decision: true, next: now + pow.TTL_MS }));
    }
    if (!fresh) {
      logger.warn("backup_redeem_challenge", { ...logCtx, bits: needBits, why: v.ok ? "replay" : v.reason });
      throw new HttpsError("failed-precondition", "challenge-required", { challenge: pow.issue(powKey, logCtx.acctHash, needBits, now) });
    }
  }

  // 3) Límite por IP: cuenta cada intento real, acierte o falle.
  const ipGate = await atomic(ipKey, (cur) => {
    const d = lim.ipAttempt(cur, now);
    return { decision: d, next: d.allowed ? d.next : undefined };
  });
  if (!ipGate.allowed) {
    logger.warn("backup_redeem_blocked", { ...logCtx, reason: "ip", retryAfterMs: ipGate.retryAfterMs });
    throw tooMany(ipGate.retryAfterMs);
  }

  const fail = async (why) => {
    await db.ref(acctKey).transaction((cur) => lim.acctFailure(cur, now));
    logger.warn("backup_redeem_failed", { ...logCtx, why });
    throw new HttpsError("permission-denied", "Datos incorrectos.");
  };

  // 4) Primer factor (contraseña) y formato del código.
  const normalized = bc.normalize(code);
  const passwordOk = await verifyFirstFactor(email.trim(), password);
  if (!passwordOk) return fail("password");
  if (!normalized) return fail("format");

  let user;
  try { user = await admin.auth().getUserByEmail(email.trim()); } catch { return fail("user"); }
  const codeHash = bc.hashCode(normalized, PEPPER.value());
  const stored = (await db.ref(`_server/backup/${user.uid}/hashes`).get()).val();
  if (!bc.matches(stored, codeHash)) return fail("code");

  // 5) Consumo atómico: si dos peticiones usan el mismo código a la vez, solo una gana.
  let consumed = false;
  await db.ref(`_server/backup/${user.uid}`).transaction((cur) => {
    if (cur?.hashes && cur.hashes[codeHash] === true) { consumed = true; return null; } // borra TODO el juego
    consumed = false;
    return undefined;
  });
  if (!consumed) return fail("race");

  // 6) Recuperación: quita el factor perdido y cierra todas las sesiones existentes.
  await admin.auth().updateUser(user.uid, { multiFactor: { enrolledFactors: null } });
  await admin.auth().revokeRefreshTokens(user.uid);
  await db.ref(acctKey).remove();

  logger.warn("mfa_reset_via_backup_code", { uidHash: sha(user.uid), ipHash: sha(ip) });
  return { ok: true };
});
