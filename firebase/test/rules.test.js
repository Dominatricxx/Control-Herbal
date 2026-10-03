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

const as = (uid) => env.authenticatedContext(uid).database();
const anon = () => env.unauthenticatedContext().database();

describe("lectura", () => {
  it("sin sesión no lee nada", async () => {
    await assertFails(get(ref(anon(), "sensor")));
    await assertFails(get(ref(anon(), "config")));
  });
  it("usuario autenticado sin rol (p. ej. anónimo) no lee", async () => {
    await assertFails(get(ref(as("intruso"), "sensor")));
  });
  it("owner, device y viewer leen /sensor", async () => {
    for (const uid of ["owner1", "esp1", "watch1"]) {
      await assertSucceeds(get(ref(as(uid), "sensor")));
    }
  });
  it("viewer no lee /control ni /config", async () => {
    await assertFails(get(ref(as("watch1"), "control")));
    await assertFails(get(ref(as("watch1"), "config")));
  });
  it("nadie lee /roles desde un cliente", async () => {
    await assertFails(get(ref(as("owner1"), "roles")));
  });
});

describe("escritura de /sensor", () => {
  it("el device puede publicar una lectura válida", async () => {
    await assertSucceeds(update(ref(as("esp1"), "sensor"), goodSensor()));
  });
  it("owner, viewer y anónimos no pueden falsificar /sensor", async () => {
    for (const db of [as("owner1"), as("watch1"), as("intruso"), anon()]) {
      await assertFails(update(ref(db, "sensor"), goodSensor()));
    }
  });
  it("rechaza valores fuera de rango o de tipo incorrecto", async () => {
    const db = as("esp1");
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), temp: 500 }));
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), soil: -1 }));
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), acc: "x".repeat(201) }));
    await assertFails(update(ref(db, "sensor"), { ...goodSensor(), hum: "mucha" }));
  });
  it("rechaza campos desconocidos", async () => {
    await assertFails(update(ref(as("esp1"), "sensor"), { ...goodSensor(), extra: 1 }));
  });
  it("limita la frecuencia de escritura (>= 4 s entre lecturas)", async () => {
    const db = as("esp1");
    await assertSucceeds(update(ref(db, "sensor"), goodSensor()));
    await assertFails(update(ref(db, "sensor"), goodSensor()));
  });
});

describe("config/tipoPlanta", () => {
  it("solo el owner escribe, con valor 1..3", async () => {
    await assertSucceeds(set(ref(as("owner1"), "config/tipoPlanta"), 2));
    await assertFails(set(ref(as("owner1"), "config/tipoPlanta"), 7));
    await assertFails(set(ref(as("esp1"), "config/tipoPlanta"), 2));
    await assertFails(set(ref(as("watch1"), "config/tipoPlanta"), 2));
    await assertFails(set(ref(as("intruso"), "config/tipoPlanta"), 2));
  });
  it("el device puede leerlo", async () => {
    await assertSucceeds(get(ref(as("esp1"), "config/tipoPlanta")));
  });
});

describe("control/riego", () => {
  it("el owner envía una orden válida", async () => {
    await assertSucceeds(set(ref(as("owner1"), "control/riego"), goodCommand("owner1")));
  });
  it("otros roles no pueden ordenar riego", async () => {
    for (const uid of ["esp1", "watch1", "intruso"]) {
      await assertFails(set(ref(as(uid), "control/riego"), goodCommand(uid)));
    }
    await assertFails(set(ref(anon(), "control/riego"), goodCommand("x")));
  });
  it("tope de duración: 1..60 s", async () => {
    const db = as("owner1");
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { duracion: 61 })));
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { duracion: 0 })));
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { duracion: 3600 })));
  });
  it("el uid del comando debe coincidir con quien lo envía", async () => {
    await assertFails(set(ref(as("owner1"), "control/riego"), goodCommand("otro")));
  });
  it("el timestamp debe ser el del servidor (anti-replay)", async () => {
    await assertFails(set(ref(as("owner1"), "control/riego"), goodCommand("owner1", { timestamp: 1 })));
  });
  it("exige nonce y no admite campos extra", async () => {
    const { nonce, ...sinNonce } = goodCommand("owner1");
    await assertFails(set(ref(as("owner1"), "control/riego"), sinNonce));
    await assertFails(set(ref(as("owner1"), "control/riego"), goodCommand("owner1", { extra: 1 })));
  });
  it("cooldown de 30 s entre órdenes", async () => {
    const db = as("owner1");
    await assertSucceeds(set(ref(db, "control/riego"), goodCommand("owner1")));
    await assertFails(set(ref(db, "control/riego"), goodCommand("owner1", { nonce: "fedcba9876543210fedc" })));
  });
  it("el device lee la orden y publica el estado, pero no la orden", async () => {
    await assertSucceeds(get(ref(as("esp1"), "control/riego")));
    await assertSucceeds(set(ref(as("esp1"), "control/estado"),
      { nonce: "0123456789abcdef0123", resultado: "ok", segundos: 10, ts: serverTimestamp() }));
    await assertFails(set(ref(as("esp1"), "control/riego"), goodCommand("esp1")));
  });
});

describe("watering_history", () => {
  it("solo el owner lo lee y escribe", async () => {
    const row = { lastWateringTime: 1000, syncTimestamp: 2000 };
    await assertSucceeds(set(ref(as("owner1"), "watering_history/1"), row));
    await assertSucceeds(get(ref(as("owner1"), "watering_history")));
    await assertFails(set(ref(as("esp1"), "watering_history/1"), row));
    await assertFails(get(ref(as("watch1"), "watering_history")));
  });
});

describe("raíz", () => {
  it("rechaza nodos no previstos", async () => {
    await assertFails(set(ref(as("owner1"), "cualquier/cosa"), 1));
    await assertFails(set(ref(as("owner1"), "roles/owner1"), "owner"));
  });
});
