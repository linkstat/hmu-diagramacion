/* ============================================================================
 * HMU Diagramación - Datos de prueba (PostgreSQL)
 *
 * Este script:
 *  1. Limpia los registros de todas las tablas (preservando schema y la
 *     seed data de EstadoTramite del DDL).
 *  2. Inserta domicilios, cargos, servicios y usuarios de prueba para uso
 *     de demo y pruebas funcionales.
 *  3. Asigna roles a los usuarios.
 *  4. Demuestra algunos UPDATE/DELETE típicos.
 *
 * Datos anonimizados: nombres y CUILs son ficticios (apellidos comunes,
 * CUILs con patrón XX-1111NNNN-N para que sea evidente que no son reales).
 * Los servicios y la estructura de roles imitan la del HMU pero no contienen
 * datos personales identificables.
 *
 * Estrategia de migración desde MariaDB:
 *  - Se eliminaron las variables `SET @x`. En su lugar, se usan subqueries
 *    con claves naturales (apellidos+nombres, número de cargo, nombre de
 *    servicio). Es más verboso pero más explícito y robusto.
 *  - El ciclo Usuario.servicioID → Servicio.direccionID → Agente.id se
 *    rompe insertando primero Usuarios sin servicioID, luego Empleados,
 *    luego Servicios, y finalmente UPDATE Usuario SET servicioID. PostgreSQL
 *    no tiene un equivalente directo de SET FOREIGN_KEY_CHECKS = 0, y dejar
 *    el FK como DEFERRABLE complica el modelo: se prefiere romper el ciclo
 *    a nivel de datos.
 *  - CURDATE() → CURRENT_DATE; UUID() → gen_random_uuid(); RAND() → random();
 *    UUID_TO_BIN/BIN_TO_UUID se eliminan (UUID es tipo nativo).
 *
 * Usuarios para pruebas de login:
 *  - Contraseña 'HMU.2026' (Argon2id) para todos, EXCEPTO:
 *  - Valdez, Mateo (CUIL 24-11114449-9) — password = CUIL, para demostrar el
 *    cambio obligatorio de la contraseña inicial en el primer ingreso.
 * ========================================================================== */


-- ----------------------------------------------------------------------------
-- 1) Limpieza de registros (preserva EstadoTramite — la seedea el DDL)
-- ----------------------------------------------------------------------------
TRUNCATE TABLE
    JornadaRegistrada, Marcacion, ParteDiario_Agente, ParteDiario,
    HoraExtra, FrancoCompensatorio,
    Memorandum_Autorizacion, Memorandum_Firmante, Memorandum_Destinatario, Memorandum,
    JornadaLaboral, DiagramaDeServicio,
    Agente_Novedad, Novedad, Autorizacion,
    AsignacionRol, Rol,
    Servicio_JefaturaDeServicio,
    Agente, Usuario, Servicio,
    Domicilio, Cargo,
    HorarioAbierto, HorarioJefeServicioGuardiaPasiva, HorarioGuardiaEnfermeria,
    HorarioGuardiaMedica, HorarioDXI, HorarioFeriante, HorarioNocturno,
    HorarioSemanal, HorarioEstandar, HorarioConFranquicia, Horario, HorarioBase
RESTART IDENTITY CASCADE;


-- ----------------------------------------------------------------------------
-- 2) Domicilios de prueba (direcciones genéricas)
-- ----------------------------------------------------------------------------
INSERT INTO Domicilio (id, calle, numeracion, barrio, ciudad, localidad, provincia)
VALUES
    (gen_random_uuid(), 'Calle Falsa',          123,  NULL, 'Córdoba',     NULL, 'Córdoba'),
    (gen_random_uuid(), 'Av. Siempreviva',      742,  NULL, 'Córdoba',     NULL, 'Córdoba'),
    (gen_random_uuid(), 'Av. del Libertador',   1500, NULL, 'La Rioja',    NULL, 'La Rioja'),
    (gen_random_uuid(), 'Calle del Sol',        200,  NULL, 'Córdoba',     NULL, 'Córdoba'),
    (gen_random_uuid(), 'Av. Central',          1450, NULL, 'Alta Gracia', NULL, 'Córdoba'),
    (gen_random_uuid(), 'Calle Principal',      441,  NULL, 'Córdoba',     NULL, 'Córdoba'),
    (gen_random_uuid(), 'Av. Norte',            FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Rosario',    NULL, 'Santa Fe'),
    (gen_random_uuid(), 'Calle Sur',            FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Famaillá',   NULL, 'Tucumán'),
    (gen_random_uuid(), 'Av. Este',             FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Santa Fe',   NULL, 'Santa Fe'),
    (gen_random_uuid(), 'Av. Oeste',            FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Iruya',      NULL, 'Jujuy'),
    (gen_random_uuid(), 'Calle Belgrano',       FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Corrientes', NULL, 'Corrientes'),
    (gen_random_uuid(), 'Av. San Martín',       FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Rosario',    NULL, 'Santa Fe'),
    (gen_random_uuid(), 'Calle del Río',        FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Paraná',     NULL, 'Entre Ríos'),
    (gen_random_uuid(), 'Av. Mitre',            FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Salta',      NULL, 'Salta'),
    (gen_random_uuid(), 'Av. Independencia',    FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Rawson',     NULL, 'Chubut'),
    (gen_random_uuid(), 'Calle Sarmiento',      FLOOR(random() * 1000 + 1)::INTEGER, NULL, 'Bariloche',  NULL, 'Río Negro');


-- ----------------------------------------------------------------------------
-- 3) Cargos
-- ----------------------------------------------------------------------------
INSERT INTO Cargo (id, numero, descripcion, agrupacion) VALUES
    (gen_random_uuid(), 0,    'Cargo sin definir',                       'INDEFINIDO'),
    (gen_random_uuid(), 200,  'Directivos',                              'PLANTAPOLITICA'),
    (gen_random_uuid(), 300,  'Jefaturas de Servicio',                   'JEFATURA'),
    (gen_random_uuid(), 400,  'Técnicos',                                'TECNICO'),
    (gen_random_uuid(), 500,  'Administrativos',                         'ADMINISTRATIVO'),
    (gen_random_uuid(), 600,  'Servicios',                               'SERVICIO'),
    (gen_random_uuid(), 700,  'Enfermería Técnicos',                     'ENFERMERIA'),
    (gen_random_uuid(), 800,  'Médicos',                                 'MEDICO'),
    (gen_random_uuid(), 900,  'Enfermería Profesionales',                'ENFERMERIA'),
    (gen_random_uuid(), 1200, 'Profesionales sin agrupación específica', 'PROFESIONAL');


-- ----------------------------------------------------------------------------
-- 4) Usuarios (anonimizados — datos ficticios)
--
-- Se insertan SIN servicioID para romper el ciclo Usuario↔Servicio↔Agente.
-- El servicioID se asigna después de que existan los Servicios (paso 7).
--
-- Hashes Argon2id (m=19456, t=2, p=1) generados con PasswordUtils.
-- Contraseña 'HMU.2026' para todos, salvo Valdez (password = CUIL, para
-- demostrar el cambio obligatorio de contraseña en el primer ingreso).
-- ----------------------------------------------------------------------------
INSERT INTO Usuario (id, nombres, apellidos, mail, cuil, fechaAlta, sexo, estado, domicilioID, passwordHash) VALUES
    -- Dirección (4 personas)
    (gen_random_uuid(), 'Juan Carlos', 'Pérez',     'perez@hmu.com.ar',     20111111111, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio WHERE calle = 'Calle Principal' AND numeracion = 441 LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Pedro',       'García',    'garcia@hmu.com.ar',    20111111122, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio WHERE calle = 'Av. Siempreviva' AND numeracion = 742 LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'María',       'Rodríguez', 'rodriguez@hmu.com.ar', 27111111133, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio WHERE ciudad = 'Alta Gracia' LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Carlos',      'González',  'gonzalez@hmu.com.ar',  20111111144, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio WHERE calle = 'Calle Principal' AND numeracion = 441 LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),

    -- Jefaturas de Servicio (12 personas + Medina/OdP que también es jefa)
    (gen_random_uuid(), 'Ana',         'Fernández', 'fernandez@hmu.com.ar', 27111111155, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Laura',       'López',     'lopez@hmu.com.ar',     27111111166, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Miguel',      'Martínez',  'martinez@hmu.com.ar',  20111111177, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Diego',       'Gómez',     'gomez@hmu.com.ar',     20111111188, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Lucía',       'Díaz',      'diaz@hmu.com.ar',      27111111199, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Pablo',       'Álvarez',   'alvarez@hmu.com.ar',   20111122200, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Sofía',       'Torres',    'torres@hmu.com.ar',    27111122211, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Andrés',      'Ruiz',      'ruiz@hmu.com.ar',      20111122222, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Sam',         'Ramírez',   'ramirez@hmu.com.ar',   20111122233, CURRENT_DATE, 'OTRO',      TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Carmen',      'Flores',    'flores@hmu.com.ar',    27111122244, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Roberto',     'Acosta',    'acosta@hmu.com.ar',    20111122255, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Patricia',    'Benítez',   'benitez@hmu.com.ar',   27111122266, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),

    -- Oficina de Personal (Medina — también es jefa de servicio; password = CUIL para test)
    (gen_random_uuid(), 'Rosa',        'Medina',    'medina@hmu.com.ar',    27111122277, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),

    -- Empleados (no jefaturas, no dirección)
    (gen_random_uuid(), 'Eduardo',     'Castro',    'castro@hmu.com.ar',    20111122288, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Fernando',    'Ortiz',     'ortiz@hmu.com.ar',     20111122299, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Ricardo',     'Silva',     'silva@hmu.com.ar',     20111133300, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Beatriz',     'Núñez',     'nunez@hmu.com.ar',     27111133311, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Cristina',    'Molina',    'molina@hmu.com.ar',    27111133322, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Mónica',      'Rojas',     'rojas@hmu.com.ar',     27111133333, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Sergio',      'Vega',      'vega@hmu.com.ar',      20111133344, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Alex',        'Cabrera',   'cabrera@hmu.com.ar',   24111133355, CURRENT_DATE, 'OTRO',      TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Silvia',      'Herrera',   'herrera@hmu.com.ar',   27111133366, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Liliana',     'Aguirre',   'aguirre@hmu.com.ar',   27111133377, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Alberto',     'Luna',      'luna@hmu.com.ar',      20111133388, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Adriana',     'Ríos',      'rios@hmu.com.ar',      27111133399, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Verónica',    'Reyes',     'reyes@hmu.com.ar',     27111144400, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Sasha',       'Cruz',      'cruz@hmu.com.ar',      24111144411, CURRENT_DATE, 'OTRO',      TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Marcelo',     'Pereyra',   'pereyra@hmu.com.ar',   20111144422, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Gustavo',     'Quiroga',   'quiroga@hmu.com.ar',   20111144433, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Susana',      'Vázquez',   'vazquez@hmu.com.ar',   27111144444, CURRENT_DATE, 'FEMENINO',  TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Hernán',      'Gutiérrez', 'gutierrez@hmu.com.ar', 20111144455, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Daniel',      'Ojeda',     'ojeda@hmu.com.ar',     20111144466, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Luis',        'Bravo',     'bravo@hmu.com.ar',     20111144477, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Tomás',       'Soria',     'soria@hmu.com.ar',     20111144488, CURRENT_DATE, 'MASCULINO', TRUE, (SELECT id FROM Domicilio ORDER BY random() LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$LoUDo+lYBJ0n85dEZDAW7Q$jy8A9NX32UQaJ6cTfGxqKr4e/cmmzm15bzqTxSBdODs'),
    (gen_random_uuid(), 'Mateo',       'Valdez',    'valdez@hmu.com.ar',    24111144499, CURRENT_DATE, 'OTRO',      TRUE, (SELECT id FROM Domicilio WHERE calle = 'Av. Independencia' LIMIT 1), '$argon2id$v=19$m=19456,t=2,p=1$jFydvJuocmb3A5UQkc7QCg$ZXThbXDI7MQSp9KjBiqOhiE0TNaDhk2RhTtNGCD0J/g');


-- ----------------------------------------------------------------------------
-- 5) Agente: insertar a TODOS los Usuarios
--
-- Convención: todo Usuario del hospital es también Agente (tiene horario,
-- francos, etc.), incluso si su rol principal es jefatura/dirección/oficina.
-- El "tipo" se discrimina por roles asignados (AsignacionRol), no por presencia
-- en sub-tablas.
-- ----------------------------------------------------------------------------
INSERT INTO Agente (id, francosCompensatoriosUtilizados, horarioActualID)
SELECT id, 0, NULL FROM Usuario;


-- ----------------------------------------------------------------------------
-- 6) Servicios
--
-- direccionID se resuelve con subquery contra Usuario por nombre+apellido.
-- ----------------------------------------------------------------------------
INSERT INTO Servicio (id, nombre, agrupacion, direccionID) VALUES
    (gen_random_uuid(), 'Administración',             'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Admisión',                   'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Anatomía Patológica',        'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Anestesia',                  'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Auditoría Médica',           'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Biomédica',                  'TECNICO',        (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Bioquímica',                 'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Camilleros',                 'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Capacitación y Docencia',    'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Cirugía',                    'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Cirugía Plástica',           'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Clínica Médica',             'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Diagnóstico por Imágenes',   'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Dirección',                  'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Enfermería',                 'ENFERMERIA',     (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Esterilización',             'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Facturación',                'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Farmacia',                   'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Habilitación',               'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Hemoterapia',                'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Informática',                'TECNICO',        (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Instrumentación Quirúrgica', 'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Kinesiología',               'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Laboratorio',                'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Lavadero',                   'SERVICIO',       (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Mantenimiento',              'TECNICO',        (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Medicina Legal',             'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Neurocirugía',               'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Nutrición',                  'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Personal',                   'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Quirófano',                  'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Registro Médico (Archivo)',  'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Salud Mental',               'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Secretaría Técnica',         'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Servicio Social',            'ADMINISTRATIVO', (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Tecnoeléctrica',             'TECNICO',        (SELECT id FROM Usuario WHERE apellidos = 'González' AND nombres = 'Carlos')),
    (gen_random_uuid(), 'Toxicología',                'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos')),
    (gen_random_uuid(), 'Traumatología y Ortopedia',  'MEDICO',         (SELECT id FROM Usuario WHERE apellidos = 'Pérez'    AND nombres = 'Juan Carlos'));


-- ----------------------------------------------------------------------------
-- 7) Asignación de servicioID a cada Usuario (cierra el ciclo)
-- ----------------------------------------------------------------------------
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Dirección')                 WHERE apellidos IN ('Pérez', 'García', 'Rodríguez', 'González');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Admisión')                  WHERE apellidos = 'Fernández';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Biomédica')                 WHERE apellidos = 'López';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Cirugía')                   WHERE apellidos = 'Martínez';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Diagnóstico por Imágenes')  WHERE apellidos = 'Gómez';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Enfermería')                WHERE apellidos = 'Díaz';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Esterilización')            WHERE apellidos = 'Álvarez';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Farmacia')                  WHERE apellidos = 'Torres';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Habilitación')              WHERE apellidos = 'Ruiz';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Informática')               WHERE apellidos IN ('Ramírez', 'Vega', 'Cabrera');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Nutrición')                 WHERE apellidos = 'Flores';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Traumatología y Ortopedia') WHERE apellidos IN ('Acosta', 'Ojeda', 'Bravo');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Toxicología')               WHERE apellidos IN ('Benítez', 'Reyes', 'Cruz');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Personal')                  WHERE apellidos IN ('Medina', 'Ortiz', 'Silva', 'Herrera');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Esterilización')            WHERE apellidos = 'Núñez';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Cirugía')                   WHERE apellidos IN ('Molina', 'Pereyra', 'Soria');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Habilitación')              WHERE apellidos = 'Rojas';
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Diagnóstico por Imágenes')  WHERE apellidos IN ('Castro', 'Gutiérrez');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Enfermería')                WHERE apellidos IN ('Aguirre', 'Luna', 'Ríos');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Biomédica')                 WHERE apellidos IN ('Quiroga', 'Valdez');
UPDATE Usuario SET servicioID = (SELECT id FROM Servicio WHERE nombre = 'Admisión')                  WHERE apellidos = 'Vázquez';


-- ----------------------------------------------------------------------------
-- 8) Vincular Jefaturas con Servicios
-- ----------------------------------------------------------------------------
INSERT INTO Servicio_JefaturaDeServicio (servicioID, agenteID) VALUES
    ((SELECT id FROM Servicio WHERE nombre = 'Admisión'),                  (SELECT id FROM Usuario WHERE apellidos = 'Fernández')),
    ((SELECT id FROM Servicio WHERE nombre = 'Biomédica'),                 (SELECT id FROM Usuario WHERE apellidos = 'López')),
    ((SELECT id FROM Servicio WHERE nombre = 'Cirugía'),                   (SELECT id FROM Usuario WHERE apellidos = 'Martínez')),
    ((SELECT id FROM Servicio WHERE nombre = 'Diagnóstico por Imágenes'),  (SELECT id FROM Usuario WHERE apellidos = 'Gómez')),
    ((SELECT id FROM Servicio WHERE nombre = 'Enfermería'),                (SELECT id FROM Usuario WHERE apellidos = 'Díaz')),
    ((SELECT id FROM Servicio WHERE nombre = 'Esterilización'),            (SELECT id FROM Usuario WHERE apellidos = 'Álvarez')),
    ((SELECT id FROM Servicio WHERE nombre = 'Farmacia'),                  (SELECT id FROM Usuario WHERE apellidos = 'Torres')),
    ((SELECT id FROM Servicio WHERE nombre = 'Habilitación'),              (SELECT id FROM Usuario WHERE apellidos = 'Ruiz')),
    ((SELECT id FROM Servicio WHERE nombre = 'Informática'),               (SELECT id FROM Usuario WHERE apellidos = 'Ramírez')),
    ((SELECT id FROM Servicio WHERE nombre = 'Nutrición'),                 (SELECT id FROM Usuario WHERE apellidos = 'Flores')),
    ((SELECT id FROM Servicio WHERE nombre = 'Personal'),                  (SELECT id FROM Usuario WHERE apellidos = 'Medina')),
    ((SELECT id FROM Servicio WHERE nombre = 'Toxicología'),               (SELECT id FROM Usuario WHERE apellidos = 'Benítez')),
    ((SELECT id FROM Servicio WHERE nombre = 'Traumatología y Ortopedia'), (SELECT id FROM Usuario WHERE apellidos = 'Acosta'));


-- ----------------------------------------------------------------------------
-- 8.b) Inicialización de Servicio.encargadoUsuarioID
--
-- Cada servicio con jefatura asignada arranca con esa misma jefatura como
-- encargado actual del papeleo (aprobaciones/rechazos/observaciones de
-- memos). Los servicios sin jefe quedan con encargadoUsuarioID = NULL hasta
-- que Oficina de Personal o Dirección asignen uno desde el ABM.
-- ----------------------------------------------------------------------------
UPDATE Servicio s
   SET encargadoUsuarioID = sjs.agenteID
  FROM Servicio_JefaturaDeServicio sjs
 WHERE s.id = sjs.servicioID;


-- ----------------------------------------------------------------------------
-- 9) Roles
-- ----------------------------------------------------------------------------
INSERT INTO Rol (id, nombre, descripcion) VALUES
    (gen_random_uuid(), 'Empleado',           'Empleado'),
    (gen_random_uuid(), 'JefaturaDeServicio', 'Jefatura de Servicio'),
    (gen_random_uuid(), 'OficinaDePersonal',  'Oficina de Personal'),
    (gen_random_uuid(), 'Direccion',          'Directivo');


-- ----------------------------------------------------------------------------
-- 10) Asignación de roles
--
-- Todos reciben 'Empleado'. Las jefaturas/dirección/oficina reciben sus
-- roles específicos adicionales. La vigencia arranca el 2026-01-01 (fecha
-- fija en el pasado): el escenario de demostración corre con hmu.hoy =
-- 2026-10-07 y la instalación puede ser posterior; con el DEFAULT
-- CURRENT_DATE los roles quedarían a futuro y el menú, vacío.
-- ----------------------------------------------------------------------------
-- Empleado para todos
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde)
SELECT u.id, r.id, DATE '2026-01-01' FROM Usuario u, Rol r WHERE r.nombre = 'Empleado';

-- JefaturaDeServicio para los jefes (incluye a Medina, que también es OdP)
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde)
SELECT u.id, r.id, DATE '2026-01-01' FROM Usuario u, Rol r
WHERE r.nombre = 'JefaturaDeServicio'
  AND u.apellidos IN (
      'Fernández', 'López', 'Martínez', 'Gómez', 'Díaz', 'Álvarez',
      'Torres', 'Ruiz', 'Ramírez', 'Flores', 'Medina', 'Benítez', 'Acosta'
  );

-- OficinaDePersonal para Medina
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde)
SELECT u.id, r.id, DATE '2026-01-01' FROM Usuario u, Rol r
WHERE r.nombre = 'OficinaDePersonal' AND u.apellidos = 'Medina';

-- Direccion para los 4 directivos
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde)
SELECT u.id, r.id, DATE '2026-01-01' FROM Usuario u, Rol r
WHERE r.nombre = 'Direccion'
  AND u.apellidos IN ('Pérez', 'García', 'Rodríguez', 'González');

-- Pérez también ejerce JefaturaDeServicio
INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde)
SELECT u.id, r.id, DATE '2026-01-01' FROM Usuario u, Rol r
WHERE r.nombre = 'JefaturaDeServicio'
  AND u.apellidos = 'Pérez' AND u.nombres = 'Juan Carlos';


-- ----------------------------------------------------------------------------
-- 11) Asignación inicial de Cargos
-- ----------------------------------------------------------------------------
UPDATE Usuario SET cargoID = (SELECT id FROM Cargo WHERE numero = 0);  -- todos en 'INDEFINIDO' por defecto

UPDATE Usuario
SET cargoID = (SELECT id FROM Cargo WHERE numero = 400)  -- TECNICO
WHERE servicioID IN (SELECT id FROM Servicio WHERE nombre IN ('Biomédica', 'Informática'));

UPDATE Usuario
SET cargoID = (SELECT id FROM Cargo WHERE numero = 300)  -- JEFATURA
WHERE apellidos = 'Ramírez' OR apellidos = 'Medina';


-- ----------------------------------------------------------------------------
-- 12) Verificaciones
-- ----------------------------------------------------------------------------
-- Asignaciones de jefaturas
SELECT s.id            AS servicio_id,
       s.nombre        AS servicio_nombre,
       e.id            AS jefatura_id,
       u.apellidos || ', ' || u.nombres AS jefatura_de_servicio
FROM Servicio s
JOIN Servicio_JefaturaDeServicio sjs ON s.id = sjs.servicioID
JOIN Agente e ON sjs.agenteID = e.id
JOIN Usuario u ON e.id = u.id
ORDER BY s.nombre;

-- Asignaciones de roles
SELECT u.id        AS usuarioID,
       u.apellidos,
       r.id        AS rolID,
       r.nombre    AS rol_nombre
FROM Usuario u
JOIN AsignacionRol ur ON u.id = ur.usuarioID
JOIN Rol r ON ur.rolID = r.id
ORDER BY u.apellidos, r.nombre;


-- ----------------------------------------------------------------------------
-- 13) Ejemplos de UPDATE / DELETE
-- ----------------------------------------------------------------------------

-- Modificar el teléfono al Jefe de Informática
UPDATE Usuario SET tel = 3517553799 WHERE apellidos = 'Ramírez';
SELECT nombres, apellidos, tel FROM Usuario WHERE apellidos = 'Ramírez';

-- Borrar a Vázquez (Susana) — cae todo por ON DELETE CASCADE
DELETE FROM Agente WHERE id = (SELECT id FROM Usuario WHERE apellidos = 'Vázquez' AND nombres = 'Susana');
DELETE FROM Usuario  WHERE apellidos = 'Vázquez' AND nombres = 'Susana';
