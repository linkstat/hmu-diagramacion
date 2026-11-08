package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.RoleData;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.RoleRepository;
import ar.com.hmu.repository.RoleRepository.AsignacionRol;
import ar.com.hmu.repository.ServicioRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Asignar roles con vigencia (CU06): la OP otorga o revoca un rol (agente,
 * JS, OP o DH) con fecha de inicio y de fin y la referencia del memorando
 * que lo dispone. Al designar una JS se cierra la del titular anterior y la
 * nueva JS pasa a ser la encargada del servicio.
 */
public class AsignacionRolService {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RoleRepository roleRepository;
    private final ServicioRepository servicioRepository;

    public AsignacionRolService(RoleRepository roleRepository, ServicioRepository servicioRepository) {
        this.roleRepository = roleRepository;
        this.servicioRepository = servicioRepository;
    }

    public List<AsignacionRol> asignaciones(Usuario u) throws ServiceException {
        try {
            return roleRepository.asignaciones(u.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al leer los roles: " + e.getMessage(), e);
        }
    }

    public List<RoleData> roles() throws ServiceException {
        try {
            return roleRepository.findAll().stream()
                    .sorted((a, b) -> a.getTipoUsuario().compareTo(b.getTipoUsuario())).toList();
        } catch (SQLException e) {
            throw new ServiceException("Error al leer los roles", e);
        }
    }

    /**
     * CU06 pasos 2 a 5.
     * @return mensaje para la OP (incluye la jefatura anterior cerrada, si la hubo).
     */
    public String asignar(Usuario usuario, RoleData rol, UUID servicioId, LocalDate desde, LocalDate hasta,
                          String memorando, Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal asigna roles.");
        }
        if (usuario == null || rol == null || desde == null) {
            throw new ServiceException("Indique el usuario, el rol y la fecha de inicio.");
        }
        if (hasta != null && hasta.isBefore(desde)) {
            throw new ServiceException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        if (memorando == null || memorando.isBlank()) {
            throw new ServiceException("Indique la referencia del memorando que dispone la asignación.");
        }
        boolean jefatura = rol.getTipoUsuario() == TipoUsuario.JEFATURADESERVICIO;
        if (jefatura && servicioId == null) {
            throw new ServiceException("Indique el servicio de la jefatura.");
        }
        try {
            // 3a: superposición con una asignación del mismo rol (y servicio).
            for (AsignacionRol a : roleRepository.asignaciones(usuario.getId())) {
                boolean mismo = a.rolId().equals(rol.getId());
                boolean superpone = (a.hasta() == null || !a.hasta().isBefore(desde))
                        && (hasta == null || !hasta.isBefore(a.desde()));
                if (mismo && superpone && (!jefatura || servicioId.equals(a.servicioId()))) {
                    throw new ServiceException("Se superpone con la asignación de " + a.rol() + " vigente desde el "
                            + a.desde().format(DDMMYYYY) + (a.hasta() != null ? " hasta el " + a.hasta().format(DDMMYYYY) : "")
                            + ". Revóquela antes o elija otra fecha.");
                }
            }
            int cerradas = 0;
            if (jefatura) {
                cerradas = roleRepository.cerrarJefaturaAnterior(servicioId, usuario.getId(), desde);
            }
            roleRepository.registrarAsignacion(usuario.getId(), rol.getId(), jefatura ? servicioId : null, desde, hasta,
                    memorando.trim(), actor.getId());
            if (jefatura) {
                servicioRepository.updateEncargado(servicioId, usuario.getId());
            }
            return "Se asignó el rol " + rol.getTipoUsuario().getDisplayName() + " a " + usuario.getApellidosNombres() + " desde el "
                    + desde.format(DDMMYYYY) + "."
                    + (cerradas > 0 ? " Se cerró la jefatura anterior del servicio el " + desde.minusDays(1).format(DDMMYYYY) + "." : "");
        } catch (SQLException e) {
            throw new ServiceException("Error al asignar el rol: " + e.getMessage(), e);
        }
    }

    /** CU06 1a. */
    public void revocar(AsignacionRol a, LocalDate hasta, Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal revoca roles.");
        }
        if (hasta == null || hasta.isBefore(a.desde())) {
            throw new ServiceException("La fecha de fin debe ser posterior al inicio de la asignación.");
        }
        try {
            roleRepository.cerrarAsignacion(a.usuarioId(), a.rolId(), hasta);
        } catch (SQLException e) {
            throw new ServiceException("Error al revocar el rol: " + e.getMessage(), e);
        }
    }
}
