package ar.com.hmu.repository;

import ar.com.hmu.model.AmbitoRegla;
import ar.com.hmu.model.ReglaValidacion;
import ar.com.hmu.model.Severidad;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Repositorio del catálogo versionado de reglas de validación (CU08, Tabla 49). */
public class ReglaValidacionRepository {

    private final DatabaseConnector databaseConnector;

    public ReglaValidacionRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    /** La versión vigente de cada regla a la fecha. */
    public List<ReglaValidacion> findVigentes(LocalDate fecha) throws SQLException {
        return buscar("WHERE vigenteDesde <= ? AND (vigenteHasta IS NULL OR vigenteHasta >= ?) ORDER BY ambito, codigo",
                fecha, fecha);
    }

    /** Todas las versiones de una regla, de la más reciente a la más antigua. */
    public List<ReglaValidacion> findVersiones(String codigo) throws SQLException {
        return buscar("WHERE codigo = ? ORDER BY vigenteDesde DESC", codigo);
    }

    /**
     * CU08 paso 5: cierra la versión vigente el día previo a {@code nueva.vigenteDesde}
     * y registra la nueva, en una transacción.
     */
    public void registrarVersion(ReglaValidacion nueva) throws SQLException {
        try (Connection c = databaseConnector.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE ReglaValidacion SET vigenteHasta = ?::date - 1
                        WHERE codigo = ? AND vigenteDesde < ? AND (vigenteHasta IS NULL OR vigenteHasta >= ?)""")) {
                    Date desde = Date.valueOf(nueva.getVigenteDesde());
                    ps.setDate(1, desde);
                    ps.setString(2, nueva.getCodigo());
                    ps.setDate(3, desde);
                    ps.setDate(4, desde);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO ReglaValidacion (id, codigo, nombre, descripcion, ambito, modalidad, severidad,
                            parametros, vigenteDesde, vigenteHasta, activa, fundamentoNormativo)
                        VALUES (?, ?, ?, ?, ?::ambito_regla, ?, ?::severidad_regla, ?::jsonb, ?, NULL, ?, ?)""")) {
                    ps.setObject(1, nueva.getId() != null ? nueva.getId() : UUID.randomUUID());
                    ps.setString(2, nueva.getCodigo());
                    ps.setString(3, nueva.getNombre());
                    ps.setString(4, nueva.getDescripcion());
                    ps.setString(5, nueva.getAmbito().name());
                    ps.setString(6, nueva.getModalidad());
                    ps.setString(7, nueva.getSeveridad().name());
                    ps.setString(8, nueva.parametrosJson());
                    ps.setDate(9, Date.valueOf(nueva.getVigenteDesde()));
                    ps.setBoolean(10, nueva.isActiva());
                    ps.setString(11, nueva.getFundamentoNormativo());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Diagramas aprobados cuyo período termina en o después de la fecha (CU08 3a). */
    public int contarDiagramasAprobadosDesde(LocalDate fecha) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM DiagramaDeServicio WHERE estado = 'APROBADO' AND fechaFin >= ?")) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<ReglaValidacion> buscar(String where, Object... parametros) throws SQLException {
        List<ReglaValidacion> lista = new ArrayList<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT r.* FROM ReglaValidacion r""" + " " + where)) {
            for (int i = 0; i < parametros.length; i++) {
                Object p = parametros[i];
                ps.setObject(i + 1, p instanceof LocalDate d ? Date.valueOf(d) : p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(c, rs));
                }
            }
        }
        return lista;
    }

    private ReglaValidacion mapear(Connection c, ResultSet rs) throws SQLException {
        ReglaValidacion r = new ReglaValidacion();
        r.setId(rs.getObject("id", UUID.class));
        r.setCodigo(rs.getString("codigo"));
        r.setNombre(rs.getString("nombre"));
        r.setDescripcion(rs.getString("descripcion"));
        r.setAmbito(AmbitoRegla.valueOf(rs.getString("ambito")));
        r.setModalidad(rs.getString("modalidad"));
        r.setSeveridad(Severidad.valueOf(rs.getString("severidad")));
        r.setVigenteDesde(rs.getDate("vigenteDesde").toLocalDate());
        Date hasta = rs.getDate("vigenteHasta");
        r.setVigenteHasta(hasta != null ? hasta.toLocalDate() : null);
        r.setActiva(rs.getBoolean("activa"));
        r.setFundamentoNormativo(rs.getString("fundamentoNormativo"));
        r.setParametros(parametros(c, r.getId()));
        return r;
    }

    private Map<String, Double> parametros(Connection c, UUID id) throws SQLException {
        Map<String, Double> m = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT key, value FROM ReglaValidacion, jsonb_each_text(parametros) WHERE id = ? ORDER BY key")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        m.put(rs.getString(1), Double.valueOf(rs.getString(2)));
                    } catch (NumberFormatException ignored) {
                        // Parámetros no numéricos: el motor no los usa.
                    }
                }
            }
        }
        return m;
    }
}
