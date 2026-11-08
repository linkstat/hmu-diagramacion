package ar.com.hmu.repository;

import ar.com.hmu.model.ParteDiario;
import ar.com.hmu.service.asistencia.DetalleParte;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Repositorio del parte diario y sus detalles (E4, DER Fig. 20b y Tabla 57). */
public class ParteDiarioRepository {

    private final DatabaseConnector databaseConnector;

    public ParteDiarioRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    public ParteDiario findByFecha(LocalDate fecha) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT p.*, u.apellidos || ', ' || u.nombres AS cerradoPor
                     FROM ParteDiario p LEFT JOIN Usuario u ON u.id = p.cerradoPorID WHERE p.fecha = ?""")) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ParteDiario p = new ParteDiario();
                p.setId(rs.getObject("id", UUID.class));
                p.setFecha(rs.getDate("fecha").toLocalDate());
                p.setEstado(ParteDiario.Estado.valueOf(rs.getString("estado")));
                Timestamp g = rs.getTimestamp("generadoEn");
                p.setGeneradoEn(g != null ? g.toLocalDateTime() : null);
                p.setGeneradoPorId(rs.getObject("generadoPorID", UUID.class));
                p.setCerradoPorId(rs.getObject("cerradoPorID", UUID.class));
                p.setCerradoPorNombre(rs.getString("cerradoPor"));
                Timestamp f = rs.getTimestamp("fechaDeCierre");
                p.setFechaCierre(f != null ? f.toLocalDateTime() : null);
                p.setVersion(rs.getInt("version"));
                return p;
            }
        }
    }

    /** Fechas del período con parte cerrado / abierto (indicadores, CU36 1a). */
    public List<LocalDate> fechasConParte(LocalDate desde, LocalDate hasta, ParteDiario.Estado estado) throws SQLException {
        List<LocalDate> fechas = new ArrayList<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT fecha FROM ParteDiario WHERE fecha BETWEEN ? AND ? AND estado = ?::estado_parte ORDER BY fecha")) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            ps.setString(3, estado.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) fechas.add(rs.getDate(1).toLocalDate());
            }
        }
        return fechas;
    }

    public void insertar(Connection c, ParteDiario p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO ParteDiario (id, fecha, periodo, estado, generadoEn, generadoPorID, version)
                VALUES (?, ?, ?, 'ABIERTO', ?, ?, 0)""")) {
            ps.setObject(1, p.getId());
            ps.setDate(2, Date.valueOf(p.getFecha()));
            ps.setString(3, p.getFecha().toString());
            ps.setTimestamp(4, Timestamp.valueOf(p.getGeneradoEn()));
            ps.setObject(5, p.getGeneradoPorId());
            ps.executeUpdate();
        }
    }

    /** CU34 paso 5, con concurrencia optimista (5a). @return false si la versión cambió. */
    public boolean cerrar(UUID parteId, int version, UUID cerradoPor) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE ParteDiario SET estado = 'CERRADO', cerradoPorID = ?, fechaDeCierre = ?,
                         modificadoPorID = ?, version = version + 1
                     WHERE id = ? AND version = ? AND estado = 'ABIERTO'""")) {
            ps.setObject(1, cerradoPor);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(ar.com.hmu.util.Reloj.ahora()));
            ps.setObject(3, cerradoPor);
            ps.setObject(4, parteId);
            ps.setInt(5, version);
            return ps.executeUpdate() == 1;
        }
    }

    /** Toda modificación del parte abierto incrementa la versión (5a). */
    public boolean tocar(Connection c, UUID parteId, int version, UUID usuario) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE ParteDiario SET version = version + 1, modificadoPorID = ?
                WHERE id = ? AND version = ? AND estado = 'ABIERTO'""")) {
            ps.setObject(1, usuario);
            ps.setObject(2, parteId);
            ps.setInt(3, version);
            return ps.executeUpdate() == 1;
        }
    }

    /** Borra los detalles del cotejo del agente que quedaron con el código indicado (la carga manual los reemplaza). */
    public void borrarDetallesDelCotejo(Connection c, UUID parteId, UUID agenteId, int codigo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                DELETE FROM ParteDiarioDetalle WHERE parteDiarioID = ? AND agenteID = ? AND origen = 'COTEJO'
                  AND codigoNovedadID = %s""".formatted(TraduccionCodigoNovedad.ID_DEL_CODIGO))) {
            ps.setObject(1, parteId);
            ps.setObject(2, agenteId);
            ps.setInt(3, codigo);
            ps.executeUpdate();
        }
    }

    public void insertarDetalles(Connection c, UUID parteId, List<DetalleParte> detalles) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO ParteDiarioDetalle (id, parteDiarioID, agenteID, servicioID, tipoNovedadID, tipoDiferencia,
                    novedadID, jornadaLaboralID, jornadaRegistradaID, origen, codigoNovedadID, observaciones, cargadoPorID)
                VALUES (?, ?, ?, ?, ?, ?::tipo_diferencia, ?, ?, ?, ?::origen_detalle_parte,
                    %s, ?, ?)""".formatted(TraduccionCodigoNovedad.ID_DEL_CODIGO))) {
            for (DetalleParte d : detalles) {
                if (d.getId() == null) d.setId(UUID.randomUUID());
                ps.setObject(1, d.getId());
                ps.setObject(2, parteId);
                ps.setObject(3, d.getAgenteId());
                ps.setObject(4, d.getServicioId());
                ps.setObject(5, d.getTipoNovedadId());
                ps.setString(6, d.getTipoDiferencia() != null ? d.getTipoDiferencia().name() : null);
                ps.setObject(7, d.getNovedadId());
                ps.setObject(8, d.getJornadaLaboralId());
                ps.setObject(9, d.getJornadaRegistradaId());
                ps.setString(10, d.getOrigen().name());
                ps.setObject(11, d.getCodigo(), java.sql.Types.INTEGER);
                ps.setString(12, d.getObservaciones());
                ps.setObject(13, d.getCargadoPorId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Al actualizar el cotejo se reemplazan los detalles automáticos; las cargas manuales quedan. */
    public void borrarDetallesAutomaticos(Connection c, UUID parteId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM ParteDiarioDetalle WHERE parteDiarioID = ? AND origen <> 'CARGA_MANUAL'")) {
            ps.setObject(1, parteId);
            ps.executeUpdate();
        }
    }

    /** Detalles guardados del parte (sin los textos de jornada, que se recalculan). */
    public List<DetalleParte> detalles(UUID parteId) throws SQLException {
        List<DetalleParte> lista = new ArrayList<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT d.*, %s AS codigo, u.apellidos || ', ' || u.nombres AS agente,
                            s.nombre AS servicio, e.vinculacion
                     FROM ParteDiarioDetalle d
                     JOIN Usuario u ON u.id = d.agenteID
                     JOIN Agente e ON e.id = d.agenteID LEFT JOIN Servicio s ON s.id = d.servicioID
                     WHERE d.parteDiarioID = ? ORDER BY s.nombre, u.apellidos, d.createdAt"""
                     .formatted(TraduccionCodigoNovedad.codigoDe("d.codigoNovedadID")))) {
            ps.setObject(1, parteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DetalleParte d = new DetalleParte();
                    d.setId(rs.getObject("id", UUID.class));
                    d.setAgenteId(rs.getObject("agenteID", UUID.class));
                    d.setAgenteNombre(rs.getString("agente"));
                    d.setServicioId(rs.getObject("servicioID", UUID.class));
                    d.setServicioNombre(rs.getString("servicio"));
                    d.setRelacionDeDependencia(ar.com.hmu.model.Vinculacion.valueOf(rs.getString("vinculacion"))
                            .esRelacionDeDependencia());
                    d.setTipoNovedadId(rs.getObject("tipoNovedadID", UUID.class));
                    String td = rs.getString("tipoDiferencia");
                    d.setTipoDiferencia(td != null ? DetalleParte.TipoDiferencia.valueOf(td) : null);
                    d.setNovedadId(rs.getObject("novedadID", UUID.class));
                    d.setJornadaLaboralId(rs.getObject("jornadaLaboralID", UUID.class));
                    d.setJornadaRegistradaId(rs.getObject("jornadaRegistradaID", UUID.class));
                    d.setOrigen(DetalleParte.Origen.valueOf(rs.getString("origen")));
                    d.setCodigo(rs.getObject("codigo", Integer.class));
                    d.setObservaciones(rs.getString("observaciones"));
                    d.setCargadoPorId(rs.getObject("cargadoPorID", UUID.class));
                    d.setCreatedAt(rs.getTimestamp("createdAt").toLocalDateTime());
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    public Connection conexion() throws SQLException {
        return databaseConnector.getConnection();
    }
}
