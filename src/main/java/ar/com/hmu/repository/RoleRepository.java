package ar.com.hmu.repository;

import java.sql.*;
import java.util.*;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.model.RoleData;

public class RoleRepository {
    private DatabaseConnector databaseConnector;

    public RoleRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    public RoleData findByNombre(String nombre) throws SQLException {
        String query = "SELECT id, nombre, descripcion FROM Rol WHERE nombre = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, nombre);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    RoleData roleData = new RoleData();
                    roleData.setId(rs.getObject("id", UUID.class));
                    roleData.setNombre(rs.getString("nombre"));
                    roleData.setDescripcion(rs.getString("descripcion"));
                    roleData.setTipoUsuario(TipoUsuario.fromInternalName(roleData.getNombre()));

                    return roleData;
                }
            }
        }
        return null;
    }

    public RoleData findByTipoUsuario(TipoUsuario tipoUsuario) throws SQLException {
        String nombre = tipoUsuario.getInternalName();
        return findByNombre(nombre);
    }

    public Set<RoleData> findAll() throws SQLException {
        Set<RoleData> rolesSet = new HashSet<>();
        String query = "SELECT id, nombre, descripcion FROM Rol";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                RoleData roleData = new RoleData();
                roleData.setId(rs.getObject("id", UUID.class));
                roleData.setNombre(rs.getString("nombre"));
                roleData.setDescripcion(rs.getString("descripcion"));
                roleData.setTipoUsuario(TipoUsuario.fromInternalName(roleData.getNombre()));
                rolesSet.add(roleData);
            }
        }

        return rolesSet;
    }

    /**
     * Asigna (o vuelve a abrir) un rol vigente desde hoy (CU06). Si el rol se
     * había cerrado en esta misma operación (ABM: revocar todos y reasignar los
     * tildados), conserva su fecha de inicio y su memorando.
     */
    public void asignarRol(UUID usuarioId, UUID rolId) throws SQLException {
        String query = """
                INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde, servicioID)
                VALUES (?, ?, ?, (SELECT CASE WHEN r.nombre = 'JefaturaDeServicio' THEN u.servicioID END
                                  FROM Usuario u, Rol r WHERE u.id = ? AND r.id = ?))
                ON CONFLICT (usuarioID, rolID) DO UPDATE SET
                    fechaDesde = CASE WHEN AsignacionRol.fechaHasta IS NOT NULL AND AsignacionRol.fechaHasta < ?::date - 1
                                      THEN ?::date ELSE AsignacionRol.fechaDesde END,
                    fechaHasta = NULL""";
        java.sql.Date hoy = java.sql.Date.valueOf(ar.com.hmu.util.Reloj.hoy());
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, usuarioId);
            stmt.setObject(2, rolId);
            stmt.setDate(3, hoy);
            stmt.setObject(4, usuarioId);
            stmt.setObject(5, rolId);
            stmt.setDate(6, hoy);
            stmt.setDate(7, hoy);
            stmt.executeUpdate();
        }
    }

    /** Cierra la vigencia de todos los roles del usuario el día previo (no borra: queda el historial). */
    public void revocarTodosLosRoles(UUID usuarioId) throws SQLException {
        String query = "UPDATE AsignacionRol SET fechaHasta = ?::date - 1 "
                + "WHERE usuarioID = ? AND (fechaHasta IS NULL OR fechaHasta >= ?::date)";
        java.sql.Date hoy = java.sql.Date.valueOf(ar.com.hmu.util.Reloj.hoy());
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setDate(1, hoy);
            stmt.setObject(2, usuarioId);
            stmt.setDate(3, hoy);
            stmt.executeUpdate();
        }
    }

    /** Una asignación de rol con su vigencia (CU06; AsignacionRol del DER). */
    public record AsignacionRol(UUID usuarioId, UUID rolId, String rol, UUID servicioId, String servicio,
                                java.time.LocalDate desde, java.time.LocalDate hasta, String memorando,
                                String otorgadoPor) {
        public boolean vigenteEl(java.time.LocalDate fecha) {
            return !desde.isAfter(fecha) && (hasta == null || !hasta.isBefore(fecha));
        }
    }

    public List<AsignacionRol> asignaciones(UUID usuarioId) throws SQLException {
        List<AsignacionRol> lista = new ArrayList<>();
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT ur.*, r.nombre AS rol, s.nombre AS servicio, o.apellidos || ', ' || o.nombres AS otorgante
                     FROM AsignacionRol ur JOIN Rol r ON r.id = ur.rolID
                     LEFT JOIN Servicio s ON s.id = ur.servicioID LEFT JOIN Usuario o ON o.id = ur.otorgadoPorID
                     WHERE ur.usuarioID = ? ORDER BY ur.fechaDesde""")) {
            ps.setObject(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date h = rs.getDate("fechaHasta");
                    lista.add(new AsignacionRol(usuarioId, rs.getObject("rolID", UUID.class), rs.getString("rol"),
                            rs.getObject("servicioID", UUID.class), rs.getString("servicio"),
                            rs.getDate("fechaDesde").toLocalDate(), h != null ? h.toLocalDate() : null,
                            rs.getString("memorandoRef"), rs.getString("otorgante")));
                }
            }
        }
        return lista;
    }

    /** Registra (o reemplaza) la asignación con su vigencia y memorando (CU06 paso 5). */
    public void registrarAsignacion(UUID usuarioId, UUID rolId, UUID servicioId, java.time.LocalDate desde,
                                    java.time.LocalDate hasta, String memorando, UUID otorgadoPor) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde, fechaHasta, servicioID, memorandoRef, otorgadoPorID)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (usuarioID, rolID) DO UPDATE SET fechaDesde = EXCLUDED.fechaDesde,
                         fechaHasta = EXCLUDED.fechaHasta, servicioID = EXCLUDED.servicioID,
                         memorandoRef = EXCLUDED.memorandoRef, otorgadoPorID = EXCLUDED.otorgadoPorID""")) {
            ps.setObject(1, usuarioId);
            ps.setObject(2, rolId);
            ps.setDate(3, java.sql.Date.valueOf(desde));
            ps.setDate(4, hasta != null ? java.sql.Date.valueOf(hasta) : null);
            ps.setObject(5, servicioId);
            ps.setString(6, memorando);
            ps.setObject(7, otorgadoPor);
            ps.executeUpdate();
        }
    }

    /** CU06 1a: revocación con fecha de fin. */
    public void cerrarAsignacion(UUID usuarioId, UUID rolId, java.time.LocalDate hasta) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE AsignacionRol SET fechaHasta = ? WHERE usuarioID = ? AND rolID = ?")) {
            ps.setDate(1, java.sql.Date.valueOf(hasta));
            ps.setObject(2, usuarioId);
            ps.setObject(3, rolId);
            ps.executeUpdate();
        }
    }

    /**
     * CU06 paso 4: al designar una JS se cierra la asignación de la jefatura
     * anterior del mismo servicio el día previo al inicio de la nueva.
     * @return cantidad de asignaciones cerradas.
     */
    public int cerrarJefaturaAnterior(UUID servicioId, UUID nuevoJefe, java.time.LocalDate desde) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE AsignacionRol ur SET fechaHasta = ?::date - 1
                     FROM Rol r WHERE r.id = ur.rolID AND r.nombre = 'JefaturaDeServicio'
                       AND ur.servicioID = ? AND ur.usuarioID <> ?
                       AND (ur.fechaHasta IS NULL OR ur.fechaHasta >= ?::date)""")) {
            ps.setDate(1, java.sql.Date.valueOf(desde));
            ps.setObject(2, servicioId);
            ps.setObject(3, nuevoJefe);
            ps.setDate(4, java.sql.Date.valueOf(desde));
            return ps.executeUpdate();
        }
    }


    public Set<RoleData> findRolesByUsuarioId(UUID usuarioId) throws SQLException {
        // Sólo los roles vigentes a la fecha (CU06: el control de acceso aplica la vigencia).
        String query = "SELECT r.id AS id, r.nombre, r.descripcion " +
                "FROM Rol r INNER JOIN AsignacionRol ur ON r.id = ur.rolID " +
                "WHERE ur.usuarioID = ? AND ur.fechaDesde <= ? AND (ur.fechaHasta IS NULL OR ur.fechaHasta >= ?)";
        Set<RoleData> roles = new HashSet<>();
        java.sql.Date hoy = java.sql.Date.valueOf(ar.com.hmu.util.Reloj.hoy());
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, usuarioId);
            stmt.setDate(2, hoy);
            stmt.setDate(3, hoy);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                RoleData roleData = new RoleData();
                roleData.setId(rs.getObject("id", UUID.class));
                roleData.setNombre(rs.getString("nombre"));
                roleData.setDescripcion(rs.getString("descripcion"));
                roles.add(roleData);
            }
        }
        return roles;
    }



}
