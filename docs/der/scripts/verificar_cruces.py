"""Informa los conectores que pasan por encima de una tabla en un SVG de Graphviz.

uso: python3 verificar_cruces.py figura.svg [margen_pt]
Un tramo cruza una tabla si atraviesa su rectángulo (achicado en `margen` puntos);
para las tablas de los extremos sólo cuentan los tramos que no llegan a ellas.
"""
import re
import sys

svg = open(sys.argv[1], encoding='utf-8').read()
M = float(sys.argv[2]) if len(sys.argv) > 2 else 2.0
num = r'-?\d+(?:\.\d+)?'


def pts(t):
    return [(float(a), float(b)) for a, b in re.findall(rf'({num}),({num})', t)]


cajas = {}
for m in re.finditer(r'<g id="node\d+" class="node">\s*<title>(.*?)</title>(.*?)</g>', svg, re.S):
    Q = [p for t in re.findall(r'points="([^"]+)"', m.group(2)) for p in pts(t)]
    if Q:
        cajas[m.group(1)] = (min(x for x, _ in Q), min(y for _, y in Q), max(x for x, _ in Q), max(y for _, y in Q))
cruces = 0
for m in re.finditer(r'<g id="edge\d+" class="edge">\s*<title>(.*?)</title>(.*?)</g>', svg, re.S):
    tail, head = m.group(1).replace('&#45;&gt;', '->').split('->')
    P = pts(re.search(r' d="([^"]+)"', m.group(2)).group(1))
    V = [P[0]] + P[3::3]
    for j, (a, b) in enumerate(zip(V, V[1:])):
        lo = (min(a[0], b[0]), min(a[1], b[1])); hi = (max(a[0], b[0]), max(a[1], b[1]))
        for n, c in cajas.items():
            if n in (tail, head) and (j == 0 or j == len(V) - 2):
                continue
            if lo[0] < c[2] - M and hi[0] > c[0] + M and lo[1] < c[3] - M and hi[1] > c[1] + M:
                cruces += 1
                print(f'  {tail}->{head} pasa sobre {n}')
print(sys.argv[1].split('/')[-1], 'cruces:', cruces)
