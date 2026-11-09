#!/bin/sh
# ============================================================
#  HMU Diagramación - Inicio de la demostración
#  Abre la aplicación con la fecha fija del escenario de prueba.
# ============================================================
command -v java >/dev/null 2>&1 || { echo "No se encontró java. Instale el JDK 25 (Eclipse Temurin)."; exit 1; }
exec java --enable-native-access=ALL-UNNAMED -Dhmu.hoy=2026-10-07 -jar hmu-diagramacion.jar
