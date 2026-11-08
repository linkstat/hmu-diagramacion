package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.RoleData;
import ar.com.hmu.repository.RoleRepository;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public class RoleService {

    private RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public RoleData findByTipoUsuario(TipoUsuario tipoUsuario) throws ServiceException {
        try {
            return roleRepository.findByTipoUsuario(tipoUsuario);
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener el rol: " + tipoUsuario.getInternalName(), e);
        }
    }

    public Set<RoleData> findAll() throws ServiceException {
        try {
            return roleRepository.findAll();
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener todos los roles", e);
        }
    }

    public void asignarRol(UUID usuarioId, UUID rolId) throws ServiceException {
        try {
            roleRepository.asignarRol(usuarioId, rolId);
        } catch (SQLException e) {
            throw new ServiceException("Error al asignar rol", e);
        }
    }

    public void revocarTodosLosRoles(UUID usuarioId) throws ServiceException {
        try {
            roleRepository.revocarTodosLosRoles(usuarioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al revocar todos los roles", e);
        }
    }

    public Set<RoleData> findRolesByUsuarioId(UUID usuarioId) throws ServiceException {
        try {
            return roleRepository.findRolesByUsuarioId(usuarioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener roles del usuario", e);
        }
    }

}
