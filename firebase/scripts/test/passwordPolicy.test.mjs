import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { evaluate, cores, loadCommonCores, parsePwnedRange, sha1Upper, pwnedCount } from "../lib/passwordPolicy.mjs";

const commonCores = loadCommonCores(new URL("../../../app/src/main/assets/common_passwords.txt", import.meta.url));
const ev = (pw, userInputs = []) => evaluate(pw, { commonCores, userInputs });
const fails = (pw, ui) => ev(pw, ui).failures;

describe("reglas duras", () => {
  it("rechaza las contraseñas típicas", () => {
    for (const pw of ["password123", "12345678", "qwerty123456", "Password123!", "P@ssw0rd!2024", "Admin1234567!", "Welcome2024!!", "ILoveYou123!!"])
      assert.notEqual(fails(pw).length, 0, pw);
  });
  it("longitud mínima 12 y máxima 128", () => {
    assert.ok(fails("Ab1!xyzQ9m").includes("LENGTH"));
    assert.ok(fails("Ab1!" + "x".repeat(130)).includes("MAX_LENGTH"));
  });
  it("exige mayúscula, minúscula, número y símbolo", () => {
    const base = "Zq7!mKp2#vXw";
    assert.deepEqual(fails(base), []);
    assert.ok(fails(base.toLowerCase()).includes("UPPER"));
    assert.ok(fails(base.toUpperCase()).includes("LOWER"));
    assert.ok(fails("Zqpmkpxvxwab!").includes("DIGIT"));
    assert.ok(fails("Zq7mKp2vXwab").includes("SYMBOL"));
  });
  it("el espacio y los símbolos no reconocidos por Firebase no cuentan como símbolo", () => {
    assert.ok(fails("Zq7 mKp2 vXwab").includes("SYMBOL"));
    assert.ok(fails("Zq7€mKp2€vXwab").includes("SYMBOL"));
  });
  it("detecta el núcleo común tras normalizar leet/acentos/dígitos", () => {
    assert.deepEqual(cores("P@ssw0rd!2024").includes("password"), true);
    assert.ok(fails("Contraseña#2024").includes("COMMON"));
    assert.ok(fails("C0ntr@señ@").includes("COMMON"));
    assert.ok(fails("ControlHerbal#2026").includes("COMMON"));
  });
  it("no bloquea frases largas que solo contienen una palabra común", () => {
    assert.deepEqual(fails("Dragonfly-Orchid-Tractor-92"), []);
  });
  it("secuencias y repeticiones", () => {
    assert.ok(fails("Xk!12345Tmqrz").includes("SEQUENCE"));
    assert.ok(fails("Xk!qwertyTmqz9").includes("SEQUENCE"));
    assert.ok(fails("Xk!9876543Tmqz").includes("SEQUENCE"));
    assert.ok(fails("Xk!aaaaTmqz9wp").includes("REPEAT"));
  });
  it("no contiene el correo ni el nombre", () => {
    assert.ok(fails("Maria#Lopez-7391xz", ["maria.lopez@example.com"]).includes("PERSONAL"));
    assert.ok(!fails("Zq7!mKp2#vXw", ["maria.lopez@example.com"]).includes("PERSONAL"));
  });
  it("aplica el umbral de zxcvbn cuando se le proporciona", () => {
    const low = evaluate("Zq7!mKp2#vXw", { commonCores, zxcvbn: () => ({ score: 2 }) });
    assert.ok(low.failures.includes("WEAK") && !low.ok);
    const high = evaluate("Zq7!mKp2#vXw", { commonCores, zxcvbn: () => ({ score: 4 }) });
    assert.equal(high.ok, true);
    assert.equal(high.level, 4);
  });
  it("el medidor se limita a 1 si falla una regla dura", () => {
    assert.equal(evaluate("Abc!1xyz", { commonCores, zxcvbn: () => ({ score: 4 }) }).level, 1);
  });
});

describe("HaveIBeenPwned (k-anonimato)", () => {
  it("SHA-1 de 'password' conocido", () => {
    assert.equal(sha1Upper("password"), "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
  });
  it("parsea el rango y devuelve el contador", () => {
    const body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2";
    assert.equal(parsePwnedRange(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"), 3861493);
    assert.equal(parsePwnedRange(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"), 0);
  });
  it("solo envía los 5 primeros caracteres del hash", async () => {
    let url;
    const fake = async (u) => { url = u; return { ok: true, text: async () => "1E4C9B93F3F0682250B6CF8331B7EE68FD8:42" }; };
    assert.equal(await pwnedCount("password", fake), 42);
    assert.equal(url, "https://api.pwnedpasswords.com/range/5BAA6");
  });
  it("devuelve null si el servicio falla (el llamador decide)", async () => {
    assert.equal(await pwnedCount("x", async () => { throw new Error("red"); }), null);
    assert.equal(await pwnedCount("x", async () => ({ ok: false })), null);
  });
});

import { generatePassword } from "../lib/passwordPolicy.mjs";
describe("generatePassword", () => {
  it("siempre cumple la política y es distinta cada vez", () => {
    const seen = new Set();
    for (let i = 0; i < 200; i++) {
      const pw = generatePassword();
      assert.equal(pw.length, 24);
      assert.deepEqual(fails(pw), [], pw);
      seen.add(pw);
    }
    assert.equal(seen.size, 200);
  });
});
