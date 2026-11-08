package ar.com.hmu.repository;

import ar.com.hmu.model.Agrupacion;
import ar.com.hmu.model.Cargo;
import ar.com.hmu.repository.dao.GenericDAO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clase encargada de las operaciones relacionadas con la entidad Cargo en la base de datos.
 */
public class CargoRepository implements GenericDAO<Cargo> {

    private DatabaseConnector databaseConnector;

    /**
     * Constructor que recibe un conector de base de datos.
     *
     * @param databaseConnector el conector de base de datos a utilizar.
     */
    public CargoRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    @Override
    public void create(Cargo cargo) throws SQLException {
        String query = "INSERT INTO Cargo (id, numero, descripcion, agrupacion) VALUES (gen_random_uuid(), ?, ?, ?)";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, cargo.getNumero());
            stmt.setString(2, cargo.getDescripcion());
            stmt.setObject(3, cargo.getAgrupacion().name(), Types.OTHER);

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al crear el cargo", e);
        }
    }

    @Override
    public Cargo readByUUID(UUID id) throws SQLException {
        String query = "SELECT id, numero, descripcion, agrupacion FROM Cargo WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Cargo(
                            id,
                            rs.getInt("numero"),
                            rs.getString("descripcion"),
                            Agrupacion.valueOf(rs.getString("agrupacion"))
                    );
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer el cargo por UUID", e);
        }
    }

    @Override
    public List<Cargo> readAll() throws SQLException {
        List<Cargo> cargos = new ArrayList<>();
        String query = "SELECT id, numero, descripcion, agrupacion FROM Cargo";

        try (Connection connection = databaseConnector.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                cargos.add(new Cargo(
                        rs.getObject("id", UUID.class),
                        rs.getInt("numero"),
                        rs.getString("descripcion"),
                        Agrupacion.valueOf(rs.getString("agrupacion"))
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer todos los cargos", e);
        }

        return cargos;
    }

    @Override
    public void update(Cargo cargo) throws SQLException {
        String query = "UPDATE Cargo SET numero = ?, descripcion = ?, agrupacion = ? WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, cargo.getNumero());
            stmt.setString(2, cargo.getDescripcion());
            stmt.setObject(3, cargo.getAgrupacion().name(), Types.OTHER);
            stmt.setObject(4, cargo.getId());

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("No se pudo actualizar el cargo; el ID proporcionado no existe.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al actualizar el cargo", e);
        }
    }

    @Override
    public void delete(Cargo cargo) throws SQLException {
        String query = "DELETE FROM Cargo WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, cargo.getId());

            int rowsDeleted = stmt.executeUpdate();
            if (rowsDeleted == 0) {
                throw new SQLException("No se pudo eliminar el cargo; el ID proporcionado no existe.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar el cargo", e);
        }
    }


    /**
     * Encuentra el número de cargo dado su descripción.
     * @param descripcion La descripción del cargo.
     * @return El número del cargo o null si no se encuentra.
     */
    public Integer findNumeroByDescripcion(String descripcion) throws SQLException {
        String query = "SELECT numero FROM Cargo WHERE descripcion = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, descripcion);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("numero");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al buscar el número por descripción", e);
        }
        return null;
    }

    public Cargo findById(UUID id) throws SQLException {
        String query = "SELECT id, numero, descripcion, agrupacion FROM Cargo WHERE id = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Cargo cargo = new Cargo();
                    cargo.setId(rs.getObject("id", UUID.class));
                    cargo.setNumero(rs.getInt("numero"));
                    cargo.setDescripcion(rs.getString("descripcion"));
                    cargo.setAgrupacion(Agrupacion.valueOf(rs.getString("agrupacion")));
                    return cargo;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error al buscar la descripción por número", e);
        }
        return null;
    }

    /**
     * Encuentra la descripción de un cargo dado su número.
     * @param numero El número del cargo.
     * @return La descripción del cargo o null si no se encuentra.
     */
    public String findDescripcionByNumero(Integer numero) throws SQLException {
        String query = "SELECT descripcion FROM Cargo WHERE numero = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, numero);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("descripcion");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error al buscar la descripción por número", e);
        }
        return null;
    }

    /**
     * Lee todos los cargos de una agrupación específica.
     * @param agrupacion La agrupación de los cargos a leer.
     * @return Una lista de cargos pertenecientes a la agrupación especificada.
     */
    public List<Cargo> readAllByAgrupacion(Agrupacion agrupacion) throws SQLException {
        List<Cargo> cargos = new ArrayList<>();
        String query = "SELECT id, numero, descripcion, agrupacion FROM Cargo WHERE agrupacion = ?";

        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setObject(1, agrupacion.name(), Types.OTHER);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    cargos.add(new Cargo(
                            rs.getObject("id", UUID.class),
                            rs.getInt("numero"),
                            rs.getString("descripcion"),
                            agrupacion
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error al leer todos los cargos por agrupación", e);
        }

        return cargos;
    }

}
