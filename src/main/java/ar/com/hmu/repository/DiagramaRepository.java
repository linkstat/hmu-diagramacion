package ar.com.hmu.repository;

import ar.com.hmu.factory.DiagramaFactory;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.repository.dao.GenericDAO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio del agregado diagrama de servicio, sobre las dos tablas
 * relacionadas: DiagramaDeServicio (cabecera) y JornadaLaboral (una fila
 * por empleado por día).
 *
 * <p>Sigue el criterio de {@link MemorandumRepository}: un repositorio por
 * agregado, con las escrituras multi-tabla en una única transacción. La
 * cabecera y las jornadas se leen por separado; la consolidación la hace
 * el service del módulo.</p>
 *
 * <p><b>Concurrencia optimista:</b> la columna {@code version} de la
 * cabecera protege al agregado completo. Toda escritura que modifica el
 * diagrama (cabecera, estado o jornadas) exige la versión esperada y la
 * incrementa; si otra sesión lo modificó primero, el método devuelve
 * {@code false} (o lanza {@link SQLException} en {@link #update}) y el
 * caller debe recargar y reintentar.</p>
 */
public class DiagramaRepository implements GenericDAO<DiagramaDeServicio> {

    private static final String COLS_DIAGRAMA =
            "id, servicioID, estado, fechaInicio, fechaFin, version, creadoPorID, " +
            "createdAt, updatedAt, aprobadoPorID, fechaAprobacion, comentariosObservacion";

    private static final String COLS_JORNADA =
            "id, diagramaID, agenteID, fecha, tipo, fechaIngreso, fechaEgreso, observaciones";

    private final DatabaseConnector databaseConnector;

    public DiagramaRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    // ============================================================
    // CRUD básico de la cabecera + jornadas en una transacción
    // ============================================================

    @Override
    public void create(DiagramaDeServicio diagrama) throws SQLException {
        String insertDiagrama = "INSERT INTO DiagramaDeServicio " +
                "(id, servicioID, estado, fechaInicio, fechaFin, creadoPorID) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement stmt = connection.prepareStatement(insertDiagrama)) {
                stmt.setObject(1, diagrama.getId());
                stmt.setObject(2, diagrama.getServicioId());
                stmt.setObject(3, diagrama.getEstado().name(), Types.OTHER);
                stmt.setObject(4, Date.valueOf(diagrama.getFechaInicio()));
                stmt.setObject(5, Date.valueOf(diagrama.getFechaFin()));
                stmt.setObject(6, diagrama.getCreadoPorId());
                stmt.executeUpdate();
            }

            insertarJornadas(connection, diagrama.getJornadas());

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al crear el diagrama de servicio", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public DiagramaDeServicio readByUUID(UUID id) throws SQLException {
        String query = "SELECT " + COLS_DIAGRAMA + " FROM DiagramaDeServicio WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return DiagramaFactory.createDiagrama(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<DiagramaDeServicio> readAll() throws SQLException {
        List<DiagramaDeServicio> diagramas = new ArrayList<>();
        String query = "SELECT " + COLS_DIAGRAMA + " FROM DiagramaDeServicio " +
                "ORDER BY fechaInicio DESC";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                diagramas.add(DiagramaFactory.createDiagrama(rs));
            }
        }
        return diagramas;
    }

    /**
     * Actualiza el rango de vigencia de la cabecera con control optimista:
     * exige que {@code diagrama.getVersion()} coincida con la versión
     * persistida e incrementa la versión.
     *
     * @throws SQLException si otra sesión modificó el diagrama primero
     *                      (conflicto de concurrencia) o el id no existe.
     */
    @Override
    public void update(DiagramaDeServicio diagrama) throws SQLException {
        String query = "UPDATE DiagramaDeServicio SET fechaInicio = ?, fechaFin = ?, " +
                "version = version + 1, updatedAt = now() " +
                "WHERE id = ? AND version = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, Date.valueOf(diagrama.getFechaInicio()));
            stmt.setObject(2, Date.valueOf(diagrama.getFechaFin()));
            stmt.setObject(3, diagrama.getId());
            stmt.setInt(4, diagrama.getVersion());
            int filas = stmt.executeUpdate();
            if (filas == 0) {
                throw new SQLException("Conflicto de concurrencia: el diagrama fue " +
                        "modificado por otra sesión (o no existe). Recargar y reintentar.");
            }
        }
    }

    /**
     * Borrado físico del diagrama. Las jornadas caen por el
     * {@code ON DELETE CASCADE} de la FK. La validación de estado (sólo
     * borrar BORRADOR) la aplica el service del módulo.
     */
    @Override
    public void delete(DiagramaDeServicio diagrama) throws SQLException {
        String query = "DELETE FROM DiagramaDeServicio WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, diagrama.getId());
            stmt.executeUpdate();
        }
    }

    // ============================================================
    // Consultas específicas de la cabecera
    // ============================================================

    /** Diagramas de un servicio, del más reciente al más viejo. */
    public List<DiagramaDeServicio> findByServicio(UUID servicioId) throws SQLException {
        List<DiagramaDeServicio> diagramas = new ArrayList<>();
        String query = "SELECT " + COLS_DIAGRAMA + " FROM DiagramaDeServicio " +
                "WHERE servicioID = ? ORDER BY fechaInicio DESC";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, servicioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    diagramas.add(DiagramaFactory.createDiagrama(rs));
                }
            }
        }
        return diagramas;
    }

    /**
     * Diagramas de un servicio cuyo rango de vigencia se solapa con
     * {@code [desde, hasta]}. Usado para impedir dos diagramas del mismo
     * servicio sobre el mismo período.
     */
    public List<DiagramaDeServicio> findSolapados(UUID servicioId, LocalDate desde,
                                                  LocalDate hasta) throws SQLException {
        List<DiagramaDeServicio> diagramas = new ArrayList<>();
        String query = "SELECT " + COLS_DIAGRAMA + " FROM DiagramaDeServicio " +
                "WHERE servicioID = ? AND fechaInicio <= ? AND fechaFin >= ? " +
                "ORDER BY fechaInicio";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, servicioId);
            stmt.setObject(2, Date.valueOf(hasta));
            stmt.setObject(3, Date.valueOf(desde));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    diagramas.add(DiagramaFactory.createDiagrama(rs));
                }
            }
        }
        return diagramas;
    }

    /** Diagramas en un estado dado (p.ej. la bandeja de OP:
     *  PENDIENTE_APROBACION de todos los servicios). */
    public List<DiagramaDeServicio> findByEstado(EstadoDiagrama estado) throws SQLException {
        List<DiagramaDeServicio> diagramas = new ArrayList<>();
        String query = "SELECT " + COLS_DIAGRAMA + " FROM DiagramaDeServicio " +
                "WHERE estado = ? ORDER BY fechaInicio";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, estado.name(), Types.OTHER);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    diagramas.add(DiagramaFactory.createDiagrama(rs));
                }
            }
        }
        return diagramas;
    }

    /**
     * Transición de estado de la state machine, con control optimista y
     * campos de auditoría. Para APROBADO/OBSERVADO llegan
     * {@code aprobadoPorId}/{@code fechaAprobacion}/{@code comentarios};
     * para el resto van en null.
     *
     * @return {@code true} si la transición se aplicó; {@code false} si hubo
     *         conflicto de concurrencia (versión esperada no coincide).
     */
    public boolean actualizarEstado(UUID diagramaId, EstadoDiagrama nuevoEstado,
                                    UUID aprobadoPorId, LocalDateTime fechaAprobacion,
                                    String comentarios, int versionEsperada) throws SQLException {
        String query = "UPDATE DiagramaDeServicio SET estado = ?, aprobadoPorID = ?, " +
                "fechaAprobacion = ?, comentariosObservacion = ?, " +
                "version = version + 1, updatedAt = now() " +
                "WHERE id = ? AND version = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, nuevoEstado.name(), Types.OTHER);
            stmt.setObject(2, aprobadoPorId);
            stmt.setObject(3, fechaAprobacion != null ? Timestamp.valueOf(fechaAprobacion) : null);
            stmt.setString(4, comentarios);
            stmt.setObject(5, diagramaId);
            stmt.setInt(6, versionEsperada);
            return stmt.executeUpdate() > 0;
        }
    }

    // ============================================================
    // Jornadas del diagrama
    // ============================================================

    /** Jornadas de un diagrama, ordenadas por fecha y empleado. */
    public List<JornadaLaboral> findJornadasByDiagramaId(UUID diagramaId) throws SQLException {
        List<JornadaLaboral> jornadas = new ArrayList<>();
        String query = "SELECT " + COLS_JORNADA + " FROM JornadaLaboral " +
                "WHERE diagramaID = ? ORDER BY fecha, agenteID";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, diagramaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    jornadas.add(DiagramaFactory.createJornada(rs));
                }
            }
        }
        return jornadas;
    }

    /**
     * Jornadas de un empleado en un rango de fechas, cruzando todos los
     * diagramas (útil para disponibilidad y para validar superposiciones
     * contra otros servicios). El filtro por estado del diagrama, si hace
     * falta, lo aplica el service.
     */
    public List<JornadaLaboral> findJornadasDeEmpleadoEnRango(UUID empleadoId, LocalDate desde,
                                                              LocalDate hasta) throws SQLException {
        List<JornadaLaboral> jornadas = new ArrayList<>();
        String query = "SELECT " + COLS_JORNADA + " FROM JornadaLaboral " +
                "WHERE agenteID = ? AND fecha BETWEEN ? AND ? ORDER BY fecha";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, empleadoId);
            stmt.setObject(2, Date.valueOf(desde));
            stmt.setObject(3, Date.valueOf(hasta));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    jornadas.add(DiagramaFactory.createJornada(rs));
                }
            }
        }
        return jornadas;
    }

    /**
     * Jornadas de los diagramas APROBADOS (vigentes) de un empleado en un
     * rango: la base de la validación de novedades (CU21) y del cotejo.
     */
    public List<JornadaLaboral> findJornadasVigentesDeEmpleadoEnRango(UUID empleadoId, LocalDate desde,
                                                                      LocalDate hasta) throws SQLException {
        List<JornadaLaboral> jornadas = new ArrayList<>();
        String query = "SELECT " + prefijar(COLS_JORNADA, "j.") + " FROM JornadaLaboral j " +
                "JOIN DiagramaDeServicio d ON d.id = j.diagramaID AND d.estado = 'APROBADO' " +
                "WHERE j.agenteID = ? AND j.fecha BETWEEN ? AND ? ORDER BY j.fecha";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, empleadoId);
            stmt.setObject(2, Date.valueOf(desde));
            stmt.setObject(3, Date.valueOf(hasta));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    jornadas.add(DiagramaFactory.createJornada(rs));
                }
            }
        }
        return jornadas;
    }

    private static String prefijar(String columnas, String alias) {
        StringBuilder sb = new StringBuilder();
        for (String col : columnas.split(",")) {
            sb.append(sb.isEmpty() ? "" : ", ").append(alias).append(col.trim());
        }
        return sb.toString();
    }

    /**
     * Reemplazo total de las jornadas de un diagrama (regeneración o guardado
     * de la grilla de edición), con control optimista sobre la cabecera.
     * Todo en una transacción: bump de versión + DELETE + batch INSERT.
     *
     * @return {@code true} si se aplicó; {@code false} si hubo conflicto de
     *         concurrencia (en ese caso no se tocó nada).
     */
    public boolean reemplazarJornadas(UUID diagramaId, List<JornadaLaboral> jornadas,
                                      int versionEsperada) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            // El bump de versión actúa como candado optimista del agregado.
            String bump = "UPDATE DiagramaDeServicio SET version = version + 1, " +
                    "updatedAt = now() WHERE id = ? AND version = ?";
            try (PreparedStatement stmt = connection.prepareStatement(bump)) {
                stmt.setObject(1, diagramaId);
                stmt.setInt(2, versionEsperada);
                if (stmt.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM JornadaLaboral WHERE diagramaID = ?")) {
                stmt.setObject(1, diagramaId);
                stmt.executeUpdate();
            }

            insertarJornadas(connection, jornadas);

            connection.commit();
            return true;
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al reemplazar las jornadas del diagrama", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    private void insertarJornadas(Connection connection,
                                  List<JornadaLaboral> jornadas) throws SQLException {
        if (jornadas == null || jornadas.isEmpty()) {
            return;
        }
        String query = "INSERT INTO JornadaLaboral " +
                "(id, diagramaID, agenteID, fecha, tipo, fechaIngreso, fechaEgreso, observaciones) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (JornadaLaboral j : jornadas) {
                stmt.setObject(1, j.getId());
                stmt.setObject(2, j.getDiagramaId());
                stmt.setObject(3, j.getEmpleadoId());
                stmt.setObject(4, Date.valueOf(j.getFecha()));
                stmt.setObject(5, j.getTipo().name(), Types.OTHER);
                stmt.setObject(6, j.getFechaIngreso() != null ? Timestamp.valueOf(j.getFechaIngreso()) : null);
                stmt.setObject(7, j.getFechaEgreso() != null ? Timestamp.valueOf(j.getFechaEgreso()) : null);
                stmt.setString(8, j.getObservaciones());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
