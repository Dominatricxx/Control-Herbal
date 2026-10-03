// Misma batería de escenarios que test/rules.test.js, ejecutada con el evaluador local.
//   cd firebase && node --test test-offline/
import { readFileSync } from "node:fs";
import { beforeEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import { Sim, SERVER_TS } from "./rtdb-rules-sim.mjs";

const rules = JSON.parse(readFileSync(new URL("../database.rules.json", import.meta.url), "utf8"));
let sim;
const U = (uid) => ({ uid });
const ANON = null;

const goodSensor = () => ({
  temp: 24.5, hum: 55, luz: 60, luz_raw: 58, soil: 40, irh: 20,
  seq: 5, somb: 0, acc: "OK", tipo: 2, boot: 1, sensores_ok: 1, timestamp: 12345, ts: SERVER_TS,
});
const goodCommand = (uid, extra = {}) => ({
  activar: 1, duracion: 10, timestamp: SERVER_TS, uid, nonce: "0123456789abcdef0123", fuente: "App_Android", ...extra,
});

beforeEach(() => {
  sim = new Sim(rules);
  sim.seed("roles/owner1", "owner");
  sim.seed("roles/esp1", "device");
  sim.seed("roles/watch1", "viewer");
  sim.seed("sensor", { temp: 20, hum: 50, soil: 30, luz: 10, ts: 1 });
});

describe("lectura", () => {
  it("sin sesión o sin rol no lee nada", () => {
    assert.equal(sim.read(ANON, "sensor"), false);
    assert.equal(sim.read(ANON, "config"), false);
    assert.equal(sim.read(U("intruso"), "sensor"), false);
  });
  it("owner, device y viewer leen /sensor", () => {
    for (const u of ["owner1", "esp1", "watch1"]) assert.equal(sim.read(U(u), "sensor"), true, u);
  });
  it("viewer no lee /control ni /config; nadie lee /roles", () => {
    assert.equal(sim.read(U("watch1"), "control"), false);
    assert.equal(sim.read(U("watch1"), "config"), false);
    assert.equal(sim.read(U("owner1"), "roles"), false);
  });
});

describe("escritura de /sensor", () => {
  it("el device publica una lectura válida", () => {
    assert.equal(sim.write(U("esp1"), "sensor", goodSensor(), "update"), true);
  });
  it("owner, viewer y anónimos no pueden falsificar /sensor", () => {
    for (const a of [U("owner1"), U("watch1"), U("intruso"), ANON])
      assert.equal(sim.write(a, "sensor", goodSensor(), "update"), false);
  });
  it("rechaza fuera de rango, tipo erróneo y campos desconocidos", () => {
    const a = U("esp1");
    assert.equal(sim.write(a, "sensor", { ...goodSensor(), temp: 500 }, "update"), false);
    assert.equal(sim.write(a, "sensor", { ...goodSensor(), soil: -1 }, "update"), false);
    assert.equal(sim.write(a, "sensor", { ...goodSensor(), acc: "x".repeat(201) }, "update"), false);
    assert.equal(sim.write(a, "sensor", { ...goodSensor(), hum: "mucha" }, "update"), false);
    assert.equal(sim.write(a, "sensor", { ...goodSensor(), extra: 1 }, "update"), false);
  });
  it("limita la frecuencia: >= 4 s entre escrituras", () => {
    const a = U("esp1");
    assert.equal(sim.write(a, "sensor", goodSensor(), "update"), true);
    sim.advance(1000);
    assert.equal(sim.write(a, "sensor", goodSensor(), "update"), false);
    sim.advance(4000);
    assert.equal(sim.write(a, "sensor", goodSensor(), "update"), true);
  });
});

describe("config/tipoPlanta", () => {
  it("solo el owner escribe, con valor 1..3", () => {
    assert.equal(sim.write(U("owner1"), "config/tipoPlanta", 2), true);
    assert.equal(sim.write(U("owner1"), "config/tipoPlanta", 7), false);
    for (const u of ["esp1", "watch1", "intruso"]) assert.equal(sim.write(U(u), "config/tipoPlanta", 2), false, u);
  });
  it("el device puede leerlo", () => assert.equal(sim.read(U("esp1"), "config/tipoPlanta"), true));
});

describe("control/riego", () => {
  it("el owner envía una orden válida", () => {
    assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("owner1")), true);
  });
  it("otros roles no pueden ordenar riego", () => {
    for (const u of ["esp1", "watch1", "intruso"]) assert.equal(sim.write(U(u), "control/riego", goodCommand(u)), false, u);
    assert.equal(sim.write(ANON, "control/riego", goodCommand("x")), false);
  });
  it("tope de duración 1..60 s", () => {
    for (const d of [61, 0, 3600]) assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("owner1", { duracion: d })), false, String(d));
    assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("owner1", { duracion: 60 })), true);
  });
  it("el uid del comando debe coincidir con el emisor", () => {
    assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("otro")), false);
  });
  it("el timestamp debe ser el del servidor (anti-replay)", () => {
    assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("owner1", { timestamp: 1 })), false);
  });
  it("exige nonce y no admite campos extra", () => {
    const { nonce, ...sin } = goodCommand("owner1");
    assert.equal(sim.write(U("owner1"), "control/riego", sin), false);
    assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("owner1", { nonce: "corto" })), false);
    assert.equal(sim.write(U("owner1"), "control/riego", goodCommand("owner1", { extra: 1 })), false);
  });
  it("cooldown de 30 s entre órdenes", () => {
    const a = U("owner1");
    assert.equal(sim.write(a, "control/riego", goodCommand("owner1")), true);
    sim.advance(5000);
    assert.equal(sim.write(a, "control/riego", goodCommand("owner1", { nonce: "fedcba9876543210fedc" })), false);
    sim.advance(30000);
    assert.equal(sim.write(a, "control/riego", goodCommand("owner1", { nonce: "fedcba9876543210fedc" })), true);
  });
  it("el device lee la orden y publica el estado, pero no la orden", () => {
    assert.equal(sim.read(U("esp1"), "control/riego"), true);
    assert.equal(sim.write(U("esp1"), "control/estado",
      { nonce: "0123456789abcdef0123", resultado: "ok", segundos: 10, ts: SERVER_TS }), true);
    assert.equal(sim.write(U("esp1"), "control/riego", goodCommand("esp1")), false);
  });
});

describe("watering_history y raíz", () => {
  it("solo el owner lo lee y escribe", () => {
    const row = { lastWateringTime: 1000, syncTimestamp: 2000 };
    assert.equal(sim.write(U("owner1"), "watering_history/1", row), true);
    assert.equal(sim.read(U("owner1"), "watering_history"), true);
    assert.equal(sim.write(U("esp1"), "watering_history/1", row), false);
    assert.equal(sim.read(U("watch1"), "watering_history"), false);
  });
  it("rechaza nodos no previstos y escribir /roles", () => {
    assert.equal(sim.write(U("owner1"), "cualquier/cosa", 1), false);
    assert.equal(sim.write(U("owner1"), "roles/owner1", "owner"), false);
  });
});
