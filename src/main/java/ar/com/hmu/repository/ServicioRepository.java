package ar.com.hmu.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ar.com.hmu.model.Agrupacion;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.repository.dao.GenericDAO;


public class ServicioRepository implements GenericDAO<Servicio> {

    private DatabaseConnector databaseConnector;

    public ServicioRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    @Override
    public void create(Servicio servicio) throws SQLException {
        String query = "INSERT INTO Servicio (id, nombre, agrupacion, direccionID, encargadoUsuarioID, dotacionMinima) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, servicio.getId());
            stmt.setString(2, servicio.getNombre());
            stmt.setObject(3, servicio.getAgrupacion().name(), Types.OTHER);
            stmt.setObject(4, servicio.getDireccionId());
            stmt.setObject(5, servicio.getEncargadoUsuarioId());
            stmt.setObject(6, servicio.getDotacionMinima(), Types.INTEGER);

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al crear el servicio", e);
        }
    }

    public void createWithoutDireccionId(Servicio servicio) throws SQLException {
        String query = "INSERT INTO Servicio (id, nombre, agrupacion, encargadoUsuarioID, dotacionMinima) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, servicio.getId());
            stmt.setString(2, servicio.getNombre());
            stmt.setObject(3, servicio.getAgrupacion().name(), Types.OTHER);
            stmt.setObject(4, servicio.getEncargadoUsuarioId());
            stmt.setObject(5, servicio.getDotacionMinima(), Types.INTEGER);

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al crear el servicio (usando el método sin direccionID)", e);
        }
    }

    @Override
    public Servicio readByUUID(UUID id) throws SQLException {
        String query = "SELECT id, nombre, agrupacion, direccionID, encargadoUsuarioID, dotacionMinima FROM Servicio WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToServicio(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer el servicio por UUID", e);
        }
        return null;
    }

    @Override
    public List<Servicio> readAll() throws SQLException {
        List<Servicio> servicios = new ArrayList<>();
        String query = "SELECT id, nombre, agrupacion, direccionID, encargadoUsuarioID, dotacionMinima FROM Servicio";
        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                servicios.add(mapResultSetToServicio(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer todos los servicios", e);
        }
        return servicios;
    }

    @Override
    public void update(Servicio servicio) throws SQLException {
        String query = "UPDATE Servicio SET nombre = ?, agrupacion = ?, direccionID = ?, encargadoUsuarioID = ?, "
                + "dotacionMinima = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, servicio.getNombre());
            stmt.setObject(2, servicio.getAgrupacion().name(), Types.OTHER);
            stmt.setObject(3, servicio.getDireccionId());
            stmt.setObject(4, servicio.getEncargadoUsuarioId());
            stmt.setObject(5, servicio.getDotacionMinima(), Types.INTEGER);
            stmt.setObject(6, servicio.getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al actualizar el servicio", e);
        }
    }

    public void updateWithoutDireccionId(Servicio servicio) throws SQLException {
        String query = "UPDATE Servicio SET nombre = ?, agrupacion = ?, encargadoUsuarioID = ?, dotacionMinima = ? "
                + "WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, servicio.getNombre());
            stmt.setObject(2, servicio.getAgrupacion().name(), Types.OTHER);
            stmt.setObject(3, servicio.getEncargadoUsuarioId());
            stmt.setObject(4, servicio.getDotacionMinima(), Types.INTEGER);
            stmt.setObject(5, servicio.getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al actualizar el servicio", e);
        }
    }

    /**
     * Actualiza únicamente el encargado actual del servicio. Útil para el
     * flujo del ABM cuando solo cambia esta asignación.
     *
     * @param servicioId       UUID del servicio.
     * @param encargadoUsuarioId UUID del nuevo encargado, o null para
     *                         dejar el servicio sin encargado.
     */
    public void updateEncargado(UUID servicioId, UUID encargadoUsuarioId) throws SQLException {
        String query = "UPDATE Servicio SET encargadoUsuarioID = ? WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, encargadoUsuarioId);
            stmt.setObject(2, servicioId);

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al actualizar el encargado del servicio", e);
        }
    }

    /**
     * Devuelve el UUID del encargado actual del servicio, o null si no hay
     * uno designado.
     */
    /** Dotación mínima del servicio (CU07/CU13); 0 si no está definida. */
    public int findDotacionMinima(UUID servicioId) throws SQLException {
        try (Connection c = databaseConnector.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(dotacionMinima, 0) FROM Servicio WHERE id = ?")) {
            ps.setObject(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public UUID findEncargadoByServicio(UUID servicioId) throws SQLException {
        String query = "SELECT encargadoUsuarioID FROM Servicio WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, servicioId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("encargadoUsuarioID", UUID.class);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al buscar el encargado del servicio", e);
        }
        return null;
    }

    @Override
    public void delete(Servicio servicio) throws SQLException {
        String query = "DELETE FROM Servicio WHERE id = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, servicio.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar el servicio", e);
        }
    }

    public Servicio findById(UUID id) throws SQLException {
        String query = "SELECT id, nombre, agrupacion, direccionID, encargadoUsuarioID, dotacionMinima FROM Servicio WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Servicio servicio = new Servicio();
                    servicio.setId(rs.getObject("id", UUID.class));
                    servicio.setNombre(rs.getString("nombre"));
                    servicio.setAgrupacion(Agrupacion.valueOf(rs.getString("agrupacion")));
                    servicio.setDireccionId(rs.getObject("direccionID", UUID.class));
                    servicio.setEncargadoUsuarioId(rs.getObject("encargadoUsuarioID", UUID.class));
                    int dot = rs.getInt("dotacionMinima");
                    servicio.setDotacionMinima(rs.wasNull() ? null : dot);
                    return servicio;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error al buscar la descripción por número", e);
        }
        return null;
    }

    /**
     * Devuelve el UUID de Servicio en base a un nombre dado
     * @param name Nombre del servicio del cual se quiere obtener el UUID
     * @return UUID del servicio buscado
     */
    public UUID findIdByName(String name) throws SQLException {
        String query = "SELECT id FROM Servicio WHERE nombre = ?";
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("id", UUID.class);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al buscar el UUID de servicio por nombre", e);
        }
        return null;
    }


    /**
     * Método para mapear filas a un ResultSet de Servicio.
     */
    private Servicio mapResultSetToServicio(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String nombre = rs.getString("nombre");
        Agrupacion agrupacion = Agrupacion.valueOf(rs.getString("agrupacion"));
        UUID direccionId = rs.getObject("direccionID", UUID.class);
        UUID encargadoUsuarioId = rs.getObject("encargadoUsuarioID", UUID.class);
        if (direccionId == null) {
            //TODO: Decidir cómo manejar este caso: lanzar una excepción o establecer como null.
            System.out.println("direccionID es null para ServicioID: " + nombre);
        }

        Servicio servicio = new Servicio(id, nombre, agrupacion, direccionId);
        servicio.setEncargadoUsuarioId(encargadoUsuarioId);
        int dot = rs.getInt("dotacionMinima");
        servicio.setDotacionMinima(rs.wasNull() ? null : dot);
        return servicio;
    }


    public int countServicios() throws SQLException {
        String query = "SELECT COUNT(*) AS total FROM Servicio";

        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al contar los servicios", e);
        }

        return 0;
    }


}
