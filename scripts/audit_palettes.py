#!/usr/bin/env python3
"""Auditoría de paletas de color de estado (accesibilidad visual).

Lee las paletas declaradas en StatusPalette.kt y comprueba, para cada una:
  * contraste WCAG de cada color contra los fondos de la app (texto normal: >= 4.5:1; alto contraste: >= 7:1)
  * que los estados ok / warn / critical sigan siendo distinguibles entre sí bajo la condición visual
    para la que se diseñó la paleta, simulándola (Machado, Oliveira y Fernandes, 2009; severidad 1.0)
    y midiendo la distancia perceptual CIEDE2000 (umbral por defecto: 20).

Uso:
  python scripts/audit_palettes.py            # verifica (código de salida != 0 si falla)
  python scripts/audit_palettes.py --search   # propone combinaciones que cumplen los umbrales
Las simulaciones son aproximaciones; no sustituyen pruebas con personas usuarias.
"""
import itertools
import math
import re
import sys
from pathlib import Path

KOTLIN = Path(__file__).resolve().parent.parent / "app/src/main/java/com/example/controlherbal/common/StatusPalette.kt"
CHART_MIN_DELTA = 15.0
BACKGROUNDS = {"blanco": "FFFFFF", "gris claro": "F5F5F5"}

# Matrices en RGB lineal (Machado et al. 2009, severidad 1.0)
CVD = {
    "protanopia":   [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
    "deuteranopia": [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
    "tritanopia":   [[1.255528, -0.076749, -0.178779], [-0.078411, 0.930809, 0.147602], [0.004733, 0.691367, 0.303900]],
}
# Condiciones que cada paleta debe soportar y contraste mínimo requerido
# min_delta: distancia CIEDE2000 mínima entre estados. Es más baja en STANDARD (naranja frente a rojo
# es difícil de separar incluso con visión normal) y en ALTO CONTRASTE (con >= 7:1 sobre blanco todos los
# colores son oscuros). En esos casos el estado se refuerza SIEMPRE con icono y texto (ver StatusLevel).
REQUIREMENTS = {
    "STANDARD":      {"conditions": [], "contrast": 4.5, "min_delta": 12.0},
    "RED_GREEN":     {"conditions": ["protanopia", "deuteranopia"], "contrast": 4.5, "min_delta": 20.0},
    "BLUE_YELLOW":   {"conditions": ["tritanopia"], "contrast": 4.5, "min_delta": 15.0},
    "HIGH_CONTRAST": {"conditions": ["protanopia", "deuteranopia", "tritanopia"], "contrast": 7.0, "min_delta": 6.5},
}


def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))

def lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def delin(c):
    c = min(1.0, max(0.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055

def luminance(h):
    r, g, b = (lin(x) for x in hex_rgb(h))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

def simulate(h, kind):
    m = CVD[kind]
    r, g, b = (lin(x) for x in hex_rgb(h))
    return tuple(delin(sum(m[i][j] * v for j, v in enumerate((r, g, b)))) for i in range(3))

def to_lab(rgb):
    r, g, b = (lin(x) for x in rgb)
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b)
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    f = lambda t: t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116
    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)

def de2000(l1, l2):
    L1, a1, b1 = l1; L2, a2, b2 = l2
    C1, C2 = math.hypot(a1, b1), math.hypot(a2, b2)
    Cb = (C1 + C2) / 2
    G = 0.5 * (1 - math.sqrt(Cb ** 7 / (Cb ** 7 + 25 ** 7)))
    a1p, a2p = (1 + G) * a1, (1 + G) * a2
    C1p, C2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360
    h2p = math.degrees(math.atan2(b2, a2p)) % 360
    dLp, dCp = L2 - L1, C2p - C1p
    if C1p * C2p == 0:
        dhp = 0
    else:
        dhp = h2p - h1p
        dhp -= 360 if dhp > 180 else (-360 if dhp < -180 else 0)
    dHp = 2 * math.sqrt(C1p * C2p) * math.sin(math.radians(dhp / 2))
    Lbp, Cbp = (L1 + L2) / 2, (C1p + C2p) / 2
    if C1p * C2p == 0:
        hbp = h1p + h2p
    elif abs(h1p - h2p) <= 180:
        hbp = (h1p + h2p) / 2
    else:
        hbp = (h1p + h2p + 360) / 2 if h1p + h2p < 360 else (h1p + h2p - 360) / 2
    T = (1 - 0.17 * math.cos(math.radians(hbp - 30)) + 0.24 * math.cos(math.radians(2 * hbp))
         + 0.32 * math.cos(math.radians(3 * hbp + 6)) - 0.20 * math.cos(math.radians(4 * hbp - 63)))
    dth = 30 * math.exp(-(((hbp - 275) / 25) ** 2))
    Rc = 2 * math.sqrt(Cbp ** 7 / (Cbp ** 7 + 25 ** 7))
    Sl = 1 + 0.015 * (Lbp - 50) ** 2 / math.sqrt(20 + (Lbp - 50) ** 2)
    Sc, Sh = 1 + 0.045 * Cbp, 1 + 0.015 * Cbp * T
    Rt = -math.sin(math.radians(2 * dth)) * Rc
    return math.sqrt((dLp / Sl) ** 2 + (dCp / Sc) ** 2 + (dHp / Sh) ** 2 + Rt * (dCp / Sc) * (dHp / Sh))

def delta(h1, h2, kind=None):
    c1 = hex_rgb(h1) if kind is None else simulate(h1, kind)
    c2 = hex_rgb(h2) if kind is None else simulate(h2, kind)
    return de2000(to_lab(c1), to_lab(c2))

def parse_palettes():
    src = KOTLIN.read_text(encoding="utf-8")
    pat = re.compile(r"PaletteMode\.(\w+)\s+to\s+StatusColors\(\s*ok\s*=\s*0xFF([0-9A-Fa-f]{6})\.toInt\(\),\s*"
                     r"warn\s*=\s*0xFF([0-9A-Fa-f]{6})\.toInt\(\),\s*critical\s*=\s*0xFF([0-9A-Fa-f]{6})\.toInt\(\),\s*"
                     r"info\s*=\s*0xFF([0-9A-Fa-f]{6})\.toInt\(\)")
    return {m.group(1): dict(ok=m.group(2).upper(), warn=m.group(3).upper(), critical=m.group(4).upper(),
                             info=m.group(5).upper())
            for m in pat.finditer(src)}

def verify_chart_colors():
    src = KOTLIN.read_text(encoding="utf-8")
    block = re.search(r"object ChartColors \{(.*?)\n\}", src, re.S)
    if not block:
        print("No se encontró ChartColors en StatusPalette.kt")
        return 1
    colors = {m.group(1): m.group(2).upper()
              for m in re.finditer(r"const val (\w+) = 0xFF([0-9A-Fa-f]{6})\.toInt\(\)", block.group(1))}
    failures = 0
    print("\n[ChartColors]", ", ".join(f"{k}=#{v}" for k, v in colors.items()))
    for k, v in colors.items():
        ratio = contrast(v, "FFFFFF")
        good = ratio >= 3.0
        failures += not good
        print(f"  contraste {k:5s} sobre blanco: {ratio:5.2f}:1 {'OK' if good else 'FALLA (mín. 3.0 para gráficos)'}")
    worst = min((delta(colors[a], colors[b], kind), a, b, kind or "visión normal")
                for kind in [None, *CVD] for a, b in itertools.combinations(colors, 2))
    good = worst[0] >= CHART_MIN_DELTA
    failures += not good
    print(f"  peor par: {worst[1]}/{worst[2]} ({worst[3]}) ΔE2000={worst[0]:.1f} {'OK' if good else 'FALLA (mín. %.0f)' % CHART_MIN_DELTA}")
    return failures

def verify():
    palettes = parse_palettes()
    failures = 0
    missing = set(REQUIREMENTS) - set(palettes)
    if missing:
        print("FALTAN paletas en StatusPalette.kt:", ", ".join(sorted(missing)))
        return 1
    for name, req in REQUIREMENTS.items():
        p = palettes[name]
        print(f"\n[{name}] ok=#{p['ok']} warn=#{p['warn']} critical=#{p['critical']} info=#{p['info']}")
        for state, hx in p.items():
            for bg_name, bg in BACKGROUNDS.items():
                ratio = contrast(hx, bg)
                good = ratio >= req["contrast"]
                failures += not good
                print(f"  contraste {state:8s} sobre {bg_name:10s}: {ratio:5.2f}:1 {'OK' if good else 'FALLA (mín. %.1f)' % req['contrast']}")
        for kind in [None] + req["conditions"]:
            for a, b in itertools.combinations(("ok", "warn", "critical"), 2):
                d = delta(p[a], p[b], kind)
                good = d >= req["min_delta"]
                failures += not good
                label = kind or "visión normal"
                print(f"  ΔE2000 {a}/{b} ({label}): {d:5.1f} {'OK' if good else 'FALLA (mín. %.1f)' % req['min_delta']}")
    failures += verify_chart_colors()
    print("\nRESULTADO:", "FALLA" if failures else "OK", f"({failures} incumplimientos)")
    return 1 if failures else 0

POOLS = {
    "ok":       ["0072B2", "005F99", "00796B", "1B7F3B", "2E7D32", "1565C0", "0B6E4F", "00695C", "1B5E20", "0D47A1", "006064"],
    "warn":     ["B34700", "B26A00", "A65E00", "8A5100", "9C4A00", "7A4B00", "B8860B", "8D6E00", "AD5A00", "6A1B9A", "7B1FA2"],
    "critical": ["B00020", "C62828", "A4262C", "B71C1C", "D81B60", "AD1457", "8E0000", "C2185B", "D84315", "BF360C", "A10000"],
}

def search():
    for name, req in REQUIREMENTS.items():
        best = None
        for ok, warn, crit in itertools.product(POOLS["ok"], POOLS["warn"], POOLS["critical"]):
            trio = {"ok": ok, "warn": warn, "critical": crit}
            if any(contrast(h, bg) < req["contrast"] for h in trio.values() for bg in BACKGROUNDS.values()):
                continue
            score = min(delta(trio[a], trio[b], k) for k in [None] + req["conditions"]
                        for a, b in itertools.combinations(trio, 2))
            if best is None or score > best[0]:
                best = (score, trio)
        print(name, "→", f"{best[0]:.1f}" if best else "sin solución", best[1] if best else "")

if __name__ == "__main__":
    if "--search" in sys.argv:
        search()
    else:
        sys.exit(verify())
