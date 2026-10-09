"use strict";
// Reto de prueba de trabajo (proof-of-work) verificado en el servidor: el "CAPTCHA" autoalojado de este proyecto.
//
// Qué es y qué NO es: encarece cada intento automatizado (el cliente debe invertir CPU antes de que el servidor
// mire la contraseña o el código) y no depende de terceros. No distingue personas de bots; para eso Google ofrece
// reCAPTCHA Enterprise (ver harden-auth.mjs). Se exige a partir del 3.er fallo de una cuenta y la dificultad
// sube con cada fallo (18, 20, 22 bits ~ 0,3 s, 1 s, 4 s en un móvil actual).
//
// Propiedades: firmado con HMAC (el cliente no puede fabricarlo ni bajar la dificultad), atado a la cuenta,
// caduca a los 2 min y es de un solo uso (anti-repetición, en index.js).
// Resolución: encontrar un `nonce` tal que SHA-256(`${salt}.${nonce}`) empiece por >= `bits` bits a cero.

const crypto = require("node:crypto");

const REQUIRED_AFTER = 3;
const BITS_BASE = 18;
const BITS_STEP = 2;
const BITS_MAX = 22;
const TTL_MS = 2 * 60 * 1000;

function bitsForFails(fails) {
  if (!Number.isFinite(fails) || fails < REQUIRED_AFTER) return 0;
  return Math.min(BITS_MAX, BITS_BASE + BITS_STEP * (fails - REQUIRED_AFTER));
}

/** Clave propia derivada del secreto existente (separación de dominios): no hay otro secreto que configurar. */
function deriveKey(secret) {
  if (!secret || secret.length < 16) throw new Error("secreto ausente o demasiado corto");
  return crypto.createHmac("sha256", secret).update("pow-v1", "utf8").digest();
}

function sign(key, salt, exp, bits, bind) {
  return crypto.createHmac("sha256", key).update(`${salt}.${exp}.${bits}.${bind}`, "utf8").digest("hex");
}

function issue(key, bind, bits, now) {
  const salt = crypto.randomBytes(16).toString("hex");
  const exp = now + TTL_MS;
  return { salt, exp, bits, sig: sign(key, salt, exp, bits, bind) };
}

function digest(salt, nonce) {
  return crypto.createHash("sha256").update(`${salt}.${nonce}`, "utf8").digest();
}

function leadingZeroBits(buf) {
  let n = 0;
  for (const byte of buf) {
    if (byte === 0) { n += 8; continue; }
    n += Math.clz32(byte) - 24;
    break;
  }
  return n;
}

/** Solución de referencia (la usan los tests; la app Android tiene la suya). */
function solve(challenge, maxIter = 50_000_000) {
  for (let nonce = 0; nonce < maxIter; nonce++) {
    if (leadingZeroBits(digest(challenge.salt, nonce)) >= challenge.bits) return String(nonce);
  }
  throw new Error("sin solución en el límite");
}

/** @returns {{ok:boolean, reason?:string, salt?:string}} */
function verify(key, bind, challenge, nonce, minBits, now) {
  if (!challenge || typeof challenge !== "object") return { ok: false, reason: "missing" };
  const { salt, exp, bits, sig } = challenge;
  if (typeof salt !== "string" || !/^[0-9a-f]{32}$/.test(salt)) return { ok: false, reason: "shape" };
  if (!Number.isInteger(exp) || !Number.isInteger(bits) || typeof sig !== "string") return { ok: false, reason: "shape" };
  if (typeof nonce !== "string" || !/^\d{1,12}$/.test(nonce)) return { ok: false, reason: "nonce" };
  if (bits < minBits) return { ok: false, reason: "downgraded" };
  const expected = Buffer.from(sign(key, salt, exp, bits, bind), "hex");
  const given = /^[0-9a-f]{64}$/.test(sig) ? Buffer.from(sig, "hex") : Buffer.alloc(0);
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return { ok: false, reason: "signature" };
  if (exp <= now) return { ok: false, reason: "expired" };
  if (leadingZeroBits(digest(salt, nonce)) < bits) return { ok: false, reason: "work" };
  return { ok: true, salt };
}

module.exports = { REQUIRED_AFTER, BITS_MAX, TTL_MS, bitsForFails, deriveKey, issue, solve, verify, leadingZeroBits, digest };
