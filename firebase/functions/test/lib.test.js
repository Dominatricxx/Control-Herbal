"use strict";
const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const bc = require("../lib/backupCodes");
const lim = require("../lib/limiter");

const PEPPER = "x".repeat(32);

describe("backupCodes", () => {
  it("genera 10 códigos únicos de 10 caracteres del alfabeto sin ambiguos", () => {
    const codes = bc.generateCodes();
    assert.equal(codes.length, 10);
    assert.equal(new Set(codes).size, 10);
    for (const c of codes) {
      assert.equal(c.length, 10);
      assert.match(c, /^[A-HJ-NP-Z2-9]{10}$/);
    }
  });
  it("normaliza espacios, guiones y minúsculas; rechaza formatos inválidos", () => {
    assert.equal(bc.normalize("abcde-fgh23"), "ABCDEFGH23");
    assert.equal(bc.normalize(" ABCDE FGH23 "), "ABCDEFGH23");
    for (const bad of ["", "corto", "ABCDEFGH2O", "ABCDEFGH231", null, 123, "A".repeat(100)]) assert.equal(bc.normalize(bad), null, String(bad));
  });
  it("el hash es determinista, depende del pepper y no revela el código", () => {
    const h = bc.hashCode("ABCDEFGH23", PEPPER);
    assert.equal(h, bc.hashCode("ABCDEFGH23", PEPPER));
    assert.notEqual(h, bc.hashCode("ABCDEFGH23", "y".repeat(32)));
    assert.ok(!h.includes("ABCDEFGH23"));
    assert.throws(() => bc.hashCode("ABCDEFGH23", "corto"));
  });
  it("matches() acepta solo hashes almacenados", () => {
    const stored = { [bc.hashCode("AAAAAAAAAA", PEPPER)]: true, [bc.hashCode("BBBBBBBBBB", PEPPER)]: true };
    assert.equal(bc.matches(stored, bc.hashCode("BBBBBBBBBB", PEPPER)), true);
    assert.equal(bc.matches(stored, bc.hashCode("CCCCCCCCCC", PEPPER)), false);
    assert.equal(bc.matches(null, bc.hashCode("CCCCCCCCCC", PEPPER)), false);
  });
  it("pretty() formatea en dos bloques", () => assert.equal(bc.pretty("ABCDEFGH23"), "ABCDE-FGH23"));
});

describe("limiter por IP: 5 intentos / 15 min", () => {
  it("permite 5 y bloquea el 6.º, informando cuánto esperar", () => {
    let s = null; const t0 = 1_000_000;
    for (let i = 0; i < 5; i++) { const r = lim.ipAttempt(s, t0 + i); assert.equal(r.allowed, true); s = r.next; }
    const r = lim.ipAttempt(s, t0 + 10);
    assert.equal(r.allowed, false);
    assert.ok(r.retryAfterMs > 14 * 60 * 1000 && r.retryAfterMs <= 15 * 60 * 1000);
  });
  it("se reabre al terminar la ventana", () => {
    let s = null; const t0 = 1_000_000;
    for (let i = 0; i < 5; i++) s = lim.ipAttempt(s, t0).next;
    assert.equal(lim.ipAttempt(s, t0 + 15 * 60 * 1000).allowed, true);
  });
  it("un estado corrupto no deja pasar de más", () => {
    assert.equal(lim.ipAttempt("basura", 5).allowed, true);
  });
});

describe("limiter por cuenta: retroceso exponencial y bloqueo", () => {
  it("sin retroceso hasta el 2.º fallo; desde el 3.º 5 s, 10 s...", () => {
    let s = null; const t = 10_000_000;
    s = lim.acctFailure(s, t); assert.equal(lim.acctCheck(s, t).allowed, true);
    s = lim.acctFailure(s, t); assert.equal(lim.acctCheck(s, t).allowed, true);
    s = lim.acctFailure(s, t); assert.equal(lim.acctCheck(s, t).retryAfterMs, 5_000);
    s = lim.acctFailure(s, t + 5_000); assert.equal(lim.acctCheck(s, t + 5_000).retryAfterMs, 10_000);
  });
  it("bloquea 15 min al 5.º fallo y duplica en cada bloqueo (tope 24 h)", () => {
    let s = null; let t = 0; const fail = () => { s = lim.acctFailure(s, t); };
    for (let i = 0; i < 5; i++) fail();
    assert.equal(lim.acctCheck(s, t).retryAfterMs, 15 * 60 * 1000);
    t += 15 * 60 * 1000;
    for (let i = 0; i < 5; i++) fail();
    assert.equal(lim.acctCheck(s, t).retryAfterMs, 30 * 60 * 1000);
    for (let k = 0; k < 12; k++) { t += 24 * 3600 * 1000; for (let i = 0; i < 5; i++) fail(); }
    assert.equal(lim.acctCheck(s, t).retryAfterMs, 24 * 3600 * 1000);
  });
  it("un éxito borra el estado", () => assert.equal(lim.acctSuccess(), null));
});

const pow = require("../lib/pow");
describe("pow: reto de prueba de trabajo", () => {
  const key = pow.deriveKey("s".repeat(40));
  const now = 1_900_000_000_000;

  it("dificultad: sin reto antes del 3.er fallo y escalada después (tope 22)", () => {
    assert.deepEqual([0, 1, 2, 3, 4, 5, 9].map(pow.bitsForFails), [0, 0, 0, 18, 20, 22, 22]);
    assert.equal(pow.bitsForFails(NaN), 0);
  });
  it("leadingZeroBits", () => {
    assert.equal(pow.leadingZeroBits(Buffer.from([0, 0, 0x10])), 19);
    assert.equal(pow.leadingZeroBits(Buffer.from([0x80])), 0);
    assert.equal(pow.leadingZeroBits(Buffer.from([0, 0x01])), 15);
  });
  it("una solución correcta se acepta", () => {
    const ch = pow.issue(key, "acct1", 14, now);
    assert.deepEqual(pow.verify(key, "acct1", ch, pow.solve(ch), 14, now), { ok: true, salt: ch.salt });
  });
  it("rechaza: sin trabajo, firma falsa, otra cuenta, caducado, dificultad rebajada y formatos raros", () => {
    const ch = pow.issue(key, "acct1", 14, now);
    const good = pow.solve(ch);
    let bad = 0; while (pow.leadingZeroBits(pow.digest(ch.salt, bad)) >= 14) bad++;
    assert.equal(pow.verify(key, "acct1", ch, String(bad), 14, now).reason, "work");
    assert.equal(pow.verify(key, "acct1", { ...ch, sig: "0".repeat(64) }, good, 14, now).reason, "signature");
    assert.equal(pow.verify(key, "acct1", { ...ch, bits: 1 }, good, 14, now).reason, "downgraded");
    assert.equal(pow.verify(key, "acct1", { ...ch, bits: 15 }, good, 14, now).reason, "signature");
    assert.equal(pow.verify(key, "acct2", ch, good, 14, now).reason, "signature");
    assert.equal(pow.verify(key, "acct1", ch, good, 14, ch.exp + 1).reason, "expired");
    assert.equal(pow.verify(key, "acct1", ch, "abc", 14, now).reason, "nonce");
    assert.equal(pow.verify(key, "acct1", ch, "-1", 14, now).reason, "nonce");
    assert.equal(pow.verify(key, "acct1", null, good, 14, now).reason, "missing");
    assert.equal(pow.verify(key, "acct1", { salt: "x" }, good, 14, now).reason, "shape");
  });
  it("exige al menos la dificultad vigente aunque el reto sea válido y más fácil", () => {
    const easy = pow.issue(key, "acct1", 10, now);
    assert.equal(pow.verify(key, "acct1", easy, pow.solve(easy), 18, now).reason, "downgraded");
  });
  it("vector de compatibilidad con la app Android", () => {
    const salt = "00112233445566778899aabbccddeeff";
    assert.equal(pow.solve({ salt, bits: 12 }), "386");
  });
});
