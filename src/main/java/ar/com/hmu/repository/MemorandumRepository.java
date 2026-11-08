package ar.com.hmu.repository;

import ar.com.hmu.factory.MemorandumFactory;
import ar.com.hmu.model.EstadoMemorandumAutorizacion;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.MemorandumAutorizacion;
import ar.com.hmu.model.MemorandumDestinatario;
import ar.com.hmu.repository.dao.GenericDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de memorándums sobre las cuatro tablas relacionadas:
 * Memorandum, Memorandum_Destinatario, Memorandum_Firmante (no usada en
 * pase 1), Memorandum_Autorizacion.
 *
 * <p>Las filas hijas (destinatarios y autorizaciones) se cargan de a una
 * por método: el caller decide cuándo. Para las bandejas (entrada/salida)
 * la consolidación la hace MemorandumService — N+1 aceptable para los
 * volúmenes esperados.</p>
 */
public class MemorandumRepository implements GenericDAO<Memorandum> {

    private final DatabaseConnector databaseConnector;

    public MemorandumRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    // ============================================================
    // CRUD básico de la cabecera + hijos en una transacción
    // ============================================================

    @Override
    public void create(Memorandum memo) throws SQLException {
        String insertMemo = "INSERT INTO Memorandum " +
                "(id, asunto, contenido, fechaEnvio, fechaRecepcion, estadoTramiteID, remitenteID) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement stmt = connection.prepareStatement(insertMemo)) {
                stmt.setObject(1, memo.getId());
                stmt.setString(2, memo.getAsunto());
                stmt.setString(3, memo.getContenido());
                stmt.setObject(4, memo.getFechaEnvio() != null ? Timestamp.valueOf(memo.getFechaEnvio()) : null);
                stmt.setObject(5, memo.getFechaRecepcion() != null ? Timestamp.valueOf(memo.getFechaRecepcion()) : null);
                stmt.setObject(6, memo.getEstadoTramiteId());
                stmt.setObject(7, memo.getRemitenteId());
                stmt.executeUpdate();
            }

            insertarDestinatarios(connection, memo.getId(), memo.getDestinatarios());
            insertarAutorizaciones(connection, memo.getAutorizaciones());

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al crear el memorándum", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public Memorandum readByUUID(UUID id) throws SQLException {
        String query = "SELECT id, asunto, contenido, fechaEnvio, fechaRecepcion, estadoTramiteID, remitenteID " +
                "FROM Memorandum WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return MemorandumFactory.createMemorandum(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Memorandum> readAll() throws SQLException {
        List<Memorandum> memos = new ArrayList<>();
        String query = "SELECT id, asunto, contenido, fechaEnvio, fechaRecepcion, estadoTramiteID, remitenteID " +
                "FROM Memorandum ORDER BY fechaEnvio DESC NULLS LAST";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                memos.add(MemorandumFactory.createMemorandum(rs));
            }
        }
        return memos;
    }

    @Override
    public void update(Memorandum memo) throws SQLException {
        String query = "UPDATE Memorandum SET asunto = ?, contenido = ?, fechaEnvio = ?, " +
                "fechaRecepcion = ?, estadoTramiteID = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, memo.getAsunto());
            stmt.setString(2, memo.getContenido());
            stmt.setObject(3, memo.getFechaEnvio() != null ? Timestamp.valueOf(memo.getFechaEnvio()) : null);
            stmt.setObject(4, memo.getFechaRecepcion() != null ? Timestamp.valueOf(memo.getFechaRecepcion()) : null);
            stmt.setObject(5, memo.getEstadoTramiteId());
            stmt.setObject(6, memo.getId());
            stmt.executeUpdate();
        }
    }

    /**
     * Borrado físico del memorándum y de todas sus filas hijas en una sola
     * transacción. La validación de estado (sólo borrar BORRADOR del propio
     * remitente) la aplica {@code MemorandumService}.
     */
    @Override
    public void delete(Memorandum memo) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            ejecutarUpdate(connection, "DELETE FROM Memorandum_Autorizacion WHERE memorandumID = ?", memo.getId());
            ejecutarUpdate(connection, "DELETE FROM Memorandum_Firmante WHERE memorandumID = ?", memo.getId());
            ejecutarUpdate(connection, "DELETE FROM Memorandum_Destinatario WHERE memorandumID = ?", memo.getId());
            ejecutarUpdate(connection, "DELETE FROM Memorandum WHERE id = ?", memo.getId());

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar el memorándum", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // Bandejas y consultas específicas
    // ============================================================

    /** Memos enviados por el usuario (incluyendo borradores en cualquier estado). */
    public List<Memorandum> findEnviadosPorRemitente(UUID remitenteId) throws SQLException {
        List<Memorandum> memos = new ArrayList<>();
        String query = "SELECT id, asunto, contenido, fechaEnvio, fechaRecepcion, estadoTramiteID, remitenteID " +
                "FROM Memorandum WHERE remitenteID = ? ORDER BY fechaEnvio DESC NULLS LAST";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, remitenteId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memos.add(MemorandumFactory.createMemorandum(rs));
                }
            }
        }
        return memos;
    }

    /** Memos recibidos por el usuario (es destinatario y el memo ya salió: no
     *  está en BORRADOR ni en PEND_AUTORIZACION). El filtro de estado lo
     *  aplica el Service para mantener este método agnóstico de UUIDs. */
    public List<Memorandum> findRecibidosPorDestinatario(UUID usuarioId) throws SQLException {
        List<Memorandum> memos = new ArrayList<>();
        String query = "SELECT m.id, m.asunto, m.contenido, m.fechaEnvio, m.fechaRecepcion, " +
                "m.estadoTramiteID, m.remitenteID " +
                "FROM Memorandum m " +
                "JOIN Memorandum_Destinatario md ON m.id = md.memorandumID " +
                "WHERE md.usuarioID = ? " +
                "ORDER BY m.fechaEnvio DESC NULLS LAST";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, usuarioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memos.add(MemorandumFactory.createMemorandum(rs));
                }
            }
        }
        return memos;
    }

    /** Memos esperando autorización del usuario indicado: hay una fila
     *  Memorandum_Autorizacion en estado PENDIENTE con autorizadoPorID NULL,
     *  y el memo en cuestión está apuntado a este autorizador (lo determina
     *  el Service vía Servicio.encargadoUsuarioID). */
    public List<Memorandum> findPendientesAutorizacionPor(UUID autorizadorId) throws SQLException {
        List<Memorandum> memos = new ArrayList<>();
        // El "está apuntado a este autorizador" se resuelve uniendo contra
        // Servicio.encargadoUsuarioID a partir del Usuario remitente.
        String query = "SELECT DISTINCT m.id, m.asunto, m.contenido, m.fechaEnvio, m.fechaRecepcion, " +
                "m.estadoTramiteID, m.remitenteID " +
                "FROM Memorandum m " +
                "JOIN Memorandum_Autorizacion ma ON m.id = ma.memorandumID " +
                "JOIN Usuario u ON m.remitenteID = u.id " +
                "JOIN Servicio s ON u.servicioID = s.id " +
                "WHERE ma.estado = 'PENDIENTE' AND ma.autorizadoPorID IS NULL " +
                "  AND s.encargadoUsuarioID = ? " +
                "ORDER BY m.fechaEnvio DESC NULLS LAST";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, autorizadorId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memos.add(MemorandumFactory.createMemorandum(rs));
                }
            }
        }
        return memos;
    }

    /** Cuenta memos no leídos por el usuario indicado (es destinatario y
     *  Memorandum_Destinatario.fechaRecepcion sigue NULL). */
    public int countNoLeidos(UUID usuarioId) throws SQLException {
        String query = "SELECT COUNT(*) FROM Memorandum_Destinatario WHERE usuarioID = ? AND fechaRecepcion IS NULL";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, usuarioId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<MemorandumDestinatario> findDestinatariosByMemoId(UUID memoId) throws SQLException {
        List<MemorandumDestinatario> dests = new ArrayList<>();
        String query = "SELECT memorandumID, usuarioID, fechaRecepcion FROM Memorandum_Destinatario " +
                "WHERE memorandumID = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, memoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    dests.add(MemorandumFactory.createDestinatario(rs));
                }
            }
        }
        return dests;
    }

    public List<MemorandumAutorizacion> findAutorizacionesByMemoId(UUID memoId) throws SQLException {
        List<MemorandumAutorizacion> auths = new ArrayList<>();
        String query = "SELECT id, memorandumID, tipoRol, autorizadoPorID, fechaAutorizacion, estado, comentarios " +
                "FROM Memorandum_Autorizacion WHERE memorandumID = ? " +
                "ORDER BY fechaAutorizacion NULLS FIRST";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, memoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    auths.add(MemorandumFactory.createAutorizacion(rs));
                }
            }
        }
        return auths;
    }

    /** Marca como leído por el destinatario (setea fechaRecepcion).
     *  Si la fila ya tenía fecha (re-lectura) la sobrescribe sólo cuando
     *  {@code force} es true; por defecto preserva la primera lectura. */
    public void marcarLeido(UUID memoId, UUID usuarioId, LocalDateTime when) throws SQLException {
        String query = "UPDATE Memorandum_Destinatario SET fechaRecepcion = ? " +
                "WHERE memorandumID = ? AND usuarioID = ? AND fechaRecepcion IS NULL";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, when != null ? Timestamp.valueOf(when) : null);
            stmt.setObject(2, memoId);
            stmt.setObject(3, usuarioId);
            stmt.executeUpdate();
        }
    }

    /** Cambia el estado de trámite de un memo (transición de la state machine). */
    public void actualizarEstadoTramite(UUID memoId, UUID nuevoEstadoTramiteId) throws SQLException {
        String query = "UPDATE Memorandum SET estadoTramiteID = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, nuevoEstadoTramiteId);
            stmt.setObject(2, memoId);
            stmt.executeUpdate();
        }
    }

    /** Setea fechaEnvio en la cabecera del memo (sólo cuando se envía la primera vez). */
    public void actualizarFechaEnvio(UUID memoId, LocalDateTime fechaEnvio) throws SQLException {
        String query = "UPDATE Memorandum SET fechaEnvio = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, fechaEnvio != null ? Timestamp.valueOf(fechaEnvio) : null);
            stmt.setObject(2, memoId);
            stmt.executeUpdate();
        }
    }

    /** Resuelve una fila de autorización: setea estado, autorizadoPorId,
     *  fechaAutorizacion y comentarios. Usado para AUTORIZAR / RECHAZAR / OBSERVAR. */
    public void resolverAutorizacion(UUID autorizacionId,
                                     EstadoMemorandumAutorizacion estado,
                                     UUID autorizadoPorId,
                                     LocalDateTime cuando,
                                     String comentarios) throws SQLException {
        String query = "UPDATE Memorandum_Autorizacion SET estado = ?, autorizadoPorID = ?, " +
                "fechaAutorizacion = ?, comentarios = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, estado.name(), Types.OTHER);
            stmt.setObject(2, autorizadoPorId);
            stmt.setObject(3, cuando != null ? Timestamp.valueOf(cuando) : null);
            stmt.setString(4, comentarios);
            stmt.setObject(5, autorizacionId);
            stmt.executeUpdate();
        }
    }

    /** Inserta una nueva fila de autorización (usado en re-envíos
     *  post-OBSERVADO: la fila vieja queda como audit trail y se crea una
     *  nueva en estado PENDIENTE). */
    public void agregarAutorizacion(MemorandumAutorizacion autorizacion) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            insertarAutorizaciones(connection, List.of(autorizacion));
        }
    }

    /**
     * Actualiza un borrador existente: cabecera + reemplazo total de
     * destinatarios. Las autorizaciones no se tocan (en BORRADOR no debería
     * haber). Todo en una transacción.
     */
    public void actualizarBorrador(Memorandum memo) throws SQLException {
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            String updateMemo = "UPDATE Memorandum SET asunto = ?, contenido = ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateMemo)) {
                stmt.setString(1, memo.getAsunto());
                stmt.setString(2, memo.getContenido());
                stmt.setObject(3, memo.getId());
                stmt.executeUpdate();
            }

            ejecutarUpdate(connection, "DELETE FROM Memorandum_Destinatario WHERE memorandumID = ?", memo.getId());
            insertarDestinatarios(connection, memo.getId(), memo.getDestinatarios());

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al actualizar el borrador del memorándum", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    private void insertarDestinatarios(Connection connection, UUID memoId,
                                       List<MemorandumDestinatario> destinatarios) throws SQLException {
        if (destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        String query = "INSERT INTO Memorandum_Destinatario (memorandumID, usuarioID, fechaRecepcion) " +
                "VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (MemorandumDestinatario d : destinatarios) {
                stmt.setObject(1, memoId);
                stmt.setObject(2, d.getUsuarioId());
                stmt.setObject(3, d.getFechaRecepcion() != null ? Timestamp.valueOf(d.getFechaRecepcion()) : null);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertarAutorizaciones(Connection connection,
                                        List<MemorandumAutorizacion> autorizaciones) throws SQLException {
        if (autorizaciones == null || autorizaciones.isEmpty()) {
            return;
        }
        String query = "INSERT INTO Memorandum_Autorizacion " +
                "(id, memorandumID, tipoRol, autorizadoPorID, fechaAutorizacion, estado, comentarios) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (MemorandumAutorizacion a : autorizaciones) {
                stmt.setObject(1, a.getId());
                stmt.setObject(2, a.getMemorandumId());
                stmt.setObject(3, a.getTipoRol().name(), Types.OTHER);
                stmt.setObject(4, a.getAutorizadoPorId());
                stmt.setObject(5, a.getFechaAutorizacion() != null ? Timestamp.valueOf(a.getFechaAutorizacion()) : null);
                stmt.setObject(6, a.getEstado().name(), Types.OTHER);
                stmt.setString(7, a.getComentarios());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void ejecutarUpdate(Connection connection, String query, UUID id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);
            stmt.executeUpdate();
        }
    }
}
