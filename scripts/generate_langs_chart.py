#!/usr/bin/env python3
"""
Genera un SVG de "porcentaje de lenguajes" con el mismo estilo visual
que control-herbal-languages-chart.svg, pero calculado en vivo a partir
de la API de lenguajes de GitHub.

Uso:
    python generate_langs_chart.py <owner>/<repo> <salida.svg>

Variables de entorno:
    GITHUB_TOKEN   (opcional, recomendado para evitar rate limits de la API)
"""
import json
import os
import sys
import urllib.request

BASELINE_Y = 330
CHART_TOP_Y = 60          # margen superior disponible para el porcentaje más alto
MAX_BAR_HEIGHT = 250       # altura que tendría una barra al 100%
MIN_BAR_HEIGHT = 6         # para que un lenguaje minoritario siga siendo visible
BAR_WIDTH = 70
LABEL_GAP_MAX = 14
LABEL_GAP_MIN = 6
ICON_SIZE = 32
ICON_GAP = 12               # separación entre la línea base y el icono
CANVAS_WIDTH = 700
CANVAS_HEIGHT = 380
MARGIN_X = 32
MAX_LANGS = 5               # cuántos lenguajes mostrar como máximo
MIN_PCT_TO_SHOW = 0.5       # ocultar ruido (< 0.5%)

# Colores oficiales aproximados (linguist) + icono simplificado (viewBox 24x24).
# Si un lenguaje no está aquí, se dibuja con un color genérico y sin icono.
LANG_STYLE = {
    "Kotlin": {
        "color": "#7F52FF",
        "icon": '<path fill="{c}" d="M24 0H0v24h24L12.04 11.96 24 0zM12.36 1.18h8.81L.91 21.66V13L12.36 1.18z"/>',
    },
    "C++": {
        "color": "#00599C",
        "icon": '<path fill="{c}" d="M22.394 6c-.167-.29-.398-.543-.652-.69L12.926.22c-.509-.294-1.34-.294-1.848 0L2.26 5.31c-.508.293-.923 1.013-.923 1.6v10.18c0 .294.104.62.271.91.167.29.398.543.652.69l8.816 5.09c.508.293 1.34.293 1.848 0l8.816-5.09c.254-.147.485-.4.652-.69.167-.29.27-.616.27-.91V6.91c.003-.294-.1-.62-.268-.91zM12 19.11c-3.92 0-7.109-3.19-7.109-7.11 0-3.92 3.19-7.11 7.11-7.11a7.133 7.133 0 016.156 3.553l-3.076 1.78a3.567 3.567 0 00-3.08-1.78A3.56 3.56 0 008.444 12 3.56 3.56 0 0012 15.555a3.57 3.57 0 003.08-1.778l3.078 1.78A7.135 7.135 0 0112 19.11zm7.11-6.715h-.79v.79h-.79v-.79h-.79v-.79h.79v-.79h.79v.79h.79zm2.962 0h-.79v.79h-.79v-.79h-.79v-.79h.79v-.79h.79v.79h.79z"/>',
    },
    "C": {
        "color": "#555555",
        "icon": '<circle cx="12" cy="12" r="10" fill="none" stroke="{c}" stroke-width="2"/><text x="12" y="16" text-anchor="middle" font-size="11" font-weight="700" fill="{c}">C</text>',
    },
    "Python": {
        "color": "#3776AB",
        "icon": (
            '<path fill="#3776AB" d="M11.727 0a16.43 16.43 0 00-2.834.248l.098-.014C6.568.662 6.129 1.558 6.129 3.21v2.182h5.726v.727H3.981l-.066-.001A3.576 3.576 0 00.408 8.999l-.004.023c-.256.872-.403 1.874-.403 2.91s.147 2.038.422 2.985l-.019-.076c.407 1.695 1.379 2.902 3.04 2.902h1.969v-2.616a3.64 3.64 0 013.574-3.557h5.722a2.885 2.885 0 002.863-2.885v-.026.001-5.452A3.204 3.204 0 0014.724.233L14.71.232a17.319 17.319 0 00-2.879-.234h-.107.005zM8.631 1.755h.017a1.091 1.091 0 11-1.091 1.094v-.008c0-.596.48-1.08 1.074-1.086h.001z"/>'
            '<path fill="#FFD43B" d="M18.287 6.119v2.542a3.672 3.672 0 01-3.572 3.63H8.991A2.922 2.922 0 006.129 15.2v5.453c0 1.551 1.349 2.464 2.862 2.91.855.277 1.839.437 2.86.437s2.005-.16 2.927-.456l-.068.019c1.44-.417 2.862-1.258 2.862-2.91v-2.184h-5.719v-.727h8.582c1.664 0 2.284-1.161 2.863-2.902.28-.87.441-1.871.441-2.91s-.161-2.04-.46-2.979l.019.07c-.411-1.656-1.2-2.902-2.863-2.902zm-3.216 13.807h.017a1.091 1.091 0 11-1.091 1.091v-.011c0-.595.48-1.077 1.074-1.08z"/>'
        ),
    },
    "Java": {
        "color": "#B07219",
        "icon": None,
    },
    "JavaScript": {
        "color": "#F7DF1E",
        "icon": None,
    },
    "TypeScript": {
        "color": "#3178C6",
        "icon": None,
    },
    "Go": {"color": "#00ADD8", "icon": None},
    "Rust": {"color": "#DEA584", "icon": None},
    "Swift": {"color": "#F05138", "icon": None},
    "Ruby": {"color": "#701516", "icon": None},
    "PHP": {"color": "#4F5D95", "icon": None},
    "HTML": {"color": "#E34C26", "icon": None},
    "CSS": {"color": "#563D7C", "icon": None},
    "Shell": {"color": "#89E051", "icon": None},
    "Dockerfile": {"color": "#384D54", "icon": None},
    "Jupyter Notebook": {"color": "#DA5B0B", "icon": None},
    "C#": {"color": "#178600", "icon": None},
}
FALLBACK_COLOR = "#8b949e"


def fetch_languages(repo: str) -> dict:
    url = f"https://api.github.com/repos/{repo}/languages"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode())


def compute_percentages(bytes_by_lang: dict):
    total = sum(bytes_by_lang.values()) or 1
    items = [(lang, b / total * 100) for lang, b in bytes_by_lang.items()]
    items.sort(key=lambda x: x[1], reverse=True)
    items = [i for i in items if i[1] >= MIN_PCT_TO_SHOW][:MAX_LANGS]
    return items


def fallback_icon(letter: str, color: str) -> str:
    return (
        f'<circle cx="12" cy="12" r="11" fill="none" stroke="{color}" stroke-width="2"/>'
        f'<text x="12" y="16" text-anchor="middle" font-family="-apple-system, Segoe UI, Roboto, '
        f'Helvetica, Arial, sans-serif" font-size="11" font-weight="700" fill="{color}">{letter}</text>'
    )


def build_svg(items):
    n = len(items)
    if n == 0:
        items = [("N/A", 0)]
        n = 1

    usable_width = CANVAS_WIDTH - 2 * MARGIN_X
    col_width = usable_width / n

    groups = []
    for i, (lang, pct) in enumerate(items):
        style = LANG_STYLE.get(lang, {})
        color = style.get("color", FALLBACK_COLOR)

        bar_h = max(MIN_BAR_HEIGHT, round((pct / 100) * MAX_BAR_HEIGHT))
        bar_h = min(bar_h, BASELINE_Y - CHART_TOP_Y)
        rect_y = BASELINE_Y - bar_h

        gap = LABEL_GAP_MIN + (LABEL_GAP_MAX - LABEL_GAP_MIN) * min(1.0, bar_h / MAX_BAR_HEIGHT)
        label_y = max(24, rect_y - gap)

        col_center = MARGIN_X + col_width * i + col_width / 2
        rect_x = col_center - BAR_WIDTH / 2
        icon_x = col_center - ICON_SIZE / 2
        icon_y = BASELINE_Y + ICON_GAP

        pct_label = f"{pct:.1f}%".rstrip("0").rstrip(".") + ("%" if not f"{pct:.1f}".endswith("0") else "")
        pct_label = f"{pct:.1f}%" if pct < 10 else f"{pct:.1f}%"

        icon_svg = style.get("icon")
        if icon_svg:
            icon_content = icon_svg.format(c=color)
        else:
            icon_content = fallback_icon(lang[0].upper(), color)

        groups.append(f'''
  <g>
    <text x="{col_center:.1f}" y="{label_y:.1f}" text-anchor="middle" font-family="-apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif" font-size="16" font-weight="600" fill="#f5f5f5">{pct_label}</text>
    <rect x="{rect_x:.1f}" y="{rect_y:.1f}" width="{BAR_WIDTH}" height="{bar_h}" rx="6" fill="{color}"/>
    <svg x="{icon_x:.1f}" y="{icon_y}" width="{ICON_SIZE}" height="{ICON_SIZE}" viewBox="0 0 24 24">
      {icon_content}
    </svg>
  </g>''')

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS_WIDTH} {CANVAS_HEIGHT}" width="{CANVAS_WIDTH}" height="{CANVAS_HEIGHT}">
  <rect x="0.5" y="0.5" width="{CANVAS_WIDTH-1}" height="{CANVAS_HEIGHT-1}" rx="16" fill="#11151c" stroke="#2a2f38" stroke-width="1"/>
  <line x1="{MARGIN_X}" y1="{BASELINE_Y}" x2="{CANVAS_WIDTH-MARGIN_X}" y2="{BASELINE_Y}" stroke="#2a2f38" stroke-width="1"/>
{"".join(groups)}
</svg>
'''
    return svg


def main():
    if len(sys.argv) != 3:
        print("Uso: python generate_langs_chart.py <owner>/<repo> <salida.svg>", file=sys.stderr)
        sys.exit(1)

    repo, out_path = sys.argv[1], sys.argv[2]
    bytes_by_lang = fetch_languages(repo)
    items = compute_percentages(bytes_by_lang)
    svg = build_svg(items)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(svg)

    print(f"Generado {out_path} con {len(items)} lenguaje(s):")
    for lang, pct in items:
        print(f"  {lang}: {pct:.1f}%")


if __name__ == "__main__":
    main()
