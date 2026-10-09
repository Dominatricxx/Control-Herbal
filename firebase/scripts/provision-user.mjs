#!/usr/bin/env node
// Alta de cuentas de Control Herbal con la política de contraseñas COMPLETA aplicada en servidor:
// reglas duras + zxcvbn >= 3 + comprobación en HaveIBeenPwned. Asigna el rol en /roles/{uid}.
// Es el único camino previsto para crear cuentas (no hay registro público en la app).
//
//   node scripts/provision-user.mjs --project <ID> --email owner@dominio.com --role owner
//   node scripts/provision-user.mjs --project <ID> --email esp32-1@dominio.com --role device --generate
//
//   --role owner|device|viewer     --generate  crea una contraseña aleatoria válida y la muestra UNA vez
//   --db-url <url>                 (por defecto https://<ID>-default-rtdb.firebaseio.com)
//   --allow-pwned-offline          continuar si HaveIBeenPwned no responde (por defecto se rechaza)
// La contraseña se lee de NEW_USER_PASSWORD o se pide por teclado (sin eco). Nunca por argumento.

import { parseArgs } from "node:util";
import { createInterface } from "node:readline";
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getDatabase } from "firebase-admin/database";
import { evaluate, loadCommonCores, pwnedCount, generatePassword } from "./lib/passwordPolicy.mjs";

const { values: a } = parseArgs({
  options: {
    project: { type: "string" }, email: { type: "string" }, role: { type: "string" },
    "db-url": { type: "string" }, generate: { type: "boolean", default: false },
    "allow-pwned-offline": { type: "boolean", default: false },
  },
});
if (!a.project || !a.email || !["owner", "device", "viewer"].includes(a.role ?? "")) {
  console.error("Uso: --project <ID> --email <correo> --role owner|device|viewer [--generate]"); process.exit(2);
}

function promptHidden(q) {
  return new Promise((resolve) => {
    const rl = createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    rl._writeToOutput = (s) => { if (s.includes(q)) process.stdout.write(s); };
    rl.question(q, (v) => { rl.close(); process.stdout.write("\n"); resolve(v); });
  });
}

let zxcvbn;
try { zxcvbn = (await import("zxcvbn")).default; }
catch { console.error("Falta la dependencia: ejecuta `npm install` en firebase/."); process.exit(2); }

const commonCores = loadCommonCores(new URL("../../app/src/main/assets/common_passwords.txt", import.meta.url));
const password = a.generate ? generatePassword() : (process.env.NEW_USER_PASSWORD || await promptHidden("Contraseña: "));

const res = evaluate(password, { userInputs: [a.email], commonCores, zxcvbn });
if (!res.ok) { console.error(`Contraseña rechazada: ${res.failures.join(", ")}`); process.exit(1); }
const pwned = await pwnedCount(password);
if (pwned === null && !a["allow-pwned-offline"]) { console.error("No se pudo consultar HaveIBeenPwned (usa --allow-pwned-offline bajo tu responsabilidad)."); process.exit(1); }
if (pwned) { console.error(`Contraseña rechazada: aparece ${pwned} veces en filtraciones conocidas.`); process.exit(1); }

const app = initializeApp({
  credential: applicationDefault(), projectId: a.project,
  databaseURL: a["db-url"] ?? `https://${a.project}-default-rtdb.firebaseio.com`,
});
const user = await getAuth(app).createUser({ email: a.email, password, emailVerified: false, disabled: false });
await getDatabase(app).ref(`roles/${user.uid}`).set(a.role);

console.log(`Cuenta creada. UID: ${user.uid}  rol: ${a.role}`);
if (a.generate) console.log(`Contraseña generada (guárdala ahora, no se volverá a mostrar):\n${password}`);
if (a.role === "owner") console.log("El owner debe iniciar sesión en la app, verificar su correo y enrolar el 2FA antes de poder usar la base de datos.");
process.exit(0);
