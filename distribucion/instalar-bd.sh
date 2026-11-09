#!/bin/sh
# ============================================================
#  HMU Diagramación - Instalación de la base de datos de prueba
#  Crea el usuario y la base hmu_diagramacion en el PostgreSQL
#  local y carga los scripts de sql/ en orden.
# ============================================================
set -e
export PGCLIENTENCODING=UTF8

command -v psql >/dev/null 2>&1 || { echo "No se encontró psql. Instale PostgreSQL 18."; exit 1; }

echo "Ingrese la contraseña del usuario \"postgres\" (la elegida al instalar PostgreSQL)."
printf "Contraseña de postgres: "
read PGPASSWORD
export PGPASSWORD

psql -U postgres -h localhost -d postgres -v ON_ERROR_STOP=1 -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'hmu_diagramacion') THEN CREATE ROLE hmu_diagramacion LOGIN PASSWORD 'hmu_diagramacion'; END IF; END \$\$;"

if ! psql -U postgres -h localhost -d postgres -t -c "SELECT 1 FROM pg_database WHERE datname = 'hmu_diagramacion'" | grep -q 1; then
    psql -U postgres -h localhost -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE hmu_diagramacion OWNER hmu_diagramacion"
fi

export PGPASSWORD=hmu_diagramacion
for f in 01_esquema.sql 02_datos_prueba.sql 03_escenario_demo.sql; do
    echo "Ejecutando sql/$f ..."
    psql -U hmu_diagramacion -h localhost -d hmu_diagramacion -v ON_ERROR_STOP=1 -q -f "sql/$f"
done

echo
echo "Base de datos lista. Ejecute ./iniciar-demo.sh para abrir la aplicación."
