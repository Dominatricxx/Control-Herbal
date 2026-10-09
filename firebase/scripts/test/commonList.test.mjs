import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { coresFromList, mergeAsset } from "../lib/commonList.mjs";
import { evaluate } from "../lib/passwordPolicy.mjs";

const SCRIPT = fileURLToPath(new URL("../fetch-common-passwords.mjs", import.meta.url));

describe("commonList", () => {
  const sample = ["123456", "password", "P@ssw0rd", "monkey1", "a1", "iloveyou", "# comentario", "", "qwertyuiop", "sunshine"].join("\n");

  it("convierte a núcleos normalizados, sin dígitos ni núcleos cortos", () => {
    const c = coresFromList(sample);
    for (const w of ["password", "monkey", "iloveyou", "sunshine"]) assert.ok(c.has(w), w);
    assert.ok(![...c].some((x) => x.length < 4));
    assert.ok(![...c].some((x) => /[^a-z]/.test(x)));
  });
  it("mergeAsset conserva la cabecera, ordena, deduplica y es idempotente", () => {
    const existing = "# cabecera\n# otra\nzebra\nabcd\n";
    const once = mergeAsset(existing, coresFromList(sample), "origen-1");
    const twice = mergeAsset(once, coresFromList(sample), "origen-2");
    assert.ok(once.startsWith("# cabecera\n# otra\n# fuente: origen-1\n"));
    assert.equal(twice.split("\n").filter((l) => l.startsWith("# fuente:")).length, 1);
    const words = (t) => t.split("\n").filter((l) => l && !l.startsWith("#"));
    assert.deepEqual(words(once), words(twice));
    assert.deepEqual(words(once), [...new Set(words(once))].sort());
    assert.ok(words(once).includes("zebra") && words(once).includes("password"));
  });
  it("de extremo a extremo: el script amplía el asset y la política usa los nuevos núcleos", () => {
    const dir = mkdtempSync(join(tmpdir(), "cl-"));
    const src = join(dir, "lista.txt"), out = join(dir, "asset.txt");
    const filler = Array.from({ length: 120 }, (_, i) => `relleno${"abcdefghij"[i % 10]}${"klmnopqrst"[(i >> 1) % 10]}${i}`);
    writeFileSync(src, ["mariposa", "tequila", ...filler].join("\n"));
    writeFileSync(out, "# cabecera\nbase\n");
    const log = execFileSync("node", [SCRIPT, "--input", src, "--output", out], { encoding: "utf8" });
    assert.match(log, /SHA-256: [0-9a-f]{64}/);
    const set = new Set(readFileSync(out, "utf8").split("\n").filter((l) => l && !l.startsWith("#")));
    assert.ok(set.has("mariposa") && set.has("tequila") && set.has("base"));
    assert.ok(evaluate("Mariposa#2024", { commonCores: set }).failures.includes("COMMON"));
  });
  it("se niega a procesar una fuente sospechosamente corta y no toca el asset", () => {
    const dir = mkdtempSync(join(tmpdir(), "cl-"));
    writeFileSync(join(dir, "x.txt"), "uno\ndos\n"); writeFileSync(join(dir, "o.txt"), "# h\n");
    assert.throws(() => execFileSync("node", [SCRIPT, "--input", join(dir, "x.txt"), "--output", join(dir, "o.txt")], { stdio: "pipe" }));
    assert.equal(readFileSync(join(dir, "o.txt"), "utf8"), "# h\n");
  });
});
