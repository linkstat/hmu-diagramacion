package ar.com.hmu.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Domicilio;
import ar.com.hmu.repository.DomicilioRepository;


public class DomicilioService {

    private final DomicilioRepository domicilioRepository;

    public DomicilioService(DomicilioRepository domicilioRepository) {
        this.domicilioRepository = domicilioRepository;
    }

    public void create(Domicilio domicilio) throws ServiceException {
        try {
            domicilioRepository.create(domicilio);
        } catch (SQLException e) {
            throw new ServiceException("Error al crear el domicilio", e);
        }
    }

    public Domicilio readByUUID(UUID id) throws ServiceException {
        try {
            return domicilioRepository.readByUUID(id);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer el domicilio", e);
        }
    }

    public List<Domicilio> readAll() throws ServiceException {
        try {
            return domicilioRepository.readAll();
        } catch (SQLException e) {
            throw new ServiceException("Error al leer todos los domicilios", e);
        }
    }

    public void update(Domicilio domicilio) throws ServiceException {
        try {
            domicilioRepository.update(domicilio);
        } catch (SQLException e) {
            throw new ServiceException("Error al actualizar el domicilio", e);
        }
    }

    public void delete(Domicilio domicilio) throws ServiceException {
        try {
            domicilioRepository.delete(domicilio);
        } catch (SQLException e) {
            throw new ServiceException("Error al eliminar el domicilio", e);
        }
    }

}

