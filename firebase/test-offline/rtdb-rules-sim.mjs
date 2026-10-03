// Evaluador LOCAL y simplificado de reglas de Realtime Database.
// Sirve para validar la LÓGICA de database.rules.json sin descargar el emulador (p. ej. en
// entornos sin red). NO sustituye a los tests contra el emulador oficial (`npm test`):
// implementa solo el subconjunto de semántica que usan estas reglas.
//   - .read/.write con cascada desde la raíz; .validate sin cascada, sobre el subárbol escrito
//     y sobre sus ancestros (con el valor resultante); comodines $x; now; auth.uid;
//     data/newData/root.child().val()/exists()/hasChildren()/isNumber()/isString().
export const SERVER_TS = { ".sv": "timestamp" };

const clone = (v) => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
const isObj = (v) => v !== null && typeof v === "object";

class Snap {
  constructor(value) { this.v = value === undefined ? null : value; }
  val() { return isObj(this.v) ? clone(this.v) : this.v; }
  exists() { return this.v !== null; }
  child(path) {
    let cur = this.v;
    for (const k of String(path).split("/").filter(Boolean)) {
      cur = isObj(cur) && k in cur ? cur[k] : null;
    }
    return new Snap(cur);
  }
  hasChildren(keys) {
    if (!isObj(this.v)) return false;
    return keys ? keys.every((k) => k in this.v) : Object.keys(this.v).length > 0;
  }
  isNumber() { return typeof this.v === "number"; }
  isString() { return typeof this.v === "string"; }
  isBoolean() { return typeof this.v === "boolean"; }
}

function evalRule(expr, ctx) {
  if (typeof expr === "boolean") return expr;
  const names = Object.keys(ctx);
  // eslint-disable-next-line no-new-func
  const fn = new Function(...names, `"use strict"; return (${expr});`);
  try { return fn(...names.map((n) => ctx[n])) === true; } catch { return false; }
}

// Busca el nodo de reglas hijo para la clave k: literal primero, luego el comodín $x.
function ruleChild(node, k) {
  if (!node) return { node: null, vars: {} };
  if (k in node && !k.startsWith(".") && !k.startsWith("$")) return { node: node[k], vars: {} };
  const wild = Object.keys(node).find((x) => x.startsWith("$"));
  return wild ? { node: node[wild], vars: { [wild]: k } } : { node: null, vars: {} };
}

export class Sim {
  constructor(rules) {
    this.rules = rules.rules;
    this.db = null;
    this.now = 1_700_000_000_000;
  }
  advance(ms) { this.now += ms; }
  seed(path, value) { this.db = this.#set(this.db, split(path), clone(value)); }

  #set(tree, keys, value) {
    if (keys.length === 0) return value;
    const t = isObj(tree) ? clone(tree) : {};
    const [k, ...rest] = keys;
    const next = this.#set(t[k], rest, value);
    if (next === null || next === undefined) delete t[k]; else t[k] = next;
    return Object.keys(t).length ? t : null;
  }

  #resolve(v) {
    if (isObj(v)) {
      if (v[".sv"] === "timestamp") return this.now;
      const o = {};
      for (const [k, x] of Object.entries(v)) o[k] = this.#resolve(x);
      return o;
    }
    return v;
  }

  #ctx(auth, path, oldRoot, newRoot, vars) {
    const at = (root) => new Snap(root).child(path.join("/"));
    return { auth, now: this.now, root: new Snap(oldRoot), data: at(oldRoot), newData: at(newRoot), ...vars };
  }

  read(auth, pathStr) {
    const keys = split(pathStr);
    let node = this.rules, vars = {};
    const check = (n, path, v) =>
      n && ".read" in n && evalRule(n[".read"], this.#ctx(auth, path, this.db, this.db, v));
    if (check(node, [], vars)) return true;
    for (let i = 0; i < keys.length; i++) {
      const r = ruleChild(node, keys[i]);
      node = r.node; vars = { ...vars, ...r.vars };
      if (check(node, keys.slice(0, i + 1), vars)) return true;
      if (!node) return false;
    }
    return false;
  }

  /** mode "set" reemplaza el nodo; "update" mezcla las claves hijas (como updateNode). */
  write(auth, pathStr, value, mode = "set") {
    const base = split(pathStr);
    const resolved = this.#resolve(clone(value));
    const ops = mode === "update"
      ? Object.entries(resolved).map(([k, v]) => [[...base, ...split(k)], v])
      : [[base, resolved]];
    let work = this.db, ok = true;
    const oldRoot = this.db;
    for (const [keys, v] of ops) {
      const newRoot = this.#set(work, keys, v);
      if (!this.#authorized(auth, keys, oldRoot, newRoot)) { ok = false; break; }
      if (!this.#valid(auth, keys, v, oldRoot, newRoot)) { ok = false; break; }
      work = newRoot;
    }
    if (ok) this.db = work;
    return ok;
  }

  #authorized(auth, keys, oldRoot, newRoot) {
    let node = this.rules, vars = {};
    const test = (n, path, v) =>
      n && ".write" in n && evalRule(n[".write"], this.#ctx(auth, path, oldRoot, newRoot, v));
    if (test(node, [], vars)) return true;
    for (let i = 0; i < keys.length; i++) {
      const r = ruleChild(node, keys[i]);
      node = r.node; vars = { ...vars, ...r.vars };
      if (test(node, keys.slice(0, i + 1), vars)) return true;
      if (!node) return false;
    }
    return false;
  }

  #valid(auth, keys, written, oldRoot, newRoot) {
    // 1) ancestros del nodo escrito (con el valor resultante)
    let node = this.rules, vars = {};
    const check = (n, path, v) =>
      !n || !(".validate" in n) || evalRule(n[".validate"], this.#ctx(auth, path, oldRoot, newRoot, v));
    if (!check(node, [], vars)) return false;
    for (let i = 0; i < keys.length; i++) {
      const r = ruleChild(node, keys[i]);
      node = r.node; vars = { ...vars, ...r.vars };
      if (i < keys.length - 1 && !check(node, keys.slice(0, i + 1), vars)) return false;
    }
    // 2) el propio nodo y todo su subárbol nuevo (los borrados no se validan)
    const walk = (n, path, v, val) => {
      if (val === null || val === undefined) return true;
      if (!check(n, path, v)) return false;
      if (isObj(val)) {
        for (const [k, child] of Object.entries(val)) {
          const r = ruleChild(n, k);
          if (!walk(r.node, [...path, k], { ...v, ...r.vars }, child)) return false;
        }
      }
      return true;
    };
    return walk(node, keys, vars, written);
  }
}

function split(p) { return String(p).split("/").filter(Boolean); }
