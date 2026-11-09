-- ============================================================================
-- Base del seed de capturas: servicio "Guardia Central" con 11 agentes
-- ficticios, sus modalidades horarias, el feriado del 12/10/2026 y los
-- diagramas de noviembre de otros servicios para la bandeja de la OP (P02).
--
-- Adaptado de los datos de demo de las capturas P01/P02. Todo es ficticio:
-- nombres inventados, CUIL con dígito verificador INVÁLIDO, mails inventados.
-- No contiene contraseñas: la jefa demo usa el mismo hash que la OP
-- (se copia en la base al cargar el escenario).
--
-- No usar directamente: lo incluye 03_escenario_demo.sql (dentro de su transacción).
-- ============================================================================
-- Servicio (encargado se completa después de crear a la jefa) -----------------
INSERT INTO Servicio (id, nombre, agrupacion, direccionID, encargadoUsuarioID, dotacionMinima)
VALUES ('de300000-0000-4000-a000-000000000001', 'Guardia Central', 'MEDICO',
        (SELECT u.id FROM Usuario u WHERE u.cuil = 20111111122), NULL, 2);

-- Agentes ----------------------------------------------------------------------
-- passwordHash de la jefa = mismo hash que Medina (contraseña de prueba HMU.2026);
-- el resto no necesita iniciar sesión.
INSERT INTO Usuario (id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel,
                     domicilioID, cargoID, servicioID, passwordHash)
SELECT v.id::uuid, v.alta::date, TRUE, v.cuil, v.ape, v.nom, v.sexo::sexo, v.mail, NULL,
       NULL, (SELECT id FROM Cargo WHERE numero = v.cargo),
       'de300000-0000-4000-a000-000000000001',
       CASE WHEN v.id LIKE '%b000-000000000001'
            THEN (SELECT passwordHash FROM Usuario WHERE cuil = 27111122277)
            ELSE 'demo-sin-login' END
FROM (VALUES
 ('de300000-0000-4000-b000-000000000001','2012-04-02',27991000018,'Paz','Mariela','FEMENINO','mariela.paz@hmu.com.ar',300),
 ('de300000-0000-4000-b000-000000000002','2016-08-15',20991000021,'Sosa','Martín','MASCULINO','martin.sosa@hmu.com.ar',400),
 ('de300000-0000-4000-b000-000000000003','2018-03-01',27991000034,'Villalba','Carolina','FEMENINO','carolina.villalba@hmu.com.ar',700),
 ('de300000-0000-4000-b000-000000000004','2011-06-20',20991000048,'Ledesma','Hugo','MASCULINO','hugo.ledesma@hmu.com.ar',800),
 ('de300000-0000-4000-b000-000000000005','2019-02-11',27991000050,'Moreno','Daniela','FEMENINO','daniela.moreno@hmu.com.ar',800),
 ('de300000-0000-4000-b000-000000000006','2015-10-05',20991000064,'Godoy','Sebastián','MASCULINO','sebastian.godoy@hmu.com.ar',700),
 ('de300000-0000-4000-b000-000000000007','2020-07-01',27991000077,'Figueroa','Natalia','FEMENINO','natalia.figueroa@hmu.com.ar',700),
 ('de300000-0000-4000-b000-000000000008','2017-09-18',20991000080,'Cáceres','Ezequiel','MASCULINO','ezequiel.caceres@hmu.com.ar',700),
 ('de300000-0000-4000-b000-000000000009','2021-01-04',27991000093,'Juárez','Florencia','FEMENINO','florencia.juarez@hmu.com.ar',800),
 ('de300000-0000-4000-b000-000000000010','2014-05-12',20991000102,'Maldonado','Gustavo','MASCULINO','gustavo.maldonado@hmu.com.ar',700),
 ('de300000-0000-4000-b000-000000000011','2022-03-21',27991000115,'Correa','Agustina','FEMENINO','agustina.correa@hmu.com.ar',500)
) AS v(id, alta, cuil, ape, nom, sexo, mail, cargo);

INSERT INTO Agente (id, francosCompensatoriosUtilizados, horarioActualID, veDiagramaCompleto)
SELECT id, 0, NULL, TRUE FROM Usuario WHERE id::text LIKE 'de300000-%';

INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde)
SELECT u.id, r.id, u.fechaAlta FROM Usuario u, Rol r
WHERE u.id::text LIKE 'de300000-%' AND r.nombre = 'Empleado';
-- Jefatura de Guardia Central con vigencia y memorando de designación (CU06).
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde, servicioID, memorandoRef, otorgadoPorID)
SELECT 'de300000-0000-4000-b000-000000000001', id, DATE '2024-03-01', 'de300000-0000-4000-a000-000000000001',
       'Memorándum DH N.º 47/2024', (SELECT id FROM Usuario WHERE cuil = 27111122277)
FROM Rol WHERE nombre = 'JefaturaDeServicio';
-- Jefatura interina de Ledesma durante las vacaciones de Paz (finalizada).
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde, fechaHasta, servicioID, memorandoRef, otorgadoPorID)
SELECT 'de300000-0000-4000-b000-000000000004', id, DATE '2026-07-01', DATE '2026-07-31',
       'de300000-0000-4000-a000-000000000001', 'Memorándum DH N.º 88/2026', (SELECT id FROM Usuario WHERE cuil = 27111122277)
FROM Rol WHERE nombre = 'JefaturaDeServicio';

UPDATE Servicio SET encargadoUsuarioID = 'de300000-0000-4000-b000-000000000001'
WHERE id = 'de300000-0000-4000-a000-000000000001';

-- Horarios (Class Table Inheritance: HorarioBase -> Horario -> modalidad) -----
INSERT INTO HorarioBase (id, tipo)
SELECT ('de300000-0000-4000-c000-0000000000' || lpad(n::text, 2, '0'))::uuid, 'Horario'
FROM generate_series(1, 11) n;

INSERT INTO Horario (id, fechaIngreso, fechaEgreso, jornadasPlanificadas, reglasHorario, horarioBaseID, modalidad)
SELECT ('de300000-0000-4000-c000-0000000000' || lpad(v.n::text, 2, '0'))::uuid,
       '2026-01-01 00:00', '2026-12-31 23:59', NULL, NULL,
       ('de300000-0000-4000-c000-0000000000' || lpad(v.n::text, 2, '0'))::uuid, v.modalidad::modalidad_horaria
FROM (VALUES (1,'HorarioJefeServicioGuardiaPasiva'), (2,'HorarioEstandar'), (3,'HorarioSemanal'),
             (4,'HorarioGuardiaMedica'), (5,'HorarioGuardiaMedica'), (6,'HorarioNocturno'),
             (7,'HorarioNocturno'), (8,'HorarioGuardiaEnfermeria'), (9,'HorarioFeriante'),
             (10,'HorarioGuardiaEnfermeria'), (11,'HorarioSemanal')) AS v(n, modalidad);

-- Paz (jefa): guardia pasiva, 6 h L-V (7 h menos 1)
INSERT INTO HorarioJefeServicioGuardiaPasiva (id, diasLaborables, horasPorDia) VALUES
 ('de300000-0000-4000-c000-000000000001', 'LUNES,MARTES,MIERCOLES,JUEVES,VIERNES', 6);
-- Sosa: estándar 7 h L-V
INSERT INTO HorarioEstandar (id, diasLaborables, horasPorDia) VALUES
 ('de300000-0000-4000-c000-000000000002', 'LUNES,MARTES,MIERCOLES,JUEVES,VIERNES', 7);
-- Villalba: semanal turno tarde 14-21 / Correa: semanal 08-15
INSERT INTO HorarioSemanal (id, distribucionSemanal, horaInicioPorDia) VALUES
 ('de300000-0000-4000-c000-000000000003', 'LUNES=7,MARTES=7,MIERCOLES=7,JUEVES=7,VIERNES=7',
  'LUNES=14:00,MARTES=14:00,MIERCOLES=14:00,JUEVES=14:00,VIERNES=14:00'),
 ('de300000-0000-4000-c000-000000000011', 'LUNES=7,MARTES=7,MIERCOLES=7,JUEVES=7,VIERNES=7',
  'LUNES=08:00,MARTES=08:00,MIERCOLES=08:00,JUEVES=08:00,VIERNES=08:00');
-- Ledesma: guardia médica 12 h — 06/10 20:00 y 07/10 14:00 dejan 6 h de descanso (a propósito)
-- Moreno:  guardia médica 12 h — esquema correcto
INSERT INTO HorarioGuardiaMedica (id, duracionGuardiaHoras, fechasGuardias, numeroGuardiasSemanal,
                                  permitirGuardiasContinuas, tiempoDescansoMinimoHoras) VALUES
 ('de300000-0000-4000-c000-000000000004', 12,
  '2026-10-02T08:00,2026-10-06T20:00,2026-10-07T14:00,2026-10-10T08:00,2026-10-13T20:00,2026-10-16T08:00,2026-10-20T20:00,2026-10-23T08:00,2026-10-27T20:00,2026-10-30T08:00',
  3, FALSE, 10),
 ('de300000-0000-4000-c000-000000000005', 12,
  '2026-10-01T08:00,2026-10-04T20:00,2026-10-08T08:00,2026-10-11T20:00,2026-10-15T08:00,2026-10-18T20:00,2026-10-22T08:00,2026-10-25T20:00,2026-10-29T08:00',
  3, FALSE, 10);
-- Godoy: nocturno con 12 noches (se esperan 14 x 10 h = 140 h) -> advertencia (a propósito)
-- Figueroa: nocturno con 14 noches -> 140 h exactas
INSERT INTO HorarioNocturno (id, diasProgramados, duracionJornadaHoras, numeroJornadasMensuales) VALUES
 ('de300000-0000-4000-c000-000000000006',
  '2026-10-03T21:00,2026-10-04T21:00,2026-10-07T21:00,2026-10-08T21:00,2026-10-11T21:00,2026-10-12T21:00,2026-10-15T21:00,2026-10-16T21:00,2026-10-19T21:00,2026-10-20T21:00,2026-10-23T21:00,2026-10-24T21:00',
  10, 14),
 ('de300000-0000-4000-c000-000000000007',
  '2026-10-01T21:00,2026-10-02T21:00,2026-10-05T21:00,2026-10-06T21:00,2026-10-09T21:00,2026-10-10T21:00,2026-10-13T21:00,2026-10-14T21:00,2026-10-17T21:00,2026-10-18T21:00,2026-10-21T21:00,2026-10-22T21:00,2026-10-25T21:00,2026-10-26T21:00',
  10, 14);
-- Cáceres: enfermería 12 guardias de 12 h (144 h >= 140) / Maldonado: 10 guardias (120 h < 140) a propósito
INSERT INTO HorarioGuardiaEnfermeria (id, duracionGuardia10Horas, duracionGuardia12Horas, fechasGuardias,
                                      numeroGuardias10Horas, numeroGuardias12Horas) VALUES
 ('de300000-0000-4000-c000-000000000008', 10, 12,
  '2026-10-01T07:00,2026-10-03T19:00,2026-10-05T07:00,2026-10-07T19:00,2026-10-09T07:00,2026-10-11T19:00,2026-10-13T07:00,2026-10-15T19:00,2026-10-17T07:00,2026-10-19T19:00,2026-10-21T07:00,2026-10-23T19:00',
  2, 10),
 ('de300000-0000-4000-c000-000000000010', 10, 12,
  '2026-10-02T07:00,2026-10-05T07:00,2026-10-08T07:00,2026-10-11T07:00,2026-10-14T07:00,2026-10-17T07:00,2026-10-20T07:00,2026-10-23T07:00,2026-10-26T07:00,2026-10-29T07:00',
  2, 10);
-- Juárez: feriante, 10 guardias de 12 h en sábados, domingos y el feriado del 12/10
INSERT INTO HorarioFeriante (id, diasNoLaborables, duracionGuardiaHoras, guardiasProgramadas, horasMinimasMensuales) VALUES
 ('de300000-0000-4000-c000-000000000009',
  '2026-10-03T08:00,2026-10-04T08:00,2026-10-10T08:00,2026-10-11T08:00,2026-10-12T08:00,2026-10-17T08:00,2026-10-18T08:00,2026-10-24T08:00,2026-10-25T08:00,2026-10-31T08:00',
  12, '', 120);

UPDATE Agente SET horarioActualID =
       ('de300000-0000-4000-c000-' || right(id::text, 12))::uuid
WHERE id::text LIKE 'de300000-%';

-- Feriado 12/10 (sólo si no hay uno activo ese día) ----------------------------
INSERT INTO Feriado (id, fecha, descripcion, estado, creadoPorID)
SELECT 'de300000-0000-4000-e000-000000000001', DATE '2026-10-12',
       'Día del Respeto a la Diversidad Cultural', 'VIGENTE',
       (SELECT id FROM Usuario WHERE cuil = 27111122277)
WHERE NOT EXISTS (SELECT 1 FROM Feriado WHERE fecha = DATE '2026-10-12'
                  AND estado IN ('VIGENTE','ALTA_PENDIENTE','BAJA_PENDIENTE'));

-- Diagramas de otros servicios para la bandeja (P02) ---------------------------
INSERT INTO DiagramaDeServicio (id, servicioID, estado, fechaInicio, fechaFin, version, creadoPorID,
                                createdAt, updatedAt, aprobadoPorID, fechaAprobacion, comentariosObservacion)
SELECT v.id::uuid, (SELECT id FROM Servicio WHERE nombre = v.serv), v.estado::estado_diagrama,
       v.ini::date, v.fin::date, v.ver,
       (SELECT u.id FROM Usuario u JOIN AsignacionRol ur ON ur.usuarioID = u.id
          JOIN Rol r ON r.id = ur.rolID
        WHERE r.nombre = 'JefaturaDeServicio' AND u.servicioID = (SELECT id FROM Servicio WHERE nombre = v.serv)
        LIMIT 1),
       v.creado::timestamp, v.upd::timestamp,
       CASE WHEN v.estado IN ('APROBADO','OBSERVADO') THEN (SELECT id FROM Usuario WHERE cuil = 27111122277) END,
       CASE WHEN v.estado IN ('APROBADO','OBSERVADO') THEN v.upd::timestamp END,
       v.coment
FROM (VALUES
 ('de300000-0000-4000-d000-000000000002','Cirugía','PENDIENTE_APROBACION','2026-11-01','2026-11-30',2,'2026-10-01 09:10','2026-10-05 10:12',NULL),
 ('de300000-0000-4000-d000-000000000003','Diagnóstico por Imágenes','PENDIENTE_APROBACION','2026-11-01','2026-11-30',3,'2026-10-02 12:30','2026-10-06 16:40',NULL),
 ('de300000-0000-4000-d000-000000000004','Enfermería','OBSERVADO','2026-11-01','2026-11-30',4,'2026-09-30 08:45','2026-10-05 11:05',
  'Faltan las guardias nocturnas del fin de semana largo del 21 al 23/11.'),
 ('de300000-0000-4000-d000-000000000005','Traumatología y Ortopedia','APROBADO','2026-11-01','2026-11-30',3,'2026-09-29 10:00','2026-10-02 09:30',NULL)
) AS v(id, serv, estado, ini, fin, ver, creado, upd, coment);

-- Diagrama preexistente de Biomédica (agosto): figuraba creado por Medina, Rosa
-- (Oficina de Personal en P03/P04). Para la demo lo firma la jefa del servicio.
-- revertir_demo.sql restaura el valor original.
UPDATE DiagramaDeServicio
SET creadoPorID = (SELECT u.id FROM Usuario u JOIN AsignacionRol ur ON ur.usuarioID = u.id
                   JOIN Rol r ON r.id = ur.rolID
                   WHERE r.nombre = 'JefaturaDeServicio'
                     AND u.servicioID = (SELECT id FROM Servicio WHERE nombre = 'Biomédica') LIMIT 1)
WHERE id = '1ff293b1-2aa9-4a67-9318-7ac2f30c83b7';
