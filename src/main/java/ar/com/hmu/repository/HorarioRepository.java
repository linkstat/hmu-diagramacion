package ar.com.hmu.repository;

import ar.com.hmu.model.Horario;
import ar.com.hmu.model.HorarioAbierto;
import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioConFranquicia;
import ar.com.hmu.model.HorarioDXI;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioGuardiaMedica;
import ar.com.hmu.model.HorarioJefeServicioGuardiaPasiva;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.HorarioSemanal;
import ar.com.hmu.util.DiaSemana;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repositorio de la jerarquía de horarios (Class Table Inheritance):
 * HorarioBase (raíz) → Horario (rama con 9 modalidades concretas, cada una
 * con su propia tabla) y HorarioConFranquicia (decorator, rama hermana).
 *
 * <p>No implementa {@link ar.com.hmu.repository.dao.GenericDAO} porque el
 * contrato polimórfico no encaja limpio (readAll y update de una jerarquía
 * completa no tienen caso de uso todavía — YAGNI). Expone lo que el módulo
 * de diagramación necesita: crear, leer por id, resolver el horario actual
 * de un empleado, asignarlo y borrar.</p>
 *
 * <p><b>Serialización de campos de patrón:</b> los campos estructurados de
 * las modalidades se persisten en columnas VARCHAR con formatos compactos y
 * legibles:</p>
 * <ul>
 *   <li>{@code List<DiaSemana>} → {@code "LUNES,MARTES"}</li>
 *   <li>{@code Map<DiaSemana,Integer>} → {@code "LUNES=7,MARTES=7"}</li>
 *   <li>{@code Map<DiaSemana,LocalTime>} → {@code "LUNES=08:00,MARTES=08:00"}</li>
 *   <li>{@code List<LocalDateTime>} → ISO-8601 separado por coma</li>
 * </ul>
 *
 * <p><b>Campos NO persistidos</b> (misdesign heredado del prototipo,
 * documentado como deuda): {@code Horario.jornadasPlanificadas} (INTEGER en
 * BD vs {@code List<JornadaLaboral>} en Java), {@code
 * HorarioFeriante.guardiasProgramadas} y {@code
 * HorarioAbierto.preferenciasHorarias} (listas de objetos en VARCHAR). Las
 * guardias concretas de un mes pertenecen al diagrama de servicio, no al
 * template de horario; esos campos quedan en memoria hasta que se decida
 * removerlos del modelo.</p>
 */
public class HorarioRepository {

    /** Modalidades válidas = nombres de las tablas concretas. Whitelist para
     *  el DELETE dinámico de {@link #borrar} (nunca interpolar sin validar). */
    private static final java.util.Set<String> MODALIDADES_VALIDAS = java.util.Set.of(
            "HorarioEstandar", "HorarioSemanal", "HorarioNocturno", "HorarioFeriante",
            "HorarioDXI", "HorarioGuardiaMedica", "HorarioGuardiaEnfermeria",
            "HorarioJefeServicioGuardiaPasiva", "HorarioAbierto");

    private final DatabaseConnector databaseConnector;

    public HorarioRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    // ============================================================
    // Operaciones públicas
    // ============================================================

    /**
     * Persiste un horario completo (fila raíz + rama + modalidad concreta)
     * en una transacción. Para {@link HorarioConFranquicia} persiste primero
     * el horario decorado (que debe ser una modalidad de {@link Horario},
     * según la FK del esquema) y luego el decorator.
     *
     * <p>Si algún id viene en null se genera uno nuevo.</p>
     */
    public void create(HorarioBase horario) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            insertar(connection, horario);

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al crear el horario", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Lee un horario por id, resolviendo la subclase concreta: primero el
     * discriminador {@code HorarioBase.tipo} (rama), después
     * {@code Horario.modalidad} (subclase).
     *
     * @return el horario tipado, o {@code null} si el id no existe.
     */
    public HorarioBase readByUUID(UUID id) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            return leer(connection, id);
        }
    }

    /**
     * Resuelve el horario vigente de un empleado vía
     * {@code Empleado.horarioActualID}.
     *
     * @return el horario tipado, o {@code null} si el empleado no existe o
     *         no tiene horario asignado.
     */
    public HorarioBase findHorarioActualDeEmpleado(UUID empleadoId) throws SQLException {
        UUID horarioId = null;
        String query = "SELECT horarioActualID FROM Agente WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, empleadoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    horarioId = rs.getObject("horarioActualID", UUID.class);
                }
            }
        }
        return (horarioId != null) ? readByUUID(horarioId) : null;
    }

    /**
     * Cierra la vigencia de un horario (CU04: la modalidad anterior termina el
     * día previo a la nueva). En una franquicia cierra el decorador y el decorado.
     */
    public void cerrarVigencia(HorarioBase horario, java.time.LocalDateTime hasta) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Timestamp ts = Timestamp.valueOf(hasta);
                if (horario instanceof HorarioConFranquicia f) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE HorarioConFranquicia SET fechaEgreso = LEAST(fechaEgreso, ?) WHERE id = ?")) {
                        ps.setTimestamp(1, ts);
                        ps.setObject(2, f.getId());
                        ps.executeUpdate();
                    }
                    horario = f.getHorarioDecorado();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE Horario SET fechaEgreso = LEAST(fechaEgreso, ?) WHERE id = ?")) {
                    ps.setTimestamp(1, ts);
                    ps.setObject(2, horario.getId());
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Asigna (o desasigna, con null) el horario vigente de un empleado. */
    public void asignarHorarioActual(UUID empleadoId, UUID horarioId) throws SQLException {
        String query = "UPDATE Agente SET horarioActualID = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, horarioId);
            stmt.setObject(2, empleadoId);
            stmt.executeUpdate();
        }
    }

    /**
     * Borrado físico del horario y sus filas asociadas en una transacción.
     * Para {@link HorarioConFranquicia} borra también el horario decorado
     * (el decorator es dueño de su decorado). Si algún empleado referencia
     * el horario, la FK lo impide y propaga {@link SQLException}: el service
     * debe desasignar primero.
     */
    public void delete(UUID id) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            borrar(connection, id);

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar el horario", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // Escritura (privado)
    // ============================================================

    private void insertar(Connection connection, HorarioBase horario) throws SQLException {
        if (horario.getId() == null) {
            horario.setId(UUID.randomUUID());
        }

        if (horario instanceof HorarioConFranquicia franquicia) {
            HorarioBase decorado = franquicia.getHorarioDecorado();
            if (!(decorado instanceof Horario)) {
                throw new IllegalArgumentException("HorarioConFranquicia debe decorar una " +
                        "modalidad de Horario (restricción del esquema: FK a Horario)");
            }
            insertar(connection, decorado);
            insertarBase(connection, franquicia.getId(), "HorarioConFranquicia");
            insertarFranquicia(connection, franquicia);
        } else if (horario instanceof Horario h) {
            insertarBase(connection, h.getId(), "Horario");
            insertarHorario(connection, h);
            insertarModalidad(connection, h);
        } else {
            throw new IllegalArgumentException("Rama de HorarioBase desconocida: " +
                    horario.getClass().getName());
        }
    }

    private void insertarBase(Connection connection, UUID id, String tipo) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO HorarioBase (id, tipo) VALUES (?, ?::tipo_horario_base)")) {
            stmt.setObject(1, id);
            stmt.setString(2, tipo);
            stmt.executeUpdate();
        }
    }

    private void insertarHorario(Connection connection, Horario h) throws SQLException {
        // horarioBaseID es redundante con id (vestigio del esquema); se setea igual.
        // jornadasPlanificadas no se persiste (deuda documentada en el javadoc de la clase).
        String query = "INSERT INTO Horario " +
                "(id, fechaIngreso, fechaEgreso, jornadasPlanificadas, reglasHorario, horarioBaseID, modalidad) " +
                "VALUES (?, ?, ?, NULL, ?, ?, ?::modalidad_horaria)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, h.getId());
            stmt.setObject(2, h.getFechaIngreso() != null ? Timestamp.valueOf(h.getFechaIngreso()) : null);
            stmt.setObject(3, h.getFechaEgreso() != null ? Timestamp.valueOf(h.getFechaEgreso()) : null);
            stmt.setString(4, h.getReglasHorario());
            stmt.setObject(5, h.getId());
            stmt.setString(6, h.getClass().getSimpleName());
            stmt.executeUpdate();
        }
    }

    private void insertarFranquicia(Connection connection, HorarioConFranquicia f) throws SQLException {
        String query = "INSERT INTO HorarioConFranquicia " +
                "(id, fechaIngreso, fechaEgreso, horasFranquicia, horarioDecoradoID) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, f.getId());
            stmt.setObject(2, f.getFechaIngreso() != null ? Timestamp.valueOf(f.getFechaIngreso()) : null);
            stmt.setObject(3, f.getFechaEgreso() != null ? Timestamp.valueOf(f.getFechaEgreso()) : null);
            stmt.setInt(4, f.getHorasFranquicia());
            stmt.setObject(5, f.getHorarioDecorado().getId());
            stmt.executeUpdate();
        }
    }

    private void insertarModalidad(Connection connection, Horario h) throws SQLException {
        switch (h) {
            case HorarioEstandar he -> ejecutar(connection,
                    "INSERT INTO HorarioEstandar (id, diasLaborables, horasPorDia) VALUES (?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, he.getId());
                        stmt.setString(2, serializarDias(he.getDiasLaborables()));
                        stmt.setInt(3, he.getHorasPorDia());
                    });
            case HorarioSemanal hs -> ejecutar(connection,
                    "INSERT INTO HorarioSemanal (id, distribucionSemanal, horaInicioPorDia) VALUES (?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, hs.getId());
                        stmt.setString(2, serializarMapaEnteros(hs.getDistribucionSemanal()));
                        stmt.setString(3, serializarMapaHoras(hs.getHoraInicioPorDia()));
                    });
            case HorarioNocturno hn -> ejecutar(connection,
                    "INSERT INTO HorarioNocturno (id, diasProgramados, duracionJornadaHoras, " +
                            "numeroJornadasMensuales) VALUES (?, ?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, hn.getId());
                        stmt.setString(2, serializarFechas(hn.getDiasProgramados()));
                        stmt.setInt(3, hn.getDuracionJornadaHoras());
                        stmt.setInt(4, hn.getNumeroJornadasMensuales());
                    });
            case HorarioFeriante hf -> ejecutar(connection,
                    "INSERT INTO HorarioFeriante (id, diasNoLaborables, duracionGuardiaHoras, " +
                            "guardiasProgramadas, horasMinimasMensuales) VALUES (?, ?, ?, '', ?)",
                    stmt -> {
                        stmt.setObject(1, hf.getId());
                        stmt.setString(2, serializarFechas(hf.getDiasNoLaborables()));
                        stmt.setInt(3, hf.getDuracionGuardiaHoras());
                        stmt.setInt(4, hf.getHorasMinimasMensuales());
                    });
            case HorarioDXI hd -> ejecutar(connection,
                    "INSERT INTO HorarioDXI (id, distribucionHoraria, horaInicioPorDia, " +
                            "horasSemanales) VALUES (?, ?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, hd.getId());
                        stmt.setString(2, serializarMapaEnteros(hd.getDistribucionHoraria()));
                        stmt.setString(3, serializarMapaHoras(hd.getHoraInicioPorDia()));
                        stmt.setInt(4, hd.getHorasSemanales());
                    });
            case HorarioGuardiaMedica hgm -> ejecutar(connection,
                    "INSERT INTO HorarioGuardiaMedica (id, duracionGuardiaHoras, fechasGuardias, " +
                            "numeroGuardiasSemanal, permitirGuardiasContinuas, tiempoDescansoMinimoHoras) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, hgm.getId());
                        stmt.setInt(2, hgm.getDuracionGuardiaHoras());
                        stmt.setString(3, serializarFechas(hgm.getFechasGuardias()));
                        stmt.setInt(4, hgm.getNumeroGuardiasSemanal());
                        stmt.setBoolean(5, hgm.isPermitirGuardiasContinuas());
                        stmt.setInt(6, hgm.getTiempoDescansoMinimoHoras());
                    });
            case HorarioGuardiaEnfermeria hge -> ejecutar(connection,
                    "INSERT INTO HorarioGuardiaEnfermeria (id, duracionGuardia10Horas, " +
                            "duracionGuardia12Horas, fechasGuardias, numeroGuardias10Horas, " +
                            "numeroGuardias12Horas) VALUES (?, ?, ?, ?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, hge.getId());
                        stmt.setInt(2, hge.getDuracionGuardia10Horas());
                        stmt.setInt(3, hge.getDuracionGuardia12Horas());
                        stmt.setString(4, serializarFechas(hge.getFechasGuardias()));
                        stmt.setInt(5, hge.getNumeroGuardias10Horas());
                        stmt.setInt(6, hge.getNumeroGuardias12Horas());
                    });
            case HorarioJefeServicioGuardiaPasiva hj -> ejecutar(connection,
                    "INSERT INTO HorarioJefeServicioGuardiaPasiva (id, diasLaborables, horasPorDia) " +
                            "VALUES (?, ?, ?)",
                    stmt -> {
                        stmt.setObject(1, hj.getId());
                        stmt.setString(2, serializarDias(hj.getDiasLaborables()));
                        stmt.setInt(3, hj.getHorasPorDia());
                    });
            case HorarioAbierto ha -> ejecutar(connection,
                    "INSERT INTO HorarioAbierto (id, flexibilidadHoraria, horasSemanales, " +
                            "preferenciasHorarias) VALUES (?, ?, ?, NULL)",
                    stmt -> {
                        stmt.setObject(1, ha.getId());
                        stmt.setBoolean(2, ha.isFlexibilidadHoraria());
                        stmt.setInt(3, ha.getHorasSemanales());
                    });
            default -> throw new IllegalArgumentException("Modalidad de Horario desconocida: " +
                    h.getClass().getName());
        }
    }

    // ============================================================
    // Lectura (privado)
    // ============================================================

    private HorarioBase leer(Connection connection, UUID id) throws SQLException {
        String tipo = null;
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT tipo FROM HorarioBase WHERE id = ?")) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    tipo = rs.getString("tipo");
                }
            }
        }
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case "Horario" -> leerHorario(connection, id);
            case "HorarioConFranquicia" -> leerFranquicia(connection, id);
            default -> throw new SQLException("Discriminador HorarioBase.tipo desconocido: " + tipo);
        };
    }

    private HorarioConFranquicia leerFranquicia(Connection connection, UUID id) throws SQLException {
        String query = "SELECT fechaIngreso, fechaEgreso, horasFranquicia, horarioDecoradoID " +
                "FROM HorarioConFranquicia WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                HorarioConFranquicia f = new HorarioConFranquicia();
                f.setId(id);
                Timestamp tsIn = rs.getTimestamp("fechaIngreso");
                f.setFechaIngreso(tsIn != null ? tsIn.toLocalDateTime() : null);
                Timestamp tsOut = rs.getTimestamp("fechaEgreso");
                f.setFechaEgreso(tsOut != null ? tsOut.toLocalDateTime() : null);
                f.setHorasFranquicia(rs.getInt("horasFranquicia"));
                UUID decoradoId = rs.getObject("horarioDecoradoID", UUID.class);
                f.setHorarioDecorado(leerHorario(connection, decoradoId));
                return f;
            }
        }
    }

    private Horario leerHorario(Connection connection, UUID id) throws SQLException {
        String modalidad = null;
        LocalDateTime fechaIngreso = null;
        LocalDateTime fechaEgreso = null;
        String reglasHorario = null;

        String query = "SELECT fechaIngreso, fechaEgreso, reglasHorario, modalidad " +
                "FROM Horario WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Timestamp tsIn = rs.getTimestamp("fechaIngreso");
                fechaIngreso = (tsIn != null) ? tsIn.toLocalDateTime() : null;
                Timestamp tsOut = rs.getTimestamp("fechaEgreso");
                fechaEgreso = (tsOut != null) ? tsOut.toLocalDateTime() : null;
                reglasHorario = rs.getString("reglasHorario");
                modalidad = rs.getString("modalidad");
            }
        }

        Horario h = leerModalidad(connection, id, modalidad);
        if (h != null) {
            h.setId(id);
            h.setFechaIngreso(fechaIngreso);
            h.setFechaEgreso(fechaEgreso);
            h.setReglasHorario(reglasHorario);
        }
        return h;
    }

    private Horario leerModalidad(Connection connection, UUID id, String modalidad) throws SQLException {
        return switch (modalidad) {
            case "HorarioEstandar" -> leerFila(connection, id,
                    "SELECT diasLaborables, horasPorDia FROM HorarioEstandar WHERE id = ?",
                    rs -> {
                        HorarioEstandar he = new HorarioEstandar();
                        he.setDiasLaborables(parsearDias(rs.getString("diasLaborables")));
                        he.setHorasPorDia(rs.getInt("horasPorDia"));
                        return he;
                    });
            case "HorarioSemanal" -> leerFila(connection, id,
                    "SELECT distribucionSemanal, horaInicioPorDia FROM HorarioSemanal WHERE id = ?",
                    rs -> {
                        HorarioSemanal hs = new HorarioSemanal();
                        hs.setDistribucionSemanal(parsearMapaEnteros(rs.getString("distribucionSemanal")));
                        hs.setHoraInicioPorDia(parsearMapaHoras(rs.getString("horaInicioPorDia")));
                        return hs;
                    });
            case "HorarioNocturno" -> leerFila(connection, id,
                    "SELECT diasProgramados, duracionJornadaHoras, numeroJornadasMensuales " +
                            "FROM HorarioNocturno WHERE id = ?",
                    rs -> {
                        HorarioNocturno hn = new HorarioNocturno();
                        hn.setDiasProgramados(parsearFechas(rs.getString("diasProgramados")));
                        hn.setDuracionJornadaHoras(rs.getInt("duracionJornadaHoras"));
                        hn.setNumeroJornadasMensuales(rs.getInt("numeroJornadasMensuales"));
                        return hn;
                    });
            case "HorarioFeriante" -> leerFila(connection, id,
                    "SELECT diasNoLaborables, duracionGuardiaHoras, horasMinimasMensuales " +
                            "FROM HorarioFeriante WHERE id = ?",
                    rs -> {
                        HorarioFeriante hf = new HorarioFeriante();
                        hf.setDiasNoLaborables(parsearFechas(rs.getString("diasNoLaborables")));
                        hf.setDuracionGuardiaHoras(rs.getInt("duracionGuardiaHoras"));
                        hf.setHorasMinimasMensuales(rs.getInt("horasMinimasMensuales"));
                        return hf;
                    });
            case "HorarioDXI" -> leerFila(connection, id,
                    "SELECT distribucionHoraria, horaInicioPorDia, horasSemanales " +
                            "FROM HorarioDXI WHERE id = ?",
                    rs -> {
                        HorarioDXI hd = new HorarioDXI();
                        hd.setDistribucionHoraria(parsearMapaEnteros(rs.getString("distribucionHoraria")));
                        hd.setHoraInicioPorDia(parsearMapaHoras(rs.getString("horaInicioPorDia")));
                        hd.setHorasSemanales(rs.getInt("horasSemanales"));
                        return hd;
                    });
            case "HorarioGuardiaMedica" -> leerFila(connection, id,
                    "SELECT duracionGuardiaHoras, fechasGuardias, numeroGuardiasSemanal, " +
                            "permitirGuardiasContinuas, tiempoDescansoMinimoHoras " +
                            "FROM HorarioGuardiaMedica WHERE id = ?",
                    rs -> {
                        HorarioGuardiaMedica hgm = new HorarioGuardiaMedica();
                        hgm.setDuracionGuardiaHoras(rs.getInt("duracionGuardiaHoras"));
                        hgm.setFechasGuardias(parsearFechas(rs.getString("fechasGuardias")));
                        hgm.setNumeroGuardiasSemanal(rs.getInt("numeroGuardiasSemanal"));
                        hgm.setPermitirGuardiasContinuas(rs.getBoolean("permitirGuardiasContinuas"));
                        hgm.setTiempoDescansoMinimoHoras(rs.getInt("tiempoDescansoMinimoHoras"));
                        return hgm;
                    });
            case "HorarioGuardiaEnfermeria" -> leerFila(connection, id,
                    "SELECT duracionGuardia10Horas, duracionGuardia12Horas, fechasGuardias, " +
                            "numeroGuardias10Horas, numeroGuardias12Horas " +
                            "FROM HorarioGuardiaEnfermeria WHERE id = ?",
                    rs -> {
                        HorarioGuardiaEnfermeria hge = new HorarioGuardiaEnfermeria();
                        hge.setDuracionGuardia10Horas(rs.getInt("duracionGuardia10Horas"));
                        hge.setDuracionGuardia12Horas(rs.getInt("duracionGuardia12Horas"));
                        hge.setFechasGuardias(parsearFechas(rs.getString("fechasGuardias")));
                        hge.setNumeroGuardias10Horas(rs.getInt("numeroGuardias10Horas"));
                        hge.setNumeroGuardias12Horas(rs.getInt("numeroGuardias12Horas"));
                        return hge;
                    });
            case "HorarioJefeServicioGuardiaPasiva" -> leerFila(connection, id,
                    "SELECT diasLaborables, horasPorDia " +
                            "FROM HorarioJefeServicioGuardiaPasiva WHERE id = ?",
                    rs -> {
                        HorarioJefeServicioGuardiaPasiva hj = new HorarioJefeServicioGuardiaPasiva();
                        hj.setDiasLaborables(parsearDias(rs.getString("diasLaborables")));
                        hj.setHorasPorDia(rs.getInt("horasPorDia"));
                        return hj;
                    });
            case "HorarioAbierto" -> leerFila(connection, id,
                    "SELECT flexibilidadHoraria, horasSemanales FROM HorarioAbierto WHERE id = ?",
                    rs -> {
                        HorarioAbierto ha = new HorarioAbierto();
                        ha.setFlexibilidadHoraria(rs.getBoolean("flexibilidadHoraria"));
                        ha.setHorasSemanales(rs.getInt("horasSemanales"));
                        return ha;
                    });
            default -> throw new SQLException("Horario.modalidad desconocida: " + modalidad);
        };
    }

    // ============================================================
    // Borrado (privado)
    // ============================================================

    private void borrar(Connection connection, UUID id) throws SQLException {
        String tipo = null;
        String modalidad = null;
        UUID decoradoId = null;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT hb.tipo, h.modalidad, f.horarioDecoradoID " +
                        "FROM HorarioBase hb " +
                        "LEFT JOIN Horario h ON h.id = hb.id " +
                        "LEFT JOIN HorarioConFranquicia f ON f.id = hb.id " +
                        "WHERE hb.id = ?")) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return; // no existe: no-op
                }
                tipo = rs.getString("tipo");
                modalidad = rs.getString("modalidad");
                decoradoId = rs.getObject("horarioDecoradoID", UUID.class);
            }
        }

        if ("HorarioConFranquicia".equals(tipo)) {
            ejecutarDelete(connection, "DELETE FROM HorarioConFranquicia WHERE id = ?", id);
            ejecutarDelete(connection, "DELETE FROM HorarioBase WHERE id = ?", id);
            if (decoradoId != null) {
                borrar(connection, decoradoId); // el decorator es dueño de su decorado
            }
        } else {
            if (!MODALIDADES_VALIDAS.contains(modalidad)) {
                throw new SQLException("Horario.modalidad desconocida o corrupta: " + modalidad);
            }
            // Fila de la modalidad concreta → Horario → HorarioBase.
            ejecutarDelete(connection, "DELETE FROM " + modalidad + " WHERE id = ?", id);
            ejecutarDelete(connection, "DELETE FROM Horario WHERE id = ?", id);
            ejecutarDelete(connection, "DELETE FROM HorarioBase WHERE id = ?", id);
        }
    }

    // ============================================================
    // Serialización de campos de patrón (privado)
    // ============================================================

    private static String serializarDias(List<DiaSemana> dias) {
        if (dias == null || dias.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (DiaSemana d : dias) {
            if (sb.length() > 0) sb.append(',');
            sb.append(d.name());
        }
        return sb.toString();
    }

    private static List<DiaSemana> parsearDias(String valor) {
        List<DiaSemana> dias = new ArrayList<>();
        if (valor == null || valor.isBlank()) {
            return dias;
        }
        for (String token : valor.split(",")) {
            dias.add(DiaSemana.valueOf(token.trim()));
        }
        return dias;
    }

    private static String serializarMapaEnteros(Map<DiaSemana, Integer> mapa) {
        if (mapa == null || mapa.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<DiaSemana, Integer> e : mapa.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static Map<DiaSemana, Integer> parsearMapaEnteros(String valor) {
        Map<DiaSemana, Integer> mapa = new EnumMap<>(DiaSemana.class);
        if (valor == null || valor.isBlank()) {
            return mapa;
        }
        for (String token : valor.split(",")) {
            String[] par = token.trim().split("=");
            mapa.put(DiaSemana.valueOf(par[0].trim()), Integer.parseInt(par[1].trim()));
        }
        return mapa;
    }

    private static String serializarMapaHoras(Map<DiaSemana, LocalTime> mapa) {
        if (mapa == null || mapa.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<DiaSemana, LocalTime> e : mapa.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static Map<DiaSemana, LocalTime> parsearMapaHoras(String valor) {
        Map<DiaSemana, LocalTime> mapa = new EnumMap<>(DiaSemana.class);
        if (valor == null || valor.isBlank()) {
            return mapa;
        }
        for (String token : valor.split(",")) {
            String[] par = token.trim().split("=");
            mapa.put(DiaSemana.valueOf(par[0].trim()), LocalTime.parse(par[1].trim()));
        }
        return mapa;
    }

    private static String serializarFechas(List<LocalDateTime> fechas) {
        if (fechas == null || fechas.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LocalDateTime f : fechas) {
            if (sb.length() > 0) sb.append(',');
            sb.append(f); // ISO-8601
        }
        return sb.toString();
    }

    private static List<LocalDateTime> parsearFechas(String valor) {
        List<LocalDateTime> fechas = new ArrayList<>();
        if (valor == null || valor.isBlank()) {
            return fechas;
        }
        for (String token : valor.split(",")) {
            fechas.add(LocalDateTime.parse(token.trim()));
        }
        return fechas;
    }

    // ============================================================
    // Micro-helpers JDBC (privado)
    // ============================================================

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    private interface Mapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static void ejecutar(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            binder.bind(stmt);
            stmt.executeUpdate();
        }
    }

    private static <T> T leerFila(Connection connection, UUID id, String sql,
                                  Mapper<T> mapper) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapper.map(rs) : null;
            }
        }
    }

    private static void ejecutarDelete(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.executeUpdate();
        }
    }
}
