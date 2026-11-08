package ar.com.hmu.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Agrupacion;
import ar.com.hmu.model.Cargo;
import ar.com.hmu.repository.CargoRepository;

public class CargoService {

    private final CargoRepository cargoRepository;

    public CargoService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    public void create(Cargo cargo) throws ServiceException {
        try {
            cargoRepository.create(cargo);
        } catch (SQLException e) {
            throw new ServiceException("Error al crear el cargo", e);
        }
    }

    public Cargo readByUUID(UUID id) throws ServiceException {
        try {
            return cargoRepository.readByUUID(id);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer el cargo", e);
        }
    }

    public List<Cargo> readAll() throws ServiceException {
        try {
            return cargoRepository.readAll();
        } catch (SQLException e) {
            throw new ServiceException("Error al leer todos los cargos", e);
        }
    }

    public void update(Cargo cargo) throws ServiceException {
        try {
            cargoRepository.update(cargo);
        } catch (SQLException e) {
            throw new ServiceException("Error al actualizar el cargo", e);
        }
    }

    public void delete(Cargo cargo) throws ServiceException {
        try {
            cargoRepository.delete(cargo);
        } catch (SQLException e) {
            throw new ServiceException("Error al eliminar el cargo", e);
        }
    }

    // Métodos adicionales si es necesario
    public Integer findNumeroByDescripcion(String descripcion) throws ServiceException {
        try {
            return cargoRepository.findNumeroByDescripcion(descripcion);
        } catch (SQLException e) {
            throw new ServiceException("Error al buscar el número por descripción", e);
        }
    }

    public String findDescripcionByNumero(Integer numero) throws ServiceException {
        try {
            return cargoRepository.findDescripcionByNumero(numero);
        } catch (SQLException e) {
            throw new ServiceException("Error al buscar la descripción por número", e);
        }
    }

    public List<Cargo> readAllByAgrupacion(Agrupacion agrupacion) throws ServiceException {
        try {
            return cargoRepository.readAllByAgrupacion(agrupacion);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer cargos por agrupación", e);
        }
    }

}

