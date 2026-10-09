#!/usr/bin/env python3
"""Auditoría estática de accesibilidad de los layouts XML de Android.

Comprueba (WCAG 2.2 / guías de accesibilidad de Android):
  1. Imágenes y botones de icono con android:contentDescription (o marcados como decorativos).
  2. Tamaños de texto en sp (respetan la preferencia de tamaño del sistema) y de al menos 12sp.
  3. Objetivos táctiles >= 48dp en elementos interactivos con tamaño fijo.
  4. Contraste del color de texto declarado: >= 4.5:1 (>= 3:1 si el texto es grande), medido contra el
     fondo propio del elemento si lo declara, o contra blanco y gris claro (#F5F5F5) si no.
  5. Campos de texto con etiqueta (hint) o enlazados a un TextInputLayout.
Es una comprobación estática y no sustituye a pruebas con TalkBack y personas usuarias.

Uso: python scripts/audit_a11y.py [--warn-only]
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"
LAYOUT_DIRS = ["app/src/main/res/layout", "wear/src/main/res/layout"]
LIGHT_BACKGROUNDS = ["FFFFFF", "F5F5F5"]

IMAGE_TAGS = {"ImageView", "ImageButton", "com.google.android.material.floatingactionbutton.FloatingActionButton"}
INTERACTIVE = {"Button", "ImageButton", "CheckBox", "RadioButton", "Switch", "ToggleButton",
               "com.google.android.material.button.MaterialButton",
               "com.google.android.material.switchmaterial.SwitchMaterial",
               "com.google.android.material.floatingactionbutton.FloatingActionButton"}
EDITABLE = {"EditText", "com.google.android.material.textfield.TextInputEditText", "AutoCompleteTextView"}


def luminance(h):
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)

def parse_color(v):
    """Devuelve RRGGBB si es #RRGGBB / #AARRGGBB opaco; None en cualquier otro caso."""
    if not v or not v.startswith("#"):
        return None
    h = v[1:]
    if len(h) == 6:
        return h.upper()
    if len(h) == 8 and h[:2].upper() == "FF":
        return h[2:].upper()
    return None

def dp(v):
    m = re.fullmatch(r"(\d+(?:\.\d+)?)dp", v or "")
    return float(m.group(1)) if m else None

def audit_file(path):
    issues = []
    tree = ET.parse(path)
    parents = {c: p for p in tree.iter() for c in p}

    def inherited_background(el):
        """Primer fondo opaco declarado en el propio elemento o en sus ancestros."""
        node = el
        while node is not None:
            bg = parse_color(node.get(ANDROID + "background")) or parse_color(node.get(ANDROID + "backgroundTint"))
            if bg:
                return bg
            node = parents.get(node)
        return None

    def inside_text_input_layout(el):
        p = parents.get(el)
        while p is not None:
            if p.tag.endswith("TextInputLayout"):
                return p
            p = parents.get(p)
        return None

    for el in tree.iter():
        tag = el.tag
        name = el.get(ANDROID + "id", "").replace("@+id/", "") or "(sin id)"
        where = f"{path.relative_to(ROOT)} <{tag.split('.')[-1]}> {name}"
        if tag in IMAGE_TAGS:
            if el.get(ANDROID + "contentDescription") is None and el.get(ANDROID + "importantForAccessibility") != "no":
                issues.append((where, "sin contentDescription (añádela o marca importantForAccessibility=\"no\" si es decorativa)"))
        ts = el.get(ANDROID + "textSize")
        if ts and not ts.endswith("sp"):
            issues.append((where, f"textSize '{ts}' no está en sp"))
        elif ts and float(ts[:-2]) < 12:
            issues.append((where, f"textSize {ts} es menor al mínimo recomendado de 12sp"))
        if tag in INTERACTIVE:
            w, h = dp(el.get(ANDROID + "layout_width")), dp(el.get(ANDROID + "layout_height"))
            minw, minh = dp(el.get(ANDROID + "minWidth")) or 0, dp(el.get(ANDROID + "minHeight")) or 0
            # 0dp = tamaño por peso de layout (no es un tamaño fijo)
            if (w and max(w, minw) < 48) or (h and max(h, minh) < 48):
                issues.append((where, f"objetivo táctil menor a 48dp ({w}x{h}dp)"))
        if tag in EDITABLE:
            til = inside_text_input_layout(el)
            has_label = el.get(ANDROID + "hint") or el.get(ANDROID + "labelFor") or (til is not None and til.get(ANDROID + "hint"))
            if not has_label:
                issues.append((where, "campo de texto sin hint/etiqueta"))
        tc = parse_color(el.get(ANDROID + "textColor"))
        if tc:
            size = el.get(ANDROID + "textSize", "")
            sz = float(size[:-2]) if size.endswith("sp") and size[:-2].replace(".", "").isdigit() else 14.0
            bold = "bold" in (el.get(ANDROID + "textStyle") or "")
            large = sz >= 24 or (sz >= 18.66 and bold)
            need = 3.0 if large else 4.5
            own_bg = inherited_background(el)
            bgs = [own_bg] if own_bg else LIGHT_BACKGROUNDS
            worst = min(contrast(tc, bg) for bg in bgs)
            if worst < need:
                issues.append((where, f"contraste de texto #{tc} = {worst:.2f}:1 (mín. {need}:1)"))
    return issues

def main():
    all_issues = []
    for d in LAYOUT_DIRS:
        for f in sorted((ROOT / d).glob("*.xml")):
            try:
                all_issues += audit_file(f)
            except ET.ParseError as e:
                all_issues.append((str(f), f"XML inválido: {e}"))
    for where, msg in all_issues:
        print(f"- {where}: {msg}")
    print(f"\nProblemas de accesibilidad: {len(all_issues)}")
    sys.exit(0 if (not all_issues or "--warn-only" in sys.argv) else 1)

if __name__ == "__main__":
    main()
