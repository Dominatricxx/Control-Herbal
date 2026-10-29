// Tests de las reglas de Realtime Database. Se ejecutan contra el emulador:
//   cd firebase && npm ci && npm test
import { readFileSync } from "node:fs";
import { before, after, beforeEach, describe, it } from "node:test";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { ref, set, update, get, serverTimestamp } from "firebase/database";

const PROJECT_ID = "demo-control-herbal";
let env;

const goodSensor = () => ({
  temp: 24.5, hum: 55, luz: 60, luz_raw: 58, soil: 40, irh: 20,
  seq: 5, somb: 0, acc: "OK", tipo: 2, boot: 1, sensores_ok: 1,
  timestamp: 12345, ts: serverTimestamp(),
});

const goodCommand = (uid, extra = {}) => ({
  activar: 1, duracion: 10, timestamp: serverTimestamp(),
  uid, nonce: "0123456789abcdef0123", fuente: "App_Android", ...extra,
});

const ownerClaims = {
  email_verified: true,
  firebase: { sign_in_second_factor: "totp" },
};

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    database: { rules: readFileSync("database.rules.json", "utf8") },
  });
});

after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    await set(ref(db, "roles/owner1"), "owner");
    await set(ref(db, "roles/esp1"), "device");
    await set(ref(db, "roles/watch1"), "viewer");
    await set(ref(db, "sensor"), { temp: 20, hum: 50, soil: 30, luz: 10, ts: 1 });
  });
});

const as = (uid, token = {}) => env.authenticatedContext(uid, token).database();
const owner = () => as("owner1", ownerClaims);
const device = () => as("esp1");
const viewer = () => as("watch1");
const anon = () => env.unauthenticatedContext().database();

describe("lectura", () => {
  it("sin sesión no lee nada", async () => {
    await assertFails(get(ref(anon(), "sensor")));
    await assertFails(get(ref(anon(), "config")));
  });
  it("usuario autenticado sin rol no lee", async () => {
    await assertFails(get(ref(as("intruso"), "sensor")));
  });
  it("owner con MFA, device y viewer leen /sensor", async () => {
    await assertSucceeds(get(ref(owner(), "sensor")));
    await assertSucceeds(get(ref(device(), "sensor")));
    await assertSucceeds(get(ref(viewer(), "sensor")));
  });
  it("owner sin TOTP no lee /sensor", async () => {
    await assertFails(get(ref(as("owner1", { email_verified: true }), "sensor")));
  });
  it("viewer no lee /control ni /config", async () => {
    await assertFails(get(ref(viewer(), "control")));
    await assertFails(get(ref(viewer(), "config")));
  });
  it("nadie lee /roles desde un cliente", async () => {
    await assertFails(get(ref(owner(), "roles")));
  });
});

describe("escritura de /sensor", () => {
  it("el device puede publicar una lectura válida", async () => {
    await assertSucceeds(update(ref(device(), "sensor"), goodSensor()));
  });
  it("owner, viewer y anónimos no pueden falsificar /sensor", async () => {
    for (const db of [owner(), viewer(), as("intruso"), anon()]) {
      await assertFails(update(ref(db, "sensor"), goodSensor()));
    }
  });
  it("rechaza valores fuera de rango o de tipo incorrecto", async () => {
    const db = device();
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), temp: 500 }));
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), soil: -1 }));
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), acc: "x".repeat(201) }));
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), hum: "mucha" }));
  });
  it("rechaza campos desconocidos", async () => {
    await assertFails(update(ref(device(), "sensor"), { ...goodSensor(), extra: 1 }));
  });
  it("limita la frecuencia de escritura (>= 4 s entre lecturas)", async () => {
    const db = device();
    await assertSucceeds(update(ref(db, "sensor"), goodSensor()));
    await assertFails(update(ref(db, "sensor"), goodSensor()));
  });
});

describe("config/tipoPlanta", () => {
  it("solo el owner con MFA escribe, con valor 1..3", async () => {
    await assertSucceeds(set(ref(owner(), "config/tipoPlanta"), 2));
    await assertFails(set(ref(owner(), "config/tipoPlanta"), 7));
    await assertFails(set(ref(device(), "config/tipoPlanta"), 2));
    await assertFails(set(ref(viewer(), "config/tipoPlanta"), 2));
    await assertFails(set(ref(as("intruso"), "config/tipoPlanta"), 2));
  });
  it("el device puede leerlo", async () => {
    await assertSucceeds(get(ref(device(), "config/tipoPlanta")));
  });
});

describe("control/riego", () => {
  it("el owner con MFA envía una orden válida", async () => {
    await assertSucceeds(set(ref(owner(), "control/riego"), goodCommand("owner1")));
  });
  it("otros roles no pueden ordenar riego", async () => {
    for (const uid of ["esp1", "watch1", "intruso"]) {
      await assertFails(set(ref(as(uid), "control/riego"), goodCommand(uid)));
    }
    await assertFails(set(ref(anon(), "control/riego"), goodCommand("x")));
  });
  it("tope de duración: 1..60 s", async () => {
    const db = owner();
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { duracion: 61 })));
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { duracion: 0 })));
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { duracion: 3600 })));
  });
  it("el uid del comando debe coincidir con quien lo envía", async () => {
    await assertFails(set(ref(owner(), "control/riego"), goodCommand("otro")));
  });
  it("el timestamp debe ser el del servidor (anti-replay)", async () => {
    await assertFails(set(ref(owner(), "control/riego"), goodCommand("owner1", { timestamp: 1 })));
  });
  it("exige nonce y no admite campos extra", async () => {
    const { nonce, ...sinNonce } = goodCommand("owner1");
    await assertFails(set(ref(owner(), "control/riego"), sinNonce));
    await assertFails(set(ref(owner(), "control/riego"), goodCommand("owner1", { extra: 1 })));
  });
  it("cooldown de 30 s entre órdenes", async () => {
    const db = owner();
    await assertSucceeds(set(ref(db, "control/riego"), goodCommand("owner1")));
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { nonce: "fedcba9876543210fedc" })));
  });
  it("el device lee la orden y publica el estado, pero no la orden", async () => {
    await assertSucceeds(get(ref(device(), "control/riego")));
    await assertSucceeds(set(ref(device(), "control/estado"),
      { nonce: "0123456789abcdef0123", resultado: "ok", segundos: 10, ts: serverTimestamp() }));
    await assertFails(set(ref(device(), "control/riego"), goodCommand("esp1")));
  });
});

describe("watering_history", () => {
  it("solo el owner con MFA lo lee y escribe", async () => {
    const row = { lastWateringTime: 1000, syncTimestamp: 2000 };
    await assertSucceeds(set(ref(owner(), "watering_history/1"), row));
    await assertSucceeds(get(ref(owner(), "watering_history")));
    await assertFails(set(ref(device(), "watering_history/1"), row));
    await assertFails(get(ref(viewer(), "watering_history")));
  });
});

describe("perfil /users", () => {
  const profile = {
    nombre: "Ada",
    apPaterno: "Lovelace",
    apMaterno: "",
    correo: "ada@example.com",
    fechaRegistro: 1,
  };
  it("solo permite leer el perfil propio con correo verificado", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await set(ref(ctx.database(), "users/u1"), profile);
    });
    await assertSucceeds(get(ref(as("u1", { email_verified: true }), "users/u1")));
    await assertFails(get(ref(as("u1", { email_verified: false }), "users/u1")));
    await assertFails(get(ref(as("u2", { email_verified: true }), "users/u1")));
  });
  it("solo guarda perfil propio verificado y el correo debe coincidir con el token", async () => {
    await assertSucceeds(set(ref(as("u1", {
      email_verified: true,
      email: profile.correo,
    }), "users/u1"), profile));
    await assertFails(set(ref(as("u1", {
      email_verified: true,
      email: "attacker@example.net",
    }), "users/u1"), profile));
    await assertFails(set(ref(as("u1", { email_verified: false, email: profile.correo }), "users/u1"), profile));
    await assertFails(set(ref(as("u2", { email_verified: true, email: profile.correo }), "users/u1"), profile));
    await assertFails(set(ref(as("u1", { email_verified: true, email: profile.correo }), "users/u1"), {
      ...profile,
      passwordHash: "must-not-be-synced",
    }));
  });
  it("permite borrar el perfil propio para poder eliminar la cuenta", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await set(ref(ctx.database(), "users/u1"), profile);
    });
    await assertSucceeds(set(ref(as("u1"), "users/u1"), null));
    await assertFails(set(ref(as("u2"), "users/u1"), null));
  });
});

describe("raíz", () => {
  it("rechaza nodos no previstos", async () => {
    await assertFails(set(ref(owner(), "cualquier/cosa"), 1));
    await assertFails(set(ref(owner(), "roles/owner1"), "owner"));
  });
});
