// Conversión de una lista de contraseñas filtradas (una por línea) a "núcleos" para assets/common_passwords.txt.
// Misma normalización que usa la política al comprobar (passwordPolicy.cores), de modo que "P@ssw0rd" y
// "password" acaben comparándose igual.
import { cores } from "./passwordPolicy.mjs";

export const MIN_CORE_LENGTH = 4;
const SOURCE_MARK = "# fuente:";

export function coresFromList(text, minLen = MIN_CORE_LENGTH) {
  const out = new Set();
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    for (const c of cores(line)) if (c.length >= minLen) out.add(c);
  }
  return out;
}

/** Une los núcleos nuevos con el contenido actual del asset, conservando la cabecera. Idempotente. */
export function mergeAsset(existingText, newCores, sourceNote) {
  const lines = existingText.split(/\r?\n/);
  const header = lines.filter((l) => l.startsWith("#") && !l.startsWith(SOURCE_MARK));
  const merged = new Set(lines.map((l) => l.trim()).filter((l) => l && !l.startsWith("#")));
  for (const c of newCores) merged.add(c);
  return [...header, `${SOURCE_MARK} ${sourceNote}`, ...[...merged].sort()].join("\n") + "\n";
}
