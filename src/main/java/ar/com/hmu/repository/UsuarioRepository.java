package ar.com.hmu.repository;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.factory.UsuarioFactory;
import ar.com.hmu.model.*;
import ar.com.hmu.repository.dao.GenericDAO;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * Clase encargada de las operaciones relacionadas con la entidad Usuario en la base de datos.
 */
public class UsuarioRepository implements GenericDAO<Usuario> {

    private DatabaseConnector databaseConnector;
    private UsuarioFactory usuarioFactory;

    public UsuarioRepository(DatabaseConnector databaseConnector, UsuarioFactory usuarioFactory) {
        this.databaseConnector = databaseConnector;
        this.usuarioFactory = usuarioFactory;
    }

    @Override
    public void create(Usuario usuario) throws SQLException {
        // Class Table Inheritance: todo Usuario del HMU es también Empleado
        // (convención del seed). Se inserta en ambas tablas en una transacción.
        Connection connection = null;
        try {
            connection = databaseConnector.getConnection();
            connection.setAutoCommit(false);

            String insertUsuario = "INSERT INTO Usuario (id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel, passwordHash, domicilioID, cargoID, servicioID) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertUsuario)) {
                stmt.setObject(1, usuario.getId());
                stmt.setDate(2, Date.valueOf(usuario.getFechaAlta()));
                stmt.setBoolean(3, usuario.getEstado());
                stmt.setLong(4, usuario.getCuil());
                stmt.setString(5, usuario.getApellidos());
                stmt.setString(6, usuario.getNombres());
                stmt.setObject(7, usuario.getSexo().name(), Types.OTHER);
                stmt.setString(8, usuario.getMail());
                stmt.setObject(9, usuario.getTel() != 0 ? usuario.getTel() : null, Types.BIGINT);
                stmt.setString(10, usuario.getEncryptedPassword());
                stmt.setObject(11, usuario.getDomicilioId());
                stmt.setObject(12, usuario.getCargoId());
                stmt.setObject(13, usuario.getServicioId());
                stmt.executeUpdate();
            }

            String insertEmpleado = "INSERT INTO Agente (id, francosCompensatoriosUtilizados, horarioActualID) VALUES (?, NULL, NULL)";
            try (PreparedStatement stmt = connection.prepareStatement(insertEmpleado)) {
                stmt.setObject(1, usuario.getId());
                stmt.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
            throw new RuntimeException("Error al crear el usuario", e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(true); connection.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public Usuario readByUUID(UUID id) throws SQLException {
        String query = "SELECT id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel, " +
                "domicilioID, cargoID, servicioID, passwordHash, profileImage " +
                "FROM Usuario WHERE estado = TRUE AND id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return usuarioFactory.createUsuario(rs);
                }
            } catch (ServiceException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer el usuario por UUID", e);
        }
        return null;
    }

    @Override
    public List<Usuario> readAll() throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        String query = "SELECT id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel, " +
                "domicilioID, cargoID, servicioID, passwordHash, profileImage " +
                "FROM Usuario WHERE estado = TRUE";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                usuarios.add(usuarioFactory.createUsuario(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer todos los usuarios", e);
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
        return usuarios;
    }

    @Override
    public void update(Usuario usuario) throws SQLException {
        String query = "UPDATE Usuario SET fechaAlta = ?, estado = ?, cuil = ?, apellidos = ?, nombres = ?, sexo = ?, mail = ?, tel = ?, " +
                "domicilioID = ?, cargoID = ?, servicioID = ?, passwordHash = ?, profileImage = ? " +
                "WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setDate(1, usuario.getFechaAlta() != null ? java.sql.Date.valueOf(usuario.getFechaAlta()) : java.sql.Date.valueOf(java.time.LocalDate.now()));
            stmt.setBoolean(2, usuario.getEstado());
            stmt.setLong(3, usuario.getCuil());
            stmt.setString(4, usuario.getApellidos());
            stmt.setString(5, usuario.getNombres());
            stmt.setObject(6, usuario.getSexo().name(), Types.OTHER);
            stmt.setString(7, usuario.getMail());
            stmt.setObject(8, usuario.getTel() != 0 ? usuario.getTel() : null, Types.BIGINT);
            stmt.setObject(9, usuario.getDomicilioId());
            stmt.setObject(10, usuario.getCargoId());
            stmt.setObject(11, usuario.getServicioId());
            stmt.setString(12, usuario.getEncryptedPassword());
            stmt.setBytes(13, usuario.getProfileImage() != null && usuario.getProfileImage().length > 0
                    ? usuario.getProfileImage()
                    : null);
            stmt.setObject(14, usuario.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar el usuario", e);
        }
    }

    @Override
    public void delete(Usuario usuario) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            String query = "DELETE FROM Usuario WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setObject(1, usuario.getId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar el usuario", e);
        }
    }

    // Métodos específicos para Usuario

    /**
     * Recupera un usuario de la base de datos basado en el CUIL proporcionado.
     * Además, carga la información de cargo asociada al usuario si está disponible.
     *
     * @param cuil El número de CUIL del usuario.
     * @return El objeto Usuario si se encuentra en la base de datos, o null si no existe.
     * @throws SQLException En caso de errores durante la consulta SQL.
     */
    public Usuario findUsuarioByCuil(long cuil) throws SQLException {
        String query = "SELECT id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel, " +
                "domicilioID, cargoID, servicioID, passwordHash, profileImage " +
                "FROM Usuario WHERE estado = TRUE AND cuil = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setLong(1, cuil);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return usuarioFactory.createUsuario(rs);
                }
            } catch (ServiceException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }


    public Usuario findUsuarioByCuil(long cuil, boolean includeDisabled) throws SQLException, ServiceException {
        if (includeDisabled) {
            String query = "SELECT id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel, " +
                    "domicilioID, cargoID, servicioID, passwordHash, profileImage " +
                    "FROM Usuario WHERE cuil = ?";
            try (Connection connection = databaseConnector.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {

                stmt.setLong(1, cuil);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return usuarioFactory.createUsuario(rs);
                    }
                }
            }
            return null;
        } else {
            return findUsuarioByCuil(cuil);
        }
    }


    /**
     * Devuelve el UUID de un usuario en base a su CUIL
     * @param cuil Número de CUIL
     * @return el UUID del usuario buscado
     * @throws SQLException
     */
    public UUID findUUIDByCuil(long cuil) throws SQLException {
        String query = "SELECT id FROM Usuario WHERE estado = TRUE AND cuil = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setLong(1, cuil);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("id", UUID.class);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al buscar UUID por CUIL", e);
        }
        return null;
    }

    /**
     * Busca la contraseña hasheada de un usuario por su CUIL.
     *
     * @param cuil el CUIL del usuario.
     * @return la contraseña hasheada del usuario o null si no se encuentra.
     * @throws SQLException si ocurre un error durante la consulta.
     */
    public String findPasswordByCuil(long cuil) throws SQLException {
        String query = "SELECT passwordHash FROM Usuario WHERE estado = TRUE AND cuil = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setLong(1, cuil);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("passwordHash");
                }
            }
        }
        return null;
    }

    /** Estado de la cuenta para el inicio de sesión (CU01); null si el CUIL no existe. */
    public record EstadoAcceso(int intentosFallidos, java.time.LocalDateTime fechaBloqueo,
                               boolean cambioPasswordRequerido) {
        public boolean bloqueada() {
            return fechaBloqueo != null;
        }
    }

    public EstadoAcceso findEstadoAcceso(long cuil) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT intentosFallidos, fechaBloqueo, cambioPasswordRequerido FROM Usuario WHERE cuil = ?")) {
            ps.setLong(1, cuil);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                java.sql.Timestamp f = rs.getTimestamp(2);
                return new EstadoAcceso(rs.getInt(1), f != null ? f.toLocalDateTime() : null, rs.getBoolean(3));
            }
        }
    }

    /** Suma un intento fallido y bloquea la cuenta al llegar al máximo (CU01 2a/2b). */
    public EstadoAcceso registrarIntentoFallido(long cuil, int maximo) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE Usuario SET intentosFallidos = intentosFallidos + 1,
                         fechaBloqueo = CASE WHEN intentosFallidos + 1 >= ? THEN COALESCE(fechaBloqueo, now())
                                             ELSE fechaBloqueo END
                     WHERE cuil = ? RETURNING intentosFallidos, fechaBloqueo, cambioPasswordRequerido""")) {
            ps.setInt(1, maximo);
            ps.setLong(2, cuil);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                java.sql.Timestamp f = rs.getTimestamp(2);
                return new EstadoAcceso(rs.getInt(1), f != null ? f.toLocalDateTime() : null, rs.getBoolean(3));
            }
        }
    }

    /** Ingreso correcto: el contador vuelve a cero (CU01 paso 3). */
    public void reiniciarIntentos(long cuil) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Usuario SET intentosFallidos = 0 WHERE cuil = ? AND intentosFallidos <> 0")) {
            ps.setLong(1, cuil);
            ps.executeUpdate();
        }
    }

    /** Restablecimiento por la OP: contraseña inicial, desbloqueo y cambio obligatorio (CU02). */
    public void restablecerAcceso(long cuil, String hashedPassword) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE Usuario SET passwordHash = ?, intentosFallidos = 0, fechaBloqueo = NULL,
                         cambioPasswordRequerido = TRUE WHERE cuil = ?""")) {
            ps.setString(1, hashedPassword);
            ps.setLong(2, cuil);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No existe el usuario con CUIL " + cuil + ".");
            }
        }
    }

    public void updatePassword(long cuil, String hashedPassword) throws SQLException {
        // Una contraseña elegida por el usuario cumple el cambio obligatorio (CU02 paso 4).
        String query = "UPDATE Usuario SET passwordHash = ?, cambioPasswordRequerido = FALSE WHERE cuil = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, hashedPassword);
            stmt.setLong(2, cuil);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("No se pudo actualizar la contraseña; el usuario con CUIL " + cuil + " no existe.\nHash: " + hashedPassword);
            }
        }
    }

    public void updateProfileImage(long cuil, byte[] imageBytes) throws SQLException {
        String query = "UPDATE Usuario SET profileImage = ? WHERE cuil = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setBytes(1, imageBytes);
            stmt.setLong(2, cuil);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("No se pudo actualizar la imagen de perfil; el usuario con CUIL " + cuil + " no existe.");
            }
        }
    }


    public int countUsuarios() {
        String query = "SELECT COUNT(*) AS total FROM Usuario WHERE estado = TRUE";

        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al contar los usuarios", e);
        }

        return 0;
    }


    public int countUsuarios(boolean includeDisabled) {
        String query = includeDisabled
                ? "SELECT COUNT(*) AS total FROM Usuario"
                : "SELECT COUNT(*) AS total FROM Usuario WHERE estado = TRUE";

        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al contar los usuarios", e);
        }

        return 0;
    }

    /**
     * Devuelve los usuarios activos pertenecientes a un servicio dado.
     * Usado para poblar selectores como el de "Encargado actual" del servicio
     * en el ABM.
     */
    public List<Usuario> findUsuariosByServicio(UUID servicioId) throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        String query = "SELECT id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, tel, " +
                "domicilioID, cargoID, servicioID, passwordHash, profileImage " +
                "FROM Usuario WHERE servicioID = ? AND estado = TRUE " +
                "ORDER BY apellidos, nombres";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, servicioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    usuarios.add(usuarioFactory.createUsuario(rs));
                }
            } catch (ServiceException e) {
                throw new RuntimeException(e);
            }
        }
        return usuarios;
    }

    /**
     * Permiso de vista del diagrama del empleado: {@code true} = ve la
     * grilla completa de su servicio en "Mi Diagrama"; {@code false} =
     * sólo sus propias jornadas. Si el empleado no existe devuelve
     * {@code true} (el default del esquema).
     */
    public boolean findVeDiagramaCompleto(UUID empleadoId) throws SQLException {
        String query = "SELECT veDiagramaCompleto FROM Agente WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, empleadoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }
        return true;
    }

    /** Setea el permiso de vista del diagrama del empleado. */
    public void setVeDiagramaCompleto(UUID empleadoId, boolean valor) throws SQLException {
        String query = "UPDATE Agente SET veDiagramaCompleto = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setBoolean(1, valor);
            stmt.setObject(2, empleadoId);
            stmt.executeUpdate();
        }
    }

    /** Cantidad de usuarios activos por servicio, en una sola consulta
     *  (para el chequeo de alertas de diagramación al login). */
    public java.util.Map<UUID, Integer> countUsuariosActivosPorServicio() throws SQLException {
        java.util.Map<UUID, Integer> conteos = new java.util.HashMap<>();
        String query = "SELECT servicioID, COUNT(*) FROM Usuario " +
                "WHERE estado = TRUE AND servicioID IS NOT NULL GROUP BY servicioID";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                conteos.put(rs.getObject(1, UUID.class), rs.getInt(2));
            }
        }
        return conteos;
    }

    public int countUsuariosByServicio(UUID servicioId) throws SQLException {
        String query = "SELECT COUNT(*) FROM Usuario WHERE servicioID = ? AND estado = TRUE";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, servicioId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public String findJefeByServicio(UUID servicioId) throws SQLException {
        // El "tipo" del usuario ya no se discrimina por columna en Usuario;
        // se resuelve uniendo contra AsignacionRol + Rol y filtrando por nombre.
        String query = "SELECT u.apellidos || ', ' || u.nombres AS Jefe " +
                "FROM Usuario u " +
                "JOIN AsignacionRol ur ON u.id = ur.usuarioID " +
                "JOIN Rol r ON ur.rolID = r.id " +
                "WHERE u.servicioID = ? AND r.nombre = ? AND u.estado = TRUE " +
                "AND ur.fechaDesde <= CURRENT_DATE AND (ur.fechaHasta IS NULL OR ur.fechaHasta >= CURRENT_DATE) " +
                "LIMIT 1";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, servicioId);
            stmt.setString(2, TipoUsuario.JEFATURADESERVICIO.getInternalName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Jefe");
                }
            }
        }
        return "";
    }


}
