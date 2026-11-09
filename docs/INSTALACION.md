# Instalación y puesta en marcha

Guía completa para evaluar el prototipo. El camino rápido en Windows está en
el [README](../README.md#-probar-en-5-minutos-windows); esta página cubre la
instalación paso a paso, GNU/Linux y la ejecución desde el código fuente.

## Requisitos

| Software | Versión | Notas |
| --- | --- | --- |
| JDK | 25 | [Eclipse Temurin](https://adoptium.net/); en Windows, marcar «Set JAVA_HOME» y «Add to PATH» |
| PostgreSQL | 18 | puerto 5432; anotar la contraseña del usuario `postgres` |
| Apache Maven | 3.9+ | solo para compilar o ejecutar desde el código fuente |

En Windows conviene agregar `C:\Program Files\PostgreSQL\18\bin` al `PATH`
(los scripts `.cmd` lo intentan automáticamente).

## A. Desde la versión compilada (recomendado)

1. Descargar el zip de la plataforma desde la
   [última release](https://github.com/linkstat/hmu-diagramacion/releases/latest)
   y descomprimirlo: contiene `hmu-diagramacion.jar`, la carpeta `sql/` y los
   scripts de puesta en marcha.
2. **Instalar la base**: ejecutar `instalar-bd.cmd` (Windows) o
   `./instalar-bd.sh` (GNU/Linux). Pide la contraseña del usuario `postgres`
   una sola vez y luego:
   - crea el rol `hmu_diagramacion` (contraseña `hmu_diagramacion`) y la base
     `hmu_diagramacion`, solo si no existen;
   - ejecuta en orden los scripts de `sql/` con `PGCLIENTENCODING=UTF8` y
     `ON_ERROR_STOP=1`.
3. **Iniciar la demostración**: `iniciar-demo.cmd` o `./iniciar-demo.sh`
   (equivalen a `java --enable-native-access=ALL-UNNAMED -Dhmu.hoy=2026-10-07
   -jar hmu-diagramacion.jar`).

El escenario de demostración transcurre el **miércoles 07/10/2026**; la fecha
se fija con `-Dhmu.hoy` para que las pantallas coincidan con el documento.
Los usuarios de prueba figuran en el [README](../README.md#-probar-en-5-minutos-windows).

## B. Desde el código fuente

```bash
git clone https://github.com/linkstat/hmu-diagramacion.git
cd hmu-diagramacion
```

### 1. Crear la base

Con los scripts de `distribucion/` (igual que en el camino A):

```bash
cp distribucion/instalar-bd.sh .   # espera encontrar sql/ en la carpeta actual
./instalar-bd.sh
```

o a mano:

```sql
CREATE USER hmu_diagramacion WITH PASSWORD 'hmu_diagramacion';
CREATE DATABASE hmu_diagramacion OWNER hmu_diagramacion ENCODING 'UTF8' TEMPLATE template0;
```

| # | Script | Qué hace |
| --- | --- | --- |
| 1 | `sql/01_esquema.sql` | Esquema completo: tablas, tipos ENUM, disparadores de auditoría, vistas de indicadores y catálogos (códigos y tipos de novedad, reglas de validación). |
| 2 | `sql/02_datos_prueba.sql` | Padrón ficticio: domicilios, cargos, servicios, agentes, roles y contraseñas de prueba (Argon2id). |
| 3 | `sql/03_escenario_demo.sql` | Escenario de demostración (servicio Guardia Central, diagramas, novedades en todos los estados, saldos de horas). Incluye a `03_escenario_demo_base.sql` y `03_escenario_demo_revertir.sql`, que deben estar en la misma carpeta. Es reejecutable: revierte el escenario anterior y lo vuelve a cargar. |

```bash
export PGCLIENTENCODING=UTF8
psql -h localhost -U hmu_diagramacion -d hmu_diagramacion -v ON_ERROR_STOP=1 -f sql/01_esquema.sql
psql -h localhost -U hmu_diagramacion -d hmu_diagramacion -v ON_ERROR_STOP=1 -f sql/02_datos_prueba.sql
psql -h localhost -U hmu_diagramacion -d hmu_diagramacion -v ON_ERROR_STOP=1 -f sql/03_escenario_demo.sql
```

(En Windows: `set PGCLIENTENCODING=UTF8` y rutas con `sql\`.)

### 2. Configurar

El `config.yaml` empaquetado
(`src/main/resources/config/config.yaml`) ya apunta a `localhost:5432` con la
base, el usuario y la contraseña anteriores, y sin envío de correo. Para otro
entorno, editarlo o partir de `config.example.yaml`; también puede indicarse
un archivo externo con `-Dhmu.config=<ruta>` sin recompilar.

Para habilitar el correo, completar la sección `smtp:` con un relay válido.
Con `host` vacío —o con `auth: true` y `password` vacía— no se envía correo:
los avisos se ven igual en la aplicación.

### 3. Ejecutar

```bash
# desde el código fuente, con la fecha del escenario
JAVA_TOOL_OPTIONS="-Dhmu.hoy=2026-10-07" mvn javafx:run

# o empaquetado
mvn -DskipTests package
java --enable-native-access=ALL-UNNAMED -Dhmu.hoy=2026-10-07 -jar target/hmu-diagramacion.jar
```

`mvn test` ejecuta las 204 pruebas (no requieren base de datos).

## Problemas frecuentes

- **`psql` no se encuentra** (Windows): agregar
  `C:\Program Files\PostgreSQL\18\bin` al `PATH` o reinstalar PostgreSQL con
  las herramientas de línea de comandos.
- **Acentos rotos al cargar los scripts** (Windows): falta
  `set PGCLIENTENCODING=UTF8` (los `.cmd` ya lo hacen).
- **La aplicación abre pero el menú está casi vacío**: la sesión corresponde a
  un usuario sin roles vigentes a la fecha de referencia; usar los usuarios de
  prueba documentados y la fecha `-Dhmu.hoy=2026-10-07`.
- **«Servidor fuera de línea» en la pantalla de ingreso**: PostgreSQL no está
  corriendo, o la sección `db:` de `config.yaml` no coincide con la base
  creada.
