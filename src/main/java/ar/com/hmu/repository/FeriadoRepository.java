package ar.com.hmu.repository;

import ar.com.hmu.model.EstadoFeriado;
import ar.com.hmu.model.Feriado;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Repositorio de feriados. Sigue las convenciones del proyecto: UUIDs
 * nativos, ENUMs PG vía {@code Types.OTHER} + {@code valueOf}, transacción
 * manual para la carga anual en bloque.
 */
public class FeriadoRepository {

    private static final String COLS =
            "id, fecha, descripcion, estado, creadoPorID, resueltoPorID, fechaResolucion, createdAt";

    private final DatabaseConnector databaseConnector;

    public FeriadoRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    public void create(Feriado feriado) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            insertar(connection, feriado);
        }
    }

    /** Carga anual en bloque, en una sola transacción (todo o nada). */
    public void createBatch(List<Feriado> feriados) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);
            for (Feriado f : feriados) {
                insertar(connection, f);
            }
            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error en la carga anual de feriados", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public Feriado readByUUID(UUID id) throws SQLException {
        String query = "SELECT " + COLS + " FROM Feriado WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
        }
        return null;
    }

    /** Feriados de un año calendario (todos los estados), ordenados por fecha. */
    public List<Feriado> findByAnio(int anio) throws SQLException {
        List<Feriado> feriados = new ArrayList<>();
        String query = "SELECT " + COLS + " FROM Feriado " +
                "WHERE fecha BETWEEN ? AND ? ORDER BY fecha";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, Date.valueOf(LocalDate.of(anio, 1, 1)));
            stmt.setObject(2, Date.valueOf(LocalDate.of(anio, 12, 31)));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    feriados.add(mapear(rs));
                }
            }
        }
        return feriados;
    }

    /** Cantidad de feriados "activos" (no historia) de un año — para la regla
     *  "la carga anual sólo procede si el año está vacío". */
    public int countActivosEnAnio(int anio) throws SQLException {
        String query = "SELECT COUNT(*) FROM Feriado " +
                "WHERE fecha BETWEEN ? AND ? " +
                "AND estado IN ('VIGENTE', 'ALTA_PENDIENTE', 'BAJA_PENDIENTE')";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, Date.valueOf(LocalDate.of(anio, 1, 1)));
            stmt.setObject(2, Date.valueOf(LocalDate.of(anio, 12, 31)));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Fechas que cuentan como feriado en el rango (VIGENTE y BAJA_PENDIENTE:
     * una baja propuesta sigue vigente hasta que la Dirección la autorice).
     * Alimenta el {@code ContextoDiagramacion} de los generadores.
     */
    public Set<LocalDate> findFechasVigentesEnRango(LocalDate desde, LocalDate hasta)
            throws SQLException {
        Set<LocalDate> fechas = new HashSet<>();
        String query = "SELECT fecha FROM Feriado " +
                "WHERE fecha BETWEEN ? AND ? AND estado IN ('VIGENTE', 'BAJA_PENDIENTE')";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, Date.valueOf(desde));
            stmt.setObject(2, Date.valueOf(hasta));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    fechas.add(rs.getDate(1).toLocalDate());
                }
            }
        }
        return fechas;
    }

    /** Propuestas esperando resolución de la Dirección, ordenadas por fecha. */
    public List<Feriado> findPendientes() throws SQLException {
        List<Feriado> feriados = new ArrayList<>();
        String query = "SELECT " + COLS + " FROM Feriado " +
                "WHERE estado IN ('ALTA_PENDIENTE', 'BAJA_PENDIENTE') ORDER BY fecha";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                feriados.add(mapear(rs));
            }
        }
        return feriados;
    }

    /** Transición de estado con auditoría de quién resolvió. */
    public void actualizarEstado(UUID id, EstadoFeriado nuevoEstado, UUID resueltoPorId,
                                 LocalDateTime fechaResolucion) throws SQLException {
        String query = "UPDATE Feriado SET estado = ?, resueltoPorID = ?, fechaResolucion = ? " +
                "WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, nuevoEstado.name(), Types.OTHER);
            stmt.setObject(2, resueltoPorId);
            stmt.setObject(3, fechaResolucion != null ? Timestamp.valueOf(fechaResolucion) : null);
            stmt.setObject(4, id);
            stmt.executeUpdate();
        }
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    private void insertar(Connection connection, Feriado f) throws SQLException {
        String query = "INSERT INTO Feriado (id, fecha, descripcion, estado, creadoPorID) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, f.getId());
            stmt.setObject(2, Date.valueOf(f.getFecha()));
            stmt.setString(3, f.getDescripcion());
            stmt.setObject(4, f.getEstado().name(), Types.OTHER);
            stmt.setObject(5, f.getCreadoPorId());
            stmt.executeUpdate();
        }
    }

    private Feriado mapear(ResultSet rs) throws SQLException {
        Feriado f = new Feriado();
        f.setId(rs.getObject("id", UUID.class));
        f.setFecha(rs.getDate("fecha").toLocalDate());
        f.setDescripcion(rs.getString("descripcion"));
        f.setEstado(EstadoFeriado.valueOf(rs.getString("estado")));
        f.setCreadoPorId(rs.getObject("creadoPorID", UUID.class));
        f.setResueltoPorId(rs.getObject("resueltoPorID", UUID.class));
        Timestamp tsRes = rs.getTimestamp("fechaResolucion");
        f.setFechaResolucion(tsRes != null ? tsRes.toLocalDateTime() : null);
        Timestamp tsCreated = rs.getTimestamp("createdAt");
        f.setCreatedAt(tsCreated != null ? tsCreated.toLocalDateTime() : null);
        return f;
    }
}
