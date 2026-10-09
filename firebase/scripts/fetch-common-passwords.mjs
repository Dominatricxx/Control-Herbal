#!/usr/bin/env node
// Amplía app/src/main/assets/common_passwords.txt con una lista pública de contraseñas filtradas
// (por defecto SecLists "10k-most-common", licencia MIT). Una sola fuente de verdad: la usan la app y los scripts.
//
//   node scripts/fetch-common-passwords.mjs                       # descarga la lista por defecto
//   node scripts/fetch-common-passwords.mjs --input lista.txt     # usa un fichero local (sin red)
//   opciones: --url <https://...>  --output <ruta>  --min-length 4
//
// Imprime el SHA-256 de la fuente para que quede constancia de qué lista se usó. Revisa el diff antes de subirlo.

import { parseArgs } from "node:util";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { coresFromList, mergeAsset, MIN_CORE_LENGTH } from "./lib/commonList.mjs";

const DEFAULT_URL = "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Common-Credentials/10k-most-common.txt";
const DEFAULT_OUT = fileURLToPath(new URL("../../app/src/main/assets/common_passwords.txt", import.meta.url));

const { values: a } = parseArgs({
  options: {
    input: { type: "string" }, url: { type: "string", default: DEFAULT_URL },
    output: { type: "string", default: DEFAULT_OUT }, "min-length": { type: "string", default: String(MIN_CORE_LENGTH) },
  },
});

let source, label;
if (a.input) { source = readFileSync(a.input, "utf8"); label = a.input; }
else {
  if (!a.url.startsWith("https://")) { console.error("Solo se admite HTTPS."); process.exit(2); }
  const res = await fetch(a.url, { signal: AbortSignal.timeout(30_000) });
  if (!res.ok) { console.error(`Descarga fallida: HTTP ${res.status}`); process.exit(1); }
  source = await res.text(); label = a.url;
}

const lines = source.split(/\r?\n/).filter((l) => l.trim() && !l.startsWith("#")).length;
if (lines < 100) { console.error(`La fuente tiene solo ${lines} líneas: parece incorrecta; no se modifica nada.`); process.exit(1); }

const sha = createHash("sha256").update(source).digest("hex");
const minLen = Number.parseInt(a["min-length"], 10);
const before = readFileSync(a.output, "utf8");
const merged = mergeAsset(before, coresFromList(source, minLen), `${label} (${lines} líneas, sha256 ${sha.slice(0, 16)}…)`);
writeFileSync(a.output, merged);

const count = (t) => t.split("\n").filter((l) => l && !l.startsWith("#")).length;
console.log(`Fuente: ${label}\nSHA-256: ${sha}\nLíneas de la fuente: ${lines}\nNúcleos: ${count(before)} -> ${count(merged)}\nEscrito: ${a.output}`);
