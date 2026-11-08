package ar.com.hmu.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.factory.DomicilioFactory;
import ar.com.hmu.model.Domicilio;
import ar.com.hmu.repository.dao.GenericDAO;


/**
 * Clase encargada de las operaciones relacionadas con la entidad Domicilio en la base de datos.
 */
public class DomicilioRepository implements GenericDAO<Domicilio> {

    private DatabaseConnector databaseConnector;

    /**
     * Constructor que recibe un conector de base de datos.
     *
     * @param databaseConnector el conector de base de datos a utilizar.
     */
    public DomicilioRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    @Override
    public void create(Domicilio domicilio) throws SQLException {
        String query = "INSERT INTO Domicilio (id, calle, numeracion, barrio, ciudad, localidad, provincia) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, domicilio.getId());
            stmt.setString(2, domicilio.getCalle());
            stmt.setInt(3, domicilio.getNumeracion());
            stmt.setString(4, domicilio.getBarrio());
            stmt.setString(5, domicilio.getCiudad());
            stmt.setString(6, domicilio.getLocalidad());
            stmt.setString(7, domicilio.getProvincia());

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al crear el domicilio", e);
        }
    }

    @Override
    public Domicilio readByUUID(UUID id) throws SQLException {
        String query = "SELECT id, calle, numeracion, barrio, ciudad, localidad, provincia FROM Domicilio WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return DomicilioFactory.createDomicilio(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer el domicilio por UUID", e);
        }
        return null;

    }

    @Override
    public List<Domicilio> readAll() throws SQLException {
        List<Domicilio> domicilios = new ArrayList<>();
        String query = "SELECT id, calle, numeracion, barrio, ciudad, localidad, provincia FROM Domicilio";

        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                domicilios.add(new Domicilio.Builder()
                        .id(rs.getObject("id", UUID.class))
                        .calle(rs.getString("calle"))
                        .numeracion(rs.getInt("numeracion"))
                        .barrio(rs.getString("barrio"))
                        .ciudad(rs.getString("ciudad"))
                        .localidad(rs.getString("localidad"))
                        .provincia(rs.getString("provincia"))
                        .build());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer todos los domicilios", e);
        }

        return domicilios;
    }

    @Override
    public void update(Domicilio domicilio) throws SQLException {
        String query = "UPDATE Domicilio SET calle = ?, numeracion = ?, barrio = ?, ciudad = ?, localidad = ?, provincia = ? WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, domicilio.getCalle());
            stmt.setInt(2, domicilio.getNumeracion());
            stmt.setString(3, domicilio.getBarrio());
            stmt.setString(4, domicilio.getCiudad());
            stmt.setString(5, domicilio.getLocalidad());
            stmt.setString(6, domicilio.getProvincia());
            stmt.setObject(7, domicilio.getId());

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("No se pudo actualizar el domicilio; el ID proporcionado no existe.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al actualizar el domicilio", e);
        }
    }

    @Override
    public void delete(Domicilio domicilio) throws SQLException {
        String query = "DELETE FROM Domicilio WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, domicilio.getId());

            int rowsDeleted = stmt.executeUpdate();
            if (rowsDeleted == 0) {
                throw new SQLException("No se pudo eliminar el domicilio; el ID proporcionado no existe.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar el domicilio", e);
        }
    }

    public Domicilio findById(UUID id) throws SQLException {
        String query = "SELECT d.id AS id, d.calle, d.numeracion, d.barrio, d.ciudad, d.localidad, d.provincia " +
                "FROM Domicilio d JOIN Usuario u ON d.id = u.domicilioID " +
                "WHERE u.domicilioID = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Domicilio domicilio = new Domicilio.Builder()
                            .id(rs.getObject("id", UUID.class))
                            .calle(rs.getString("calle"))
                            .numeracion(rs.getInt("numeracion"))
                            .barrio(rs.getString("barrio"))
                            .ciudad(rs.getString("ciudad"))
                            .localidad(rs.getString("localidad"))
                            .provincia(rs.getString("provincia"))
                            .build();
                    return domicilio;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error al buscar la descripción por número", e);
        }
        return null;
    }

    public Domicilio findByIdAndUserId(UUID domicilioId, UUID usuarioId) throws SQLException, ServiceException {
        String query = "SELECT d.id AS id, d.calle, d.numeracion, d.barrio, d.ciudad, d.localidad, d.provincia " +
                "FROM Domicilio d JOIN Usuario u ON d.id = u.domicilioID " +
                "WHERE d.id = ? AND u.id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, domicilioId);
            stmt.setObject(2, usuarioId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Domicilio domicilio = new Domicilio.Builder()
                            .id(rs.getObject("id", UUID.class))
                            .calle(rs.getString("calle"))
                            .numeracion(rs.getInt("numeracion"))
                            .barrio(rs.getString("barrio"))
                            .ciudad(rs.getString("ciudad"))
                            .localidad(rs.getString("localidad"))
                            .provincia(rs.getString("provincia"))
                            .build();
                    return domicilio;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ServiceException("Domicilio no encontrado para el usuario con ID: " + usuarioId.toString());

        }
        return null;
    }

}
