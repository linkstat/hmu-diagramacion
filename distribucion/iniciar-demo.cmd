@echo off
rem ============================================================
rem  HMU Diagramacion - Inicio de la demostracion
rem  Abre la aplicacion con la fecha fija del escenario de prueba.
rem ============================================================
where java >nul 2>nul
if errorlevel 1 (
    echo No se encontro java.exe. Instale el JDK 25 (Eclipse Temurin) y reintente.
    pause
    exit /b 1
)
java --enable-native-access=ALL-UNNAMED -Dhmu.hoy=2026-10-07 -jar hmu-diagramacion.jar
if errorlevel 1 pause
