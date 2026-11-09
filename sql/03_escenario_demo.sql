-- ============================================================================
-- Escenario de demostración. SOLO para la base de prueba.
--
-- Escenario: semana del 5 al 7 de octubre de 2026 en el HMU.
--   * Guardia Central (11 agentes ficticios) con el diagrama de OCTUBRE
--     APROBADO (generado por la app desde las modalidades; se corrige el
--     descanso de Ledesma del 06-07/10) y el de NOVIEMBRE en BORRADOR con un
--     error bloqueante (descanso de 6 h de Ledesma, 03-04/11) y advertencias
--     de carga mensual (P01).
--   * Diagramas de noviembre de otros servicios en PENDIENTE / OBSERVADO /
--     APROBADO (P02).
--   * Novedades en todos los estados del circuito (P03): pendientes de JS,
--     OP y DH, observada, rechazada, aprobada con Secretaría de Salud
--     pendiente, aplicadas (horas extra, pase de salida de la jefa,
--     licencia externa) y un borrador.
--   * Avisos en la aplicación (CU27) y saldos de horas.
--
-- Todo es ficticio (nombres inventados, CUIL con dígito verificador
-- inválido). Idempotente: revierte lo anterior y vuelve a cargar.
--
-- Uso:  psql -h <host> -U hmu_diagramacion -d hmu_diagramacion -f sql/03_escenario_demo.sql
-- Revertir: psql ... -f sql/03_escenario_demo_revertir.sql
-- ============================================================================
\set ON_ERROR_STOP on
\ir 03_escenario_demo_revertir.sql

BEGIN;

\ir 03_escenario_demo_base.sql

-- ---------------------------------------------------------------------------
-- Diagramas de Guardia Central: octubre APROBADO, noviembre BORRADOR (P01)
-- ---------------------------------------------------------------------------
-- Jornadas con horario de octubre tal como las generó la app desde las
-- modalidades: (agente, día, tipo, hora de ingreso, duración en horas).
-- Agente n = de300000-0000-4000-b000-0000000000nn.
CREATE TEMP TABLE patron_oct (ag INT, dia INT, tipo tipo_jornada, hin TIME, dur INT) ON COMMIT DROP;
INSERT INTO patron_oct VALUES
(1,1,'TURNO_NORMAL','07:00',6), (1,2,'TURNO_NORMAL','07:00',6), (1,5,'TURNO_NORMAL','07:00',6), 
(1,6,'TURNO_NORMAL','07:00',6), (1,7,'TURNO_NORMAL','07:00',6), (1,8,'TURNO_NORMAL','07:00',6), 
(1,9,'TURNO_NORMAL','07:00',6), (1,13,'TURNO_NORMAL','07:00',6), (1,14,'TURNO_NORMAL','07:00',6), 
(1,15,'TURNO_NORMAL','07:00',6), (1,16,'TURNO_NORMAL','07:00',6), (1,19,'TURNO_NORMAL','07:00',6), 
(1,20,'TURNO_NORMAL','07:00',6), (1,21,'TURNO_NORMAL','07:00',6), (1,22,'TURNO_NORMAL','07:00',6), 
(1,23,'TURNO_NORMAL','07:00',6), (1,26,'TURNO_NORMAL','07:00',6), (1,27,'TURNO_NORMAL','07:00',6), 
(1,28,'TURNO_NORMAL','07:00',6), (1,29,'TURNO_NORMAL','07:00',6), (1,30,'TURNO_NORMAL','07:00',6), 
(2,1,'TURNO_NORMAL','07:00',7), (2,2,'TURNO_NORMAL','07:00',7), (2,5,'TURNO_NORMAL','07:00',7), 
(2,6,'TURNO_NORMAL','07:00',7), (2,7,'TURNO_NORMAL','07:00',7), (2,8,'TURNO_NORMAL','07:00',7), 
(2,9,'TURNO_NORMAL','07:00',7), (2,13,'TURNO_NORMAL','07:00',7), (2,14,'TURNO_NORMAL','07:00',7), 
(2,15,'TURNO_NORMAL','07:00',7), (2,16,'TURNO_NORMAL','07:00',7), (2,19,'TURNO_NORMAL','07:00',7), 
(2,20,'TURNO_NORMAL','07:00',7), (2,21,'TURNO_NORMAL','07:00',7), (2,22,'TURNO_NORMAL','07:00',7), 
(2,23,'TURNO_NORMAL','07:00',7), (2,26,'TURNO_NORMAL','07:00',7), (2,27,'TURNO_NORMAL','07:00',7), 
(2,28,'TURNO_NORMAL','07:00',7), (2,29,'TURNO_NORMAL','07:00',7), (2,30,'TURNO_NORMAL','07:00',7), 
(3,1,'TURNO_NORMAL','14:00',7), (3,2,'TURNO_NORMAL','14:00',7), (3,5,'TURNO_NORMAL','14:00',7), 
(3,6,'TURNO_NORMAL','14:00',7), (3,7,'TURNO_NORMAL','14:00',7), (3,8,'TURNO_NORMAL','14:00',7), 
(3,9,'TURNO_NORMAL','14:00',7), (3,13,'TURNO_NORMAL','14:00',7), (3,14,'TURNO_NORMAL','14:00',7), 
(3,15,'TURNO_NORMAL','14:00',7), (3,16,'TURNO_NORMAL','14:00',7), (3,19,'TURNO_NORMAL','14:00',7), 
(3,20,'TURNO_NORMAL','14:00',7), (3,21,'TURNO_NORMAL','14:00',7), (3,22,'TURNO_NORMAL','14:00',7), 
(3,23,'TURNO_NORMAL','14:00',7), (3,26,'TURNO_NORMAL','14:00',7), (3,27,'TURNO_NORMAL','14:00',7), 
(3,28,'TURNO_NORMAL','14:00',7), (3,29,'TURNO_NORMAL','14:00',7), (3,30,'TURNO_NORMAL','14:00',7), 
(4,2,'GUARDIA_ACTIVA','08:00',12), (4,6,'GUARDIA_ACTIVA','20:00',12), (4,7,'GUARDIA_ACTIVA','14:00',12), 
(4,10,'GUARDIA_ACTIVA','08:00',12), (4,13,'GUARDIA_ACTIVA','20:00',12), (4,16,'GUARDIA_ACTIVA','08:00',12), 
(4,20,'GUARDIA_ACTIVA','20:00',12), (4,23,'GUARDIA_ACTIVA','08:00',12), (4,27,'GUARDIA_ACTIVA','20:00',12), 
(4,30,'GUARDIA_ACTIVA','08:00',12), (5,1,'GUARDIA_ACTIVA','08:00',12), (5,4,'GUARDIA_ACTIVA','20:00',12), 
(5,8,'GUARDIA_ACTIVA','08:00',12), (5,11,'GUARDIA_ACTIVA','20:00',12), (5,15,'GUARDIA_ACTIVA','08:00',12), 
(5,18,'GUARDIA_ACTIVA','20:00',12), (5,22,'GUARDIA_ACTIVA','08:00',12), (5,25,'GUARDIA_ACTIVA','20:00',12), 
(5,29,'GUARDIA_ACTIVA','08:00',12), (6,3,'TURNO_NORMAL','21:00',10), (6,4,'TURNO_NORMAL','21:00',10), 
(6,7,'TURNO_NORMAL','21:00',10), (6,8,'TURNO_NORMAL','21:00',10), (6,11,'TURNO_NORMAL','21:00',10), 
(6,12,'TURNO_NORMAL','21:00',10), (6,15,'TURNO_NORMAL','21:00',10), (6,16,'TURNO_NORMAL','21:00',10), 
(6,19,'TURNO_NORMAL','21:00',10), (6,20,'TURNO_NORMAL','21:00',10), (6,23,'TURNO_NORMAL','21:00',10), 
(6,24,'TURNO_NORMAL','21:00',10), (7,1,'TURNO_NORMAL','21:00',10), (7,2,'TURNO_NORMAL','21:00',10), 
(7,5,'TURNO_NORMAL','21:00',10), (7,6,'TURNO_NORMAL','21:00',10), (7,9,'TURNO_NORMAL','21:00',10), 
(7,10,'TURNO_NORMAL','21:00',10), (7,13,'TURNO_NORMAL','21:00',10), (7,14,'TURNO_NORMAL','21:00',10), 
(7,17,'TURNO_NORMAL','21:00',10), (7,18,'TURNO_NORMAL','21:00',10), (7,21,'TURNO_NORMAL','21:00',10), 
(7,22,'TURNO_NORMAL','21:00',10), (7,25,'TURNO_NORMAL','21:00',10), (7,26,'TURNO_NORMAL','21:00',10), 
(8,1,'GUARDIA_ACTIVA','07:00',12), (8,3,'GUARDIA_ACTIVA','19:00',12), (8,5,'GUARDIA_ACTIVA','07:00',12), 
(8,7,'GUARDIA_ACTIVA','19:00',12), (8,9,'GUARDIA_ACTIVA','07:00',12), (8,11,'GUARDIA_ACTIVA','19:00',12), 
(8,13,'GUARDIA_ACTIVA','07:00',12), (8,15,'GUARDIA_ACTIVA','19:00',12), (8,17,'GUARDIA_ACTIVA','07:00',12), 
(8,19,'GUARDIA_ACTIVA','19:00',12), (8,21,'GUARDIA_ACTIVA','07:00',12), (8,23,'GUARDIA_ACTIVA','19:00',12), 
(9,3,'GUARDIA_ACTIVA','08:00',12), (9,4,'GUARDIA_ACTIVA','08:00',12), (9,10,'GUARDIA_ACTIVA','08:00',12), 
(9,11,'GUARDIA_ACTIVA','08:00',12), (9,12,'GUARDIA_ACTIVA','08:00',12), (9,17,'GUARDIA_ACTIVA','08:00',12), 
(9,18,'GUARDIA_ACTIVA','08:00',12), (9,24,'GUARDIA_ACTIVA','08:00',12), (9,25,'GUARDIA_ACTIVA','08:00',12), 
(9,31,'GUARDIA_ACTIVA','08:00',12), (10,2,'GUARDIA_ACTIVA','07:00',12), (10,5,'GUARDIA_ACTIVA','07:00',12), 
(10,8,'GUARDIA_ACTIVA','07:00',12), (10,11,'GUARDIA_ACTIVA','07:00',12), (10,14,'GUARDIA_ACTIVA','07:00',12), 
(10,17,'GUARDIA_ACTIVA','07:00',12), (10,20,'GUARDIA_ACTIVA','07:00',12), 
(10,23,'GUARDIA_ACTIVA','07:00',12), (10,26,'GUARDIA_ACTIVA','07:00',12), 
(10,29,'GUARDIA_ACTIVA','07:00',12), (11,1,'TURNO_NORMAL','08:00',7), (11,2,'TURNO_NORMAL','08:00',7), 
(11,5,'TURNO_NORMAL','08:00',7), (11,6,'TURNO_NORMAL','08:00',7), (11,7,'TURNO_NORMAL','08:00',7), 
(11,8,'TURNO_NORMAL','08:00',7), (11,9,'TURNO_NORMAL','08:00',7), (11,13,'TURNO_NORMAL','08:00',7), 
(11,14,'TURNO_NORMAL','08:00',7), (11,15,'TURNO_NORMAL','08:00',7), (11,16,'TURNO_NORMAL','08:00',7), 
(11,19,'TURNO_NORMAL','08:00',7), (11,20,'TURNO_NORMAL','08:00',7), (11,21,'TURNO_NORMAL','08:00',7), 
(11,22,'TURNO_NORMAL','08:00',7), (11,23,'TURNO_NORMAL','08:00',7), (11,26,'TURNO_NORMAL','08:00',7), 
(11,27,'TURNO_NORMAL','08:00',7), (11,28,'TURNO_NORMAL','08:00',7), (11,29,'TURNO_NORMAL','08:00',7), 
(11,30,'TURNO_NORMAL','08:00',7);

CREATE FUNCTION pg_temp.agente(n INT) RETURNS UUID LANGUAGE sql IMMUTABLE AS
$$ SELECT ('de300000-0000-4000-b000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

-- Octubre aprobado: el descanso de 6 h de Ledesma (06/10 20:00 -> 07/10 14:00)
-- se corrigió antes de presentarlo: la guardia del 07/10 pasa al 09/10 08:00.
INSERT INTO DiagramaDeServicio (id, estado, fechaInicio, fechaFin, servicioID, version, creadoPorID,
                                createdAt, updatedAt, aprobadoPorID, fechaAprobacion)
VALUES ('de400000-0000-4000-d000-000000000010', 'APROBADO', '2026-10-01', '2026-10-31',
        'de300000-0000-4000-a000-000000000001', 4, pg_temp.agente(1),
        '2026-09-18 10:15', '2026-09-28 12:40',
        (SELECT id FROM Usuario WHERE cuil = 27111122277), '2026-09-28 12:40');

INSERT INTO JornadaLaboral (id, diagramaID, agenteID, fecha, tipo, fechaIngreso, fechaEgreso)
SELECT gen_random_uuid(), 'de400000-0000-4000-d000-000000000010', pg_temp.agente(a.n), d::date,
       COALESCE(p.tipo, 'FRANCO'),
       CASE WHEN p.tipo IS NOT NULL THEN d::date + p.hin END,
       CASE WHEN p.tipo IS NOT NULL THEN d::date + p.hin + make_interval(hours => p.dur) END
FROM generate_series(1, 11) AS a(n)
CROSS JOIN generate_series(DATE '2026-10-01', DATE '2026-10-31', INTERVAL '1 day') AS d
LEFT JOIN (SELECT * FROM patron_oct WHERE NOT (ag = 4 AND dia = 7)
           UNION ALL SELECT 4, 9, 'GUARDIA_ACTIVA', '08:00', 12) p
       ON p.ag = a.n AND p.dia = extract(day FROM d);

-- Noviembre en borrador: mismo esquema semanal (octubre + 28 días; el 29 y
-- 30/11 repiten el 4 y 5/10). Conserva el error de Ledesma (03/11 20:00 ->
-- 04/11 14:00: 6 h de descanso) para la captura de P01.
INSERT INTO DiagramaDeServicio (id, estado, fechaInicio, fechaFin, servicioID, version, creadoPorID,
                                createdAt, updatedAt)
VALUES ('de400000-0000-4000-d000-000000000011', 'BORRADOR', '2026-11-01', '2026-11-30',
        'de300000-0000-4000-a000-000000000001', 2, pg_temp.agente(1), '2026-10-05 09:30', '2026-10-06 17:50');

INSERT INTO JornadaLaboral (id, diagramaID, agenteID, fecha, tipo, fechaIngreso, fechaEgreso)
SELECT gen_random_uuid(), 'de400000-0000-4000-d000-000000000011', pg_temp.agente(a.n), d::date,
       COALESCE(p.tipo, 'FRANCO'),
       CASE WHEN p.tipo IS NOT NULL THEN d::date + p.hin END,
       CASE WHEN p.tipo IS NOT NULL THEN d::date + p.hin + make_interval(hours => p.dur) END
FROM generate_series(1, 11) AS a(n)
CROSS JOIN generate_series(DATE '2026-11-01', DATE '2026-11-30', INTERVAL '1 day') AS d
LEFT JOIN patron_oct p
       ON p.ag = a.n
      AND p.dia = CASE WHEN extract(day FROM d) <= 28 THEN extract(day FROM d) + 3
                       ELSE extract(day FROM d) - 25 END;

-- Cirugía, noviembre PENDIENTE de la OP (P02 / P02b): grilla cargada para que
-- el diagrama no se vea vacío. Turnos de 7 h en días hábiles (mañana y tarde)
-- y un guardista de 12 h cada tres días.
INSERT INTO JornadaLaboral (id, diagramaID, agenteID, fecha, tipo, fechaIngreso, fechaEgreso)
SELECT gen_random_uuid(), 'de300000-0000-4000-d000-000000000002', e.id, d::date, j.tipo::tipo_jornada,
       CASE WHEN j.tipo <> 'FRANCO' THEN d::date + j.hin END,
       CASE WHEN j.tipo <> 'FRANCO' THEN d::date + j.hin + make_interval(hours => j.dur) END
FROM (SELECT u.id, row_number() OVER (ORDER BY u.apellidos) AS n
      FROM Usuario u JOIN Agente em ON em.id = u.id
      WHERE u.estado AND u.servicioID = (SELECT id FROM Servicio WHERE nombre = 'Cirugía')) e
CROSS JOIN generate_series(DATE '2026-11-01', DATE '2026-11-30', INTERVAL '1 day') AS d
CROSS JOIN LATERAL (SELECT
       CASE WHEN e.n = 3 THEN CASE WHEN extract(day FROM d)::int % 3 = 1 THEN 'GUARDIA_ACTIVA' ELSE 'FRANCO' END
            WHEN extract(isodow FROM d) IN (6, 7) THEN 'FRANCO'
            ELSE 'TURNO_NORMAL' END AS tipo,
       CASE WHEN e.n = 3 THEN TIME '08:00' WHEN e.n % 2 = 0 THEN TIME '14:00' ELSE TIME '07:00' END AS hin,
       CASE WHEN e.n = 3 THEN 12 ELSE 7 END AS dur) j;


-- ---------------------------------------------------------------------------
-- Agentes: vinculación, saldo inicial de horas y acceso del agente demo
-- ---------------------------------------------------------------------------
UPDATE Agente SET vinculacion = 'MONOTRIBUTISTA' WHERE id = pg_temp.agente(11);
UPDATE Agente SET vinculacion = 'CONTRATADO'     WHERE id = pg_temp.agente(3);
-- Saldo del banco de horas: créditos (más abajo, en "Banco de horas").

-- Moreno, Daniela: agente demo para la vista del agente en el video
-- (misma contraseña de prueba que la OP; el hash se copia de Medina).
UPDATE Usuario SET passwordHash = (SELECT passwordHash FROM Usuario WHERE cuil = 27111122277)
WHERE id = pg_temp.agente(5);
-- Ficha completa para la captura del ABM (P07): domicilio en Córdoba y teléfono ficticio.
INSERT INTO Domicilio (id, calle, numeracion, barrio, ciudad, localidad, provincia)
VALUES ('de300000-0000-4000-f000-000000000005', 'Av. Colón', 2450, 'Alto Alberdi', 'Córdoba', 'Córdoba Capital',
        'Córdoba');
UPDATE Usuario SET tel = 3515550123, domicilioID = 'de300000-0000-4000-f000-000000000005'
WHERE id = pg_temp.agente(5);


-- ---------------------------------------------------------------------------
-- Novedades (P03): una por estado del circuito
-- ---------------------------------------------------------------------------
CREATE FUNCTION pg_temp.tipo(c TEXT) RETURNS UUID LANGUAGE sql STABLE AS
$$ SELECT id FROM TipoNovedad WHERE codigo = c $$;
CREATE FUNCTION pg_temp.nov(n INT) RETURNS UUID LANGUAGE sql IMMUTABLE AS
$$ SELECT ('de400000-0000-4000-a100-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
CREATE FUNCTION pg_temp.op() RETURNS UUID LANGUAGE sql STABLE AS
$$ SELECT id FROM Usuario WHERE cuil = 27111122277 $$;          -- Medina, Rosa (OP)
CREATE FUNCTION pg_temp.dh() RETURNS UUID LANGUAGE sql STABLE AS
$$ SELECT id FROM Usuario WHERE cuil = 20111111122 $$;          -- García, Pedro (DH)

INSERT INTO Novedad (id, tipoNovedadID, agenteID, contraparteAgenteID, fechaDesde, fechaHasta, horaDesde,
                     horaHasta, cantHoras, motivo, estado, origen, solicitadoPorID, fechaSolicitud,
                     referenciaExterna, version, createdAt, updatedAt)
VALUES
 -- 1. CG Moreno -> Ledesma, pendiente de la OP (captura principal de P03)
 (pg_temp.nov(1), pg_temp.tipo('CG'), pg_temp.agente(5), pg_temp.agente(4), '2026-10-18', '2026-10-23', NULL, NULL,
  NULL, 'Examen final de la especialidad el 19/10 por la mañana; intercambio acordado con Ledesma.',
  'PENDIENTE_OFICINA_PERSONAL', 'SOLICITUD', pg_temp.agente(5), '2026-10-05 08:14', NULL, 2,
  '2026-10-05 08:10', '2026-10-05 10:32'),
 -- 2. FC Juárez, pendiente de la OP
 (pg_temp.nov(2), pg_temp.tipo('FC'), pg_temp.agente(9), NULL, '2026-10-17', '2026-10-17', NULL, NULL,
  12, 'Compensación de las horas extra de septiembre.',
  'PENDIENTE_OFICINA_PERSONAL', 'SOLICITUD', pg_temp.agente(9), '2026-10-02 09:40', NULL, 2,
  '2026-10-02 09:38', '2026-10-02 13:05'),
 -- 3. CH Sosa, pendiente de la JS
 (pg_temp.nov(3), pg_temp.tipo('CH'), pg_temp.agente(2), NULL, '2026-10-14', '2026-10-14', '08:00', '15:00',
  7, 'Turno de control médico a las 7:30.',
  'PENDIENTE_JEFATURA', 'SOLICITUD', pg_temp.agente(2), '2026-10-06 11:05', NULL, 1,
  '2026-10-06 11:02', '2026-10-06 11:05'),
 -- 4. CG Godoy -> Figueroa, pendiente de la JS
 (pg_temp.nov(4), pg_temp.tipo('CG'), pg_temp.agente(6), pg_temp.agente(7), '2026-10-24', '2026-10-25', NULL, NULL,
  NULL, 'Compromiso familiar el sábado 24.',
  'PENDIENTE_JEFATURA', 'SOLICITUD', pg_temp.agente(6), '2026-10-06 18:20', NULL, 1,
  '2026-10-06 18:15', '2026-10-06 18:20'),
 -- 5. JI Figueroa, pendiente de la DH
 (pg_temp.nov(5), pg_temp.tipo('FJ'), pg_temp.agente(7), NULL, '2026-10-06', '2026-10-06', NULL, NULL,
  NULL, 'Reposo por gastroenteritis con certificado médico.',
  'PENDIENTE_DIRECCION', 'SOLICITUD', pg_temp.agente(7), '2026-10-06 10:02', NULL, 3,
  '2026-10-06 10:00', '2026-10-06 15:20'),
 -- 6. OM Correa, observada por la OP
 (pg_temp.nov(6), pg_temp.tipo('OM'), pg_temp.agente(11), NULL, '2026-10-02', '2026-10-02', '08:00', NULL,
  NULL, 'Olvidé marcar el ingreso; el reloj del acceso norte no respondía.',
  'OBSERVADA', 'SOLICITUD', pg_temp.agente(11), '2026-10-02 16:30', NULL, 3,
  '2026-10-02 16:28', '2026-10-05 11:45'),
 -- 7. HE Cáceres, aplicada: se queda tras la guardia del 03/10 (domingo 04/10, cómputo doble);
 --    las horas las acredita el cotejo de sus marcaciones.
 (pg_temp.nov(7), pg_temp.tipo('HE'), pg_temp.agente(8), NULL, '2026-10-03', '2026-10-03', NULL, NULL,
  NULL, 'Refuerzo al terminar la guardia por ingreso múltiple de politraumatizados.',
  'APLICADA', 'SOLICITUD', pg_temp.agente(8), '2026-10-03 18:00', NULL, 5,
  '2026-10-03 17:55', '2026-10-05 13:00'),
 -- 8. PSSR Paz (la jefa: primera instancia la DH), aplicada: sale 11:00, egreso diagramado 13:00
 (pg_temp.nov(8), pg_temp.tipo('PSSR'), pg_temp.agente(1), NULL, '2026-10-06', '2026-10-06', '11:00', '13:00',
  2, 'Trámite personal en el Registro Civil.',
  'APLICADA', 'SOLICITUD', pg_temp.agente(1), '2026-10-06 08:30', NULL, 5,
  '2026-10-06 08:28', '2026-10-06 10:40'),
 -- 9. Licencia de origen externo (VeDi) de Villalba, asentada por la OP (CU29)
 (pg_temp.nov(9), pg_temp.tipo('LIC'), pg_temp.agente(3), NULL, '2026-10-05', '2026-10-09', NULL, NULL,
  NULL, 'Licencia por carpeta médica prolongada del 05 al 09/10.',
  'APLICADA', 'LICENCIA_EXTERNA', pg_temp.op(), '2026-10-05 09:05', 'VeDi 2026-118734', 1,
  '2026-10-05 09:05', '2026-10-05 09:05'),
 -- 10. FC Maldonado, rechazada por la JS
 (pg_temp.nov(10), pg_temp.tipo('FC'), pg_temp.agente(10), NULL, '2026-10-20', '2026-10-20', NULL, NULL,
  12, 'Franco por asuntos particulares.',
  'RECHAZADA', 'SOLICITUD', pg_temp.agente(10), '2026-10-05 14:10', NULL, 2,
  '2026-10-05 14:08', '2026-10-06 17:00'),
 -- 11. JI Godoy (3 días: supera el tope mensual), aprobada; espera la Secretaría de Salud
 (pg_temp.nov(11), pg_temp.tipo('FJ'), pg_temp.agente(6), NULL, '2026-10-01', '2026-10-03', NULL, NULL,
  NULL, 'Reposo por lumbalgia aguda.',
  'APROBADA', 'SOLICITUD', pg_temp.agente(6), '2026-10-01 09:00', NULL, 4,
  '2026-10-01 08:55', '2026-10-05 12:00'),
 -- 12. Borrador de Moreno (sólo lo ve ella)
 (pg_temp.nov(12), pg_temp.tipo('PSP'), pg_temp.agente(5), NULL, '2026-10-29', '2026-10-29', '15:00', '17:00',
  2, 'Turno odontológico.', 'BORRADOR', 'SOLICITUD', pg_temp.agente(5), NULL, NULL, 0,
  '2026-10-06 21:10', '2026-10-06 21:10');

-- Justificación de las horas extras (la fórmula usual; la del 12/09 se carga más abajo).
UPDATE Novedad SET justificacion = 'Necesidad del servicio' WHERE id = pg_temp.nov(7);

-- Tipo de licencia de la licencia externa de Villalba: 11 · Carpeta médica prolongada.
UPDATE Novedad SET codigoNovedadID = (SELECT id FROM CodigoNovedad WHERE codigo = 11) WHERE id = pg_temp.nov(9);

-- Instancias del circuito (NovedadAutorizacion) con su trazabilidad.
INSERT INTO NovedadAutorizacion (id, novedadID, instancia, orden, estado, resueltoPorID, fechaResolucion, comentarios)
SELECT ('de400000-0000-4000-a200-' || lpad((row_number() OVER ())::text, 12, '0'))::uuid,
       pg_temp.nov(v.nov), v.inst::instancia_novedad, v.orden, v.estado::estado_novedad_autorizacion,
       v.quien, v.fecha::timestamp, v.coment
FROM (VALUES
 (1, 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-05 10:32', 'Cobertura verificada.'),
 (1, 'OFICINA_PERSONAL', 2, 'PENDIENTE', NULL, NULL, NULL),
 (1, 'DIRECCION', 3, 'PENDIENTE', NULL, NULL, NULL),
 (2, 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-02 13:05', 'Con reemplazo asegurado.'),
 (2, 'OFICINA_PERSONAL', 2, 'PENDIENTE', NULL, NULL, NULL),
 (2, 'DIRECCION', 3, 'PENDIENTE', NULL, NULL, NULL),
 (3, 'JEFATURA', 1, 'PENDIENTE', NULL, NULL, NULL),
 (3, 'OFICINA_PERSONAL', 2, 'PENDIENTE', NULL, NULL, NULL),
 (3, 'DIRECCION', 3, 'PENDIENTE', NULL, NULL, NULL),
 (4, 'JEFATURA', 1, 'PENDIENTE', NULL, NULL, NULL),
 (4, 'OFICINA_PERSONAL', 2, 'PENDIENTE', NULL, NULL, NULL),
 (4, 'DIRECCION', 3, 'PENDIENTE', NULL, NULL, NULL),
 (5, 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-06 12:15', 'Ausencia comunicada a primera hora.'),
 (5, 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-06 15:20', 'Certificado médico conforme.'),
 (5, 'DIRECCION', 3, 'PENDIENTE', NULL, NULL, NULL),
 (6, 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-03 09:10', 'Constaté su presencia en el servicio.'),
 (6, 'OFICINA_PERSONAL', 2, 'OBSERVADO', pg_temp.op(), '2026-10-05 11:45',
  'Indicar el horario real de ingreso y adjuntar la constancia firmada por el jefe de guardia.'),
 (7, 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-04 09:00', 'Refuerzo dispuesto por la jefatura.'),
 (7, 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-05 10:00', NULL),
 (7, 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-05 13:00', NULL),
 (8, 'DIRECCION', 1, 'AUTORIZADO', pg_temp.dh(), '2026-10-06 09:00', 'Autorizado.'),
 (8, 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-06 09:30', 'Dentro del tope mensual de pases.'),
 (8, 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-06 10:40', NULL),
 (10, 'JEFATURA', 1, 'RECHAZADO', pg_temp.agente(1), '2026-10-06 17:00',
  'Cobertura insuficiente: ese día el servicio queda con 3 agentes (mínimo 4).'),
 (11, 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-01 11:00', NULL),
 (11, 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-02 10:00', 'Supera el tope mensual: se eleva a la Secretaría de Salud.'),
 (11, 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-05 12:00', NULL),
 (11, 'SECRETARIA_SALUD', 4, 'PENDIENTE', NULL, NULL, NULL)
) AS v(nov, inst, orden, estado, quien, fecha, coment);

-- Respaldos documentales (PDF ficticios generados para la demo).
INSERT INTO NovedadAdjunto (id, novedadID, nombreArchivo, tipoMime, contenido, cargadoPorID, fechaCarga) VALUES
 ('de400000-0000-4000-a300-000000000001', pg_temp.nov(1), 'constancia_examen.pdf', 'application/pdf',
  decode('255044462d312e340a312030206f626a0a3c3c202f54797065202f436174616c6f67202f5061676573203220302052203e3e0a656e646f626a0a322030206f626a0a3c3c202f54797065202f5061676573202f4b696473205b33203020525d202f436f756e742031203e3e0a656e646f626a0a332030206f626a0a3c3c202f54797065202f50616765202f506172656e74203220302052202f4d65646961426f78205b30203020353935203834325d202f5265736f7572636573203c3c202f466f6e74203c3c202f4631203420302052203e3e203e3e202f436f6e74656e7473203520302052203e3e0a656e646f626a0a342030206f626a0a3c3c202f54797065202f466f6e74202f53756274797065202f5479706531202f42617365466f6e74202f48656c766574696361202f456e636f64696e67202f57696e416e7369456e636f64696e67203e3e0a656e646f626a0a352030206f626a0a3c3c202f4c656e67746820323633203e3e0a73747265616d0a4254202f46312031332054662037322037363020546420313620544c0a28434f4e5354414e434941204445204558414d454e2028646f63756d656e746f20666963746963696f20646520707275656261292920546a20542a0a282920546a20542a0a2853652064656a6120636f6e7374616e6369612064652071756520656c206167656e74652072696e646520656c206578616d656e2066696e616c2920546a20542a0a286465206c6120657370656369616c6964616420656c206469612031392f31302f323032362061206c61732030393a30302068732e2920546a20542a0a282920546a20542a0a284669726d6120792073656c6c6f3a202d2d2d2920546a20542a0a45540a656e6473747265616d0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303538203030303030206e200a30303030303030313135203030303030206e200a30303030303030323431203030303030206e200a30303030303030333338203030303030206e200a747261696c65720a3c3c202f53697a652036202f526f6f74203120302052203e3e0a7374617274787265660a3635320a2525454f460a', 'hex'), pg_temp.agente(5), '2026-10-05 08:13'),
 ('de400000-0000-4000-a300-000000000002', pg_temp.nov(5), 'certificado_medico.pdf', 'application/pdf',
  decode('255044462d312e340a312030206f626a0a3c3c202f54797065202f436174616c6f67202f5061676573203220302052203e3e0a656e646f626a0a322030206f626a0a3c3c202f54797065202f5061676573202f4b696473205b33203020525d202f436f756e742031203e3e0a656e646f626a0a332030206f626a0a3c3c202f54797065202f50616765202f506172656e74203220302052202f4d65646961426f78205b30203020353935203834325d202f5265736f7572636573203c3c202f466f6e74203c3c202f4631203420302052203e3e203e3e202f436f6e74656e7473203520302052203e3e0a656e646f626a0a342030206f626a0a3c3c202f54797065202f466f6e74202f53756274797065202f5479706531202f42617365466f6e74202f48656c766574696361202f456e636f64696e67202f57696e416e7369456e636f64696e67203e3e0a656e646f626a0a352030206f626a0a3c3c202f4c656e67746820323035203e3e0a73747265616d0a4254202f46312031332054662037322037363020546420313620544c0a28434552544946494341444f204d454449434f2028646f63756d656e746f20666963746963696f20646520707275656261292920546a20542a0a282920546a20542a0a28536520636572746966696361207265706f736f206c61626f72616c20706f722032342068732061207061727469722064656c2030362f31302f323032362e2920546a20542a0a282920546a20542a0a284669726d6120792073656c6c6f3a202d2d2d2920546a20542a0a45540a656e6473747265616d0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303538203030303030206e200a30303030303030313135203030303030206e200a30303030303030323431203030303030206e200a30303030303030333338203030303030206e200a747261696c65720a3c3c202f53697a652036202f526f6f74203120302052203e3e0a7374617274787265660a3539340a2525454f460a', 'hex'), pg_temp.agente(7), '2026-10-06 10:01'),
 ('de400000-0000-4000-a300-000000000003', pg_temp.nov(11), 'certificado_medico.pdf', 'application/pdf',
  decode('255044462d312e340a312030206f626a0a3c3c202f54797065202f436174616c6f67202f5061676573203220302052203e3e0a656e646f626a0a322030206f626a0a3c3c202f54797065202f5061676573202f4b696473205b33203020525d202f436f756e742031203e3e0a656e646f626a0a332030206f626a0a3c3c202f54797065202f50616765202f506172656e74203220302052202f4d65646961426f78205b30203020353935203834325d202f5265736f7572636573203c3c202f466f6e74203c3c202f4631203420302052203e3e203e3e202f436f6e74656e7473203520302052203e3e0a656e646f626a0a342030206f626a0a3c3c202f54797065202f466f6e74202f53756274797065202f5479706531202f42617365466f6e74202f48656c766574696361202f456e636f64696e67202f57696e416e7369456e636f64696e67203e3e0a656e646f626a0a352030206f626a0a3c3c202f4c656e67746820323030203e3e0a73747265616d0a4254202f46312031332054662037322037363020546420313620544c0a28434552544946494341444f204d454449434f2028646f63756d656e746f20666963746963696f20646520707275656261292920546a20542a0a282920546a20542a0a28536520636572746966696361207265706f736f206c61626f72616c2064656c2030312f31302f3230323620616c2030332f31302f323032362e2920546a20542a0a282920546a20542a0a284669726d6120792073656c6c6f3a202d2d2d2920546a20542a0a45540a656e6473747265616d0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303538203030303030206e200a30303030303030313135203030303030206e200a30303030303030323431203030303030206e200a30303030303030333338203030303030206e200a747261696c65720a3c3c202f53697a652036202f526f6f74203120302052203e3e0a7374617274787265660a3538390a2525454f460a', 'hex'), pg_temp.agente(6), '2026-10-01 08:58');

-- Novedades del banco de horas y de pases (B y C):
--   14. FC de Moreno del 22/10, aplicado el 05/10: consumió FIFO 6 h del crédito que vence el 14/11 y
--       6 h del que vence el 11/03/2027.
--   15. HE de Moreno del 01/10 (se queda hasta las 22:05: cómputo doble desde las 21:00).
--   16. HE de Correa del 05/10, día hábil (se va 16:40: 1 h 30 min en bloques de 30 min).
--   17. Devolución del PSSR de Paz (06/10): 08/10 de 13:00 a 15:00, dentro de los 7 días.
--   18. HE de Moreno del sábado 12/09 (corte de energía programado): originó el crédito del 12/09.
--   19. PSSR de Moreno en la guardia del domingo 04/10 (20:00-08:00): sale 05:30, 2 h 30 min
--       pendientes de devolución (plazo de 7 días corridos: hasta el 11/10).
INSERT INTO Novedad (id, tipoNovedadID, agenteID, fechaDesde, fechaHasta, horaDesde, horaHasta, cantHoras, motivo,
                     justificacion, estado, origen, solicitadoPorID, fechaSolicitud, novedadOrigenID, version,
                     createdAt, updatedAt)
VALUES
 (pg_temp.nov(14), pg_temp.tipo('FC'), pg_temp.agente(5), '2026-10-22', '2026-10-22', NULL, NULL, 12,
  'Franco compensatorio por las horas extras del invierno.', NULL, 'APLICADA', 'SOLICITUD', pg_temp.agente(5),
  '2026-10-02 09:15', NULL, 5, '2026-10-02 09:10', '2026-10-05 12:40'),
 (pg_temp.nov(15), pg_temp.tipo('HE'), pg_temp.agente(5), '2026-10-01', '2026-10-01', NULL, NULL, NULL,
  'Cobertura de la guardia nocturna hasta el relevo.', 'Necesidad del servicio', 'APLICADA', 'SOLICITUD',
  pg_temp.agente(5),
  '2026-10-01 19:30', NULL, 5, '2026-10-01 19:28', '2026-10-02 11:10'),
 (pg_temp.nov(16), pg_temp.tipo('HE'), pg_temp.agente(11), '2026-10-05', '2026-10-05', NULL, NULL, NULL,
  'Cierre de la facturación mensual de prestaciones.', 'Necesidad del servicio', 'APLICADA', 'SOLICITUD',
  pg_temp.agente(11),
  '2026-10-05 17:00', NULL, 5, '2026-10-05 16:58', '2026-10-06 11:30'),
 (pg_temp.nov(17), pg_temp.tipo('DEV'), pg_temp.agente(1), '2026-10-08', '2026-10-08', '13:00', '15:00', 2,
  'Devolución del pase sin retorno del 06/10.', NULL, 'APLICADA', 'SOLICITUD', pg_temp.agente(1),
  '2026-10-06 12:00', pg_temp.nov(8), 5, '2026-10-06 11:58', '2026-10-06 16:00'),
 (pg_temp.nov(18), pg_temp.tipo('HE'), pg_temp.agente(5), '2026-09-12', '2026-09-12', NULL, NULL, 10,
  'Permanencia en el servicio durante el corte de energía del sábado, con pacientes internados.',
  'Necesidad de realizar tareas de mantenimiento eléctrico con corte programado', 'APLICADA', 'SOLICITUD',
  pg_temp.agente(5), '2026-09-10 10:20', NULL, 5, '2026-09-10 10:18', '2026-09-14 12:30'),
 (pg_temp.nov(19), pg_temp.tipo('PSSR'), pg_temp.agente(5), '2026-10-04', '2026-10-04', '05:30', '08:00', 2.5,
  'Viaje a Río Cuarto a primera hora del lunes por un trámite familiar.', NULL, 'APLICADA', 'SOLICITUD',
  pg_temp.agente(5), '2026-10-02 09:30', NULL, 5, '2026-10-02 09:28', '2026-10-02 14:10');
INSERT INTO NovedadAutorizacion (id, novedadID, instancia, orden, estado, resueltoPorID, fechaResolucion, comentarios)
VALUES
 ('de400000-0000-4000-a200-000000000141', pg_temp.nov(14), 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-02 12:00', 'Cobertura asegurada.'),
 ('de400000-0000-4000-a200-000000000142', pg_temp.nov(14), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-05 10:15', 'Saldo vigente suficiente.'),
 ('de400000-0000-4000-a200-000000000143', pg_temp.nov(14), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-05 12:40', NULL),
 ('de400000-0000-4000-a200-000000000151', pg_temp.nov(15), 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-01 19:45', 'Autorizo hasta el relevo.'),
 ('de400000-0000-4000-a200-000000000152', pg_temp.nov(15), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-02 09:00', NULL),
 ('de400000-0000-4000-a200-000000000153', pg_temp.nov(15), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-02 11:10', NULL),
 ('de400000-0000-4000-a200-000000000161', pg_temp.nov(16), 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-05 18:00', NULL),
 ('de400000-0000-4000-a200-000000000162', pg_temp.nov(16), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-06 09:20', NULL),
 ('de400000-0000-4000-a200-000000000163', pg_temp.nov(16), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-06 11:30', NULL),
 ('de400000-0000-4000-a200-000000000171', pg_temp.nov(17), 'DIRECCION', 1, 'AUTORIZADO', pg_temp.dh(), '2026-10-06 13:00', NULL),
 ('de400000-0000-4000-a200-000000000172', pg_temp.nov(17), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-06 14:30', 'Dentro del plazo de 7 días.'),
 ('de400000-0000-4000-a200-000000000173', pg_temp.nov(17), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-06 16:00', NULL),
 ('de400000-0000-4000-a200-000000000181', pg_temp.nov(18), 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-09-10 12:00', 'Corte programado por Mantenimiento.'),
 ('de400000-0000-4000-a200-000000000182', pg_temp.nov(18), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-09-11 09:30', NULL),
 ('de400000-0000-4000-a200-000000000183', pg_temp.nov(18), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-09-11 13:00', NULL),
 ('de400000-0000-4000-a200-000000000191', pg_temp.nov(19), 'JEFATURA', 1, 'AUTORIZADO', pg_temp.agente(1), '2026-10-02 11:00', 'Cubre el relevo de las 08:00.'),
 ('de400000-0000-4000-a200-000000000192', pg_temp.nov(19), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-10-02 12:30', 'Dentro del tope mensual de pases.'),
 ('de400000-0000-4000-a200-000000000193', pg_temp.nov(19), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-02 14:10', NULL);

UPDATE JornadaLaboral SET tipo = 'FRANCO_COMPENSATORIO', fechaIngreso = NULL, fechaEgreso = NULL,
       novedadID = pg_temp.nov(14), observaciones = 'Franco compensatorio'
WHERE diagramaID = 'de400000-0000-4000-d000-000000000010' AND agenteID = pg_temp.agente(5)
  AND fecha = '2026-10-22';

-- Banco de horas: créditos de horas extras para FC con vencimiento a 180 días (RV-BANCO-VENC).
-- Las horas extras del 01 al 05/10 las acredita el cotejo al generar los partes (captura P04).
INSERT INTO MovimientoHoras (id, agenteID, novedadID, fecha, tipo, horas, concepto, saldoResultante,
                             fechaVencimiento, horasRestantes, creditoID, registradoPorID)
SELECT ('de400000-0000-4000-a400-0000000000' || lpad(v.n::text, 2, '0'))::uuid, pg_temp.agente(v.n), NULL,
       DATE '2026-08-14', 'CREDITO', v.h, 'Horas extras para FC acumuladas (saldo al 14/08/2026)', v.h,
       DATE '2026-08-14' + 180, v.h, NULL, pg_temp.op()
FROM (VALUES (1, 6), (2, 3), (3, 2), (4, 10), (7, 4), (8, 8), (9, 20), (10, 8)) AS v(n, h);
-- Moreno: un crédito vencido, dos vigentes y el FC del 22/10 que los consumió por orden de vencimiento.
INSERT INTO MovimientoHoras (id, agenteID, novedadID, fecha, tipo, horas, concepto, saldoResultante,
                             fechaVencimiento, horasRestantes, creditoID, registradoPorID)
VALUES
 ('de400000-0000-4000-a400-000000000101', pg_temp.agente(5), NULL, '2026-03-02', 'CREDITO', 2,
  'Horas extras para FC del 02/03/2026: 2 h (2 h trabajadas en bloques de 30 min)', 2, '2026-08-29', 0, NULL, pg_temp.op()),
 ('de400000-0000-4000-a400-000000000102', pg_temp.agente(5), NULL, '2026-08-30', 'VENCIMIENTO', -2,
  'Vencimiento del crédito del 02/03/2026', 0, NULL, NULL, 'de400000-0000-4000-a400-000000000101', pg_temp.op()),
 ('de400000-0000-4000-a400-000000000103', pg_temp.agente(5), NULL, '2026-05-18', 'CREDITO', 6,
  'Horas extras para FC del 18/05/2026: 6 h (6 h trabajadas en bloques de 30 min)', 8, '2026-11-14', 0, NULL, pg_temp.op()),
 ('de400000-0000-4000-a400-000000000104', pg_temp.agente(5), pg_temp.nov(18), '2026-09-12', 'CREDITO', 10,
  'Horas extras para FC del 12/09/2026: 10 h (5 h trabajadas en bloques de 30 min; 10 bloques dobles)', 16, '2027-03-11', 4,
  NULL, pg_temp.op()),
 ('de400000-0000-4000-a400-000000000105', pg_temp.agente(5), pg_temp.nov(14), '2026-10-05', 'DEBITO', -6,
  'Franco compensatorio del 22/10/2026 (crédito del 18/05/2026)', 10, NULL, NULL,
  'de400000-0000-4000-a400-000000000103', pg_temp.dh()),
 ('de400000-0000-4000-a400-000000000106', pg_temp.agente(5), pg_temp.nov(14), '2026-10-05', 'DEBITO', -6,
  'Franco compensatorio del 22/10/2026 (crédito del 12/09/2026)', 4, NULL, NULL,
  'de400000-0000-4000-a400-000000000104', pg_temp.dh());
UPDATE Agente e SET saldoHoras = COALESCE((SELECT SUM(m.horasRestantes) FROM MovimientoHoras m
                                              WHERE m.agenteID = e.id AND m.tipo = 'CREDITO'
                                                AND m.fechaVencimiento >= DATE '2026-10-07'), 0)
WHERE e.id::text LIKE 'de300000-%';

UPDATE JornadaLaboral SET tipo = 'LICENCIA', fechaIngreso = NULL, fechaEgreso = NULL,
       novedadID = pg_temp.nov(9), observaciones = 'Licencia VeDi 2026-118734'
WHERE diagramaID = 'de400000-0000-4000-d000-000000000010' AND agenteID = pg_temp.agente(3)
  AND fecha BETWEEN '2026-10-05' AND '2026-10-09';


-- ---------------------------------------------------------------------------
-- Avisos en la aplicación (CU27)
-- ---------------------------------------------------------------------------
INSERT INTO Notificacion (id, usuarioID, tipo, asunto, cuerpo, entidad, entidadID, fechaCreacion, fechaLectura, enviadaEmail)
SELECT ('de400000-0000-4000-a500-' || lpad((row_number() OVER ())::text, 12, '0'))::uuid,
       v.u, v.tipo, v.asunto, v.cuerpo, 'Novedad', pg_temp.nov(v.nov), v.f::timestamp, v.leida::timestamp, v.mail
FROM (VALUES
 (pg_temp.agente(5), 'NOVEDAD_PENDIENTE_JEFATURA', 1,
  'Solicitud registrada: Cambio de guardia de Moreno, Daniela (18/10/2026 al 23/10/2026)',
  'La solicitud quedó pendiente de la Jefatura (JS).', '2026-10-05 08:14', '2026-10-05 08:20', TRUE),
 (pg_temp.agente(5), 'NOVEDAD_PENDIENTE_OFICINA_PERSONAL', 1,
  'Autorizada: Cambio de guardia de Moreno, Daniela (18/10/2026 al 23/10/2026)',
  'Jefatura (JS) autorizó la novedad. Ahora está pendiente de Personal (OP).' || chr(10) || 'Comentario: Cobertura verificada.',
  '2026-10-05 10:32', NULL, TRUE),
 (pg_temp.agente(4), 'NOVEDAD_PENDIENTE_OFICINA_PERSONAL', 1,
  'Autorizada: Cambio de guardia de Moreno, Daniela (18/10/2026 al 23/10/2026)',
  'Jefatura (JS) autorizó la novedad. Ahora está pendiente de Personal (OP).', '2026-10-05 10:32', NULL, TRUE),
 (pg_temp.agente(11), 'NOVEDAD_OBSERVADA', 6,
  'Novedad observada: Omisión de marcación de Correa, Agustina (02/10/2026)',
  'Personal (OP) devolvió para corrección la novedad.' || chr(10)
   || 'Comentario: Indicar el horario real de ingreso y adjuntar la constancia firmada por el jefe de guardia.',
  '2026-10-05 11:45', NULL, FALSE),
 (pg_temp.agente(1), 'NOVEDAD_PENDIENTE', 3,
  'Novedad pendiente de Jefatura (JS): Cambio de horario de Sosa, Martín (14/10/2026)',
  'Espera la decisión de Jefatura (JS).', '2026-10-06 11:05', NULL, TRUE),
 (pg_temp.agente(1), 'NOVEDAD_PENDIENTE', 4,
  'Novedad pendiente de Jefatura (JS): Cambio de guardia de Godoy, Sebastián (24/10/2026 al 25/10/2026)',
  'Espera la decisión de Jefatura (JS).', '2026-10-06 18:20', NULL, TRUE),
 (pg_temp.op(), 'NOVEDAD_PENDIENTE', 1,
  'Novedad pendiente de Personal (OP): Cambio de guardia de Moreno, Daniela (18/10/2026 al 23/10/2026)',
  'Espera la decisión de Personal (OP).', '2026-10-05 10:32', NULL, TRUE),
 (pg_temp.op(), 'NOVEDAD_PENDIENTE', 2,
  'Novedad pendiente de Personal (OP): Franco compensatorio de Juárez, Florencia (17/10/2026)',
  'Espera la decisión de Personal (OP).', '2026-10-02 13:05', '2026-10-02 14:00', TRUE),
 (pg_temp.dh(), 'NOVEDAD_PENDIENTE', 5,
  'Novedad pendiente de Dirección (DH): Falta justificada de Figueroa, Natalia (06/10/2026)',
  'Espera la decisión de Dirección (DH).', '2026-10-06 15:20', NULL, TRUE)
) AS v(u, tipo, nov, asunto, cuerpo, f, leida, mail);


-- ---------------------------------------------------------------------------
-- Reglas de validación (P12, CU08): versión anterior cerrada del tope de
-- faltas justificadas (en 2025 el tope era de 3 por mes).
-- ---------------------------------------------------------------------------
INSERT INTO ReglaValidacion (id, codigo, nombre, descripcion, ambito, modalidad, severidad, parametros,
                             vigenteDesde, vigenteHasta, activa)
SELECT 'de400000-0000-4000-a600-000000000001', codigo, nombre, descripcion, ambito, modalidad, severidad,
       '{"topeMensual": 3}', '2025-01-01', '2025-12-31', TRUE
FROM ReglaValidacion WHERE codigo = 'RV-NOV-TOPE-FJ' AND vigenteDesde = '2026-01-01';

-- ---------------------------------------------------------------------------
-- Octubre aprobado de otros servicios (para que el parte diario abarque al
-- hospital): Cirugía, Diagnóstico por Imágenes, Enfermería y Farmacia.
-- ---------------------------------------------------------------------------
CREATE FUNCTION pg_temp.u(c BIGINT) RETURNS UUID LANGUAGE sql STABLE AS
$$ SELECT id FROM Usuario WHERE cuil = c $$;

INSERT INTO DiagramaDeServicio (id, estado, fechaInicio, fechaFin, servicioID, version, creadoPorID,
                                createdAt, updatedAt, aprobadoPorID, fechaAprobacion)
SELECT v.id::uuid, 'APROBADO', '2026-10-01', '2026-10-31', (SELECT id FROM Servicio WHERE nombre = v.serv), 3,
       pg_temp.u(v.js), '2026-09-15 09:00', '2026-09-27 11:20', pg_temp.op(), '2026-09-27 11:20'
FROM (VALUES ('de400000-0000-4000-d000-000000000020', 'Cirugía', 20111111177),
             ('de400000-0000-4000-d000-000000000021', 'Diagnóstico por Imágenes', 20111111188),
             ('de400000-0000-4000-d000-000000000022', 'Enfermería', 27111111199),
             ('de400000-0000-4000-d000-000000000023', 'Farmacia', 27111122211)) AS v(id, serv, js);

-- Dotación mínima de esos servicios (CU13, indicadores de cobertura CU36).
UPDATE Servicio SET dotacionMinima = v.dot
FROM (VALUES ('Cirugía', 2), ('Diagnóstico por Imágenes', 2), ('Enfermería', 2), ('Farmacia', 1)) AS v(nombre, dot)
WHERE Servicio.nombre = v.nombre;

-- Patrón por agente: (diagrama, CUIL, regla, hora de ingreso, duración).
--   HAB = días hábiles · PAR / IMPAR = días hábiles pares / impares
--   LJ = lunes a jueves · C3-n = cada 3 días desde el día n
CREATE TEMP TABLE patron_otros (diag TEXT, cuil BIGINT, regla TEXT, hin TIME, dur INT) ON COMMIT DROP;
INSERT INTO patron_otros VALUES
 ('20', 20111111177, 'PAR',   '08:00', 12), ('20', 20111144422, 'IMPAR', '08:00', 12),
 ('20', 27111133322, 'HAB',   '07:00', 7),  ('20', 20111144488, 'HAB',   '07:00', 7),
 ('21', 20111111188, 'LJ',    '08:00', 6),  ('21', 20111122288, 'LJ',    '08:00', 6),
 ('21', 20111144455, 'LJ',    '08:00', 6),
 ('22', 27111111199, 'HAB',   '07:00', 7),  ('22', 27111133377, 'C3-1',  '07:00', 12),
 ('22', 20111133388, 'C3-2',  '19:00', 12), ('22', 27111133399, 'C3-3',  '07:00', 12),
 ('23', 27111122211, 'HAB',   '07:00', 7);

INSERT INTO JornadaLaboral (id, diagramaID, agenteID, fecha, tipo, fechaIngreso, fechaEgreso)
SELECT gen_random_uuid(), ('de400000-0000-4000-d000-0000000000' || p.diag)::uuid, pg_temp.u(p.cuil), d::date,
       CASE WHEN t.trabaja THEN (CASE WHEN p.dur >= 12 THEN 'GUARDIA_ACTIVA' ELSE 'TURNO_NORMAL' END)::tipo_jornada
            ELSE 'FRANCO'::tipo_jornada END,
       CASE WHEN t.trabaja THEN d::date + p.hin END,
       CASE WHEN t.trabaja THEN d::date + p.hin + make_interval(hours => p.dur) END
FROM patron_otros p
CROSS JOIN generate_series(DATE '2026-10-01', DATE '2026-10-31', INTERVAL '1 day') AS d
CROSS JOIN LATERAL (SELECT CASE
        WHEN p.regla = 'HAB'   THEN extract(isodow FROM d) <= 5 AND d::date <> DATE '2026-10-12'
        WHEN p.regla = 'PAR'   THEN extract(isodow FROM d) <= 5 AND d::date <> DATE '2026-10-12' AND extract(day FROM d)::int % 2 = 0
        WHEN p.regla = 'IMPAR' THEN extract(isodow FROM d) <= 5 AND d::date <> DATE '2026-10-12' AND extract(day FROM d)::int % 2 = 1
        WHEN p.regla = 'LJ'    THEN extract(isodow FROM d) <= 4 AND d::date <> DATE '2026-10-12'
        ELSE (extract(day FROM d)::int - substr(p.regla, 4)::int) % 3 = 0
    END AS trabaja) t;

-- Comisión de servicio de Torres (Farmacia) el 06/10, aprobada (la jefa: 1.ª instancia la DH).
INSERT INTO Novedad (id, tipoNovedadID, agenteID, fechaDesde, fechaHasta, motivo, estado, origen, solicitadoPorID,
                     fechaSolicitud, version, createdAt, updatedAt)
VALUES (pg_temp.nov(13), pg_temp.tipo('CS'), pg_temp.u(27111122211), '2026-10-06', '2026-10-06',
        'Congreso Argentino de Farmacia Hospitalaria.', 'APLICADA', 'SOLICITUD', pg_temp.u(27111122211),
        '2026-09-29 10:20', 5, '2026-09-29 10:18', '2026-10-01 12:30');
INSERT INTO NovedadAutorizacion (id, novedadID, instancia, orden, estado, resueltoPorID, fechaResolucion, comentarios)
VALUES ('de400000-0000-4000-a200-000000000101', pg_temp.nov(13), 'DIRECCION', 1, 'AUTORIZADO', pg_temp.dh(), '2026-09-29 13:00', 'Autorizado.'),
       ('de400000-0000-4000-a200-000000000102', pg_temp.nov(13), 'OFICINA_PERSONAL', 2, 'AUTORIZADO', pg_temp.op(), '2026-09-30 09:40', 'Constancia de inscripción conforme.'),
       ('de400000-0000-4000-a200-000000000103', pg_temp.nov(13), 'DIRECCION', 3, 'AUTORIZADO', pg_temp.dh(), '2026-10-01 12:30', NULL);


-- ---------------------------------------------------------------------------
-- Marcaciones del 05 y 06/10 (P04, Fig. 24): dos lotes del sistema de relojes
-- y las jornadas registradas que conformó la importación. Situaciones del 06/10:
--   Martínez 10 min tarde · Correa 4 min tarde (dentro de la tolerancia) ·
--   Figueroa sin marcaciones (falta justificada pendiente de la DH) ·
--   Sosa sin egreso · Paz se retira a las 11:00 (pase sin retorno aprobado, PSSR 333) ·
--   Torres en comisión de servicio · Moreno asiste de franco (capacitación) ·
--   Villalba de licencia (VeDi).
-- ---------------------------------------------------------------------------
INSERT INTO LoteMarcaciones (id, nombreArchivo, hashArchivo, fechaImportacion, importadoPorID, cantidadRegistros,
                             rangoDesde, rangoHasta)
SELECT ('de400000-0000-4000-a700-00000000000' || d)::uuid, 'reloj_hmu_2026100' || d || '.csv',
       'de400000a7000000000000000000000000000000000000000000000000000' || d || 'f',
       DATE '2026-10-01' + d + TIME '08:45', pg_temp.op(), 0,
       DATE '2026-09-30' + d, DATE '2026-09-30' + d + CASE WHEN d = 6 THEN 1 ELSE 0 END
FROM generate_series(1, 6) AS d;

CREATE TEMP TABLE marcas (ag UUID, jornada INT, ingreso TIMESTAMP, egreso TIMESTAMP) ON COMMIT DROP;
INSERT INTO marcas
SELECT j.agenteID, row_number() OVER (ORDER BY j.agenteID, j.fecha)::int,
       j.fechaIngreso - make_interval(mins => ((row_number() OVER (ORDER BY j.agenteID, j.fecha)) * 7 % 9)::int),
       j.fechaEgreso + make_interval(mins => ((row_number() OVER (ORDER BY j.agenteID, j.fecha)) * 5 % 8)::int)
FROM JornadaLaboral j JOIN DiagramaDeServicio d ON d.id = j.diagramaID AND d.estado = 'APROBADO'
WHERE j.fecha BETWEEN '2026-10-01' AND '2026-10-06' AND j.fechaIngreso IS NOT NULL;

-- Situaciones del 01 al 05/10 (para los indicadores, CU36).
DELETE FROM marcas m WHERE m.ag = pg_temp.u(20111133388) AND m.ingreso::date = '2026-10-02';   -- Luna: inasistencia
DELETE FROM marcas m WHERE m.ag = pg_temp.u(20111144488) AND m.ingreso::date = '2026-10-05';   -- Soria: inasistencia
DELETE FROM marcas m WHERE m.ag = pg_temp.agente(6) AND m.ingreso::date BETWEEN '2026-10-01' AND '2026-10-03'; -- Godoy: JI
UPDATE marcas m SET ingreso = ingreso + interval '25 minutes'
WHERE m.ag = pg_temp.u(20111122288) AND m.ingreso::date = '2026-10-01';                         -- Castro: tarde
-- Horas extras para FC (las acredita el cotejo): Moreno hasta las 22:05 (doble desde las 21:00),
-- Cáceres tras la guardia hasta el domingo 09:10 (doble) y Correa, día hábil, hasta las 16:40.
UPDATE marcas m SET egreso = '2026-10-01 22:05' WHERE m.ag = pg_temp.agente(5) AND m.ingreso::date = '2026-10-01';
UPDATE marcas m SET egreso = '2026-10-04 09:10' WHERE m.ag = pg_temp.agente(8) AND m.ingreso::date = '2026-10-03';
UPDATE marcas m SET egreso = '2026-10-05 16:40' WHERE m.ag = pg_temp.agente(11) AND m.ingreso::date = '2026-10-05';
-- Moreno se retira a las 05:30 de la guardia del 04/10 (pase sin retorno aprobado).
UPDATE marcas m SET egreso = '2026-10-05 05:30' WHERE m.ag = pg_temp.agente(5) AND m.ingreso::date = '2026-10-04';

-- Situaciones del 06/10.
UPDATE marcas SET ingreso = date_trunc('minute', ingreso) WHERE true;
UPDATE marcas m SET ingreso = '2026-10-06 08:10', egreso = '2026-10-06 20:05'
WHERE m.ag = pg_temp.u(20111111177) AND m.ingreso::date = '2026-10-06';
UPDATE marcas m SET ingreso = '2026-10-06 08:04', egreso = '2026-10-06 15:02'
WHERE m.ag = pg_temp.agente(11) AND m.ingreso::date = '2026-10-06';
DELETE FROM marcas m WHERE m.ag = pg_temp.agente(7) AND m.ingreso::date = '2026-10-06';
UPDATE marcas m SET ingreso = '2026-10-06 06:57', egreso = NULL
WHERE m.ag = pg_temp.agente(2) AND m.ingreso::date = '2026-10-06';
UPDATE marcas m SET ingreso = '2026-10-06 07:00', egreso = '2026-10-06 11:00'
WHERE m.ag = pg_temp.agente(1) AND m.ingreso::date = '2026-10-06';
DELETE FROM marcas m WHERE m.ag = pg_temp.u(27111122211) AND m.ingreso::date = '2026-10-06';
UPDATE marcas m SET ingreso = '2026-10-06 19:52', egreso = '2026-10-07 08:05'
WHERE m.ag = pg_temp.agente(4) AND m.ingreso::date = '2026-10-06';
INSERT INTO marcas VALUES (pg_temp.agente(5), 9999, '2026-10-06 08:00', '2026-10-06 12:00');

CREATE TEMP TABLE marcacion_seed (id UUID, ag UUID, fh TIMESTAMP, ingreso BOOLEAN, jornada INT) ON COMMIT DROP;
INSERT INTO marcacion_seed SELECT gen_random_uuid(), ag, ingreso, TRUE, jornada FROM marcas;
INSERT INTO marcacion_seed SELECT gen_random_uuid(), ag, egreso, FALSE, jornada FROM marcas WHERE egreso IS NOT NULL;

INSERT INTO Marcacion (id, fechaMarcacion, observaciones, tipoMarcacion, validada, agenteID, loteID, dispositivo)
SELECT id, fh, NULL, CASE WHEN ingreso THEN 'INGRESO' ELSE 'EGRESO' END::tipo_marcacion, TRUE, ag,
       ('de400000-0000-4000-a700-00000000000' || LEAST(6, GREATEST(1, extract(day FROM fh)::int))
            || '')::uuid,
       CASE WHEN extract(minute FROM fh)::int % 2 = 0 THEN 'Reloj acceso norte' ELSE 'Reloj guardia central' END
FROM marcacion_seed;

UPDATE LoteMarcaciones l SET cantidadRegistros = (SELECT count(*) FROM Marcacion m WHERE m.loteID = l.id)
WHERE l.id::text LIKE 'de400000-%';

INSERT INTO JornadaRegistrada (id, fecha, agenteID, marcacionIngresoID, marcacionEgresoID, duracionMinutos,
                                    omisionIngreso, omisionEgreso)
SELECT gen_random_uuid(), i.fh::date, i.ag, i.id, e.id,
       CASE WHEN e.fh IS NOT NULL THEN (extract(epoch FROM e.fh - i.fh) / 60)::int END,
       FALSE, e.id IS NULL
FROM marcacion_seed i LEFT JOIN marcacion_seed e ON e.jornada = i.jornada AND e.ag = i.ag AND NOT e.ingreso
WHERE i.ingreso;

-- ---------------------------------------------------------------------------
-- Calendario de feriados 2026 (P13, CU09) y un alta pendiente de la DH.
-- Sólo las fechas que todavía no tengan un feriado activo.
-- ---------------------------------------------------------------------------
INSERT INTO Feriado (id, fecha, descripcion, estado, creadoPorID, createdAt)
SELECT ('de400000-0000-4000-a800-' || lpad(row_number() OVER (ORDER BY v.f)::text, 12, '0'))::uuid,
       v.f::date, v.d, 'VIGENTE', pg_temp.op(), '2025-12-15 10:00'
FROM (VALUES ('2026-01-01', 'Año Nuevo'), ('2026-02-16', 'Carnaval'), ('2026-02-17', 'Carnaval'),
             ('2026-03-24', 'Día Nacional de la Memoria por la Verdad y la Justicia'),
             ('2026-04-02', 'Día del Veterano y de los Caídos en la Guerra de Malvinas'),
             ('2026-04-03', 'Viernes Santo'), ('2026-05-01', 'Día del Trabajador'),
             ('2026-05-25', 'Día de la Revolución de Mayo'),
             ('2026-06-15', 'Paso a la Inmortalidad del Gral. Martín Miguel de Güemes'),
             ('2026-06-20', 'Paso a la Inmortalidad del Gral. Manuel Belgrano'),
             ('2026-07-09', 'Día de la Independencia'),
             ('2026-08-17', 'Paso a la Inmortalidad del Gral. José de San Martín'),
             ('2026-11-23', 'Día de la Soberanía Nacional'), ('2026-12-25', 'Navidad')) AS v(f, d)
WHERE NOT EXISTS (SELECT 1 FROM Feriado x WHERE x.fecha = v.f::date
                  AND x.estado IN ('VIGENTE', 'ALTA_PENDIENTE', 'BAJA_PENDIENTE'));

INSERT INTO Feriado (id, fecha, descripcion, estado, creadoPorID, createdAt)
SELECT 'de400000-0000-4000-a800-000000000099', DATE '2026-11-06',
       'Asueto administrativo: Día del Trabajador Municipal', 'ALTA_PENDIENTE', pg_temp.op(), '2026-10-05 12:10'
WHERE NOT EXISTS (SELECT 1 FROM Feriado x WHERE x.fecha = DATE '2026-11-06'
                  AND x.estado IN ('VIGENTE', 'ALTA_PENDIENTE', 'BAJA_PENDIENTE'));

-- Feriados cargados a mano en la BD de desarrollo sin tildes.
UPDATE Feriado SET descripcion = 'Día del Respeto a la Diversidad Cultural'
WHERE fecha = DATE '2026-10-12' AND descripcion = 'Dia del Respeto a la Diversidad Cultural';
UPDATE Feriado SET descripcion = 'Inmaculada Concepción'
WHERE fecha = DATE '2026-12-08' AND descripcion = 'Inmaculada Concepcion';

COMMIT;
