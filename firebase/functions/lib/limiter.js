"use strict";
// Limitador de intentos (lógica pura). Estado serializable en Realtime Database.
//
//  Por IP:      máximo 5 intentos por ventana de 15 min (cuenten como fallo o no).
//  Por cuenta:  retroceso exponencial desde el 3.er fallo consecutivo (5 s, 10 s, 20 s... hasta 5 min)
//               y bloqueo al 5.º fallo: 15 min, que se duplica en cada bloqueo sucesivo (tope 24 h).

const IP = Object.freeze({ max: 5, windowMs: 15 * 60 * 1000 });
const ACCT = Object.freeze({
  backoffAfter: 3, backoffBaseMs: 5_000, backoffMaxMs: 5 * 60 * 1000,
  lockAfter: 5, lockBaseMs: 15 * 60 * 1000, lockMaxMs: 24 * 60 * 60 * 1000,
});

/** Cuenta un intento. @returns {{allowed:boolean, retryAfterMs:number, next:object}} */
function ipAttempt(state, now, cfg = IP) {
  let s = state && typeof state === "object" ? { ...state } : { count: 0, windowStart: now };
  if (now - s.windowStart >= cfg.windowMs) s = { count: 0, windowStart: now };
  if (s.count >= cfg.max) {
    return { allowed: false, retryAfterMs: s.windowStart + cfg.windowMs - now, next: s };
  }
  return { allowed: true, retryAfterMs: 0, next: { count: s.count + 1, windowStart: s.windowStart } };
}

/** ¿Puede esta cuenta intentar ahora? No modifica el estado. */
function acctCheck(state, now) {
  const until = Math.max(state?.lockUntil || 0, state?.retryAt || 0);
  return until > now ? { allowed: false, retryAfterMs: until - now } : { allowed: true, retryAfterMs: 0 };
}

function acctFailure(state, now, cfg = ACCT) {
  const s = { fails: 0, lockouts: 0, lockUntil: 0, retryAt: 0, ...(state || {}) };
  s.fails += 1;
  s.lastFail = now;
  if (s.fails >= cfg.lockAfter) {
    s.lockouts += 1;
    s.lockUntil = now + Math.min(cfg.lockMaxMs, cfg.lockBaseMs * 2 ** (s.lockouts - 1));
    s.fails = 0;
    s.retryAt = 0;
  } else if (s.fails >= cfg.backoffAfter) {
    s.retryAt = now + Math.min(cfg.backoffMaxMs, cfg.backoffBaseMs * 2 ** (s.fails - cfg.backoffAfter));
  }
  return s;
}

/** Un éxito reinicia fallos y bloqueos. */
function acctSuccess() {
  return null; // null borra el nodo en RTDB
}

module.exports = { IP, ACCT, ipAttempt, acctCheck, acctFailure, acctSuccess };
