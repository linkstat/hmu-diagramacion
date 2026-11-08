package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoFeriado;
import ar.com.hmu.model.Feriado;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.FeriadoRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow de feriados institucionales (decisión de alcance 2026-07-08):
 *
 * <ul>
 *   <li><b>Carga anual</b>: la Oficina de Personal carga en bloque los
 *       feriados de un año y quedan VIGENTES directos. Sólo procede si el
 *       año no tiene todavía feriados activos.</li>
 *   <li><b>Cambios posteriores</b> (p.ej. feriado administrativo decretado
 *       durante el año): OP los propone (alta o baja) y quedan pendientes
 *       hasta que la <b>Dirección</b> autorice o rechace. Un feriado con
 *       baja pendiente sigue vigente hasta la autorización.</li>
 * </ul>
 *
 * <p>Los feriados vigentes alimentan la diagramación de servicios
 * (generadores, validador, plantilla feriante) a través de
 * {@code ContextoDiagramacion}.</p>
 */
public class FeriadoService {

    private final FeriadoRepository feriadoRepository;

    public FeriadoService(FeriadoRepository feriadoRepository) {
        this.feriadoRepository = feriadoRepository;
    }

    // ============================================================
    // Carga anual (OP, directa)
    // ============================================================

    /**
     * Carga en bloque los feriados de un año (quedan VIGENTES directos).
     *
     * @param anio     año calendario al que pertenecen todas las fechas.
     * @param feriados fecha → descripción; todas las fechas deben caer en
     *                 {@code anio}.
     * @param actor    usuario con rol Oficina de Personal.
     * @throws ServiceException si el actor no es OP, alguna fecha cae fuera
     *                          del año, o el año ya tiene feriados activos
     *                          (los cambios posteriores van por propuesta).
     */
    public void cargarAnio(int anio, Map<LocalDate, String> feriados, Usuario actor)
            throws ServiceException {
        validarEsOficinaDePersonal(actor);
        if (feriados == null || feriados.isEmpty()) {
            throw new ServiceException("La carga anual requiere al menos un feriado.");
        }
        for (LocalDate fecha : feriados.keySet()) {
            if (fecha.getYear() != anio) {
                throw new ServiceException("La fecha " + fecha + " no pertenece al año " + anio + ".");
            }
        }
        try {
            if (feriadoRepository.countActivosEnAnio(anio) > 0) {
                throw new ServiceException("El año " + anio + " ya tiene feriados cargados: "
                        + "los cambios se proponen individualmente y los autoriza la Dirección.");
            }
            List<Feriado> lote = new ArrayList<>();
            for (Map.Entry<LocalDate, String> e : feriados.entrySet()) {
                lote.add(new Feriado(UUID.randomUUID(), e.getKey(),
                        e.getValue(), EstadoFeriado.VIGENTE, actor.getId()));
            }
            lote.sort(java.util.Comparator.comparing(Feriado::getFecha));
            feriadoRepository.createBatch(lote);
        } catch (SQLException e) {
            throw new ServiceException("Error en la carga anual de feriados", e);
        }
    }

    // ============================================================
    // Propuestas (OP) y resolución (Dirección)
    // ============================================================

    /** Propone el alta de un feriado (queda ALTA_PENDIENTE hasta que la
     *  Dirección lo autorice). */
    public Feriado proponerAlta(LocalDate fecha, String descripcion, Usuario actor)
            throws ServiceException {
        validarEsOficinaDePersonal(actor);
        if (fecha == null || descripcion == null || descripcion.isBlank()) {
            throw new ServiceException("El feriado requiere fecha y descripción.");
        }
        try {
            Feriado f = new Feriado(UUID.randomUUID(), fecha, descripcion.trim(),
                    EstadoFeriado.ALTA_PENDIENTE, actor.getId());
            feriadoRepository.create(f);
            return f;
        } catch (SQLException e) {
            throw new ServiceException("Error al proponer el feriado (¿ya existe uno en esa fecha?)", e);
        }
    }

    /** Propone la baja de un feriado vigente (sigue vigente hasta que la
     *  Dirección autorice la baja). */
    public void proponerBaja(UUID feriadoId, Usuario actor) throws ServiceException {
        validarEsOficinaDePersonal(actor);
        try {
            Feriado f = feriadoRepository.readByUUID(feriadoId);
            if (f == null) {
                throw new ServiceException("El feriado no existe.");
            }
            if (f.getEstado() != EstadoFeriado.VIGENTE) {
                throw new ServiceException("Sólo se puede proponer la baja de un feriado VIGENTE. "
                        + "Estado actual: " + f.getEstado());
            }
            feriadoRepository.actualizarEstado(feriadoId, EstadoFeriado.BAJA_PENDIENTE, null, null);
        } catch (SQLException e) {
            throw new ServiceException("Error al proponer la baja del feriado", e);
        }
    }

    /** La Dirección autoriza una propuesta: alta pendiente pasa a VIGENTE;
     *  baja pendiente pasa a ANULADO. */
    public void autorizar(UUID feriadoId, Usuario actor) throws ServiceException {
        validarEsDireccion(actor);
        resolver(feriadoId, actor, true);
    }

    /** La Dirección rechaza una propuesta: alta pendiente pasa a RECHAZADO;
     *  baja pendiente vuelve a VIGENTE. */
    public void rechazar(UUID feriadoId, Usuario actor) throws ServiceException {
        validarEsDireccion(actor);
        resolver(feriadoId, actor, false);
    }

    // ============================================================
    // Consultas
    // ============================================================

    public List<Feriado> feriadosDelAnio(int anio) throws ServiceException {
        try {
            return feriadoRepository.findByAnio(anio);
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los feriados del año", e);
        }
    }

    /** Propuestas esperando resolución de la Dirección. */
    public List<Feriado> pendientes() throws ServiceException {
        try {
            return feriadoRepository.findPendientes();
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar las propuestas de feriados", e);
        }
    }

    /** Fechas que cuentan como feriado en el rango (para la diagramación). */
    public Set<LocalDate> fechasVigentes(LocalDate desde, LocalDate hasta) throws ServiceException {
        try {
            return feriadoRepository.findFechasVigentesEnRango(desde, hasta);
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los feriados vigentes", e);
        }
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    private void resolver(UUID feriadoId, Usuario actor, boolean autoriza) throws ServiceException {
        try {
            Feriado f = feriadoRepository.readByUUID(feriadoId);
            if (f == null) {
                throw new ServiceException("El feriado no existe.");
            }
            EstadoFeriado nuevo = switch (f.getEstado()) {
                case ALTA_PENDIENTE -> autoriza ? EstadoFeriado.VIGENTE : EstadoFeriado.RECHAZADO;
                case BAJA_PENDIENTE -> autoriza ? EstadoFeriado.ANULADO : EstadoFeriado.VIGENTE;
                default -> throw new ServiceException("El feriado no tiene una propuesta pendiente. "
                        + "Estado actual: " + f.getEstado());
            };
            feriadoRepository.actualizarEstado(feriadoId, nuevo, actor.getId(), LocalDateTime.now());
        } catch (SQLException e) {
            throw new ServiceException("Error al resolver la propuesta de feriado", e);
        }
    }

    private void validarEsOficinaDePersonal(Usuario actor) throws ServiceException {
        if (actor == null || actor.getId() == null
                || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal gestiona los feriados.");
        }
    }

    private void validarEsDireccion(Usuario actor) throws ServiceException {
        if (actor == null || actor.getId() == null
                || !actor.hasRole(TipoUsuario.DIRECCION)) {
            throw new ServiceException("Sólo la Dirección autoriza o rechaza cambios de feriados.");
        }
    }
}
