"""Desplaza tablas de un layout de Graphviz y deja que neato -n2 vuelva a trazar los conectores.

uso: python3 ajustar_posiciones.py posicionado.dot ajustes.txt salida.dot
posicionado.dot: salida de `dot -Tdot`. ajustes.txt: una línea por tabla, `tabla dx dy`
en puntos (dx > 0 a la derecha, dy > 0 hacia abajo). Se quitan las rutas de las aristas
para que neato -n2 las calcule con las posiciones nuevas.
"""
import re
import sys

src, ajustes, out = sys.argv[1:4]
delta = {}
for linea in open(ajustes, encoding='utf-8'):
    p = linea.split('#')[0].split()
    if len(p) == 3:
        delta[p[0]] = (float(p[1]), float(p[2]))
s = open(src, encoding='utf-8').read()


# Nodos: `nombre [ ... pos="x,y" ... ];` (en Graphviz y crece hacia arriba).
lineas = []
for bloque in re.split(r'(?<=;)\n', s):
    m = re.match(r'(\s*(\w+)\s*\[)(.*)$', bloque, re.S)
    if m and m.group(2) in delta and ' -> ' not in bloque:
        dx, dy = delta[m.group(2)]
        bloque = m.group(1) + re.sub(r'pos="([-\d.]+),([-\d.]+)"',
                                     lambda q: f'pos="{float(q.group(1)) + dx:.2f},{float(q.group(2)) - dy:.2f}"',
                                     m.group(3), count=1)
    if ' -> ' in bloque:
        bloque = re.sub(r',?\s*pos="[^"]*"', '', bloque)
        bloque = re.sub(r'\[\s*,\s*', '[', bloque)
    lineas.append(bloque)
s = '\n'.join(lineas)
s = re.sub(r'\bbb="[^"]*",?\s*', '', s, count=1)
open(out, 'w', encoding='utf-8').write(s)
print(out, 'tablas movidas:', ', '.join(f'{k} ({v[0]:+g},{v[1]:+g})' for k, v in delta.items()))
