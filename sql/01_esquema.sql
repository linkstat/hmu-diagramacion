/* ============================================================================
 * HMU Diagramación - DDL completo (PostgreSQL 18)
 *
 * Crea el esquema completo en una base vacía.
 *
 * Convenciones:
 *  - Clave primaria id UUID DEFAULT gen_random_uuid() en todas las tablas
 *    (también en las subclases de Class Table Inheritance, donde id es a la
 *    vez la FK al padre, y en las tablas puente, cuya clave compuesta queda
 *    como UNIQUE). Única excepción: EventoAuditoria.id BIGSERIAL.
 *  - Claves naturales (CUIL, legajo, CodigoNovedad.codigo, TipoNovedad.codigo,
 *    ReglaValidacion codigo + vigenteDesde...) como UNIQUE.
 *  - Claves foráneas <rol o entidad>ID, UUID, declaradas en línea
 *    (REFERENCES) y con un índice idx_<tabla>_<columna>.
 *  - Nombres sin comillas: tablas en PascalCase, columnas en camelCase
 *    (PostgreSQL los guarda en minúsculas); tipos enumerados, índices y
 *    restricciones CHECK (chk_<tabla>_<regla>) en snake_case. PK, FK, UNIQUE
 *    y NOT NULL llevan el nombre automático de PostgreSQL.
 *  - Enumerados como tipos ENUM. TIMESTAMP sin zona horaria (el HMU opera en
 *    una sola zona). Horas NUMERIC(6,2); saldos NUMERIC(7,2).
 *  - Polimorfismo de Usuario por roles (Rol + AsignacionRol): Agente es la
 *    única subclase persistida. Usuario.servicioID se conserva en Usuario
 *    (redundancia consciente, ver CLAUDE.md).
 *
 * Para regenerar desde cero:
 *   psql -d hmu_diagramacion -f sql/01_esquema.sql
 * ========================================================================== */


/* ----------------------------------------------------------------------------
 * Tipos enumerados (por dominio, para reusarlos entre columnas)
 * -------------------------------------------------------------------------- */
CREATE TYPE agrupacion_cargo AS ENUM (
    'INDEFINIDO', 'PLANTAPOLITICA', 'JEFATURA', 'TECNICO', 'ADMINISTRATIVO',
    'SERVICIO', 'ENFERMERIA', 'MEDICO', 'PROFESIONAL'
);
CREATE TYPE agrupacion_servicio AS ENUM (
    'ADMINISTRATIVO', 'SERVICIO', 'MEDICO', 'ENFERMERIA', 'TECNICO', 'PLANTAPOLITICA'
);
CREATE TYPE sexo AS ENUM ('FEMENINO', 'MASCULINO', 'OTRO');
CREATE TYPE vinculacion AS ENUM
    ('PLANTA_PERMANENTE', 'PLANTA_TRANSITORIA', 'CONTRATADO', 'FUNCIONARIO',
     'MONOTRIBUTISTA', 'BECARIO');
CREATE TYPE tipo_horario_base AS ENUM ('Horario', 'HorarioConFranquicia');
CREATE TYPE modalidad_horaria AS ENUM
    ('HorarioEstandar', 'HorarioSemanal', 'HorarioNocturno', 'HorarioFeriante', 'HorarioDXI',
     'HorarioGuardiaMedica', 'HorarioGuardiaEnfermeria', 'HorarioJefeServicioGuardiaPasiva', 'HorarioAbierto');
CREATE TYPE tipo_rol_autorizacion AS ENUM
    ('EMPLEADO', 'JEFATURADESERVICIO', 'OFICINADEPERSONAL', 'DIRECCION');
CREATE TYPE tipo_rol_memo_autorizacion AS ENUM
    ('JEFATURADESERVICIO', 'OFICINADEPERSONAL', 'DIRECCION', 'USUARIO');
CREATE TYPE estado_memo_autorizacion AS ENUM ('PENDIENTE', 'AUTORIZADO', 'RECHAZADO', 'OBSERVADO');
CREATE TYPE estado_feriado AS ENUM
    ('VIGENTE', 'ALTA_PENDIENTE', 'BAJA_PENDIENTE', 'RECHAZADO', 'ANULADO');
CREATE TYPE estado_diagrama AS ENUM
    ('BORRADOR', 'PENDIENTE_APROBACION', 'APROBADO', 'OBSERVADO');
CREATE TYPE tipo_jornada AS ENUM
    ('TURNO_NORMAL', 'GUARDIA_ACTIVA', 'GUARDIA_PASIVA',
     'FRANCO', 'FRANCO_COMPENSATORIO', 'LICENCIA');
CREATE TYPE estado_novedad AS ENUM
    ('BORRADOR', 'PENDIENTE_JEFATURA', 'PENDIENTE_OFICINA_PERSONAL', 'PENDIENTE_DIRECCION',
     'OBSERVADA', 'APROBADA', 'APLICADA', 'RECHAZADA', 'ANULADA');
CREATE TYPE origen_novedad AS ENUM ('SOLICITUD', 'COTEJO', 'LICENCIA_EXTERNA');
CREATE TYPE instancia_novedad AS ENUM
    ('JEFATURA', 'OFICINA_PERSONAL', 'DIRECCION', 'SECRETARIA_SALUD');
CREATE TYPE estado_novedad_autorizacion AS ENUM ('PENDIENTE', 'AUTORIZADO', 'RECHAZADO', 'OBSERVADO');
CREATE TYPE tipo_movimiento_horas AS ENUM ('CREDITO', 'DEBITO', 'VENCIMIENTO', 'AJUSTE');
CREATE TYPE tipo_marcacion AS ENUM ('INGRESO', 'EGRESO');
CREATE TYPE estado_parte AS ENUM ('ABIERTO', 'CERRADO');
CREATE TYPE tipo_diferencia AS ENUM
    ('INASISTENCIA', 'LLEGADA_TARDE', 'RETIRO_ANTICIPADO', 'OMISION_INGRESO', 'OMISION_EGRESO',
     'GUARDIA_NO_CUBIERTA', 'PRESENTE_SIN_DIAGRAMA');
CREATE TYPE origen_detalle_parte AS ENUM ('COTEJO', 'NOVEDAD_APROBADA', 'LICENCIA_EXTERNA', 'CARGA_MANUAL');
-- ASISTENCIA: reglas del cotejo de marcaciones (RV-COTEJO-TOLER, RV-HE-COMPUTO).
CREATE TYPE ambito_regla AS ENUM ('DIAGRAMA', 'NOVEDAD', 'AMBOS', 'ASISTENCIA');
CREATE TYPE severidad_regla AS ENUM ('BLOQUEANTE', 'ADVERTENCIA');
CREATE TYPE operacion_auditoria AS ENUM ('INSERT', 'UPDATE', 'DELETE');


/* ----------------------------------------------------------------------------
 * Jerarquía de horarios (Class Table Inheritance)
 *
 * HorarioBase es la raíz; Horario y HorarioConFranquicia (decorator de un
 * Horario) son sus dos ramas. Las modalidades concretas heredan de Horario.
 * -------------------------------------------------------------------------- */
CREATE TABLE HorarioBase (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo tipo_horario_base NOT NULL
);

CREATE TABLE Horario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES HorarioBase(id),
    fechaIngreso TIMESTAMP NOT NULL,
    fechaEgreso TIMESTAMP NOT NULL,
    jornadasPlanificadas INTEGER NULL,
    reglasHorario VARCHAR(255) NULL,
    horarioBaseID UUID NOT NULL,
    modalidad modalidad_horaria NOT NULL
);

CREATE TABLE HorarioConFranquicia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES HorarioBase(id),
    fechaIngreso TIMESTAMP NOT NULL,
    fechaEgreso TIMESTAMP NOT NULL,
    horasFranquicia INTEGER NOT NULL,
    horarioDecoradoID UUID NOT NULL REFERENCES Horario(id)
);

CREATE TABLE HorarioEstandar (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    diasLaborables VARCHAR(255) NOT NULL,
    horasPorDia INTEGER NOT NULL
);

CREATE TABLE HorarioSemanal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    distribucionSemanal VARCHAR(255) NOT NULL,
    horaInicioPorDia VARCHAR(255) NOT NULL
);

CREATE TABLE HorarioNocturno (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    diasProgramados TEXT NOT NULL,
    duracionJornadaHoras INTEGER NOT NULL,
    numeroJornadasMensuales INTEGER NOT NULL
);

CREATE TABLE HorarioFeriante (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    diasNoLaborables TEXT NOT NULL,
    duracionGuardiaHoras INTEGER NOT NULL,
    guardiasProgramadas TEXT NOT NULL,
    horasMinimasMensuales INTEGER NOT NULL
);

CREATE TABLE HorarioDXI (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    distribucionHoraria VARCHAR(255) NOT NULL,
    horaInicioPorDia VARCHAR(255) NOT NULL,
    horasSemanales INTEGER NOT NULL
);

CREATE TABLE HorarioGuardiaMedica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    duracionGuardiaHoras INTEGER NOT NULL,
    fechasGuardias TEXT NOT NULL,
    numeroGuardiasSemanal INTEGER NOT NULL,
    permitirGuardiasContinuas BOOLEAN NOT NULL,
    tiempoDescansoMinimoHoras INTEGER NOT NULL
);

CREATE TABLE HorarioGuardiaEnfermeria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    duracionGuardia10Horas INTEGER NOT NULL,
    duracionGuardia12Horas INTEGER NOT NULL,
    fechasGuardias TEXT NOT NULL,
    numeroGuardias10Horas INTEGER NOT NULL,
    numeroGuardias12Horas INTEGER NOT NULL
);

CREATE TABLE HorarioJefeServicioGuardiaPasiva (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    diasLaborables VARCHAR(255) NOT NULL,
    horasPorDia INTEGER NOT NULL
);

CREATE TABLE HorarioAbierto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Horario(id),
    flexibilidadHoraria BOOLEAN NOT NULL,
    horasSemanales INTEGER NOT NULL,
    preferenciasHorarias VARCHAR(255) NULL
);


/* ----------------------------------------------------------------------------
 * Cargo, Domicilio, Servicio, Usuario y Agente
 *
 * Ciclo Servicio.direccionID -> Agente -> Usuario -> Servicio: las FK de
 * Servicio se agregan después de crear Usuario y Agente.
 * -------------------------------------------------------------------------- */
CREATE TABLE Cargo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero INTEGER NOT NULL UNIQUE,
    descripcion VARCHAR(255) NOT NULL,
    agrupacion agrupacion_cargo NOT NULL
);

CREATE TABLE Domicilio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calle VARCHAR(255) NOT NULL,
    numeracion INTEGER NULL,
    barrio VARCHAR(255) NULL,
    ciudad VARCHAR(255) NULL,
    localidad VARCHAR(255) NULL,
    provincia VARCHAR(255) NULL
);

-- direccionID: el agente con rol DIRECCION a cargo del servicio.
-- encargadoUsuarioID: encargado actual del papeleo (autorizaciones de memos).
-- dotacionMinima: base de la regla RV-DOTACION-MIN (porcentaje por franja).
CREATE TABLE Servicio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255) NOT NULL,
    agrupacion agrupacion_servicio NOT NULL,
    direccionID UUID NOT NULL,
    encargadoUsuarioID UUID NULL,
    dotacionMinima INTEGER NULL
);

-- Bloqueo tras 5 intentos fallidos y cambio obligatorio de contraseña (CU01/CU02).
CREATE TABLE Usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fechaAlta DATE NOT NULL,
    estado BOOLEAN NOT NULL,
    cuil BIGINT NOT NULL UNIQUE,
    apellidos VARCHAR(255) NOT NULL,
    nombres VARCHAR(255) NOT NULL,
    sexo sexo NOT NULL,
    mail VARCHAR(255) NOT NULL,
    tel BIGINT NULL,
    domicilioID UUID NULL REFERENCES Domicilio(id),
    cargoID UUID NULL REFERENCES Cargo(id),
    servicioID UUID NULL REFERENCES Servicio(id),
    passwordHash VARCHAR(255) NOT NULL,
    profileImage BYTEA NULL,
    intentosFallidos INTEGER NOT NULL DEFAULT 0,
    fechaBloqueo TIMESTAMP NULL,
    cambioPasswordRequerido BOOLEAN NOT NULL DEFAULT FALSE
);

-- Agente (Tabla 48): única subclase persistida de Usuario.
-- veDiagramaCompleto: en "Mi Diagrama" ve la grilla de su servicio o sólo sus jornadas.
-- saldoHoras: suma de las horas restantes de los créditos vigentes (MovimientoHoras).
-- vinculacion: separa el parte (relación de dependencia / monotributistas y becarios).
CREATE TABLE Agente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES Usuario(id) ON DELETE CASCADE,
    francosCompensatoriosUtilizados INTEGER NULL,
    horarioActualID UUID NULL REFERENCES HorarioBase(id),
    veDiagramaCompleto BOOLEAN NOT NULL DEFAULT TRUE,
    saldoHoras NUMERIC(7,2) NOT NULL DEFAULT 0,
    vinculacion vinculacion NOT NULL DEFAULT 'PLANTA_PERMANENTE',
    legajo INTEGER NULL UNIQUE
);

ALTER TABLE Servicio ADD FOREIGN KEY (direccionID) REFERENCES Agente(id);
ALTER TABLE Servicio ADD FOREIGN KEY (encargadoUsuarioID) REFERENCES Usuario(id);

CREATE TABLE Servicio_JefaturaDeServicio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    servicioID UUID NOT NULL REFERENCES Servicio(id) ON DELETE CASCADE,
    agenteID UUID NOT NULL REFERENCES Agente(id) ON DELETE CASCADE,
    UNIQUE (servicioID, agenteID)
);


/* ----------------------------------------------------------------------------
 * Roles con vigencia (CU06): un usuario puede tener varios roles; cada
 * asignación guarda su vigencia, el servicio y el memorando que la dispone.
 * -------------------------------------------------------------------------- */
CREATE TABLE Rol (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(255) NULL
);

CREATE TABLE AsignacionRol (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuarioID UUID NOT NULL REFERENCES Usuario(id) ON DELETE CASCADE,
    rolID UUID NOT NULL REFERENCES Rol(id) ON DELETE CASCADE,
    fechaDesde DATE NOT NULL DEFAULT CURRENT_DATE,
    fechaHasta DATE NULL,
    servicioID UUID NULL REFERENCES Servicio(id),
    memorandoRef VARCHAR(100) NULL,
    otorgadoPorID UUID NULL REFERENCES Usuario(id),
    UNIQUE (usuarioID, rolID)
);


/* ----------------------------------------------------------------------------
 * Autorización (heredada, sin uso) y EstadoTramite (memos)
 * -------------------------------------------------------------------------- */
CREATE TABLE Autorizacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fechaAutorizacion TIMESTAMP NOT NULL,
    tipoRol tipo_rol_autorizacion NOT NULL,
    autorizadoPorID UUID NOT NULL REFERENCES Usuario(id)
);

CREATE TABLE EstadoTramite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(50) NOT NULL
);
INSERT INTO EstadoTramite (nombre) VALUES
    ('BORRADOR'), ('ENVIADO'), ('PENDIENTE DE FIRMA'), ('PENDIENTE DE AUTORIZACION'), ('AUTORIZADO'),
    ('RECHAZADO'), ('OBSERVADO'), ('LEIDO'), ('COMPLETADO');


/* ----------------------------------------------------------------------------
 * Novedades (CU20–CU29)
 *
 * Circuito por instancias (NovedadAutorizacion): JS, o DH si el agente es JS
 * -> OP -> DH, más la Secretaría de Salud al superar el tope de faltas
 * justificadas, hasta aplicarse sobre el diagrama vigente.
 * -------------------------------------------------------------------------- */
-- CodigoNovedad: código numérico con el que se informa a Capital Humano, con la
-- denominación del HMU, la de Capital Humano y la sigla interna. 222 (CH/CG) y
-- 333 (PSSR) son códigos internos del HMU. esLicencia: se ofrece como tipo de
-- licencia de origen externo (CU29).
CREATE TABLE CodigoNovedad (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo INTEGER NOT NULL UNIQUE,
    denominacionHmu VARCHAR(120) NOT NULL,
    denominacionCapitalHumano VARCHAR(120) NOT NULL,
    esLicencia BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    sigla VARCHAR(6) NULL
);

INSERT INTO CodigoNovedad (codigo, denominacionHmu, denominacionCapitalHumano, esLicencia) VALUES
 (1, 'Falta injustificada', 'INASISTENCIA INJUSTIF/CARPETA MÉD. NO VÁLIDA "D"', FALSE),
 (2, 'Falta justificada', 'INASISTENCIA JUSTIFICADA', FALSE),
 (3, 'Llegada tarde injustificada', 'TARDE INJUSTIFICADA (SIN DEVOLUCIÓN) "D"', FALSE),
 (4, 'Llegada tarde injustificada (con devolución)', 'TARDE INJUSTIFICADA (CON DEVOLUCIÓN)', FALSE),
 (5, 'Llegada tarde justificada', 'TARDE JUSTIFICADA', FALSE),
 (6, 'Franco compensatorio', 'FRANCO COMPENSATORIO', FALSE),
 (7, 'SUSPENSIÓN "D"', 'SUSPENSIÓN "D"', FALSE),
 (8, 'Comisión de servicio', 'COMISIÓN', FALSE),
 (9, 'COMPENSATORIO POR MATERNIDAD', 'COMPENSATORIO POR MATERNIDAD', FALSE),
 (10, 'Carpeta médica', 'LICENCIA POR SALUD PERÍODO CORTO', TRUE),
 (11, 'Carpeta médica prolongada', 'LICENCIA POR SALUD PERÍODO LARGO', TRUE),
 (12, 'Carpeta médica por accidente de trabajo', 'LICENCIA POR SALUD ACCIDENTE DE TRABAJO', TRUE),
 (13, 'Carpeta médica por familiar', 'LICENCIA POR FAMILIAR ENFERMO', TRUE),
 (14, 'Maternidad', 'LICENCIA POR MATERNIDAD', TRUE),
 (15, 'HOMEOFFICE', 'HOMEOFFICE', FALSE),
 (16, 'LICENCIA ANUAL CARGO 2014', 'LICENCIA ANUAL CARGO 2014', TRUE),
 (17, 'DISPENSA LABORAL - FUERO SINDICAL - DELEGADOS', 'DISPENSA LABORAL - FUERO SINDICAL - DELEGADOS', FALSE),
 (18, 'LICENCIA POR DONACIÓN DE SANGRE', 'LICENCIA POR DONACIÓN DE SANGRE', TRUE),
 (19, 'LICENCIA POR ADOPCIÓN', 'LICENCIA POR ADOPCIÓN', TRUE),
 (21, 'Matrimonio', 'LICENCIA POR MATRIMONIO', TRUE),
 (22, 'Duelo', 'LICENCIA POR DUELO', TRUE),
 (23, 'Nacimiento de hijo', 'LICENCIA POR NACIMIENTO DE HIJO', TRUE),
 (24, 'LICENCIA POR MATRIMONIO DE HIJO', 'LICENCIA POR MATRIMONIO DE HIJO', TRUE),
 (25, 'Examen', 'LICENCIA POR EXAMEN', TRUE),
 (26, 'LICENCIA SIN GOCE DE SUELDO "D"', 'LICENCIA SIN GOCE DE SUELDO "D"', TRUE),
 (27, 'LICENCIA CON GOCE DE SUELDO', 'LICENCIA CON GOCE DE SUELDO', TRUE),
 (28, 'Franco', 'DESCANSO COMPENSATORIO SEMANAL', FALSE),
 (29, 'RECESO ADMINISTRATIVO - NO CONVOCADO', 'RECESO ADMINISTRATIVO - NO CONVOCADO', FALSE),
 (30, 'LICENCIA PROFILÁCTICA', 'LICENCIA PROFILÁCTICA', TRUE),
 (31, 'LICENCIA ANUAL CARGO 2019', 'LICENCIA ANUAL CARGO 2019', TRUE),
 (32, 'LICENCIA PREVENTIVA POR EMERGENCIA SANITARIA', 'LICENCIA PREVENTIVA POR EMERGENCIA SANITARIA', TRUE),
 (33, 'LICENCIA POR SALUD PERÍODO LARGO 50% "D"', 'LICENCIA POR SALUD PERÍODO LARGO 50% "D"', TRUE),
 (34, 'LICENCIA POR SALUD PERÍODO SIN GOCE "D"', 'LICENCIA POR SALUD PERÍODO SIN GOCE "D"', TRUE),
 (35, 'CARGA PÚBLICA / CONCURSO PÚBLICO', 'CARGA PÚBLICA / CONCURSO PÚBLICO', FALSE),
 (36, 'SUSPENSIÓN PROVISORIA "D"', 'SUSPENSIÓN PROVISORIA "D"', FALSE),
 (37, 'LICENCIA POR SALUD 1ER AÑO DECRETO 3711/11', 'LICENCIA POR SALUD 1ER AÑO DECRETO 3711/11', TRUE),
 (38, 'PRIVADO DE LA LIBERTAD "D"', 'PRIVADO DE LA LIBERTAD "D"', FALSE),
 (39, 'APERCIBIMIENTO POR FALTAS', 'APERCIBIMIENTO POR FALTAS', FALSE),
 (40, 'SUSPENSIÓN PÒR FALTAS "D"', 'SUSPENSIÓN PÒR FALTAS "D"', FALSE),
 (41, 'Falta justificada por fuerza mayor', 'FALTA JUSTIFICADA POR RAZONES DE FUERZA MAYOR', FALSE),
 (42, 'TARDE JUSTIFICADA POR RAZONES DE FUERZA MAYOR', 'TARDE JUSTIFICADA POR RAZONES DE FUERZA MAYOR', FALSE),
 (43, 'LICENCIA POR SALUD 2DO AÑO DCRTO 3711/11', 'LICENCIA POR SALUD 2DO AÑO DCRTO 3711/11', TRUE),
 (44, 'LICENCIA/SALUD EXCEDIDA PERÍODO CALIF. "D"', 'LICENCIA/SALUD EXCEDIDA PERÍODO CALIF. "D"', TRUE),
 (45, 'LICENCIA GREMIAL CON GOCE DE HABERES', 'LICENCIA GREMIAL CON GOCE DE HABERES', TRUE),
 (46, 'LICENCIA GREMIAL SIN GOCE DE HABERES "D"', 'LICENCIA GREMIAL SIN GOCE DE HABERES "D"', TRUE),
 (47, 'RECESO ESCOLAR - EDUCACIÓN Y CULTURA', 'RECESO ESCOLAR - EDUCACIÓN Y CULTURA', FALSE),
 (48, 'OMISIÓN INJUSTIFICADA, NO SANCIONABLE "D"', 'OMISIÓN INJUSTIFICADA, NO SANCIONABLE "D"', FALSE),
 (49, 'EVENTO DEPORTIVO CON GOCE', 'EVENTO DEPORTIVO CON GOCE', FALSE),
 (50, 'EVENTO DEPORTIVO SIN GOCE "D"', 'EVENTO DEPORTIVO SIN GOCE "D"', FALSE),
 (51, 'ADHESIÓN PARO DE T 24 HS. "D"', 'ADHESIÓN PARO DE T 24 HS. "D"', FALSE),
 (52, 'Capacitación', 'LICENCIA POR CAPACITACIÓN CON GOCE 100%', TRUE),
 (53, 'LICENCIA POR CAPACITACIÓN SIN GOCE "D"', 'LICENCIA POR CAPACITACIÓN SIN GOCE "D"', TRUE),
 (54, 'LICENCIA POR CAPACITACIÓN CON GOCE 50%', 'LICENCIA POR CAPACITACIÓN CON GOCE 50%', TRUE),
 (55, 'TARDE JUSTIFICADA C/RESOLUCIÓN (+60'')', 'TARDE JUSTIFICADA C/RESOLUCIÓN (+60'')', FALSE),
 (56, 'Retiro anticipado (más de una hora)', 'ABANDONO DE TAREAS MÁS DE UNA HORA "D"', FALSE),
 (57, 'Retiro anticipado (menos de una hora)', 'ABANDONO DE TAREAS MENOS DE UNA HORA "D"', FALSE),
 (58, 'CARG. ELECT. O RRPOLÍTICA JERÁRQUICOS "D"', 'CARG. ELECT. O RRPOLÍTICA JERÁRQUICOS "D"', FALSE),
 (59, 'INTEGRACIÓN GRUPO FAMILIAR "D"', 'INTEGRACIÓN GRUPO FAMILIAR "D"', FALSE),
 (60, 'SUSPENSIÓN POR ABANDONO DE TAREAS "D"', 'SUSPENSIÓN POR ABANDONO DE TAREAS "D"', FALSE),
 (61, 'LICENCIA ANUAL CARGO 2007', 'LICENCIA ANUAL CARGO 2007', TRUE),
 (62, 'LICENCIA ANUAL CARGO 2015', 'LICENCIA ANUAL CARGO 2015', TRUE),
 (63, 'LICENCIA ANUAL CARGO 2016', 'LICENCIA ANUAL CARGO 2016', TRUE),
 (65, 'LICENCIA SANITARIA - DCRTO Nº 4257/12', 'LICENCIA SANITARIA - DCRTO Nº 4257/12', TRUE),
 (66, 'DOCENTE SUPLENTE S/ACTIVIDAD', 'DOCENTE SUPLENTE S/ACTIVIDAD', FALSE),
 (67, 'LICENCIA ANUAL CARGO 2017', 'LICENCIA ANUAL CARGO 2017', TRUE),
 (69, 'ALTA MÉDICA', 'ALTA MÉDICA', FALSE),
 (70, 'LICENCIA/VIOLACIÓN DE GÉNERO - DCRTO 3943/17', 'LICENCIA/VIOLACIÓN DE GÉNERO - DCRTO 3943/17', TRUE),
 (71, 'Omisión de marcación justificada', 'OMISIÓN DE MARCACIÓN JUSTIFICADA/SECRETARÍA', FALSE),
 (72, 'LICENCIA ANUAL CARGO 2018', 'LICENCIA ANUAL CARGO 2018', TRUE),
 (76, 'FESTIVIDAD RELIGIOSA', 'FESTIVIDAD RELIGIOSA', FALSE),
 (77, 'EXAMEN GINECOLÓGICO', 'EXAMEN GINECOLÓGICO', TRUE),
 (78, 'SALIDA ANTICIPADA PARO DE TRANSPORTE', 'SALIDA ANTICIPADA PARO DE TRANSPORTE', FALSE),
 (80, 'BAJA, RENUNCIA, JUBILACIÓN, CESANTÍA, EXHON.', 'BAJA, RENUNCIA, JUBILACIÓN, CESANTÍA, EXHON.', FALSE),
 (81, 'P.A.V. PASIVIDAD ANTICIPADA VOLUNTARIA', 'P.A.V. PASIVIDAD ANTICIPADA VOLUNTARIA', FALSE),
 (85, 'LICENCIA ANUAL CARGO 2013', 'LICENCIA ANUAL CARGO 2013', TRUE),
 (86, 'LICENCIA X EXAMEN/ÚLTIMA MATERIA', 'LICENCIA X EXAMEN/ÚLTIMA MATERIA', TRUE),
 (88, 'LICENCIA ANUAL CARGO 2010', 'LICENCIA ANUAL CARGO 2010', TRUE),
 (89, 'LICENCIA ANUAL CARGO 2011', 'LICENCIA ANUAL CARGO 2011', TRUE),
 (90, 'LICENCIA ANUAL CARGO 2012', 'LICENCIA ANUAL CARGO 2012', TRUE),
 (91, 'LICENCIA ANUAL CARGO 2020', 'LICENCIA ANUAL CARGO 2020', TRUE),
 (92, 'LICENCIA ANUAL CARGO 2021', 'LICENCIA ANUAL CARGO 2021', TRUE),
 (99, 'Presente', 'PRESENTE', FALSE),
 (100, 'LICENCIA ANUAL CARGO 2022', 'LICENCIA ANUAL CARGO 2022', TRUE),
 (101, 'LICENCIA ANUAL CARGO 2023', 'LICENCIA ANUAL CARGO 2023', TRUE),
 (102, 'LICENCIA ANUAL CARGO 2024', 'LICENCIA ANUAL CARGO 2024', TRUE),
 (104, 'LICENCIA ANUAL CARGO 2025', 'LICENCIA ANUAL CARGO 2025', TRUE),
 (105, 'LICENCIA ANUAL CARGO 2026', 'LICENCIA ANUAL CARGO 2026', TRUE),
 (666, 'Sin justificar (revisar marcación)', 'DESCONOCIDO. REVISAR MARCACION', FALSE),
 (222, 'Cambio de horario o de guardia (CH/CG)', 'CAMBIO DE HORARIO / CAMBIO DE GUARDIA', FALSE),
 (333, 'Pase de salida sin retorno (PSSR)', 'PASE DE SALIDA SIN RETORNO', FALSE);

-- Siglas internas del HMU.
UPDATE CodigoNovedad c SET sigla = v.sigla
FROM (VALUES (1, 'FI'), (2, 'FJ'), (41, 'FJ'), (3, 'T'), (4, 'T'), (5, 'T'), (42, 'T'), (55, 'T'),
             (6, 'FC'), (8, 'CS'), (52, 'CAP'), (53, 'CAP'), (54, 'CAP'), (56, 'RA'), (57, 'RA'),
             (48, 'OM'), (71, 'OM'), (28, 'F'), (99, 'P'), (222, 'CH/CG'), (333, 'PSSR'), (666, '—'))
     AS v(codigo, sigla)
WHERE c.codigo = v.codigo AND c.sigla IS NULL;
UPDATE CodigoNovedad SET sigla = 'LIC' WHERE esLicencia AND sigla IS NULL;

-- TipoNovedad: afectaSaldoHoras +1 acredita horas (horas extras), -1 las
-- consume (franco compensatorio), 0 no afecta. codigoNovedadID: código con el
-- que el tipo llega al parte; nulo si no se informa (horas extras, pases con
-- retorno y oficial, devolución) o si depende de cada novedad (licencia externa).
CREATE TABLE TipoNovedad (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    requiereContraparte BOOLEAN NOT NULL DEFAULT FALSE,
    afectaSaldoHoras SMALLINT NOT NULL DEFAULT 0
        CONSTRAINT chk_tiponovedad_afectasaldohoras CHECK (afectaSaldoHoras BETWEEN -1 AND 1),
    requiereDocumentacion BOOLEAN NOT NULL DEFAULT FALSE,
    esExterna BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    codigoNovedadID UUID NULL REFERENCES CodigoNovedad(id)
);

INSERT INTO TipoNovedad (codigo, nombre, requiereContraparte, afectaSaldoHoras, requiereDocumentacion, esExterna,
                         codigoNovedadID)
SELECT v.codigo, v.nombre, v.contraparte, v.saldo, v.doc, v.externa, c.id
FROM (VALUES ('CG',   'Cambio de guardia',             TRUE,   0, FALSE, FALSE, 222),
             ('CH',   'Cambio de horario',             FALSE,  0, FALSE, FALSE, 222),
             ('FC',   'Franco compensatorio',          FALSE, -1, FALSE, FALSE, 6),
             ('PSO',  'Pase de salida oficial',        FALSE,  0, FALSE, FALSE, NULL),
             ('PSP',  'Pase de salida con retorno',    FALSE,  0, FALSE, FALSE, NULL),
             ('PSSR', 'Pase de salida sin retorno',    FALSE,  0, FALSE, FALSE, 333),
             ('DEV',  'Devolución de pase de salida',  FALSE,  0, FALSE, FALSE, NULL),
             ('FJ',   'Falta justificada',             FALSE,  0, TRUE,  FALSE, 2),
             ('HE',   'Horas extras para FC',          FALSE,  1, FALSE, FALSE, NULL),
             ('OM',   'Omisión de marcación',          FALSE,  0, FALSE, FALSE, 71),
             ('CS',   'Comisión de servicio',          FALSE,  0, TRUE,  FALSE, 8),
             ('LIC',  'Licencia (externa)',            FALSE,  0, FALSE, TRUE,  NULL))
     AS v(codigo, nombre, contraparte, saldo, doc, externa, cod)
LEFT JOIN CodigoNovedad c ON c.codigo = v.cod;

-- Novedad (Tabla 52). codigoNovedadID: código propio cuando difiere del de su
-- tipo (por ejemplo, 41 o el tipo de licencia externa). novedadOrigenID: el
-- pase de salida que devuelve una devolución. justificacion: la de las horas
-- extras (habitualmente «Necesidad del servicio»), distinta del motivo.
CREATE TABLE Novedad (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipoNovedadID UUID NOT NULL REFERENCES TipoNovedad(id),
    codigoNovedadID UUID NULL REFERENCES CodigoNovedad(id),
    novedadOrigenID UUID NULL REFERENCES Novedad(id),
    agenteID UUID NOT NULL REFERENCES Agente(id),
    contraparteAgenteID UUID NULL REFERENCES Agente(id),
    fechaDesde DATE NOT NULL,
    fechaHasta DATE NOT NULL,
    horaDesde TIME NULL,
    horaHasta TIME NULL,
    cantHoras NUMERIC(6,2) NULL,
    motivo TEXT NULL,
    justificacion VARCHAR(255) NULL,
    estado estado_novedad NOT NULL DEFAULT 'BORRADOR',
    origen origen_novedad NOT NULL DEFAULT 'SOLICITUD',
    solicitadoPorID UUID NOT NULL REFERENCES Usuario(id),
    fechaSolicitud TIMESTAMP NULL,
    referenciaExterna VARCHAR(100) NULL,
    version INTEGER NOT NULL DEFAULT 0,
    createdAt TIMESTAMP NOT NULL DEFAULT now(),
    updatedAt TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_novedad_fechas CHECK (fechaHasta >= fechaDesde)
);

-- Heredada de la etapa anterior (N:M); sin uso.
CREATE TABLE Agente_Novedad (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agenteID UUID NOT NULL REFERENCES Agente(id),
    novedadID UUID NOT NULL REFERENCES Novedad(id),
    UNIQUE (agenteID, novedadID)
);

-- NovedadAutorizacion (Tabla 53): una fila por instancia del circuito.
CREATE TABLE NovedadAutorizacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    novedadID UUID NOT NULL REFERENCES Novedad(id) ON DELETE CASCADE,
    instancia instancia_novedad NOT NULL,
    orden SMALLINT NOT NULL,
    estado estado_novedad_autorizacion NOT NULL DEFAULT 'PENDIENTE',
    resueltoPorID UUID NULL REFERENCES Usuario(id),
    fechaResolucion TIMESTAMP NULL,
    comentarios TEXT NULL,
    UNIQUE (novedadID, orden)
);

-- Respaldo documental de la novedad (DER, Fig. 20c).
CREATE TABLE NovedadAdjunto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    novedadID UUID NOT NULL REFERENCES Novedad(id) ON DELETE CASCADE,
    nombreArchivo VARCHAR(255) NOT NULL,
    tipoMime VARCHAR(100) NOT NULL,
    contenido BYTEA NOT NULL,
    cargadoPorID UUID NOT NULL REFERENCES Usuario(id),
    fechaCarga TIMESTAMP NOT NULL DEFAULT now()
);

-- MovimientoHoras (Tabla 54): banco de horas. Los créditos vencen
-- (fechaVencimiento) y guardan sus horas restantes; un débito (FC) o un
-- vencimiento consume un único crédito (creditoID): un FC que consume varios
-- créditos genera un débito por crédito, todos con el mismo novedadID.
CREATE TABLE MovimientoHoras (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agenteID UUID NOT NULL REFERENCES Agente(id),
    novedadID UUID NULL REFERENCES Novedad(id),
    tipo tipo_movimiento_horas NOT NULL,
    fecha DATE NOT NULL,
    fechaVencimiento DATE NULL,
    horas NUMERIC(6,2) NOT NULL,
    horasRestantes NUMERIC(6,2) NULL,
    creditoID UUID NULL REFERENCES MovimientoHoras(id),
    concepto VARCHAR(255) NOT NULL,
    saldoResultante NUMERIC(7,2) NOT NULL,
    registradoPorID UUID NOT NULL REFERENCES Usuario(id),
    CONSTRAINT chk_movimientohoras_credito CHECK (
        CASE WHEN tipo = 'CREDITO' THEN fechaVencimiento IS NOT NULL AND horasRestantes IS NOT NULL
             ELSE fechaVencimiento IS NULL AND horasRestantes IS NULL END),
    CONSTRAINT chk_movimientohoras_creditoid CHECK (
        (tipo IN ('DEBITO', 'VENCIMIENTO')) = (creditoID IS NOT NULL))
);

-- Aviso en la aplicación + estado del correo (CU27). tipo y entidad son
-- abiertos (NOVEDAD_<estado>, RECORDATORIO_DIAGRAMA, PARTE_CERRADO...).
CREATE TABLE Notificacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuarioID UUID NOT NULL REFERENCES Usuario(id),
    tipo VARCHAR(50) NOT NULL,
    asunto VARCHAR(255) NOT NULL,
    cuerpo TEXT NULL,
    entidad VARCHAR(50) NULL,
    entidadID UUID NULL,
    fechaCreacion TIMESTAMP NOT NULL DEFAULT now(),
    fechaLectura TIMESTAMP NULL,
    enviadaEmail BOOLEAN NOT NULL DEFAULT FALSE
);


/* ----------------------------------------------------------------------------
 * Feriados (CU09): carga anual directa de la OP; las altas y bajas
 * posteriores las autoriza la Dirección. Un feriado con baja pendiente sigue
 * vigente.
 * -------------------------------------------------------------------------- */
CREATE TABLE Feriado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha DATE NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    estado estado_feriado NOT NULL DEFAULT 'VIGENTE',
    creadoPorID UUID NOT NULL REFERENCES Usuario(id),
    resueltoPorID UUID NULL REFERENCES Usuario(id),
    fechaResolucion TIMESTAMP NULL,
    createdAt TIMESTAMP NOT NULL DEFAULT now()
);


/* ----------------------------------------------------------------------------
 * Diagramación de servicios (Tablas 50 y 51)
 * -------------------------------------------------------------------------- */
-- Diagrama mensual: BORRADOR -> PENDIENTE_APROBACION -> APROBADO / OBSERVADO,
-- con versión para la concurrencia optimista.
CREATE TABLE DiagramaDeServicio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    servicioID UUID NOT NULL REFERENCES Servicio(id),
    estado estado_diagrama NOT NULL DEFAULT 'BORRADOR',
    fechaInicio DATE NOT NULL,
    fechaFin DATE NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    creadoPorID UUID NOT NULL REFERENCES Usuario(id),
    createdAt TIMESTAMP NOT NULL DEFAULT now(),
    updatedAt TIMESTAMP NOT NULL DEFAULT now(),
    aprobadoPorID UUID NULL REFERENCES Usuario(id),
    fechaAprobacion TIMESTAMP NULL,
    comentariosObservacion TEXT NULL,
    CONSTRAINT chk_diagramadeservicio_fechas CHECK (fechaFin >= fechaInicio)
);

-- Una fila por agente por día del diagrama. fechaIngreso/fechaEgreso nulos en
-- francos y licencias; el nocturno cruza la medianoche, por eso no hay
-- unicidad por agente y fecha (la superposición la valida la aplicación).
CREATE TABLE JornadaLaboral (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diagramaID UUID NOT NULL REFERENCES DiagramaDeServicio(id) ON DELETE CASCADE,
    agenteID UUID NOT NULL REFERENCES Agente(id),
    fecha DATE NOT NULL,
    tipo tipo_jornada NOT NULL,
    fechaIngreso TIMESTAMP NULL,
    fechaEgreso TIMESTAMP NULL,
    observaciones TEXT NULL,
    novedadID UUID NULL REFERENCES Novedad(id)
);


/* ----------------------------------------------------------------------------
 * Memorandos (fuera del alcance del prototipo)
 * -------------------------------------------------------------------------- */
CREATE TABLE Memorandum (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asunto VARCHAR(255) NOT NULL,
    contenido TEXT NOT NULL,
    fechaEnvio TIMESTAMP NULL,
    fechaRecepcion TIMESTAMP NULL,
    estadoTramiteID UUID NOT NULL REFERENCES EstadoTramite(id),
    remitenteID UUID NOT NULL REFERENCES Usuario(id)
);

-- fechaRecepcion = fecha de lectura (nulo: no leído).
CREATE TABLE Memorandum_Destinatario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memorandumID UUID NOT NULL REFERENCES Memorandum(id),
    usuarioID UUID NOT NULL REFERENCES Usuario(id),
    fechaRecepcion TIMESTAMP NULL,
    UNIQUE (memorandumID, usuarioID)
);

CREATE TABLE Memorandum_Firmante (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memorandumID UUID NOT NULL REFERENCES Memorandum(id),
    usuarioID UUID NOT NULL REFERENCES Usuario(id),
    fechaFirma TIMESTAMP NOT NULL,
    UNIQUE (memorandumID, usuarioID)
);

CREATE TABLE Memorandum_Autorizacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memorandumID UUID NOT NULL REFERENCES Memorandum(id),
    tipoRol tipo_rol_memo_autorizacion NOT NULL,
    autorizadoPorID UUID NULL REFERENCES Usuario(id),
    fechaAutorizacion TIMESTAMP NULL,
    estado estado_memo_autorizacion NOT NULL,
    comentarios TEXT NULL
);


/* ----------------------------------------------------------------------------
 * Compensaciones heredadas (sin uso: el banco de horas es
 * MovimientoHoras). jefaturaID: agente con rol de jefatura.
 * -------------------------------------------------------------------------- */
CREATE TABLE FrancoCompensatorio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cantHoras NUMERIC(6,2) NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    fechaAutorizacion TIMESTAMP NULL,
    fechaDeAplicacion DATE NULL,
    estadoTramiteID UUID NOT NULL REFERENCES EstadoTramite(id),
    autorizadaPorID UUID NULL REFERENCES Usuario(id),
    agenteID UUID NOT NULL REFERENCES Agente(id),
    jefaturaID UUID NULL REFERENCES Agente(id)
);

CREATE TABLE HoraExtra (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    descripcion VARCHAR(255) NOT NULL,
    fechaIngreso TIMESTAMP NOT NULL,
    fechaEgreso TIMESTAMP NOT NULL,
    ponderacion INTEGER NOT NULL,
    fechaAutorizacion TIMESTAMP NULL,
    estadoTramiteID UUID NOT NULL REFERENCES EstadoTramite(id),
    autorizadaPorID UUID NULL REFERENCES Usuario(id),
    agenteID UUID NOT NULL REFERENCES Agente(id),
    jefaturaID UUID NULL REFERENCES Agente(id)
);


/* ----------------------------------------------------------------------------
 * Marcaciones, cotejo y parte diario (CU30–CU35)
 * -------------------------------------------------------------------------- */
-- LoteMarcaciones (Tabla 55): cada archivo importado del sistema de relojes.
CREATE TABLE LoteMarcaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombreArchivo VARCHAR(255) NOT NULL,
    hashArchivo VARCHAR(64) NOT NULL UNIQUE,
    fechaImportacion TIMESTAMP NOT NULL DEFAULT now(),
    importadoPorID UUID NOT NULL REFERENCES Usuario(id),
    cantidadRegistros INTEGER NOT NULL,
    rangoDesde DATE NULL,
    rangoHasta DATE NULL
);

CREATE TABLE Marcacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fechaMarcacion TIMESTAMP NOT NULL,
    observaciones VARCHAR(255) NULL,
    tipoMarcacion tipo_marcacion NOT NULL,
    validada BOOLEAN NOT NULL,
    agenteID UUID NOT NULL REFERENCES Agente(id),
    loteID UUID NULL REFERENCES LoteMarcaciones(id),
    dispositivo VARCHAR(100) NULL
);

-- JornadaRegistrada (Tabla 56): las marcaciones deben ser del mismo agente, de
-- ingreso y de egreso (lo valida la aplicación); nulas si se omitieron.
CREATE TABLE JornadaRegistrada (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha DATE NOT NULL,
    agenteID UUID NOT NULL REFERENCES Agente(id),
    marcacionIngresoID UUID NULL REFERENCES Marcacion(id),
    marcacionEgresoID UUID NULL REFERENCES Marcacion(id),
    duracionMinutos INTEGER NULL,
    omisionIngreso BOOLEAN NOT NULL DEFAULT FALSE,
    omisionEgreso BOOLEAN NOT NULL DEFAULT FALSE
);

-- ParteDiario (DER, Fig. 20b): un parte por fecha, con versión.
CREATE TABLE ParteDiario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha DATE NULL,
    estado estado_parte NOT NULL DEFAULT 'ABIERTO',
    periodo VARCHAR(50) NULL,
    generadoEn TIMESTAMP NULL,
    generadoPorID UUID NULL REFERENCES Usuario(id),
    fechaDeCierre TIMESTAMP NULL,
    cerradoPorID UUID NULL REFERENCES Usuario(id),
    modificadoPorID UUID NULL REFERENCES Usuario(id),
    oficinaPersonalID UUID NULL REFERENCES Agente(id),
    version INTEGER NOT NULL DEFAULT 0
);

-- Heredada (N:M); sin uso: el detalle es ParteDiarioDetalle.
CREATE TABLE ParteDiario_Agente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parteDiarioID UUID NOT NULL REFERENCES ParteDiario(id),
    agenteID UUID NOT NULL REFERENCES Agente(id),
    UNIQUE (parteDiarioID, agenteID)
);

-- ParteDiarioDetalle (Tabla 57): un detalle por agente (y las cargas manuales).
-- Al reconformar las jornadas registradas (importación repetible) el detalle
-- conserva su código: las FK a las jornadas quedan en nulo.
CREATE TABLE ParteDiarioDetalle (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parteDiarioID UUID NOT NULL REFERENCES ParteDiario(id) ON DELETE CASCADE,
    agenteID UUID NOT NULL REFERENCES Agente(id),
    servicioID UUID NULL REFERENCES Servicio(id),
    tipoNovedadID UUID NULL REFERENCES TipoNovedad(id),
    tipoDiferencia tipo_diferencia NULL,
    novedadID UUID NULL REFERENCES Novedad(id),
    jornadaLaboralID UUID NULL REFERENCES JornadaLaboral(id) ON DELETE SET NULL,
    jornadaRegistradaID UUID NULL REFERENCES JornadaRegistrada(id) ON DELETE SET NULL,
    origen origen_detalle_parte NOT NULL,
    codigoNovedadID UUID NOT NULL REFERENCES CodigoNovedad(id),
    observaciones TEXT NULL,
    cargadoPorID UUID NULL REFERENCES Usuario(id),
    createdAt TIMESTAMP NOT NULL DEFAULT now()
);


/* ----------------------------------------------------------------------------
 * Reglas de validación parametrizadas (CU08)
 * -------------------------------------------------------------------------- */
CREATE TABLE ReglaValidacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(20) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT NULL,
    ambito ambito_regla NOT NULL,
    modalidad VARCHAR(50) NULL,
    severidad severidad_regla NOT NULL,
    parametros JSONB NOT NULL DEFAULT '{}'::jsonb,
    vigenteDesde DATE NOT NULL,
    vigenteHasta DATE NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fundamentoNormativo VARCHAR(150) NULL,
    UNIQUE (codigo, vigenteDesde),
    CONSTRAINT chk_reglavalidacion_vigencia CHECK (vigenteHasta IS NULL OR vigenteHasta >= vigenteDesde),
    CONSTRAINT chk_reglavalidacion_parametros CHECK (jsonb_typeof(parametros) = 'object')
);

-- Catálogo inicial: los valores con los que el motor validaba en código.
INSERT INTO ReglaValidacion (codigo, nombre, descripcion, ambito, modalidad, severidad, parametros, vigenteDesde)
VALUES
 ('RV-DESCANSO-10H', 'Descanso mínimo entre jornadas',
  'No pueden transcurrir menos de N horas corridas entre un egreso y el ingreso siguiente (condicionalidad general del HMU).',
  'AMBOS', NULL, 'BLOQUEANTE', '{"horasMinimas": 10}', '2026-01-01'),
 ('RV-SUPERPOSICION', 'Superposición de jornadas',
  'Las jornadas de un mismo agente no pueden superponerse (el nocturno se compara por sus horarios completos).',
  'AMBOS', NULL, 'BLOQUEANTE', '{}', '2026-01-01'),
 ('RV-HORARIO', 'Horario de la jornada',
  'Los turnos y guardias deben tener ingreso y egreso, con el egreso posterior al ingreso.',
  'DIAGRAMA', NULL, 'BLOQUEANTE', '{}', '2026-01-01'),
 ('RV-DURACION-MIN', 'Duración mínima de la jornada',
  'Toda jornada con horario debe durar al menos N horas.',
  'DIAGRAMA', NULL, 'BLOQUEANTE', '{"horasMinimas": 1}', '2026-01-01'),
 ('RV-TURNO-MAX', 'Duración máxima del turno',
  'Un turno normal no puede superar N horas.',
  'DIAGRAMA', NULL, 'BLOQUEANTE', '{"horasMaximas": 12}', '2026-01-01'),
 ('RV-GUARDIA-MAX', 'Duración máxima de la guardia',
  'Una guardia (activa o pasiva) planificada no puede superar N horas.',
  'DIAGRAMA', NULL, 'BLOQUEANTE', '{"horasMaximas": 24}', '2026-01-01'),
 ('RV-CARGA-NOCT', 'Carga mensual del nocturno',
  'Las horas planificadas del personal nocturno deben coincidir con las de su modalidad (140 h/mes), con la tolerancia indicada.',
  'DIAGRAMA', 'HorarioNocturno', 'ADVERTENCIA', '{"toleranciaHoras": 0}', '2026-01-01'),
 ('RV-CARGA-FER', 'Carga mínima del feriante',
  'El personal feriante debe cumplir el mínimo mensual de horas de su modalidad (120 h/mes).',
  'DIAGRAMA', 'HorarioFeriante', 'ADVERTENCIA', '{"toleranciaHoras": 0}', '2026-01-01'),
 ('RV-CARGA-ENF', 'Carga mensual de guardia de enfermería',
  'Las guardias de enfermería deben sumar las horas mensuales de la modalidad (10 de 12 h + 2 de 10 h).',
  'DIAGRAMA', 'HorarioGuardiaEnfermeria', 'ADVERTENCIA', '{"toleranciaHoras": 0}', '2026-01-01'),
 ('RV-DOTACION-MIN', 'Dotación mínima del servicio',
  'En cada día y franja horaria (mañana, tarde y noche) debe haber al menos el porcentaje indicado de la dotación mínima del servicio.',
  'DIAGRAMA', NULL, 'ADVERTENCIA',
  '{"mananaDesde": 7, "tardeDesde": 14, "nocheDesde": 21, "porcentajeManiana": 100, "porcentajeTarde": 50, "porcentajeNoche": 50}',
  '2026-01-01'),
 ('RV-NOV-SALDO-FC', 'Saldo de horas para franco compensatorio',
  'El franco compensatorio no puede superar el saldo de horas del agente.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{}', '2026-01-01'),
 ('RV-NOV-TOPE-FJ', 'Tope mensual de faltas justificadas',
  'Superado el tope mensual de faltas justificadas, la novedad requiere la autorización de la Secretaría de Salud.',
  'NOVEDAD', NULL, 'ADVERTENCIA', '{"topeMensual": 2}', '2026-01-01'),
 ('RV-NOV-SUPERPOS', 'Superposición con licencias y novedades',
  'La novedad no puede superponerse con una licencia del agente; con otras novedades en trámite se advierte.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{}', '2026-01-01');

-- Tolerancia del cotejo (CU32 paso 2).
INSERT INTO ReglaValidacion (codigo, nombre, descripcion, ambito, modalidad, severidad, parametros, vigenteDesde)
VALUES ('RV-COTEJO-TOLER', 'Tolerancia del cotejo de asistencia',
        'Minutos de tolerancia para el ingreso y el egreso respecto de la jornada diagramada.',
        'ASISTENCIA', NULL, 'ADVERTENCIA', '{"toleranciaMinutos": 5}', '2026-01-01');

-- Banco de horas, francos compensatorios y pases.
INSERT INTO ReglaValidacion (codigo, nombre, descripcion, ambito, modalidad, severidad, parametros, vigenteDesde)
VALUES
 ('RV-HE-COMPUTO', 'Cómputo de horas extras para FC',
  'Sólo cuenta el tiempo posterior al egreso diagramado, en bloques completos; tope de jornada desde el ingreso '
  || 'diagramado (salvo guardias, el excedente se pierde); cómputo doble en feriados, asuetos, fines de semana y '
  || 'franja nocturna.',
  'ASISTENCIA', NULL, 'ADVERTENCIA',
  '{"fraccionMinutos": 30, "topeJornadaHoras": 12, "factorDoble": 2, "nocheDesde": 21, "nocheHasta": 6}',
  '2026-01-01'),
 ('RV-BANCO-VENC', 'Vencimiento de las horas del banco',
  'Cada crédito de horas vence a los N días de acreditado; lo no usado se da de baja con un movimiento de vencimiento.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{"vencimientoDias": 180}', '2026-01-01'),
 ('RV-NOV-FC-FECHA', 'Fecha del franco compensatorio',
  'Normalmente futura; retroactiva dentro del mismo mes o, si cambió el mes, hasta el día N del mes siguiente.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{"diaLimiteMesSiguiente": 7}', '2026-01-01'),
 ('RV-NOV-PASE-MAX', 'Duración máxima del pase de salida con retorno',
  'Horas máximas fuera de la jornada por cada pase con retorno.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{"horasMaximas": 3}', '2026-01-01'),
 ('RV-NOV-PSSR', 'Pase de salida sin retorno',
  'Puede iniciarse hasta N horas antes del egreso diagramado.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{"anticipoMaxHoras": 3}', '2026-01-01'),
 ('RV-NOV-PASE-TOPE', 'Tope mensual de pases de salida',
  'Horas por mes sumando los pases con y sin retorno.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{"topeMensualHoras": 5}', '2026-01-01'),
 ('RV-NOV-DEVOL', 'Devolución de pases de salida',
  'Plazo en días corridos para devolver el tiempo de un pase con retorno (0 = el mismo día) y de un PSSR; no se '
  || 'pueden devolver más minutos que los pendientes.',
  'NOVEDAD', NULL, 'BLOQUEANTE', '{"plazoConRetornoDias": 0, "plazoSinRetornoDias": 7}', '2026-01-01');

-- Fundamento normativo, al final de los mensajes de validación.
UPDATE ReglaValidacion SET fundamentoNormativo = 'Decreto 1605/2018'
WHERE codigo IN ('RV-DESCANSO-10H', 'RV-TURNO-MAX');
UPDATE ReglaValidacion SET fundamentoNormativo = 'Ordenanza 7974'
WHERE codigo IN ('RV-CARGA-NOCT', 'RV-CARGA-FER', 'RV-CARGA-ENF');


/* ----------------------------------------------------------------------------
 * Índices: uno por cada clave foránea (idx_<tabla>_<columna>) y los de
 * consulta frecuente.
 * -------------------------------------------------------------------------- */
CREATE INDEX idx_agente_horarioactual ON Agente(horarioActualID);

CREATE INDEX idx_agente_novedad_novedad ON Agente_Novedad(novedadID);

CREATE INDEX idx_autorizacion_autorizadopor ON Autorizacion(autorizadoPorID);

CREATE INDEX idx_diagramadeservicio_aprobadopor ON DiagramaDeServicio(aprobadoPorID);
CREATE INDEX idx_diagramadeservicio_creadopor ON DiagramaDeServicio(creadoPorID);
CREATE INDEX idx_diagramadeservicio_servicio ON DiagramaDeServicio(servicioID);

CREATE INDEX idx_domicilio_barrio ON Domicilio(barrio);
CREATE INDEX idx_domicilio_calle ON Domicilio(calle);
CREATE INDEX idx_domicilio_ciudad ON Domicilio(ciudad);
CREATE INDEX idx_domicilio_localidad ON Domicilio(localidad);

CREATE INDEX idx_feriado_creadopor ON Feriado(creadoPorID);
CREATE UNIQUE INDEX idx_feriado_fecha_activo ON Feriado(fecha) WHERE estado IN ('VIGENTE', 'ALTA_PENDIENTE', 'BAJA_PENDIENTE');
CREATE INDEX idx_feriado_resueltopor ON Feriado(resueltoPorID);

CREATE INDEX idx_francocompensatorio_agente ON FrancoCompensatorio(agenteID);
CREATE INDEX idx_francocompensatorio_autorizadapor ON FrancoCompensatorio(autorizadaPorID);
CREATE INDEX idx_francocompensatorio_estadotramite ON FrancoCompensatorio(estadoTramiteID);
CREATE INDEX idx_francocompensatorio_jefatura ON FrancoCompensatorio(jefaturaID);

CREATE INDEX idx_horaextra_agente ON HoraExtra(agenteID);
CREATE INDEX idx_horaextra_autorizadapor ON HoraExtra(autorizadaPorID);
CREATE INDEX idx_horaextra_estadotramite ON HoraExtra(estadoTramiteID);
CREATE INDEX idx_horaextra_jefatura ON HoraExtra(jefaturaID);

CREATE INDEX idx_horarioconfranquicia_horariodecorado ON HorarioConFranquicia(horarioDecoradoID);

CREATE INDEX idx_jornadalaboral_agente_fecha ON JornadaLaboral(agenteID, fecha);
CREATE INDEX idx_jornadalaboral_diagrama ON JornadaLaboral(diagramaID);
CREATE INDEX idx_jornadalaboral_novedad ON JornadaLaboral(novedadID);

CREATE INDEX idx_jornadaregistrada_agente_fecha ON JornadaRegistrada(agenteID, fecha);
CREATE INDEX idx_jornadaregistrada_marcacionegreso ON JornadaRegistrada(marcacionEgresoID);
CREATE INDEX idx_jornadaregistrada_marcacioningreso ON JornadaRegistrada(marcacionIngresoID);

CREATE INDEX idx_lotemarcaciones_importadopor ON LoteMarcaciones(importadoPorID);

CREATE INDEX idx_marcacion_agente_fecha ON Marcacion(agenteID, fechaMarcacion);
CREATE INDEX idx_marcacion_lote ON Marcacion(loteID);

CREATE INDEX idx_memorandum_estadotramite ON Memorandum(estadoTramiteID);
CREATE INDEX idx_memorandum_remitente ON Memorandum(remitenteID);

CREATE INDEX idx_memorandum_autorizacion_autorizadopor ON Memorandum_Autorizacion(autorizadoPorID);
CREATE INDEX idx_memorandum_autorizacion_memorandum ON Memorandum_Autorizacion(memorandumID);

CREATE INDEX idx_memorandum_destinatario_usuario ON Memorandum_Destinatario(usuarioID);

CREATE INDEX idx_memorandum_firmante_usuario ON Memorandum_Firmante(usuarioID);

CREATE INDEX idx_movimientohoras_agente ON MovimientoHoras(agenteID, fecha);
CREATE INDEX idx_movimientohoras_credito ON MovimientoHoras(creditoID);
CREATE INDEX idx_movimientohoras_novedad ON MovimientoHoras(novedadID);
CREATE INDEX idx_movimientohoras_registradopor ON MovimientoHoras(registradoPorID);
CREATE INDEX idx_movimientohoras_vencimiento ON MovimientoHoras(agenteID, fechaVencimiento) WHERE tipo = 'CREDITO';

CREATE INDEX idx_notificacion_usuario ON Notificacion(usuarioID, fechaLectura);

CREATE INDEX idx_novedad_agente_fechas ON Novedad(agenteID, fechaDesde, fechaHasta);
CREATE INDEX idx_novedad_codigonovedad ON Novedad(codigoNovedadID);
CREATE INDEX idx_novedad_contraparteagente ON Novedad(contraparteAgenteID);
CREATE INDEX idx_novedad_estado ON Novedad(estado);
CREATE INDEX idx_novedad_novedadorigen ON Novedad(novedadOrigenID);
CREATE INDEX idx_novedad_solicitadopor ON Novedad(solicitadoPorID);
CREATE INDEX idx_novedad_tiponovedad ON Novedad(tipoNovedadID);

CREATE INDEX idx_novedadadjunto_cargadopor ON NovedadAdjunto(cargadoPorID);
CREATE INDEX idx_novedadadjunto_novedad ON NovedadAdjunto(novedadID);

CREATE INDEX idx_novedadautorizacion_novedad ON NovedadAutorizacion(novedadID);
CREATE INDEX idx_novedadautorizacion_resueltopor ON NovedadAutorizacion(resueltoPorID);

CREATE INDEX idx_partediario_cerradopor ON ParteDiario(cerradoPorID);
CREATE UNIQUE INDEX idx_partediario_fecha ON ParteDiario(fecha);
CREATE INDEX idx_partediario_generadopor ON ParteDiario(generadoPorID);
CREATE INDEX idx_partediario_modificadopor ON ParteDiario(modificadoPorID);
CREATE INDEX idx_partediario_oficinapersonal ON ParteDiario(oficinaPersonalID);

CREATE INDEX idx_partediario_agente_agente ON ParteDiario_Agente(agenteID);

CREATE INDEX idx_partediariodetalle_agente ON ParteDiarioDetalle(agenteID);
CREATE INDEX idx_partediariodetalle_cargadopor ON ParteDiarioDetalle(cargadoPorID);
CREATE INDEX idx_partediariodetalle_codigonovedad ON ParteDiarioDetalle(codigoNovedadID);
CREATE INDEX idx_partediariodetalle_jornadalaboral ON ParteDiarioDetalle(jornadaLaboralID);
CREATE INDEX idx_partediariodetalle_jornadaregistrada ON ParteDiarioDetalle(jornadaRegistradaID);
CREATE INDEX idx_partediariodetalle_novedad ON ParteDiarioDetalle(novedadID);
CREATE INDEX idx_partediariodetalle_partediario ON ParteDiarioDetalle(parteDiarioID);
CREATE INDEX idx_partediariodetalle_servicio ON ParteDiarioDetalle(servicioID);
CREATE INDEX idx_partediariodetalle_tiponovedad ON ParteDiarioDetalle(tipoNovedadID);

CREATE INDEX idx_reglavalidacion_vigencia ON ReglaValidacion(codigo, vigenteDesde, vigenteHasta);

CREATE INDEX idx_servicio_direccion ON Servicio(direccionID);
CREATE INDEX idx_servicio_encargadousuario ON Servicio(encargadoUsuarioID);
CREATE UNIQUE INDEX idx_servicio_nombre ON Servicio(nombre);

CREATE INDEX idx_servicio_jefaturadeservicio_agente ON Servicio_JefaturaDeServicio(agenteID);

CREATE INDEX idx_tiponovedad_codigonovedad ON TipoNovedad(codigoNovedadID);

CREATE INDEX idx_usuario_apellidos ON Usuario(apellidos);
CREATE INDEX idx_usuario_cargo ON Usuario(cargoID);
CREATE INDEX idx_usuario_domicilio ON Usuario(domicilioID);
CREATE UNIQUE INDEX idx_usuario_mail ON Usuario(mail);
CREATE INDEX idx_usuario_nombres ON Usuario(nombres);
CREATE INDEX idx_usuario_servicio ON Usuario(servicioID);
CREATE UNIQUE INDEX idx_usuario_tel ON Usuario(tel);

CREATE INDEX idx_asignacionrol_otorgadopor ON AsignacionRol(otorgadoPorID);
CREATE INDEX idx_asignacionrol_rol ON AsignacionRol(rolID);
CREATE INDEX idx_asignacionrol_servicio ON AsignacionRol(servicioID);
CREATE INDEX idx_asignacionrol_usuario ON AsignacionRol(usuarioID);


/* ----------------------------------------------------------------------------
 * Auditoría por disparadores (RNF03)
 *
 * EventoAuditoria es de sólo inserción; fn_auditoria registra quién
 * (app.usuario_id, fijada por la aplicación con set_config(..., true) en cada
 * transacción), cuándo, tabla, registro y valores anterior y posterior.
 * usuarioID no es FK: el evento sobrevive al registro que lo originó.
 * -------------------------------------------------------------------------- */
CREATE TABLE EventoAuditoria (
    id BIGSERIAL PRIMARY KEY,
    fechaHora TIMESTAMP NOT NULL DEFAULT clock_timestamp(),
    usuarioID UUID NULL,
    tabla VARCHAR(63) NOT NULL,
    registroID UUID NULL,
    operacion operacion_auditoria NOT NULL,
    datosAnteriores JSONB NULL,
    datosPosteriores JSONB NULL
);
CREATE INDEX idx_eventoauditoria_registro ON EventoAuditoria(tabla, registroID);
CREATE INDEX idx_eventoauditoria_usuario ON EventoAuditoria(usuarioID, fechaHora);

-- No se registran el hash de la contraseña, la foto ni el contenido de los adjuntos.
CREATE FUNCTION fn_auditoria() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_usuario  UUID;
    v_anterior JSONB;
    v_posterior JSONB;
    v_registro UUID;
BEGIN
    BEGIN
        v_usuario := NULLIF(current_setting('app.usuario_id', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_usuario := NULL;
    END;

    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        v_anterior := to_jsonb(OLD) - 'passwordhash' - 'profileimage' - 'contenido';
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        v_posterior := to_jsonb(NEW) - 'passwordhash' - 'profileimage' - 'contenido';
    END IF;

    -- Una actualización que no cambia nada no deja rastro.
    IF TG_OP = 'UPDATE' AND v_anterior = v_posterior THEN
        RETURN NULL;
    END IF;

    v_registro := COALESCE(v_posterior, v_anterior) ->> 'id';

    INSERT INTO EventoAuditoria (usuarioID, tabla, registroID, operacion, datosAnteriores, datosPosteriores)
    VALUES (v_usuario, TG_TABLE_NAME, v_registro, TG_OP::operacion_auditoria, v_anterior, v_posterior);
    RETURN NULL;
END;
$$;

-- La tabla de auditoría es de sólo inserción.
CREATE FUNCTION fn_auditoria_inmutable() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'EventoAuditoria es de sólo inserción: no se permite % (RNF03).', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_eventoauditoria_inmutable
    BEFORE UPDATE OR DELETE ON EventoAuditoria
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_inmutable();
CREATE TRIGGER trg_eventoauditoria_sin_truncate
    BEFORE TRUNCATE ON EventoAuditoria
    FOR EACH STATEMENT EXECUTE FUNCTION fn_auditoria_inmutable();
REVOKE UPDATE, DELETE, TRUNCATE ON EventoAuditoria FROM PUBLIC;

-- Disparadores de auditoría sobre las tablas núcleo.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'usuario', 'agente', 'asignacionrol', 'servicio', 'cargo', 'domicilio',
        'horariobase', 'horario', 'horarioconfranquicia', 'horarioestandar', 'horariosemanal',
        'horarionocturno', 'horarioferiante', 'horariodxi', 'horarioguardiamedica',
        'horarioguardiaenfermeria', 'horariojefeservicioguardiapasiva', 'horarioabierto',
        'feriado', 'diagramadeservicio', 'jornadalaboral',
        'codigonovedad', 'tiponovedad', 'novedad', 'novedadautorizacion', 'novedadadjunto', 'movimientohoras',
        'memorandum', 'memorandum_destinatario', 'memorandum_autorizacion',
        'lotemarcaciones', 'marcacion', 'jornadaregistrada', 'partediario', 'partediariodetalle',
        'reglavalidacion'
    ] LOOP
        EXECUTE format('CREATE TRIGGER trg_auditoria AFTER INSERT OR UPDATE OR DELETE ON %I '
                       'FOR EACH ROW EXECUTE FUNCTION fn_auditoria()', t);
    END LOOP;
END $$;

-- updatedAt: toda modificación del trámite incrementa la versión; si la
-- aplicación no fijó updatedAt (con su fecha de referencia), lo fija la base.
CREATE FUNCTION fn_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.version IS DISTINCT FROM OLD.version AND NEW.updatedAt IS NOT DISTINCT FROM OLD.updatedAt THEN
        NEW.updatedAt := now();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_updated_at BEFORE UPDATE ON Novedad FOR EACH ROW EXECUTE FUNCTION fn_updated_at();
CREATE TRIGGER trg_updated_at BEFORE UPDATE ON DiagramaDeServicio FOR EACH ROW EXECUTE FUNCTION fn_updated_at();


/* ----------------------------------------------------------------------------
 * Vistas de indicadores (CU36).
 * -------------------------------------------------------------------------- */
CREATE OR REPLACE VIEW v_indicador_ausentismo AS
SELECT p.fecha,
       d.servicioID,
       s.nombre AS servicio,
       CASE
           WHEN lic.es THEN 'Licencias'
           WHEN cn.codigo IN (2, 41) THEN 'Faltas justificadas'
           WHEN cn.codigo = 1 OR (cn.codigo = 666 AND d.tipoDiferencia IN ('INASISTENCIA', 'GUARDIA_NO_CUBIERTA'))
               THEN 'Faltas injustificadas'
           WHEN cn.codigo = 6 THEN 'Francos compensatorios'
           WHEN cn.codigo = 333 THEN 'Pases de salida'
           WHEN cn.codigo IN (3, 4, 5, 42, 55) OR (cn.codigo = 666 AND d.tipoDiferencia = 'LLEGADA_TARDE')
               THEN 'Llegadas tarde'
       END AS tipo,
       CASE
           WHEN j.fechaIngreso IS NOT NULL THEN (extract(epoch FROM j.fechaEgreso - j.fechaIngreso) / 60)::int
           WHEN lic.es OR cn.codigo = 6 THEN 420
           ELSE 0
       END AS minutosProgramados,
       CASE
           WHEN lic.es OR cn.codigo IN (1, 2, 41, 6)
                OR (cn.codigo = 666 AND d.tipoDiferencia IN ('INASISTENCIA', 'GUARDIA_NO_CUBIERTA'))
               THEN COALESCE((extract(epoch FROM j.fechaEgreso - j.fechaIngreso) / 60)::int, 420)
           WHEN cn.codigo = 333 THEN COALESCE((extract(epoch FROM n.horaHasta - n.horaDesde) / 60)::int, 0)
           WHEN cn.codigo IN (3, 4, 5, 42, 55) OR (cn.codigo = 666 AND d.tipoDiferencia = 'LLEGADA_TARDE')
               THEN GREATEST(0, (extract(epoch FROM mi.fechaMarcacion - j.fechaIngreso) / 60)::int)
           ELSE 0
       END AS minutosAusencia
FROM ParteDiario p
JOIN ParteDiarioDetalle d ON d.parteDiarioID = p.id
JOIN CodigoNovedad cn ON cn.id = d.codigoNovedadID
-- Licencias: códigos del catálogo marcados como licencia, salvo las capacitaciones (52 a 54).
CROSS JOIN LATERAL (SELECT cn.esLicencia AND cn.codigo NOT IN (52, 53, 54) AS es) lic
LEFT JOIN Servicio s ON s.id = d.servicioID
LEFT JOIN JornadaLaboral j ON j.id = d.jornadaLaboralID
LEFT JOIN Novedad n ON n.id = d.novedadID
LEFT JOIN JornadaRegistrada r ON r.id = d.jornadaRegistradaID
LEFT JOIN Marcacion mi ON mi.id = r.marcacionIngresoID
WHERE p.estado = 'CERRADO';

-- Cobertura: para cada día con parte cerrado, servicio con dotación mínima y
-- franja (parámetros de la regla RV-DOTACION-MIN vigente), los agentes
-- diagramados y presentes a la mitad de la franja y el mínimo exigido. Las
-- franjas en las que el servicio no diagrama a nadie no se informan.
CREATE OR REPLACE VIEW v_indicador_cobertura AS
SELECT x.* FROM (
    SELECT p.fecha, s.id AS servicioID, s.nombre AS servicio, f.franja, f.desde, f.hasta,
           (SELECT count(DISTINCT j.agenteID)
              FROM JornadaLaboral j
              JOIN DiagramaDeServicio dg ON dg.id = j.diagramaID AND dg.estado = 'APROBADO' AND dg.servicioID = s.id
             WHERE j.fechaIngreso <= ref.t AND j.fechaEgreso > ref.t) AS diagramados,
           (SELECT count(DISTINCT r.agenteID)
              FROM JornadaRegistrada r
              JOIN Usuario u ON u.id = r.agenteID AND u.servicioID = s.id
              JOIN Marcacion mi ON mi.id = r.marcacionIngresoID
              LEFT JOIN Marcacion me ON me.id = r.marcacionEgresoID
             WHERE mi.fechaMarcacion <= ref.t
               AND COALESCE(me.fechaMarcacion, mi.fechaMarcacion + interval '12 hours') > ref.t) AS presentes,
           ceil(s.dotacionMinima * f.pct / 100.0)::int AS minimo
    FROM ParteDiario p
    CROSS JOIN Servicio s
    CROSS JOIN LATERAL (
        SELECT r.parametros AS prm FROM ReglaValidacion r
        WHERE r.codigo = 'RV-DOTACION-MIN' AND r.activa AND r.vigenteDesde <= p.fecha
          AND (r.vigenteHasta IS NULL OR r.vigenteHasta >= p.fecha)
        ORDER BY r.vigenteDesde DESC LIMIT 1) rv
    CROSS JOIN LATERAL (VALUES
        ('mañana', (rv.prm->>'mananaDesde')::int, (rv.prm->>'tardeDesde')::int, (rv.prm->>'porcentajeManiana')::int),
        ('tarde',  (rv.prm->>'tardeDesde')::int,  (rv.prm->>'nocheDesde')::int, (rv.prm->>'porcentajeTarde')::int),
        ('noche',  (rv.prm->>'nocheDesde')::int,  (rv.prm->>'mananaDesde')::int, (rv.prm->>'porcentajeNoche')::int)
    ) AS f(franja, desde, hasta, pct)
    CROSS JOIN LATERAL (SELECT p.fecha + make_interval(hours => f.desde)
                               + make_interval(mins => ((f.hasta - f.desde + 24) % 24) * 30) AS t) ref
    WHERE p.estado = 'CERRADO' AND COALESCE(s.dotacionMinima, 0) > 0
) x
WHERE x.diagramados > 0;

