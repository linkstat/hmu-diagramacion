Diagrama entidad-relación de la base `hmu_diagramacion` (Figura 20 del TFG, completa y particionada en 20a–20e para A4).

`scripts/` lo genera desde el catálogo de PostgreSQL: `./scripts/generar_figuras.sh` (requiere graphviz y librsvg2-bin; conexión por variables `PG*`, p. ej. `PGDATABASE=hmu_diagramacion`). La salida completa es `der_hmu_diagramacion.svg/.png`.
