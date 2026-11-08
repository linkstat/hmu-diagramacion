package ar.com.hmu.factory;

import ar.com.hmu.model.EstadoMemorandumAutorizacion;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.MemorandumAutorizacion;
import ar.com.hmu.model.MemorandumDestinatario;
import ar.com.hmu.model.TipoRolMemoAutorizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Mapeo {@link ResultSet} → POJO para las cuatro entidades del módulo
 * de memorándums. Métodos estáticos (siguiendo el estilo de
 * {@link DomicilioFactory}) porque el mapeo es mecánico y no requiere
 * colaboradores (a diferencia de {@link UsuarioFactory} que carga roles).
 *
 * <p>Convenciones aplicadas:</p>
 * <ul>
 *   <li>Los timestamps se traducen a {@link java.time.LocalDateTime}; null
 *       cuando la columna es null.</li>
 *   <li>Los ENUMs PostgreSQL se mapean con
 *       {@code Enum.valueOf(rs.getString(...))} sobre los enums Java
 *       homónimos.</li>
 * </ul>
 */
public class MemorandumFactory {

    private MemorandumFactory() {
        // Sólo métodos estáticos.
    }

    /**
     * Construye la cabecera de un memorándum.
     * Espera columnas: id, asunto, contenido, fechaEnvio, fechaRecepcion,
     * estadoTramiteID, remitenteID.
     */
    public static Memorandum createMemorandum(ResultSet rs) throws SQLException {
        Memorandum m = new Memorandum();
        m.setId(rs.getObject("id", UUID.class));
        m.setAsunto(rs.getString("asunto"));
        m.setContenido(rs.getString("contenido"));
        Timestamp tsEnvio = rs.getTimestamp("fechaEnvio");
        m.setFechaEnvio(tsEnvio != null ? tsEnvio.toLocalDateTime() : null);
        Timestamp tsRecep = rs.getTimestamp("fechaRecepcion");
        m.setFechaRecepcion(tsRecep != null ? tsRecep.toLocalDateTime() : null);
        m.setEstadoTramiteId(rs.getObject("estadoTramiteID", UUID.class));
        m.setRemitenteId(rs.getObject("remitenteID", UUID.class));
        return m;
    }

    /**
     * Construye una fila de Memorandum_Destinatario.
     * Espera columnas: memorandumID, usuarioID, fechaRecepcion.
     */
    public static MemorandumDestinatario createDestinatario(ResultSet rs) throws SQLException {
        MemorandumDestinatario d = new MemorandumDestinatario();
        d.setMemorandumId(rs.getObject("memorandumID", UUID.class));
        d.setUsuarioId(rs.getObject("usuarioID", UUID.class));
        Timestamp ts = rs.getTimestamp("fechaRecepcion");
        d.setFechaRecepcion(ts != null ? ts.toLocalDateTime() : null);
        return d;
    }

    /**
     * Construye una fila de Memorandum_Autorizacion.
     * Espera columnas: id, memorandumID, tipoRol, autorizadoPorID,
     * fechaAutorizacion, estado, comentarios.
     */
    public static MemorandumAutorizacion createAutorizacion(ResultSet rs) throws SQLException {
        MemorandumAutorizacion a = new MemorandumAutorizacion();
        a.setId(rs.getObject("id", UUID.class));
        a.setMemorandumId(rs.getObject("memorandumID", UUID.class));
        a.setTipoRol(TipoRolMemoAutorizacion.valueOf(rs.getString("tipoRol")));
        a.setAutorizadoPorId(rs.getObject("autorizadoPorID", UUID.class));
        Timestamp tsAuth = rs.getTimestamp("fechaAutorizacion");
        a.setFechaAutorizacion(tsAuth != null ? tsAuth.toLocalDateTime() : null);
        a.setEstado(EstadoMemorandumAutorizacion.valueOf(rs.getString("estado")));
        a.setComentarios(rs.getString("comentarios"));
        return a;
    }
}
