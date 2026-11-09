@echo off
rem ============================================================
rem  HMU Diagramacion - Instalacion de la base de datos de prueba
rem  Crea el usuario y la base hmu_diagramacion en el PostgreSQL
rem  local y carga los scripts de sql\ en orden.
rem ============================================================
chcp 65001 >nul
setlocal
set PGCLIENTENCODING=UTF8

where psql >nul 2>nul
if errorlevel 1 set "PATH=%PATH%;C:\Program Files\PostgreSQL\18\bin"
where psql >nul 2>nul
if errorlevel 1 (
    echo No se encontro psql.exe. Instale PostgreSQL 18 o agregue su carpeta bin al PATH.
    pause
    exit /b 1
)

echo Ingrese la contrasena del usuario "postgres" (la elegida al instalar PostgreSQL).
set /p PGPASSWORD=Contrasena de postgres:

psql -U postgres -h localhost -d postgres -v ON_ERROR_STOP=1 -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'hmu_diagramacion') THEN CREATE ROLE hmu_diagramacion LOGIN PASSWORD 'hmu_diagramacion'; END IF; END $$;"
if errorlevel 1 goto :error

psql -U postgres -h localhost -d postgres -t -c "SELECT 1 FROM pg_database WHERE datname = 'hmu_diagramacion'" | findstr 1 >nul
if errorlevel 1 (
    psql -U postgres -h localhost -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE hmu_diagramacion OWNER hmu_diagramacion"
    if errorlevel 1 goto :error
)

set PGPASSWORD=hmu_diagramacion
for %%F in (01_esquema.sql 02_datos_prueba.sql 03_escenario_demo.sql) do (
    echo Ejecutando sql\%%F ...
    psql -U hmu_diagramacion -h localhost -d hmu_diagramacion -v ON_ERROR_STOP=1 -q -f "sql\%%F"
    if errorlevel 1 goto :error
)

echo.
echo Base de datos lista. Ejecute iniciar-demo.cmd para abrir la aplicacion.
pause
exit /b 0

:error
echo.
echo Fallo la instalacion de la base. Revise el mensaje anterior.
pause
exit /b 1
