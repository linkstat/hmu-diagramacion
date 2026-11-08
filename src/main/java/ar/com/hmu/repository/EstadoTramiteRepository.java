package ar.com.hmu.repository;

import ar.com.hmu.model.EstadoTramite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Repositorio de la tabla {@code EstadoTramite} (catálogo de 9 filas estáticas
 * sembradas por el DDL).
 *
 * <p>Los {@code EstadoTramite} se persisten como UUID FK desde Memorandum,
 * Novedad, etc., pero en el modelo de dominio se manejan como {@link EstadoTramite}
 * (enum). Este repository hace el mapeo bidireccional entre enum y UUID con
 * un cache estático cargado en el primer acceso.</p>
 *
 * <p>El cache nunca se invalida: si la tabla {@code EstadoTramite} se modifica
 * en runtime quedaría stale. Es aceptable porque es seed data fija.</p>
 */
public class EstadoTramiteRepository {

    private final DatabaseConnector databaseConnector;

    /** Cache enum → UUID. Carga lazy en primer acceso. */
    private Map<EstadoTramite, UUID> idsPorEstado;
    /** Cache UUID → enum. Carga lazy en primer acceso. */
    private Map<UUID, EstadoTramite> estadosPorId;

    public EstadoTramiteRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    /** Devuelve el UUID que corresponde al estado pasado, o null si la tabla
     *  no tiene fila para ese nombre (no debería ocurrir si el seed está OK). */
    public UUID getId(EstadoTramite estado) throws SQLException {
        ensureCached();
        return idsPorEstado.get(estado);
    }

    /** Devuelve el {@link EstadoTramite} que corresponde al UUID, o null si
     *  el UUID no existe en la tabla. */
    public EstadoTramite getEstadoTramite(UUID id) throws SQLException {
        ensureCached();
        return estadosPorId.get(id);
    }

    /** Recarga el cache desde la BD. Útil si se modificó la tabla en runtime
     *  (caso raro: la tabla es seed fija). */
    public synchronized void recargarCache() throws SQLException {
        idsPorEstado = new EnumMap<>(EstadoTramite.class);
        estadosPorId = new HashMap<>();
        String query = "SELECT id, nombre FROM EstadoTramite";
        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                String nombre = rs.getString("nombre");
                EstadoTramite estado = EstadoTramite.fromDbName(nombre);
                idsPorEstado.put(estado, id);
                estadosPorId.put(id, estado);
            }
        }
    }

    private synchronized void ensureCached() throws SQLException {
        if (idsPorEstado == null) {
            recargarCache();
        }
    }
}
