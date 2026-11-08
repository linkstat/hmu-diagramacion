-- ============================================================================
-- Revierte el seed de capturas (seed_capturas.sql + seed_capturas_base.sql):
-- borra SOLO lo cargado por el seed (prefijos de300000- y de400000-) y los
-- diagramas del servicio demo "Guardia Central". No toca datos preexistentes.
--
-- Uso: psql -h <host> -U aromito -d aromito -f sql/seed_capturas_revertir.sql
-- ============================================================================
BEGIN;

-- Versiones de reglas cargadas por el seed y versiones nuevas registradas
-- desde la app durante las pruebas (se restaura el catálogo inicial).
DELETE FROM ReglaValidacion WHERE id::text LIKE 'de400000-%' OR vigenteDesde > '2026-01-01';
UPDATE ReglaValidacion SET vigenteHasta = NULL WHERE vigenteDesde = '2026-01-01';

-- Parte diario, marcaciones y diagramas de octubre de otros servicios del seed.
DELETE FROM ParteDiarioDetalle WHERE agenteID::text LIKE 'de300000-%';
DELETE FROM ParteDiario WHERE fecha BETWEEN '2026-10-01' AND '2026-10-31';
-- Lotes del escenario (los del seed y los importados desde la app durante las
-- pruebas, p. ej. docs/demo/reloj_hmu_20261007.csv) y marcaciones de los agentes demo.
CREATE TEMP TABLE lotes_escenario ON COMMIT DROP AS
    SELECT id FROM LoteMarcaciones
    WHERE id::text LIKE 'de400000-%' OR rangoDesde BETWEEN '2026-10-01' AND '2026-10-31';
CREATE TEMP TABLE marcas_escenario ON COMMIT DROP AS
    SELECT id FROM Marcacion
    WHERE loteID IN (SELECT id FROM lotes_escenario) OR agenteID::text LIKE 'de300000-%';
DELETE FROM JornadaRegistrada WHERE marcacionIngresoID IN (SELECT id FROM marcas_escenario)
                                      OR marcacionEgresoID IN (SELECT id FROM marcas_escenario)
                                      OR agenteID::text LIKE 'de300000-%';
DELETE FROM Marcacion WHERE id IN (SELECT id FROM marcas_escenario);
DELETE FROM LoteMarcaciones WHERE id IN (SELECT id FROM lotes_escenario);
DELETE FROM DiagramaDeServicio WHERE id::text LIKE 'de400000-%';

UPDATE Servicio SET dotacionMinima = NULL
WHERE nombre IN ('Cirugía', 'Diagnóstico por Imágenes', 'Enfermería', 'Farmacia');

DELETE FROM Feriado WHERE id::text LIKE 'de400000-%';

-- Agentes incorporados con docs/demo/padron_nuevos_agentes.csv (CU05), si se importó.
CREATE TEMP TABLE importados_demo ON COMMIT DROP AS
    SELECT id FROM Usuario WHERE cuil::text ~ '^2[0-7]99990[0-9]{4}$';
DELETE FROM AsignacionRol WHERE usuarioID IN (SELECT id FROM importados_demo);
DELETE FROM Agente WHERE id IN (SELECT id FROM importados_demo);
DELETE FROM Usuario WHERE id IN (SELECT id FROM importados_demo);

-- Novedades, avisos, movimientos de horas y lotes del seed (prefijo de400000-).
DELETE FROM Notificacion    WHERE id::text LIKE 'de400000-%'
                               OR usuarioID::text LIKE 'de300000-%';
DELETE FROM MovimientoHoras WHERE novedadID::text LIKE 'de400000-%'
                               OR agenteID::text LIKE 'de300000-%';
UPDATE JornadaLaboral SET novedadID = NULL WHERE novedadID::text LIKE 'de400000-%';
DELETE FROM Novedad         WHERE id::text LIKE 'de400000-%'
                               OR agenteID::text LIKE 'de300000-%';
-- Diagramas: los generados por la app para el servicio demo + los de la bandeja.
-- (JornadaLaboral cae por ON DELETE CASCADE.)
DELETE FROM DiagramaDeServicio
WHERE servicioID = 'de300000-0000-4000-a000-000000000001'
   OR id::text LIKE 'de300000-%';

-- Jornadas de agentes demo que hubieran quedado en otros diagramas (defensivo)
DELETE FROM JornadaLaboral WHERE agenteID::text LIKE 'de300000-%';

-- Horarios: desasignar y borrar la jerarquía (modalidad -> Horario -> HorarioBase)
UPDATE Agente SET horarioActualID = NULL WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioJefeServicioGuardiaPasiva WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioEstandar                  WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioSemanal                   WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioGuardiaMedica             WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioNocturno                  WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioGuardiaEnfermeria         WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioFeriante                  WHERE id::text LIKE 'de300000-%';
DELETE FROM Horario                          WHERE id::text LIKE 'de300000-%';
DELETE FROM HorarioBase                      WHERE id::text LIKE 'de300000-%';

-- Agentes y servicio
UPDATE Servicio SET encargadoUsuarioID = NULL WHERE id = 'de300000-0000-4000-a000-000000000001';
DELETE FROM AsignacionRol WHERE usuarioID::text LIKE 'de300000-%';
DELETE FROM Agente    WHERE id::text LIKE 'de300000-%';
DELETE FROM Usuario     WHERE id::text LIKE 'de300000-%';
DELETE FROM Domicilio   WHERE id::text LIKE 'de300000-%';
DELETE FROM Servicio    WHERE id = 'de300000-0000-4000-a000-000000000001';

-- Diagrama preexistente de Biomédica: restaurar el creador original (Medina, Rosa)
UPDATE DiagramaDeServicio SET creadoPorID = (SELECT id FROM Usuario WHERE cuil = 27111122277)
WHERE id = '1ff293b1-2aa9-4a67-9318-7ac2f30c83b7';

-- Feriado (sólo si lo insertó la demo)
DELETE FROM Feriado WHERE id = 'de300000-0000-4000-e000-000000000001';

COMMIT;
