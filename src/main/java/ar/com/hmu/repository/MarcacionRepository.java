package ar.com.hmu.repository;

import ar.com.hmu.factory.DiagramaFactory;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Vinculacion;
import ar.com.hmu.service.asistencia.CotejoAsistencia;
import ar.com.hmu.service.asistencia.JornadaRegistrada;
import ar.com.hmu.service.asistencia.LoteMarcaciones;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marcaciones de los relojes, lotes importados y jornadas registradas
 * (E4, Tablas 55 y 56; tablas LoteMarcaciones, MarcacionEmpleado y
 * RegistroJornadaLaboral), más las consultas del cotejo (CU31).
 */
public class MarcacionRepository {

    /** Una marcación leída del archivo, ya asociada al agente. */
    public record Marcacion(UUID id, UUID agenteId, LocalDateTime fechaHora, boolean ingreso, String dispositivo) {
    }

    private final DatabaseConnector databaseConnector;

    public MarcacionRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    public Connection conexion() throws SQLException {
        return databaseConnector.getConnection();
    }

    // ============================================================
    // Lotes
    // ============================================================

    public boolean existeHash(String hash) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM LoteMarcaciones WHERE hashArchivo = ?")) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insertarLote(Connection c, LoteMarcaciones l) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO LoteMarcaciones (id, nombreArchivo, hashArchivo, fechaImportacion, importadoPorID,
                    cantidadRegistros, rangoDesde, rangoHasta) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setObject(1, l.id());
            ps.setString(2, l.nombreArchivo());
            ps.setString(3, l.hashArchivo());
            ps.setTimestamp(4, Timestamp.valueOf(l.fechaImportacion()));
            ps.setObject(5, l.importadoPorId());
            ps.setInt(6, l.cantidadRegistros());
            ps.setDate(7, l.rangoDesde() != null ? Date.valueOf(l.rangoDesde().toLocalDate()) : null);
            ps.setDate(8, l.rangoHasta() != null ? Date.valueOf(l.rangoHasta().toLocalDate()) : null);
            ps.executeUpdate();
        }
    }

    /** El último lote que incluye la fecha (para el encabezado del parte). */
    public LoteMarcaciones ultimoLoteQueCubre(LocalDate fecha) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT l.*, u.apellidos || ', ' || u.nombres AS importador,
                            (SELECT min(fechaMarcacion) FROM Marcacion m WHERE m.loteID = l.id) AS desdeHora,
                            (SELECT max(fechaMarcacion) FROM Marcacion m WHERE m.loteID = l.id) AS hastaHora
                     FROM LoteMarcaciones l JOIN Usuario u ON u.id = l.importadoPorID
                     WHERE ? BETWEEN l.rangoDesde AND l.rangoHasta
                     ORDER BY l.fechaImportacion DESC LIMIT 1""")) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp d = rs.getTimestamp("desdeHora");
                Timestamp h = rs.getTimestamp("hastaHora");
                return new LoteMarcaciones(rs.getObject("id", UUID.class), rs.getString("nombreArchivo"),
                        rs.getString("hashArchivo"), rs.getTimestamp("fechaImportacion").toLocalDateTime(),
                        rs.getObject("importadoPorID", UUID.class), rs.getString("importador"),
                        rs.getInt("cantidadRegistros"), d != null ? d.toLocalDateTime() : null,
                        h != null ? h.toLocalDateTime() : null);
            }
        }
    }

    // ============================================================
    // Marcaciones y jornadas registradas
    // ============================================================

    /** CUIL -> id de los agentes del padrón (activos o no: el reloj puede traer bajas recientes). */
    public Map<Long, UUID> agentesPorCuil() throws SQLException {
        Map<Long, UUID> m = new HashMap<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT u.cuil, u.id FROM Usuario u JOIN Agente e ON e.id = u.id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.put(rs.getLong(1), rs.getObject(2, UUID.class));
            }
        }
        return m;
    }

    public void insertarMarcaciones(Connection c, UUID loteId, List<Marcacion> marcaciones) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO Marcacion (id, fechaMarcacion, observaciones, tipoMarcacion, validada,
                    agenteID, loteID, dispositivo)
                VALUES (?, ?, NULL, ?::tipo_marcacion, TRUE, ?, ?, ?)""")) {
            for (Marcacion m : marcaciones) {
                ps.setObject(1, m.id());
                ps.setTimestamp(2, Timestamp.valueOf(m.fechaHora()));
                ps.setString(3, m.ingreso() ? "INGRESO" : "EGRESO");
                ps.setObject(4, m.agenteId());
                ps.setObject(5, loteId);
                ps.setString(6, m.dispositivo());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Todas las marcaciones del agente en el rango (de todos los lotes: la importación es repetible). */
    public List<Marcacion> marcacionesDe(Connection c, UUID agenteId, LocalDateTime desde, LocalDateTime hasta)
            throws SQLException {
        List<Marcacion> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, fechaMarcacion, tipoMarcacion, dispositivo FROM Marcacion
                WHERE agenteID = ? AND fechaMarcacion BETWEEN ? AND ? ORDER BY fechaMarcacion""")) {
            ps.setObject(1, agenteId);
            ps.setTimestamp(2, Timestamp.valueOf(desde));
            ps.setTimestamp(3, Timestamp.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Marcacion(rs.getObject(1, UUID.class), agenteId,
                            rs.getTimestamp(2).toLocalDateTime(), "INGRESO".equals(rs.getString(3)), rs.getString(4)));
                }
            }
        }
        return lista;
    }

    public void borrarJornadasRegistradas(Connection c, UUID agenteId, LocalDate desde, LocalDate hasta)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM JornadaRegistrada WHERE agenteID = ? AND fecha BETWEEN ? AND ?")) {
            ps.setObject(1, agenteId);
            ps.setDate(2, Date.valueOf(desde));
            ps.setDate(3, Date.valueOf(hasta));
            ps.executeUpdate();
        }
    }

    public void insertarJornadaRegistrada(Connection c, JornadaRegistrada j) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO JornadaRegistrada (id, fecha, agenteID, marcacionIngresoID, marcacionEgresoID,
                    duracionMinutos, omisionIngreso, omisionEgreso) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setObject(1, j.id());
            ps.setDate(2, Date.valueOf(j.fecha()));
            ps.setObject(3, j.agenteId());
            ps.setObject(4, j.marcacionIngresoId());
            ps.setObject(5, j.marcacionEgresoId());
            if (j.duracionMinutos() != null) ps.setInt(6, j.duracionMinutos()); else ps.setNull(6, java.sql.Types.INTEGER);
            ps.setBoolean(7, j.omisionIngreso());
            ps.setBoolean(8, j.omisionEgreso());
            ps.executeUpdate();
        }
    }

    // ============================================================
    // Cotejo (CU31)
    // ============================================================

    /** Jornadas diagramadas del día en diagramas APROBADOS, por agente. */
    public Map<UUID, JornadaLaboral> diagramadasDelDia(LocalDate fecha) throws SQLException {
        Map<UUID, JornadaLaboral> m = new HashMap<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT j.id, j.diagramaID, j.agenteID, j.fecha, j.tipo, j.fechaIngreso, j.fechaEgreso, j.observaciones
                     FROM JornadaLaboral j JOIN DiagramaDeServicio d ON d.id = j.diagramaID AND d.estado = 'APROBADO'
                     WHERE j.fecha = ?""")) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JornadaLaboral j = DiagramaFactory.createJornada(rs);
                    m.put(j.getEmpleadoId(), j);
                }
            }
        }
        return m;
    }

    /** Jornadas registradas que corresponden al día, por agente. */
    public Map<UUID, JornadaRegistrada> registradasDelDia(LocalDate fecha) throws SQLException {
        Map<UUID, JornadaRegistrada> m = new HashMap<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT r.id, r.agenteID, r.fecha, mi.id AS mi, mi.fechaMarcacion AS ingreso,
                            me.id AS me, me.fechaMarcacion AS egreso
                     FROM JornadaRegistrada r
                     LEFT JOIN Marcacion mi ON mi.id = r.marcacionIngresoID
                     LEFT JOIN Marcacion me ON me.id = r.marcacionEgresoID
                     WHERE r.fecha = ? ORDER BY COALESCE(mi.fechaMarcacion, me.fechaMarcacion)""")) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp in = rs.getTimestamp("ingreso");
                    Timestamp out = rs.getTimestamp("egreso");
                    UUID agente = rs.getObject("agenteID", UUID.class);
                    m.putIfAbsent(agente, new JornadaRegistrada(rs.getObject("id", UUID.class), agente,
                            rs.getDate("fecha").toLocalDate(), in != null ? in.toLocalDateTime() : null,
                            out != null ? out.toLocalDateTime() : null, rs.getObject("mi", UUID.class),
                            rs.getObject("me", UUID.class)));
                }
            }
        }
        return m;
    }

    /** Datos para el parte de los agentes indicados (servicio y vinculación). */
    public List<CotejoAsistencia.Agente> agentes(Collection<UUID> ids) throws SQLException {
        List<CotejoAsistencia.Agente> lista = new ArrayList<>();
        if (ids.isEmpty()) return lista;
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT u.id, u.apellidos || ', ' || u.nombres AS nombre, s.id AS sid, s.nombre AS servicio,
                            e.vinculacion
                     FROM Usuario u JOIN Agente e ON e.id = u.id LEFT JOIN Servicio s ON s.id = u.servicioID
                     WHERE u.id = ANY (?)""")) {
            ps.setArray(1, c.createArrayOf("uuid", ids.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new CotejoAsistencia.Agente(rs.getObject("id", UUID.class), rs.getString("nombre"),
                            rs.getObject("sid", UUID.class), rs.getString("servicio"),
                            Vinculacion.valueOf(rs.getString("vinculacion")).esRelacionDeDependencia()));
                }
            }
        }
        return lista;
    }

    /** Tolerancia del cotejo (regla RV-COTEJO-TOLER vigente a la fecha), en minutos. */
    public int toleranciaMinutos(LocalDate fecha) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT (parametros->>'toleranciaMinutos')::int FROM ReglaValidacion
                     WHERE codigo = 'RV-COTEJO-TOLER' AND activa AND vigenteDesde <= ?
                       AND (vigenteHasta IS NULL OR vigenteHasta >= ?)""")) {
            ps.setDate(1, Date.valueOf(fecha));
            ps.setDate(2, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 5;
            }
        }
    }
}
