"use strict";
// Códigos de respaldo de 2FA. Lógica pura (sin Firebase) para poder probarla sin red.
//  - 10 códigos de 10 caracteres sobre un alfabeto de 32 símbolos sin ambiguos (~50 bits cada uno).
//  - En el servidor solo se guarda HMAC-SHA256(pepper, código): un volcado de la base no revela los códigos.
//  - De un solo uso. La comparación recorre todos los hashes en tiempo constante.

const crypto = require("node:crypto");

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin I, O, 0, 1
const CODE_LENGTH = 10;
const CODE_COUNT = 10;

function generateCodes(count = CODE_COUNT) {
  const codes = [];
  for (let i = 0; i < count; i++) {
    let c = "";
    for (let j = 0; j < CODE_LENGTH; j++) c += ALPHABET[crypto.randomInt(ALPHABET.length)];
    codes.push(c);
  }
  return codes;
}

/** "abcde-fgh23" -> "ABCDEFGH23". Devuelve null si el formato no es válido. */
function normalize(input) {
  if (typeof input !== "string" || input.length > 32) return null;
  const c = input.replace(/[\s-]/g, "").toUpperCase();
  if (c.length !== CODE_LENGTH) return null;
  for (const ch of c) if (!ALPHABET.includes(ch)) return null;
  return c;
}

function pretty(code) {
  return `${code.slice(0, 5)}-${code.slice(5)}`;
}

function hashCode(code, pepper) {
  if (!pepper || pepper.length < 16) throw new Error("pepper ausente o demasiado corto");
  return crypto.createHmac("sha256", pepper).update(code, "utf8").digest("hex");
}

/** @param {Record<string,true>} stored mapa hash->true. @returns {boolean} */
function matches(stored, candidateHash) {
  const cand = Buffer.from(candidateHash, "hex");
  let found = false;
  for (const h of Object.keys(stored || {})) {
    const buf = Buffer.from(h, "hex");
    if (buf.length === cand.length && crypto.timingSafeEqual(buf, cand)) found = true;
  }
  return found;
}

module.exports = { ALPHABET, CODE_LENGTH, CODE_COUNT, generateCodes, normalize, pretty, hashCode, matches };
