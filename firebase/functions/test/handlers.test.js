"use strict";
// Prueba de extremo a extremo de las funciones con dobles de firebase-admin / functions / fetch.
const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const Module = require("node:module");

const PEPPER = "p".repeat(40);
const clone = (v) => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));

// ---------- dobles ----------
let tree, authCalls, logs, users, clock;
const getPath = (path) => path.split("/").reduce((n, k) => (n && typeof n === "object" ? n[k] : undefined), tree);
function setPath(path, value) {
  const keys = path.split("/");
  let n = tree;
  for (const k of keys.slice(0, -1)) n = n[k] = typeof n[k] === "object" && n[k] ? n[k] : {};
  if (value === null || value === undefined) delete n[keys.at(-1)]; else n[keys.at(-1)] = clone(value);
}
const fakeDb = {
  ref: (path) => ({
    get: async () => ({ val: () => clone(getPath(path) ?? null) }),
    set: async (v) => setPath(path, v),
    remove: async () => setPath(path, null),
    transaction: async (fn) => {
      const res = fn(clone(getPath(path) ?? null));
      if (res !== undefined) setPath(path, res);
    },
  }),
};
class HttpsError extends Error {
  constructor(code, message, details) { super(message); this.code = code; this.details = details; }
}
const stubs = {
  "firebase-admin": {
    initializeApp() {},
    database: () => fakeDb,
    auth: () => ({
      getUserByEmail: async (e) => { const u = users[e.toLowerCase()]; if (!u) throw new Error("no user"); return { uid: u.uid }; },
      updateUser: async (uid, p) => authCalls.push(["updateUser", uid, p]),
      revokeRefreshTokens: async (uid) => authCalls.push(["revoke", uid]),
    }),
  },
  "firebase-functions/v2/https": { onCall: (_o, h) => h, HttpsError },
  "firebase-functions/params": { defineString: () => ({ value: () => "KEY" }), defineSecret: () => ({ value: () => PEPPER }) },
  "firebase-functions/logger": { info: (...a) => logs.push(["info", a]), warn: (...a) => logs.push(["warn", a]), error: (...a) => logs.push(["error", a]) },
};
const origLoad = Module._load;
Module._load = function (req, ...rest) { return stubs[req] ?? origLoad.call(this, req, ...rest); };
const realNow = Date.now;
Date.now = () => clock;
global.fetch = async (_url, opts) => {
  const { email, password } = JSON.parse(opts.body);
  const u = users[email.toLowerCase()];
  if (u && u.password === password) return { ok: true, json: async () => ({ mfaPendingCredential: "x" }) };
  return { ok: false, json: async () => ({ error: { message: "INVALID_LOGIN_CREDENTIALS" } }) };
};
const fns = require("../index.js");
process.on("exit", () => { Date.now = realNow; });

const totpReq = (uid = "u1") => ({ auth: { uid, token: { firebase: { sign_in_second_factor: "totp" } } }, data: {} });
const redeemReq = (data, ip = "1.2.3.4") => ({ data, rawRequest: { headers: { "x-forwarded-for": ip }, ip } });
const pow = require("../lib/pow");
/** Como la app: si el servidor pide un reto, lo resuelve y reintenta una vez. */
async function redeemSolving(data, ip) {
  try { return await fns.redeemBackupCode(redeemReq(data, ip)); }
  catch (e) {
    if (e.code === "failed-precondition" && e.details?.challenge) {
      const ch = e.details.challenge;
      return fns.redeemBackupCode(redeemReq({ ...data, challenge: ch, nonce: pow.solve(ch) }, ip));
    }
    throw e;
  }
}
const reject = async (p) => { try { await p; } catch (e) { return e; } assert.fail("debía rechazar"); };

beforeEach(() => {
  tree = {}; authCalls = []; logs = []; clock = 1_800_000_000_000;
  users = { "ana@example.com": { uid: "u1", password: "Zq7!mKp2#vXw" } };
});

describe("generateBackupCodes", () => {
  it("exige sesión y segundo factor TOTP", async () => {
    assert.equal((await reject(fns.generateBackupCodes({ data: {} }))).code, "unauthenticated");
    const sinMfa = { auth: { uid: "u1", token: { firebase: { sign_in_provider: "password" } } }, data: {} };
    assert.equal((await reject(fns.generateBackupCodes(sinMfa))).code, "permission-denied");
    const sms = { auth: { uid: "u1", token: { firebase: { sign_in_second_factor: "phone" } } }, data: {} };
    assert.equal((await reject(fns.generateBackupCodes(sms))).code, "permission-denied");
  });
  it("devuelve 10 códigos y en la base solo quedan hashes", async () => {
    const r = await fns.generateBackupCodes(totpReq());
    assert.equal(r.codes.length, 10);
    const dump = JSON.stringify(tree);
    for (const c of r.codes) assert.ok(!dump.includes(c.replace("-", "")), "el código no debe estar en claro");
    assert.equal(Object.keys(tree._server.backup.u1.hashes).length, 10);
  });
  it("regenerar invalida el juego anterior y se limita a 3 por hora", async () => {
    const first = await fns.generateBackupCodes(totpReq());
    await fns.generateBackupCodes(totpReq());
    const old = (await reject(fns.redeemBackupCode(redeemReq({ email: "ana@example.com", password: users["ana@example.com"].password, code: first.codes[0] }, "9.9.9.9"))));
    assert.equal(old.code, "permission-denied");
    await fns.generateBackupCodes(totpReq());
    assert.equal((await reject(fns.generateBackupCodes(totpReq()))).code, "resource-exhausted");
    clock += 61 * 60 * 1000;
    await fns.generateBackupCodes(totpReq());
  });
  it("backupCodesStatus informa cuántos quedan", async () => {
    await fns.generateBackupCodes(totpReq());
    assert.equal((await fns.backupCodesStatus(totpReq())).remaining, 10);
  });
});

describe("redeemBackupCode", () => {
  const good = async () => {
    const { codes } = await fns.generateBackupCodes(totpReq());
    return { email: "ana@example.com", password: users["ana@example.com"].password, code: codes[3], codes };
  };

  it("camino feliz: consume el juego, quita el 2FA perdido y revoca sesiones", async () => {
    const g = await good();
    const r = await fns.redeemBackupCode(redeemReq({ email: g.email, password: g.password, code: g.code.toLowerCase() }));
    assert.deepEqual(r, { ok: true });
    assert.deepEqual(authCalls[0], ["updateUser", "u1", { multiFactor: { enrolledFactors: null } }]);
    assert.deepEqual(authCalls[1], ["revoke", "u1"]);
    assert.equal(tree._server?.backup?.u1, undefined);
    // El mismo código no sirve dos veces.
    const e = await reject(fns.redeemBackupCode(redeemReq({ email: g.email, password: g.password, code: g.code }, "5.5.5.5")));
    assert.equal(e.code, "permission-denied");
  });
  it("contraseña incorrecta, código incorrecto, formato inválido o usuario inexistente: mismo error genérico", async () => {
    const g = await good();
    const bad = [
      { email: g.email, password: "otra-clave-Mala1!", code: g.code },
      { email: g.email, password: g.password, code: "AAAAA-BBBBB" },
      { email: g.email, password: g.password, code: "x" },
      { email: "nadie@example.com", password: "lo-que-sea-Aa1!", code: g.code },
    ];
    let i = 0;
    for (const b of bad) {
      const e = await reject(fns.redeemBackupCode(redeemReq(b, `7.7.7.${i++}`)));
      assert.equal(e.code, "permission-denied");
      assert.equal(e.message, "Datos incorrectos.");
    }
    assert.deepEqual(authCalls, []);
  });
  it("retroceso exponencial: tras el 3.er fallo hay que esperar", async () => {
    const g = await good();
    for (let i = 0; i < 3; i++)
      await reject(fns.redeemBackupCode(redeemReq({ email: g.email, password: g.password, code: "AAAAA-BBBBB" }, `8.8.8.${i}`)));
    const e = await reject(fns.redeemBackupCode(redeemReq({ email: g.email, password: g.password, code: g.code }, "8.8.8.99")));
    assert.equal(e.code, "resource-exhausted");
    assert.equal(e.details.retryAfterSeconds, 5);
    clock += 5_001;                                     // pasado el retroceso, los datos correctos funcionan
    assert.deepEqual(await redeemSolving({ email: g.email, password: g.password, code: g.code }, "8.8.8.99"), { ok: true });
  });
  it("bloqueo de cuenta al 5.º fallo aunque el atacante cambie de IP", async () => {
    const g = await good();
    for (let i = 0; i < 5; i++) {
      clock += 6 * 60 * 1000;                           // respeta el retroceso para llegar al 5.º
      await reject(redeemSolving({ email: g.email, password: "mala-Clave1!x", code: g.code }, `6.6.6.${i}`));
    }
    const e = await reject(fns.redeemBackupCode(redeemReq({ email: g.email, password: g.password, code: g.code }, "6.6.6.200")));
    assert.equal(e.code, "resource-exhausted");
    assert.ok(e.details.retryAfterSeconds > 14 * 60);
  });
  it("límite por IP: 5 intentos / 15 min con correos distintos", async () => {
    for (let i = 0; i < 5; i++)
      await reject(fns.redeemBackupCode(redeemReq({ email: `u${i}@example.com`, password: "mala-Clave1!x", code: "AAAAA-BBBBB" }, "4.4.4.4")));
    const e = await reject(fns.redeemBackupCode(redeemReq({ email: "u9@example.com", password: "mala-Clave1!x", code: "AAAAA-BBBBB" }, "4.4.4.4")));
    assert.equal(e.code, "resource-exhausted");
    clock += 15 * 60 * 1000 + 1;
    const e2 = await reject(fns.redeemBackupCode(redeemReq({ email: "u9@example.com", password: "mala-Clave1!x", code: "AAAAA-BBBBB" }, "4.4.4.4")));
    assert.equal(e2.code, "permission-denied");         // ya no está limitada por IP
  });
  it("dos canjes simultáneos del mismo código: solo uno tiene éxito", async () => {
    const g = await good();
    const run = (ip) => fns.redeemBackupCode(redeemReq({ email: g.email, password: g.password, code: g.code }, ip)).then(() => "ok", () => "fail");
    const res = await Promise.all([run("3.3.3.1"), run("3.3.3.2")]);
    assert.equal(res.filter((r) => r === "ok").length, 1);
  });
  it("registra todos los fallos sin correo ni IP en claro", async () => {
    const g = await good();
    await reject(fns.redeemBackupCode(redeemReq({ email: g.email, password: "mala-Clave1!x", code: g.code }, "1.2.3.4")));
    const warns = logs.filter((l) => l[0] === "warn");
    assert.ok(warns.length >= 1);
    const dump = JSON.stringify(warns);
    assert.ok(dump.includes("backup_redeem_failed"));
    assert.ok(!dump.includes("ana@example.com") && !dump.includes("1.2.3.4") && !dump.includes("mala-Clave1"));
  });
  it("rechaza argumentos mal formados", async () => {
    for (const d of [undefined, {}, { email: 5, password: "x", code: "x" }, { email: "a@b.c", password: "x".repeat(200), code: "x" }])
      assert.equal((await reject(fns.redeemBackupCode(redeemReq(d)))).code, "invalid-argument");
  });
});

describe("redeemBackupCode: reto de prueba de trabajo desde el 3.er fallo", () => {
  const MAIL = "ana@example.com";
  const goodPwd = () => users[MAIL].password;
  const attempt = (extra = {}, ip = "2.2.2.9") =>
    reject(fns.redeemBackupCode(redeemReq({ email: MAIL, password: goodPwd(), code: "AAAAA-BBBBB", ...extra }, ip)));
  const wrong = (ip) => reject(fns.redeemBackupCode(redeemReq({ email: MAIL, password: "mala-Clave1!x", code: "AAAAA-BBBBB" }, ip)));
  const acctFails = () => Object.values(tree._server?.rl?.acct ?? {})[0]?.fails;
  async function threeFailures() {
    for (let i = 0; i < 3; i++) await wrong(`2.2.2.${i}`);
    clock += 6_000; // pasa el retroceso del 3.er fallo
  }

  it("los 2 primeros fallos no exigen reto; el siguiente sí, y pedirlo no consume intentos", async () => {
    await threeFailures();
    assert.equal(acctFails(), 3);
    const e = await attempt();
    assert.equal(e.code, "failed-precondition");
    assert.equal(e.message, "challenge-required");
    assert.equal(e.details.challenge.bits, 18);
    assert.equal(acctFails(), 3);
    const ipKey = require("node:crypto").createHash("sha256").update("2.2.2.9").digest("hex").slice(0, 32);
    assert.equal(tree._server.rl.ip?.[ipKey], undefined);
  });
  it("con el reto resuelto se procesa la petición (un código erróneo cuenta como fallo)", async () => {
    await threeFailures();
    const e = await reject(redeemSolving({ email: MAIL, password: goodPwd(), code: "AAAAA-BBBBB" }, "2.2.2.9"));
    assert.equal(e.code, "permission-denied");
    assert.equal(acctFails(), 4);
  });
  it("cada reto vale una sola vez (anti-repetición) y la dificultad sube", async () => {
    await threeFailures();
    const first = await attempt();
    const ch = first.details.challenge, nonce = pow.solve(ch);
    const used = await attempt({ challenge: ch, nonce });
    assert.equal(used.code, "permission-denied");
    clock += 11_000;
    const replay = await attempt({ challenge: ch, nonce });
    assert.equal(replay.code, "failed-precondition");
    assert.equal(replay.details.challenge.bits, 20);
  });
  it("no se puede rebajar la dificultad ni reutilizar un reto de otra cuenta", async () => {
    await threeFailures();
    const ch = (await attempt()).details.challenge;
    await reject(redeemSolving({ email: MAIL, password: goodPwd(), code: "AAAAA-BBBBB" }, "2.2.2.9"));
    clock += 11_000;
    const easy = await attempt({ challenge: ch, nonce: pow.solve(ch) });
    assert.equal(easy.code, "failed-precondition");
    for (let i = 0; i < 3; i++) await reject(fns.redeemBackupCode(redeemReq({ email: "otra@example.com", password: "x-Mala1!", code: "AAAAA-BBBBB" }, `5.5.5.${i}`)));
    clock += 6_000;
    const cross = await reject(fns.redeemBackupCode(redeemReq({ email: "otra@example.com", password: "x-Mala1!", code: "AAAAA-BBBBB", challenge: ch, nonce: pow.solve(ch) }, "5.5.5.9")));
    assert.equal(cross.code, "failed-precondition");
  });
  it("un reto caducado se rechaza y se emite otro", async () => {
    await threeFailures();
    const ch = (await attempt()).details.challenge, nonce = pow.solve(ch);
    clock += 3 * 60 * 1000;
    assert.equal((await attempt({ challenge: ch, nonce })).code, "failed-precondition");
  });
  it("los retos se registran sin datos personales", async () => {
    await threeFailures();
    await attempt();
    const dump = JSON.stringify(logs.filter((l) => l[0] === "warn"));
    assert.ok(dump.includes("backup_redeem_challenge"));
    assert.ok(!dump.includes(MAIL) && !dump.includes("2.2.2.9"));
  });
});
