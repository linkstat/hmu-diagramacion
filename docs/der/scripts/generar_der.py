"""Genera el DER de la base aromito (Graphviz) desde el catálogo de PostgreSQL.

uso: python3 der.py <salida.dot> [--con-memos]
Excluye las tablas heredadas sin uso en el E4. Los nombres se muestran con el
casing del DDL completo (sql/aromito_creacion_de_tablas.sql).
"""
import re
import subprocess
import sys

SALIDA = sys.argv[1]
CON_MEMOS = '--con-memos' in sys.argv
ORTO = '--orto' in sys.argv   # conectores rectos a 90°: llegan al borde de la tabla (sin puerto de columna);
# sin flechas: las marcas las dibuja agregar_marcas.py (Graphviz 2.42 las invierte en modo ortho)
PARTE = next((a.split('=', 1)[1] for a in sys.argv if a.startswith('--parte=')), None)

# Partición del DER para el documento (Figuras 20a a 20e, una por página A4 vertical).
PARTES = {
    'a': ['usuario', 'agente', 'rol', 'asignacionrol', 'servicio', 'cargo', 'domicilio'],
    'b': ['diagramadeservicio', 'jornadalaboral', 'feriado', 'reglavalidacion', 'eventoauditoria', 'notificacion'],
    'c': ['lotemarcaciones', 'marcacion', 'jornadaregistrada', 'partediario', 'partediariodetalle'],
    'd': ['codigonovedad', 'tiponovedad', 'novedad', 'novedadautorizacion', 'novedadadjunto', 'movimientohoras'],
    'e': ['horariobase', 'horario', 'horarioconfranquicia', 'horarioestandar', 'horariosemanal',
          'horarionocturno', 'horarioferiante', 'horariodxi', 'horarioguardiamedica',
          'horarioguardiaenfermeria', 'horariojefeservicioguardiapasiva', 'horarioabierto'],
}
# Subtipos de Horario en filas (Figura 20e): cada fila en un mismo rango.
SUBTIPOS = ['horarioestandar', 'horariosemanal', 'horarionocturno', 'horarioferiante', 'horariodxi',
            'horarioguardiamedica', 'horarioguardiaenfermeria', 'horariojefeservicioguardiapasiva', 'horarioabierto']


def opcion(nombre, defecto):
    return next((a.split('=', 1)[1] for a in sys.argv if a.startswith(f'--{nombre}=')), defecto)


RANKDIR = opcion('rankdir', 'LR')
NODESEP = opcion('nodesep', '0.65')
RANKSEP = opcion('ranksep', '1.4')
COLUMNAS = int(opcion('columnas', '0'))   # > 0: subtipos de Horario en grilla de N columnas
PARTE_DE = {t: k for k, ts in PARTES.items() for t in ts}

HEREDADAS = {'autorizacion', 'agente_novedad', 'francocompensatorio', 'horaextra',
             'partediario_agente', 'servicio_jefaturadeservicio'}
MEMOS = {'memorandum', 'memorandum_destinatario', 'memorandum_firmante',
         'memorandum_autorizacion', 'estadotramite'}
EXCLUIR = HEREDADAS | (set() if CON_MEMOS else MEMOS)

MODULOS = [
    ('Padrón, roles y seguridad', '#dbe9f6', '#2f6ea5',
     ['usuario', 'agente', 'rol', 'asignacionrol', 'servicio', 'cargo', 'domicilio']),
    ('Modalidades horarias', '#e6f2e1', '#4a8a3c',
     ['horariobase', 'horario', 'horarioconfranquicia', 'horarioestandar', 'horariosemanal',
      'horarionocturno', 'horarioferiante', 'horariodxi', 'horarioguardiamedica',
      'horarioguardiaenfermeria', 'horariojefeservicioguardiapasiva', 'horarioabierto']),
    ('Diagramación y reglas', '#fdf0d5', '#b07a12',
     ['diagramadeservicio', 'jornadalaboral', 'feriado', 'reglavalidacion']),
    ('Novedades y banco de horas', '#f7e1e1', '#a8443f',
     ['codigonovedad', 'tiponovedad', 'novedad', 'novedadautorizacion', 'novedadadjunto',
      'movimientohoras', 'notificacion']),
    ('Asistencia y parte diario', '#ece3f5', '#6b4a9a',
     ['lotemarcaciones', 'marcacion', 'jornadaregistrada', 'partediario', 'partediariodetalle']),
    ('Auditoría', '#eeeeee', '#555555', ['eventoauditoria']),
    ('Memorándums', '#e0f1f1', '#2f7f7f',
     ['memorandum', 'memorandum_destinatario', 'memorandum_firmante', 'memorandum_autorizacion',
      'estadotramite']),
]
MODULO_DE = {t: (n, f, b) for n, f, b, ts in MODULOS for t in ts}


def sql(q):
    out = subprocess.run(['psql', '-At', '-F', '\t', '-c', q], capture_output=True, text=True, check=True)
    return [l.split('\t') for l in out.stdout.splitlines() if l]


# Casing del DDL completo: minúsculas -> forma declarada.
ddl = open('/home/pub/seminare/sql/aromito_creacion_de_tablas.sql', encoding='utf-8').read()
ddl = re.sub(r'--[^\n]*|/\*.*?\*/', ' ', ddl, flags=re.S)
CASO = {}
ORDEN = {}   # (tabla, columna) -> posición en el CREATE TABLE del DDL (orden lógico, PK primero)
for m in re.finditer(r'CREATE TABLE (\w+)\s*\((.*?)\n\);', ddl, flags=re.S):
    CASO.setdefault(m.group(1).lower(), m.group(1))
    for i, linea in enumerate(m.group(2).splitlines()):
        p = linea.strip().split()
        if p and p[0] not in ('UNIQUE', 'CONSTRAINT', 'PRIMARY', 'FOREIGN', 'CASE', 'ELSE', '(tipo'):
            CASO.setdefault(p[0].lower(), p[0])
            ORDEN[(m.group(1).lower(), p[0].lower())] = i


def caso(x):
    return CASO.get(x, x)


def tipo(t):
    t = (t.replace('character varying', 'varchar').replace('timestamp without time zone', 'timestamp')
         .replace('time without time zone', 'time').replace('public.', ''))
    return t


tablas = [r[0] for r in sql("SELECT c.relname FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace "
                            "AND c.relkind = 'r' ORDER BY 1") if r[0] not in EXCLUIR]
columnas = {}
for t, col, ty, nn in sql("""
        SELECT c.relname, a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull
        FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r' AND a.attnum > 0
          AND NOT a.attisdropped ORDER BY c.relname, a.attnum"""):
    columnas.setdefault(t, []).append((col, tipo(ty), nn == 't'))
claves = {}
for t, tp, cols in sql("""
        SELECT c.relname, k.contype, string_agg(a.attname, ',' ORDER BY u.ord)
        FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid
        CROSS JOIN LATERAL unnest(k.conkey) WITH ORDINALITY AS u(attnum, ord)
        JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = u.attnum
        WHERE k.connamespace = 'public'::regnamespace AND k.contype IN ('p', 'u', 'f')
        GROUP BY c.relname, k.oid, k.contype"""):
    for col in cols.split(','):
        claves.setdefault((t, col), set()).add({'p': 'PK', 'u': 'UQ', 'f': 'FK'}[tp])
fks = sql("""
        SELECT c.relname, a.attname, r.relname, k.confdeltype
        FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid JOIN pg_class r ON r.oid = k.confrelid
        JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = k.conkey[1]
        WHERE k.connamespace = 'public'::regnamespace AND k.contype = 'f' ORDER BY 1, 2""")
# Índices únicos que no son restricción (Usuario.mail, Usuario.tel, Servicio.nombre...): también UQ.
for t, cols in sql("""
        SELECT c.relname, string_agg(a.attname, ',') FROM pg_index i
        JOIN pg_class c ON c.oid = i.indrelid JOIN pg_class x ON x.oid = i.indexrelid
        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
        WHERE c.relnamespace = 'public'::regnamespace AND i.indisunique AND NOT i.indisprimary
          AND i.indpred IS NULL AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = i.indexrelid)
        GROUP BY c.relname, i.indexrelid"""):
    for col in cols.split(','):
        claves.setdefault((t, col), set()).add('UQ')


COMPLETAS = set(PARTES[PARTE]) if PARTE else set(tablas)
REFERENCIAS = set()
if PARTE:
    for t, col, ref, _ in fks:
        if t in EXCLUIR or ref in EXCLUIR:
            continue
        if t in COMPLETAS and ref not in COMPLETAS:
            REFERENCIAS.add(ref)
        if ref in COMPLETAS and t not in COMPLETAS:
            REFERENCIAS.add(t)
    tablas = [t for t in tablas if t in COMPLETAS | REFERENCIAS]


def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


L = ['digraph DER {',
     f'  graph [rankdir={RANKDIR}, splines={"ortho" if ORTO else "true"}, overlap=false, nodesep={NODESEP}, '
     f'ranksep={RANKSEP}, pad=0.4, '
     'fontname="DejaVu Sans", bgcolor="white", newrank=true];',
     '  node [shape=plaintext, margin=0, fontname="DejaVu Sans", fontsize=10];',
     '  edge [color="#5c6773", penwidth=1.1, arrowsize=0.8, fontname="DejaVu Sans"];']

for t in tablas:
    _, fondo, borde = MODULO_DE.get(t, ('', '#ffffff', '#333333'))
    if t in REFERENCIAS:
        # Recuadro de referencia alto: deja lugar para que lleguen varios conectores separados.
        L.append(f'  {t} [label=<<TABLE BORDER="1" COLOR="{borde}" STYLE="DASHED" CELLBORDER="0" CELLSPACING="0" '
                 f'CELLPADDING="8" BGCOLOR="white"><TR><TD><FONT COLOR="{borde}" POINT-SIZE="11"><B>{esc(caso(t))}</B>'
                 f'</FONT></TD></TR><TR><TD><FONT POINT-SIZE="8" COLOR="#6b7280">ver Figura 20{PARTE_DE.get(t, "")}'
                 f'</FONT></TD></TR><TR><TD HEIGHT="10"></TD></TR></TABLE>>];')
        continue
    filas = [f'<TR><TD COLSPAN="3" BGCOLOR="{borde}" ALIGN="CENTER"><FONT COLOR="white" POINT-SIZE="11">'
             f'<B>{esc(caso(t))}</B></FONT></TD></TR>']
    for col, ty, nn in sorted(columnas[t], key=lambda c: ORDEN.get((t, c[0]), 999)):
        k = claves.get((t, col), set())
        marca = ' '.join(x for x in ('PK', 'FK', 'UQ') if x in k) or '&#160;'
        nombre = esc(caso(col))
        nombre = f'<B>{nombre}</B>' if nn else nombre
        color = '#1f2937' if nn else '#6b7280'
        filas.append(f'<TR><TD ALIGN="LEFT" PORT="{col}_i"><FONT POINT-SIZE="8" COLOR="{borde}">{marca}</FONT></TD>'
                     f'<TD ALIGN="LEFT"><FONT COLOR="{color}">{nombre}</FONT></TD>'
                     f'<TD ALIGN="LEFT" PORT="{col}"><FONT POINT-SIZE="9" COLOR="#6b7280">{esc(ty)}</FONT></TD></TR>')
    L.append(f'  {t} [label=<<TABLE BORDER="1" COLOR="{borde}" CELLBORDER="0" CELLSPACING="0" '
             f'CELLPADDING="3" BGCOLOR="{fondo}">' + ''.join(filas) + '</TABLE>>];')

# Agrupar por módulo (subgrafos sin borde para acercar las tablas relacionadas).
if not PARTE:
    for i, (nombre, _, _, ts) in enumerate(MODULOS):
        presentes = [t for t in ts if t in tablas]
        if presentes:
            L.append(f'  subgraph cluster_{i} {{ style=invis; ' + '; '.join(presentes) + '; }')
# Grilla de subtipos: filas de N tablas en un mismo rango, encadenadas con aristas invisibles
# (Graphviz no las incluye en el SVG).
if COLUMNAS:
    sub = [t for t in SUBTIPOS if t in tablas]
    filas = [sub[i:i + COLUMNAS] for i in range(0, len(sub), COLUMNAS)]
    for f in filas:
        L.append('  { rank=same; ' + '; '.join(f) + '; }')
    for f1, f2 in zip(filas, filas[1:]):
        for x, y in zip(f1, f2):
            L.append(f'  {x} -> {y} [style=invis];')

for t, col, ref, _ in fks:
    if t in EXCLUIR or ref in EXCLUIR or t not in tablas or ref not in tablas:
        continue
    if t in REFERENCIAS and ref in REFERENCIAS:
        continue
    nn = next(x[2] for x in columnas[t] if x[0] == col)
    uno_a_uno = col == 'id'      # Class Table Inheritance: id es PK y FK
    lado_hijo = 'tee' if uno_a_uno else 'crow'
    lado_padre = 'teetee' if nn else 'teeodot'
    origen = t if t in REFERENCIAS or ORTO else f'{t}:{col}'
    L.append(f'  {origen} -> {ref} [dir={"none" if ORTO else "both"}, arrowtail={lado_hijo}, arrowhead={lado_padre}];')

# Leyenda
mods = ''.join(f'<TR><TD BGCOLOR="{b}" WIDTH="14"></TD><TD ALIGN="LEFT">{esc(n)}</TD></TR>'
               for n, _, b, ts in MODULOS if any(t in tablas for t in ts))
L.append('  leyenda [label=<<TABLE BORDER="1" COLOR="#9ca3af" CELLBORDER="0" CELLSPACING="2" CELLPADDING="3">'
         '<TR><TD COLSPAN="2"><B>Referencias</B></TD></TR>' + mods +
         '<TR><TD COLSPAN="2" ALIGN="LEFT"><B>negrita</B>: NOT NULL · <FONT COLOR="#6b7280">gris</FONT>: admite nulo</TD></TR>'
         '<TR><TD COLSPAN="2" ALIGN="LEFT">PK clave primaria · FK clave foránea · UQ única&#160;&#160;</TD></TR>'
         '<TR><TD COLSPAN="2" ALIGN="LEFT">pata de gallo: muchos · ‖ uno · o| cero o uno&#160;&#160;</TD></TR>'
         + ('<TR><TD COLSPAN="2" ALIGN="LEFT">borde punteado: tabla de otra parte del diagrama&#160;&#160;&#160;</TD></TR>' if PARTE else '')
         + '</TABLE>>];')
L.append('}')
open(SALIDA, 'w', encoding='utf-8').write('\n'.join(L))
print(f'{len(tablas)} tablas ({len(REFERENCIAS)} de referencia); excluidas: {", ".join(sorted(EXCLUIR))}')
