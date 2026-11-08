package ar.com.hmu.repository;

import ar.com.hmu.model.Notificacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Repositorio de avisos en la aplicación (tabla Notificacion, CU27). */
public class NotificacionRepository {

    private final DatabaseConnector databaseConnector;

    public NotificacionRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    public void insertar(Notificacion n) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO Notificacion (id, usuarioID, tipo, asunto, cuerpo, entidad, entidadID, enviadaEmail)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING fechaCreacion""")) {
            if (n.getId() == null) {
                n.setId(UUID.randomUUID());
            }
            ps.setObject(1, n.getId());
            ps.setObject(2, n.getUsuarioId());
            ps.setString(3, n.getTipo());
            ps.setString(4, n.getAsunto());
            ps.setString(5, n.getCuerpo());
            ps.setString(6, n.getEntidad());
            ps.setObject(7, n.getEntidadId());
            ps.setBoolean(8, n.isEnviadaEmail());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                n.setFechaCreacion(rs.getTimestamp(1).toLocalDateTime());
            }
        }
    }

    public List<Notificacion> findByUsuario(UUID usuarioId, int limite) throws SQLException {
        return buscar("WHERE usuarioID = ? ORDER BY fechaCreacion DESC LIMIT " + limite, usuarioId);
    }

    /** Avisos cuyo correo no salió (para el reintento). */
    public List<Notificacion> findSinEmailEnviado() throws SQLException {
        return buscar("WHERE NOT enviadaEmail AND fechaCreacion > now() - interval '7 days' ORDER BY fechaCreacion", null);
    }

    public int contarNoLeidas(UUID usuarioId) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM Notificacion WHERE usuarioID = ? AND fechaLectura IS NULL")) {
            ps.setObject(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void marcarLeidas(UUID usuarioId) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Notificacion SET fechaLectura = now() WHERE usuarioID = ? AND fechaLectura IS NULL")) {
            ps.setObject(1, usuarioId);
            ps.executeUpdate();
        }
    }

    /** Ya hay un aviso de ese tipo para el usuario hoy (tope diario de los recordatorios, CU19). */
    public boolean existeHoy(UUID usuarioId, String tipo) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM Notificacion WHERE usuarioID = ? AND tipo = ? AND fechaCreacion::date = CURRENT_DATE")) {
            ps.setObject(1, usuarioId);
            ps.setString(2, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void marcarEnviadaEmail(UUID notificacionId) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE Notificacion SET enviadaEmail = TRUE WHERE id = ?")) {
            ps.setObject(1, notificacionId);
            ps.executeUpdate();
        }
    }

    private List<Notificacion> buscar(String where, UUID parametro) throws SQLException {
        List<Notificacion> lista = new ArrayList<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM Notificacion " + where)) {
            if (parametro != null) {
                ps.setObject(1, parametro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notificacion n = new Notificacion(rs.getObject("usuarioID", UUID.class), rs.getString("tipo"),
                            rs.getString("asunto"), rs.getString("cuerpo"), rs.getString("entidad"),
                            rs.getObject("entidadID", UUID.class));
                    n.setId(rs.getObject("id", UUID.class));
                    n.setFechaCreacion(rs.getTimestamp("fechaCreacion").toLocalDateTime());
                    Timestamp fl = rs.getTimestamp("fechaLectura");
                    n.setFechaLectura(fl != null ? fl.toLocalDateTime() : null);
                    n.setEnviadaEmail(rs.getBoolean("enviadaEmail"));
                    lista.add(n);
                }
            }
        }
        return lista;
    }
}
