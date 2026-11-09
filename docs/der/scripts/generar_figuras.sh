#!/bin/bash
# Genera el DER completo y las Figuras 20a-20d de la base aromito con conectores
# rectos y exporta SVG y PNG (200 dpi) en este directorio:
#   1. generar_der.py arma el .dot desde el catálogo; dot calcula las posiciones.
#   2. ajustar_posiciones.py aplica ajustes_<figura>.txt (si existe) y neato -n2
#      vuelve a trazar los conectores con las posiciones finales.
#   3. separar_conectores.py separa los tramos paralelos; agregar_marcas.py dibuja
#      las marcas de pata de gallo; verificar_cruces.py informa conectores sobre tablas.
# Requiere graphviz y librsvg2-bin; conexión a la base por las variables PG*.
set -e
D=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
cd "$D"
gen() {  # gen <nombre> [opciones de generar_der.py]
    local n=$1; shift
    python3 generar_der.py "$T/$n.dot" --orto "$@" > /dev/null
    dot -Tdot "$T/$n.dot" -o "$T/$n.pos.dot"
    local aj="$D/ajustes_$n.txt"; [ -f "$aj" ] || aj=/dev/null
    python3 ajustar_posiciones.py "$T/$n.pos.dot" "$aj" "$T/$n.aj.dot" > /dev/null
    neato -n2 -Tsvg "$T/$n.aj.dot" -o "$T/$n.crudo.svg"
    python3 separar_conectores.py "$T/$n.crudo.svg" "$T/$n.separado.svg" 12 > /dev/null
    python3 agregar_marcas.py "$T/$n.dot" "$T/$n.separado.svg" "$n.svg" > /dev/null
    rsvg-convert -d 200 -p 200 -b white "$n.svg" -o "$n.png"
    echo "$n.png $(identify -format '%wx%h' "$n.png") · $(python3 verificar_cruces.py "$n.svg" | tail -1 | sed 's/.*cruces/cruces/')"
}
gen DER_aromito
# Particiones para A4 vertical (área útil ~453 × 600 pt): relación ancho/alto entre
# 0,7 y 1,0, la mayor escala posible y sin conectores sobre tablas.
gen Figura20a_DER --parte=a --rankdir=LR --nodesep=0.45 --ranksep=0.3
gen Figura20b_DER --parte=b --rankdir=LR --nodesep=0.45 --ranksep=0.3
gen Figura20c_DER --parte=c --rankdir=LR --nodesep=0.6 --ranksep=0.3
gen Figura20d_DER --parte=d --rankdir=LR --nodesep=0.6 --ranksep=0.3
gen Figura20e_DER --parte=e --rankdir=TB --nodesep=0.2 --ranksep=0.5 --columnas=2
for f in Figura20?_DER.svg; do
    python3 -c "import re,sys; W,H=map(float,re.search(r'<svg width=\"([\d.]+)pt\" height=\"([\d.]+)pt\"',open(sys.argv[1]).read()).groups()); print(f'{sys.argv[1][:9]}: {W:.0f} x {H:.0f} pt, ancho/alto {W/H:.2f}, escala en 453 x 600: {min(453/W,600/H):.3f}')" "$f"
done
