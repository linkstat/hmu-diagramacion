"""Dibuja las marcas de pata de gallo en un SVG de Graphviz con conectores sin flechas.

uso: python3 agregar_marcas.py figura.dot entrada.svg salida.svg

Graphviz 2.42 en modo ortho cambia de extremo las flechas de las aristas que invierte
internamente; por eso der.py --orto genera las aristas con dir=none y este script
dibuja cada marca (arrowtail en el extremo de la tabla origen, arrowhead en el de la
tabla referenciada) según lo declarado en el .dot.
"""
import math
import re
import sys

DOT, SRC, OUT = sys.argv[1:4]
COLOR, ANCHO = '#5c6773', 1.1
num = r'-?\d+(?:\.\d+)?'


def pts(t):
    return [(float(a), float(b)) for a, b in re.findall(rf'({num}),({num})', t)]


# Aristas del .dot en orden de declaración (Graphviz las numera igual: edge1, edge2...).
decl = [(m.group(1), m.group(2), m.group(3), m.group(4)) for m in re.finditer(
    r'^  (\w+)(?::\w+)? -> (\w+) \[dir=\w+, arrowtail=(\w+), arrowhead=(\w+)\]', open(DOT).read(), re.M)]

s = open(SRC, encoding='utf-8').read()
cajas = {}
for m in re.finditer(r'<g id="node\d+" class="node">\s*<title>(.*?)</title>(.*?)</g>', s, re.S):
    Q = [p for t in re.findall(r'points="([^"]+)"', m.group(2)) for p in pts(t)]
    if Q:
        cajas[m.group(1)] = (min(x for x, _ in Q), min(y for _, y in Q), max(x for x, _ in Q), max(y for _, y in Q))


def dist_caja(p, c):
    dx = max(c[0] - p[0], 0, p[0] - c[2])
    dy = max(c[1] - p[1], 0, p[1] - c[3])
    return math.hypot(dx, dy)


def atraviesa(V, c, m=2.0):
    """Algún tramo intermedio del conector pasa por dentro de la caja c."""
    for a, b in list(zip(V, V[1:]))[1:-1]:
        lo = (min(a[0], b[0]), min(a[1], b[1])); hi = (max(a[0], b[0]), max(a[1], b[1]))
        if lo[0] < c[2] - m and hi[0] > c[0] + m and lo[1] < c[3] - m and hi[1] > c[1] + m:
            return True
    return False


def linea(a, b):
    return (f'<path fill="none" stroke="{COLOR}" stroke-width="{ANCHO}" '
            f'd="M{a[0]:.2f},{a[1]:.2f}L{b[0]:.2f},{b[1]:.2f}"/>')


def marcas(nombre, e, u):
    """Elementos SVG de la marca en el extremo e; u = dirección unitaria hacia la tabla."""
    n = (-u[1], u[0])
    at = lambda d, k=0: (e[0] - u[0] * d + n[0] * k, e[1] - u[1] * d + n[1] * k)
    el = []
    resto = nombre
    if resto.startswith('crow'):      # pata de gallo: muchos (los dedos tocan la tabla)
        b = at(11)
        el += [linea(b, at(0, 5.5)), linea(b, at(0)), linea(b, at(0, -5.5))]
        return el
    d = 5.0
    for parte in re.findall(r'tee|odot', resto):
        if parte == 'tee':
            el.append(linea(at(d, 5.5), at(d, -5.5)))
            d += 4.0
        else:
            c = at(d + 3.5)
            el.append(f'<ellipse fill="white" stroke="{COLOR}" stroke-width="{ANCHO}" '
                      f'cx="{c[0]:.2f}" cy="{c[1]:.2f}" rx="3.5" ry="3.5"/>')
            d += 7.0
    return el


# Por par de tablas, en orden de declaración (el SVG puede venir de un .dot reordenado).
pendientes = {}
for tail, head, at_, ah in decl:
    pendientes.setdefault((tail, head), []).append((at_, ah))
aristas = list(re.finditer(r'(<g id="edge(\d+)" class="edge">)(.*?)(</g>)', s, re.S))
assert len(aristas) == len(decl), (len(aristas), len(decl))
salida, ultimo = [], 0
for m in aristas:
    titulo = re.search(r'<title>(.*?)</title>', m.group(3)).group(1).replace('&#45;&gt;', '->')
    tail, head = titulo.split('->')
    at_, ah = pendientes[(tail, head)].pop(0)
    cuerpo = m.group(3)
    P = pts(re.search(r' d="([^"]+)"', cuerpo).group(1))
    V = [P[0]] + P[3::3]
    V = [p for i, p in enumerate(V) if i == 0 or p != V[i - 1]]
    if tail == head and atraviesa(V, cajas[tail]):
        # Autorrelación que Graphviz (ortho) traza sobre la propia tabla: bucle por la derecha.
        c = cajas[tail]
        y1, y2 = c[1] + (c[3] - c[1]) * 0.30, c[1] + (c[3] - c[1]) * 0.55
        V = [(c[2], y1), (c[2] + 32, y1), (c[2] + 32, y2), (c[2], y2)]
        d = 'M%.2f,%.2f' % V[0] + ''.join('L%.2f,%.2f' % p for p in V[1:])
        cuerpo = re.sub(r' d="[^"]+"', f' d="{d}"', cuerpo, count=1)
    ini, fin = V[0], V[-1]
    # El extremo que toca la tabla origen lleva arrowtail; el otro, arrowhead.
    if dist_caja(ini, cajas[tail]) + dist_caja(fin, cajas[head]) > dist_caja(fin, cajas[tail]) + dist_caja(ini, cajas[head]):
        ini, fin, V = fin, ini, V[::-1]

    def unit(a, b):
        L = math.hypot(b[0] - a[0], b[1] - a[1]) or 1
        return ((b[0] - a[0]) / L, (b[1] - a[1]) / L)

    extra = marcas(at_, ini, unit(V[1], V[0])) + marcas(ah, fin, unit(V[-2], V[-1]))
    salida.append(s[ultimo:m.start(3)])
    salida.append(cuerpo + ''.join(extra))
    ultimo = m.start(4)
salida.append(s[ultimo:])
open(OUT, 'w', encoding='utf-8').write(''.join(salida))
print(OUT, 'aristas', len(aristas))
