package ar.com.hmu.factory;

import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Mapeo {@link ResultSet} → POJO para las entidades del módulo de
 * diagramación de servicios. Métodos estáticos, siguiendo el estilo de
 * {@link MemorandumFactory}.
 *
 * <p>Convenciones aplicadas:</p>
 * <ul>
 *   <li>Las columnas DATE se traducen a {@link java.time.LocalDate}; los
 *       TIMESTAMP a {@link java.time.LocalDateTime}; null cuando la columna
 *       es null.</li>
 *   <li>Los ENUMs PostgreSQL ({@code estado_diagrama}, {@code tipo_jornada})
 *       se mapean con {@code Enum.valueOf(rs.getString(...))} sobre los
 *       enums Java homónimos.</li>
 * </ul>
 */
public class DiagramaFactory {

    private DiagramaFactory() {
        // Sólo métodos estáticos.
    }

    /**
     * Construye la cabecera de un diagrama de servicio.
     * Espera columnas: id, servicioID, estado, fechaInicio, fechaFin,
     * version, creadoPorID, createdAt, updatedAt, aprobadoPorID,
     * fechaAprobacion, comentariosObservacion.
     */
    public static DiagramaDeServicio createDiagrama(ResultSet rs) throws SQLException {
        DiagramaDeServicio d = new DiagramaDeServicio();
        d.setId(rs.getObject("id", UUID.class));
        d.setServicioId(rs.getObject("servicioID", UUID.class));
        d.setEstado(EstadoDiagrama.valueOf(rs.getString("estado")));
        Date fi = rs.getDate("fechaInicio");
        d.setFechaInicio(fi != null ? fi.toLocalDate() : null);
        Date ff = rs.getDate("fechaFin");
        d.setFechaFin(ff != null ? ff.toLocalDate() : null);
        d.setVersion(rs.getInt("version"));
        d.setCreadoPorId(rs.getObject("creadoPorID", UUID.class));
        Timestamp tsCreated = rs.getTimestamp("createdAt");
        d.setCreatedAt(tsCreated != null ? tsCreated.toLocalDateTime() : null);
        Timestamp tsUpdated = rs.getTimestamp("updatedAt");
        d.setUpdatedAt(tsUpdated != null ? tsUpdated.toLocalDateTime() : null);
        d.setAprobadoPorId(rs.getObject("aprobadoPorID", UUID.class));
        Timestamp tsAprob = rs.getTimestamp("fechaAprobacion");
        d.setFechaAprobacion(tsAprob != null ? tsAprob.toLocalDateTime() : null);
        d.setComentariosObservacion(rs.getString("comentariosObservacion"));
        return d;
    }

    /**
     * Construye una jornada laboral planificada.
     * Espera columnas: id, diagramaID, empleadoID, fecha, tipo,
     * fechaIngreso, fechaEgreso, observaciones.
     */
    public static JornadaLaboral createJornada(ResultSet rs) throws SQLException {
        JornadaLaboral j = new JornadaLaboral();
        j.setId(rs.getObject("id", UUID.class));
        j.setDiagramaId(rs.getObject("diagramaID", UUID.class));
        j.setEmpleadoId(rs.getObject("agenteID", UUID.class));
        Date fecha = rs.getDate("fecha");
        j.setFecha(fecha != null ? fecha.toLocalDate() : null);
        j.setTipo(TipoJornada.valueOf(rs.getString("tipo")));
        Timestamp tsIn = rs.getTimestamp("fechaIngreso");
        j.setFechaIngreso(tsIn != null ? tsIn.toLocalDateTime() : null);
        Timestamp tsOut = rs.getTimestamp("fechaEgreso");
        j.setFechaEgreso(tsOut != null ? tsOut.toLocalDateTime() : null);
        j.setObservaciones(rs.getString("observaciones"));
        return j;
    }
}
