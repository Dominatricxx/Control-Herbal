// Política de contraseñas de Control Herbal (versión de referencia para el servidor).
// La app Android (common/PasswordPolicy.kt) implementa EXACTAMENTE las mismas reglas.
//
// Reglas duras (todas obligatorias):
//   LENGTH      >= 12 caracteres            MAX_LENGTH  <= 128
//   UPPER/LOWER/DIGIT/SYMBOL  al menos uno de cada (ASCII; SYMBOL = el conjunto que acepta Firebase Auth)
//   PERSONAL    no contiene el correo/nombre (fragmentos >= 4 caracteres)
//   REPEAT      sin 4+ caracteres idénticos seguidos
//   SEQUENCE    sin secuencias de 5+ (abcde, 12345, qwert, asdfg, zxcvb, y al revés)
//   COMMON      su "núcleo" normalizado no está en common_passwords.txt (p. ej. P@ssw0rd!2024)
//   WEAK        zxcvbn puntúa < 3
//   PWNED       aparece en HaveIBeenPwned (comprobación aparte, k-anonimato)

import { createHash, randomInt as cryptoRandomInt } from "node:crypto";
import { readFileSync } from "node:fs";

export const POLICY = Object.freeze({ minLength: 12, maxLength: 128, minScore: 3 });

// Conjunto de caracteres especiales que Firebase Auth reconoce como "no alfanuméricos".
export const SYMBOLS = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`+=-";

const ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "abcdefghijklmnopqrstuvwxyz"];

export function loadCommonCores(path) {
  const text = readFileSync(path, "utf8");
  return new Set(text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith("#")));
}

const codePoints = (s) => [...s];

/**
 * Núcleos normalizados: minúsculas, sin acentos, leet->letras, y se descarta lo que no sea a-z.
 * Se generan variantes (recortando o no los bordes no alfabéticos; "1" como i o l) porque un
 * "!" o un "2024" al final son decoración, pero un "@" al principio puede ser una "a".
 */
export function cores(password) {
  const base = password
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
  const leet = { "@": "a", "0": "o", "3": "e", "4": "a", "5": "s", "7": "t", "$": "s", "!": "i", "+": "t" };
  const out = new Set();
  for (const trimStart of [false, true]) {
    for (const trimEnd of [false, true]) {
      for (const one of ["i", "l"]) {
        let s = base;
        if (trimStart) s = s.replace(/^[^a-z]+/, "");
        if (trimEnd) s = s.replace(/[^a-z]+$/, "");
        let core = "";
        for (const ch of s) {
          const m = ch === "1" ? one : (leet[ch] ?? ch);
          if (m >= "a" && m <= "z") core += m;
        }
        if (core) out.add(core);
      }
    }
  }
  return [...out];
}

function hasSequence(pw, len = 5) {
  const lower = pw.toLowerCase();
  const runs = [];
  for (const row of ROWS) runs.push(row, [...row].reverse().join(""));
  for (const row of runs) {
    for (let i = 0; i + len <= row.length; i++) {
      if (lower.includes(row.slice(i, i + len))) return true;
    }
  }
  return false;
}

function hasRepeat(pw, n = 4) {
  const cps = codePoints(pw);
  let run = 1;
  for (let i = 1; i < cps.length; i++) {
    run = cps[i] === cps[i - 1] ? run + 1 : 1;
    if (run >= n) return true;
  }
  return false;
}

function containsPersonal(pw, userInputs) {
  const lower = pw.toLowerCase();
  for (const raw of userInputs) {
    for (const tok of String(raw).toLowerCase().split(/[^a-z0-9ñáéíóúü]+/i)) {
      if (tok.length >= 4 && lower.includes(tok)) return true;
    }
  }
  return false;
}

/**
 * @param {string} password
 * @param {{userInputs?: string[], commonCores?: Set<string>, zxcvbn?: Function}} opts
 * @returns {{ok:boolean, failures:string[], score:number, level:number}}
 */
export function evaluate(password, { userInputs = [], commonCores = new Set(), zxcvbn = null } = {}) {
  const failures = [];
  const len = codePoints(password).length;
  if (len < POLICY.minLength) failures.push("LENGTH");
  if (len > POLICY.maxLength) failures.push("MAX_LENGTH");
  if (!/[A-Z]/.test(password)) failures.push("UPPER");
  if (!/[a-z]/.test(password)) failures.push("LOWER");
  if (!/[0-9]/.test(password)) failures.push("DIGIT");
  if (![...password].some((c) => SYMBOLS.includes(c))) failures.push("SYMBOL");
  if (containsPersonal(password, userInputs)) failures.push("PERSONAL");
  if (hasRepeat(password)) failures.push("REPEAT");
  if (hasSequence(password)) failures.push("SEQUENCE");
  if (cores(password).some((c) => c.length > 0 && commonCores.has(c))) failures.push("COMMON");

  let score = 0;
  if (zxcvbn) {
    score = zxcvbn(password.slice(0, 256), userInputs).score;
    if (score < POLICY.minScore) failures.push("WEAK");
  }
  const hard = failures.some((f) => f !== "WEAK");
  return { ok: failures.length === 0, failures, score, level: hard ? Math.min(score, 1) : score };
}

/** HaveIBeenPwned con k-anonimato: solo se envían los 5 primeros caracteres del SHA-1. */
export function sha1Upper(s) {
  return createHash("sha1").update(s, "utf8").digest("hex").toUpperCase();
}

export function parsePwnedRange(body, suffix) {
  for (const line of body.split(/\r?\n/)) {
    const [suf, count] = line.trim().split(":");
    if (suf === suffix) return Number.parseInt(count, 10) || 0;
  }
  return 0;
}

/** @returns {Promise<number|null>} apariciones conocidas, o null si no se pudo consultar. */
export async function pwnedCount(password, fetchImpl = fetch) {
  const hash = sha1Upper(password);
  try {
    const res = await fetchImpl(`https://api.pwnedpasswords.com/range/${hash.slice(0, 5)}`, {
      headers: { "Add-Padding": "true", "User-Agent": "control-herbal-password-check" },
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) return null;
    return parsePwnedRange(await res.text(), hash.slice(5));
  } catch {
    return null;
  }
}

/** Genera una contraseña aleatoria que cumple la política (para cuentas de dispositivo/servicio). */
export function generatePassword(length = 24) {
  const pick = (set) => set[cryptoRandomInt(set.length)];
  const U = "ABCDEFGHJKLMNPQRSTUVWXYZ", L = "abcdefghijkmnopqrstuvwxyz", D = "23456789", S = "!@#%&*+=-_?";
  for (let attempt = 0; attempt < 50; attempt++) {
    const chars = [pick(U), pick(L), pick(D), pick(S)];
    const all = U + L + D + S;
    while (chars.length < length) chars.push(pick(all));
    for (let i = chars.length - 1; i > 0; i--) { const j = cryptoRandomInt(i + 1); [chars[i], chars[j]] = [chars[j], chars[i]]; }
    const pw = chars.join("");
    if (!hasSequence(pw) && !hasRepeat(pw)) return pw;
  }
  throw new Error("no se pudo generar una contraseña válida");
}

