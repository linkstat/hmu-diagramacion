package ar.com.hmu.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;

public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicioService(ServicioRepository servicioRepository, UsuarioRepository usuarioRepository) {
        this.servicioRepository = servicioRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public void create(Servicio servicio) throws ServiceException {
        try {
            if(servicio.getDireccionId() != null) {
                servicioRepository.create(servicio);
            } else {
                servicioRepository.createWithoutDireccionId(servicio);
            }
        } catch (SQLException e) {
            throw new ServiceException("Error al crear el servicio", e);
        }
    }

    public Servicio readByUUID(UUID id) throws ServiceException {
        try {
            return servicioRepository.readByUUID(id);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer el servicio", e);
        }
    }

    public List<Servicio> readAll() throws ServiceException {
        try {
            return servicioRepository.readAll();
        } catch (SQLException e) {
            throw new ServiceException("Error al leer todos los servicios", e);
        }
    }

    public void update(Servicio servicio) throws ServiceException {
        try {
            if (servicio.getDireccionId() != null) {
                servicioRepository.update(servicio);
            } else {
                servicioRepository.updateWithoutDireccionId(servicio);
            }
        } catch (SQLException e) {
            throw new ServiceException("Error al actualizar el servicio", e);
        }
    }

    public void delete(Servicio servicio) throws ServiceException {
        try {
            servicioRepository.delete(servicio);
        } catch (SQLException e) {
            throw new ServiceException("Error al eliminar el servicio", e);
        }
    }

    // Métodos adicionales si es necesario
    public UUID findIdByName(String name) throws ServiceException {
        try {
            return servicioRepository.findIdByName(name);
        } catch (SQLException e) {
            throw new ServiceException("Error al buscar el UUID de servicio por nombre", e);
        }
    }

    public int countServicios() throws ServiceException {
        try {
            return servicioRepository.countServicios();
        } catch (SQLException e) {
            throw new ServiceException("Error al contar los servicios", e);
        }
    }

    public int countUsuariosByServicio(UUID servicioId) throws ServiceException {
        try {
            return usuarioRepository.countUsuariosByServicio(servicioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener la cantidad de usuarios del servicio", e);
        }
    }

    public String findJefeByServicio(UUID servicioId) throws ServiceException {
        try {
            return usuarioRepository.findJefeByServicio(servicioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener el jefe a cargo del servicio", e);
        }
    }

    /**
     * Devuelve los usuarios activos del servicio. Usado para poblar el selector
     * de encargado en el ABM de Servicios.
     */
    public List<Usuario> findUsuariosByServicio(UUID servicioId) throws ServiceException {
        try {
            return usuarioRepository.findUsuariosByServicio(servicioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener los usuarios del servicio", e);
        }
    }

    /**
     * Devuelve el UUID del encargado actual del servicio, o null si el
     * servicio no tiene encargado designado.
     */
    public UUID getEncargado(UUID servicioId) throws ServiceException {
        try {
            return servicioRepository.findEncargadoByServicio(servicioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener el encargado del servicio", e);
        }
    }

    /**
     * Asigna o reasigna el encargado actual del servicio. El encargado debe
     * pertenecer al servicio (Usuario.servicioId == servicioId). Pasar null
     * desasigna al encargado actual.
     *
     * @throws ServiceException si el usuario no pertenece al servicio o si el
     *                          servicio no existe.
     */
    public void setEncargado(UUID servicioId, UUID encargadoUsuarioId) throws ServiceException {
        try {
            Servicio servicio = servicioRepository.readByUUID(servicioId);
            if (servicio == null) {
                throw new ServiceException("El servicio no existe.");
            }
            if (encargadoUsuarioId != null) {
                Usuario candidato = usuarioRepository.readByUUID(encargadoUsuarioId);
                if (candidato == null) {
                    throw new ServiceException("El usuario seleccionado como encargado no existe.");
                }
                if (candidato.getServicioId() == null || !candidato.getServicioId().equals(servicioId)) {
                    throw new ServiceException(
                            "El usuario seleccionado no pertenece al servicio: " + servicio.getNombre());
                }
            }
            servicioRepository.updateEncargado(servicioId, encargadoUsuarioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al actualizar el encargado del servicio", e);
        }
    }

}

