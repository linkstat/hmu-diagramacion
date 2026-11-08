package ar.com.hmu.service.diagramacion;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.DiagramaRepository;
import ar.com.hmu.repository.FeriadoRepository;
import ar.com.hmu.repository.HorarioRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.notification.NotificationService;
import ar.com.hmu.service.notification.TipoEventoDiagrama;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica de negocio del módulo de diagramación de servicios (RFS02).
 *
 * <p>Casos de uso (Fase 2):</p>
 * <ul>
 *   <li>Crear borrador: genera la grilla inicial aplicando el Horario de
 *       cada empleado del servicio (Strategy por modalidad).</li>
 *   <li>Guardar jornadas editadas (sólo BORRADOR/OBSERVADO).</li>
 *   <li>Validar (reglas estructurales + advertencias de carga mensual).</li>
 *   <li>Enviar a aprobación (bloquea si hay violaciones estructurales).</li>
 *   <li>Aprobar / observar (Oficina de Personal).</li>
 *   <li>Eliminar borrador.</li>
 *   <li>Consultas: diagramas por servicio, pendientes de aprobación,
 *       jornadas de un diagrama.</li>
 * </ul>
 *
 * <p>Las transiciones de estado se delegan a {@link DiagramaStateMachine};
 * las validaciones de jornadas a {@link DiagramaValidator}; las
 * notificaciones por email a un {@link NotificationService} inyectado.</p>
 *
 * <p><b>Permisos:</b> gestionan el diagrama (crear/editar/enviar/eliminar)
 * la jefatura del propio servicio, la Oficina de Personal y la Dirección.
 * Aprueban u observan sólo usuarios con rol Oficina de Personal
 * (RFS02: la jefatura genera, OP aprueba).</p>
 *
 * <p><b>Concurrencia:</b> el control optimista viene de la capa repository
 * ({@code version}); ante conflicto se lanza {@link ServiceException} y el
 * caller debe recargar el diagrama.</p>
 *
 * <p><b>Auditoría de observaciones:</b> a diferencia de los memos (una fila
 * de autorización por iteración), el diagrama conserva sólo la última
 * observación ({@code comentariosObservacion} se limpia al reenviar).
 * Suficiente para el pase actual; si se pidiera historial completo, se
 * modelaría una tabla de iteraciones.</p>
 */
public class DiagramaService {

    private final DiagramaRepository diagramaRepository;
    private final HorarioRepository horarioRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final FeriadoRepository feriadoRepository;
    private final NotificationService notificationService;
    /** Catálogo de reglas (CU08); null = valores predeterminados del motor. */
    private ar.com.hmu.service.ReglaValidacionService reglas;

    public DiagramaService(DiagramaRepository diagramaRepository,
                           HorarioRepository horarioRepository,
                           ServicioRepository servicioRepository,
                           UsuarioRepository usuarioRepository,
                           FeriadoRepository feriadoRepository,
                           NotificationService notificationService) {
        this.diagramaRepository = diagramaRepository;
        this.horarioRepository = horarioRepository;
        this.servicioRepository = servicioRepository;
        this.usuarioRepository = usuarioRepository;
        this.feriadoRepository = feriadoRepository;
        this.notificationService = notificationService;
    }

    /** Conecta el motor de validación con las reglas parametrizadas en la BD (CU08). */
    public void setReglas(ar.com.hmu.service.ReglaValidacionService reglas) {
        this.reglas = reglas;
    }

    /** Reglas vigentes a la fecha (predeterminadas si no hay catálogo). */
    public CatalogoReglas reglasVigentes(LocalDate fecha) {
        return reglas != null && fecha != null ? reglas.catalogo(fecha) : CatalogoReglas.PREDETERMINADO;
    }

    // ============================================================
    // Casos de uso
    // ============================================================

    /**
     * Crea un diagrama nuevo en BORRADOR para el servicio y rango dados,
     * generando la grilla inicial desde el Horario vigente de cada empleado
     * del servicio. Los empleados sin horario asignado reciben una grilla
     * toda FRANCO (editable a mano).
     *
     * @throws ServiceException si el actor no puede gestionar el servicio,
     *                          el rango es inválido o ya existe un diagrama
     *                          del servicio solapado con el período.
     */
    public DiagramaDeServicio crearBorrador(UUID servicioId, LocalDate desde, LocalDate hasta,
                                            Usuario creador) throws ServiceException {
        if (servicioId == null) {
            throw new ServiceException("Debe indicarse el servicio del diagrama.");
        }
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new ServiceException("Rango de fechas inválido: desde debe ser anterior o igual a hasta.");
        }
        validarPuedeGestionar(creador, servicioId);

        try {
            List<DiagramaDeServicio> solapados = diagramaRepository.findSolapados(servicioId, desde, hasta);
            if (!solapados.isEmpty()) {
                throw new ServiceException("Ya existe un diagrama del servicio que se solapa con el período "
                        + desde + " – " + hasta + " (estado: " + solapados.get(0).getEstado() + ").");
            }

            DiagramaDeServicio diagrama = new DiagramaDeServicio(UUID.randomUUID(), servicioId,
                    EstadoDiagrama.BORRADOR, desde, hasta, creador.getId());

            // Los feriados vigentes del rango cuentan como día no laborable
            // para los generadores de patrón semanal.
            ContextoDiagramacion ctx = new ContextoDiagramacion(
                    feriadoRepository.findFechasVigentesEnRango(desde, hasta));
            for (Usuario empleado : usuarioRepository.findUsuariosByServicio(servicioId)) {
                HorarioBase horario = horarioRepository.findHorarioActualDeEmpleado(empleado.getId());
                List<JornadaLaboral> jornadas = (horario != null)
                        ? GeneradorJornadasFactory.paraHorario(horario).generar(horario, desde, hasta, ctx)
                        : SoporteGeneracion.todoFranco(desde, hasta);
                GeneradorJornadas.estampar(jornadas, diagrama.getId(), empleado.getId());
                diagrama.getJornadas().addAll(jornadas);
            }

            diagramaRepository.create(diagrama);
            return diagrama;
        } catch (SQLException e) {
            throw new ServiceException("Error al crear el diagrama de servicio", e);
        }
    }

    /**
     * Guarda la grilla editada (reemplazo total de jornadas). Sólo válido
     * en BORRADOR u OBSERVADO. Actualiza {@code diagrama} en memoria
     * (versión y jornadas) si la operación se aplica.
     */
    public void guardarJornadas(DiagramaDeServicio diagrama, List<JornadaLaboral> jornadas,
                                Usuario actor) throws ServiceException {
        if (diagrama == null || diagrama.getId() == null) {
            throw new ServiceException("Diagrama inválido.");
        }
        if (jornadas == null) {
            throw new ServiceException("La lista de jornadas no puede ser null.");
        }
        validarPuedeGestionar(actor, diagrama.getServicioId());
        DiagramaStateMachine.validarEditar(diagrama.getEstado());

        for (JornadaLaboral j : jornadas) {
            if (j.getEmpleadoId() == null || j.getFecha() == null || j.getTipo() == null) {
                throw new ServiceException("Toda jornada debe tener empleado, fecha y tipo.");
            }
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            j.setDiagramaId(diagrama.getId());
        }

        try {
            boolean ok = diagramaRepository.reemplazarJornadas(diagrama.getId(), jornadas,
                    diagrama.getVersion());
            if (!ok) {
                throw new ServiceException("El diagrama fue modificado por otra sesión. "
                        + "Recargá y volvé a intentar.");
            }
            diagrama.setVersion(diagrama.getVersion() + 1);
            diagrama.setJornadas(jornadas);
        } catch (SQLException e) {
            throw new ServiceException("Error al guardar las jornadas del diagrama", e);
        }
    }

    /** Reglas estructurales sobre las jornadas persistidas del diagrama
     *  (para el botón "Validar" de la UI y como gate del envío). */
    public List<Violacion> validarJornadas(UUID diagramaId) throws ServiceException {
        try {
            List<JornadaLaboral> jornadas = diagramaRepository.findJornadasByDiagramaId(diagramaId);
            if (reglas == null) {
                return DiagramaValidator.validar(jornadas);
            }
            return DiagramaValidator.evaluar(jornadas, horariosDe(jornadas), reglasDelDiagrama(diagramaId),
                    dotacionDelDiagrama(diagramaId)).bloqueantes();
        } catch (SQLException e) {
            throw new ServiceException("Error al validar las jornadas del diagrama", e);
        }
    }

    /** Advertencias de carga horaria mensual (no bloquean el envío). */
    public List<Violacion> advertenciasCarga(UUID diagramaId) throws ServiceException {
        try {
            List<JornadaLaboral> jornadas = diagramaRepository.findJornadasByDiagramaId(diagramaId);
            Map<UUID, HorarioBase> horarios = horariosDe(jornadas);
            if (reglas == null) {
                return DiagramaValidator.advertenciasCargaMensual(jornadas, horarios);
            }
            return DiagramaValidator.evaluar(jornadas, horarios, reglasDelDiagrama(diagramaId),
                    dotacionDelDiagrama(diagramaId)).advertencias();
        } catch (SQLException e) {
            throw new ServiceException("Error al calcular las advertencias de carga del diagrama", e);
        }
    }

    private Map<UUID, HorarioBase> horariosDe(List<JornadaLaboral> jornadas) throws SQLException {
        Map<UUID, HorarioBase> horarios = new HashMap<>();
        for (JornadaLaboral j : jornadas) {
            UUID empleadoId = j.getEmpleadoId();
            if (empleadoId != null && !horarios.containsKey(empleadoId)) {
                HorarioBase h = horarioRepository.findHorarioActualDeEmpleado(empleadoId);
                if (h != null) {
                    horarios.put(empleadoId, h);
                }
            }
        }
        return horarios;
    }

    /** Dotación mínima del servicio del diagrama (CU13); 0 si no está definida. */
    private int dotacionDelDiagrama(UUID diagramaId) throws SQLException {
        DiagramaDeServicio d = diagramaRepository.readByUUID(diagramaId);
        return d != null ? servicioRepository.findDotacionMinima(d.getServicioId()) : 0;
    }

    /** Versión de las reglas vigente al inicio del período del diagrama (CU08 3a). */
    private CatalogoReglas reglasDelDiagrama(UUID diagramaId) throws SQLException {
        DiagramaDeServicio d = diagramaRepository.readByUUID(diagramaId);
        return reglasVigentes(d != null ? d.getFechaInicio() : ar.com.hmu.util.Reloj.hoy());
    }

    /**
     * Envía el diagrama a aprobación de la Oficina de Personal. Bloquea si
     * las jornadas tienen violaciones estructurales. Al reenviar tras una
     * observación, la observación anterior se limpia. Notifica a todos los
     * usuarios con rol Oficina de Personal.
     */
    public void enviarParaAprobacion(DiagramaDeServicio diagrama, Usuario actor) throws ServiceException {
        if (diagrama == null || diagrama.getId() == null) {
            throw new ServiceException("Diagrama inválido.");
        }
        validarPuedeGestionar(actor, diagrama.getServicioId());
        DiagramaStateMachine.validarEnviarParaAprobacion(diagrama.getEstado());

        List<Violacion> violaciones = validarJornadas(diagrama.getId());
        if (!violaciones.isEmpty()) {
            StringBuilder sb = new StringBuilder("El diagrama tiene " + violaciones.size()
                    + (violaciones.size() == 1 ? " violación" : " violaciones") + " y no puede enviarse:");
            violaciones.stream().limit(5).forEach(v -> sb.append("\n- ").append(v));
            if (violaciones.size() > 5) {
                sb.append("\n… y ").append(violaciones.size() - 5).append(" más.");
            }
            throw new ServiceException(sb.toString());
        }

        try {
            boolean ok = diagramaRepository.actualizarEstado(diagrama.getId(),
                    EstadoDiagrama.PENDIENTE_APROBACION, null, null, null, diagrama.getVersion());
            if (!ok) {
                throw new ServiceException("El diagrama fue modificado por otra sesión. "
                        + "Recargá y volvé a intentar.");
            }
            diagrama.setEstado(EstadoDiagrama.PENDIENTE_APROBACION);
            diagrama.setVersion(diagrama.getVersion() + 1);
            diagrama.setAprobadoPorId(null);
            diagrama.setFechaAprobacion(null);
            diagrama.setComentariosObservacion(null);

            String nombreServicio = nombreServicio(diagrama.getServicioId());
            for (Usuario op : usuarioRepository.readAll()) {
                if (op.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
                    notificationService.notify(op, TipoEventoDiagrama.DIAGRAMA_PENDIENTE_APROBACION,
                            diagrama, nombreServicio, null);
                }
            }
        } catch (SQLException e) {
            throw new ServiceException("Error al enviar el diagrama a aprobación", e);
        }
    }

    /**
     * Aprueba el diagrama (sólo Oficina de Personal). A partir de acá el
     * diagrama es inmutable. Notifica a la jefatura que lo creó.
     */
    public void aprobar(DiagramaDeServicio diagrama, Usuario aprobador) throws ServiceException {
        if (diagrama == null || diagrama.getId() == null) {
            throw new ServiceException("Diagrama inválido.");
        }
        validarEsOficinaDePersonal(aprobador);
        DiagramaStateMachine.validarAprobar(diagrama.getEstado());

        try {
            LocalDateTime ahora = ar.com.hmu.util.Reloj.ahora();
            boolean ok = diagramaRepository.actualizarEstado(diagrama.getId(),
                    EstadoDiagrama.APROBADO, aprobador.getId(), ahora, null, diagrama.getVersion());
            if (!ok) {
                throw new ServiceException("El diagrama fue modificado por otra sesión. "
                        + "Recargá y volvé a intentar.");
            }
            diagrama.setEstado(EstadoDiagrama.APROBADO);
            diagrama.setVersion(diagrama.getVersion() + 1);
            diagrama.setAprobadoPorId(aprobador.getId());
            diagrama.setFechaAprobacion(ahora);

            notificarAlCreador(diagrama, TipoEventoDiagrama.DIAGRAMA_APROBADO, null);
        } catch (SQLException e) {
            throw new ServiceException("Error al aprobar el diagrama", e);
        }
    }

    /**
     * Observa el diagrama con comentarios obligatorios (sólo Oficina de
     * Personal): vuelve a la jefatura para corrección. Notifica al creador
     * con los comentarios.
     */
    public void observar(DiagramaDeServicio diagrama, String comentarios, Usuario actor)
            throws ServiceException {
        if (diagrama == null || diagrama.getId() == null) {
            throw new ServiceException("Diagrama inválido.");
        }
        if (comentarios == null || comentarios.isBlank()) {
            throw new ServiceException("La observación requiere comentarios para la jefatura.");
        }
        validarEsOficinaDePersonal(actor);
        DiagramaStateMachine.validarObservar(diagrama.getEstado());

        try {
            LocalDateTime ahora = ar.com.hmu.util.Reloj.ahora();
            boolean ok = diagramaRepository.actualizarEstado(diagrama.getId(),
                    EstadoDiagrama.OBSERVADO, actor.getId(), ahora, comentarios, diagrama.getVersion());
            if (!ok) {
                throw new ServiceException("El diagrama fue modificado por otra sesión. "
                        + "Recargá y volvé a intentar.");
            }
            diagrama.setEstado(EstadoDiagrama.OBSERVADO);
            diagrama.setVersion(diagrama.getVersion() + 1);
            diagrama.setAprobadoPorId(actor.getId());
            diagrama.setFechaAprobacion(ahora);
            diagrama.setComentariosObservacion(comentarios);

            notificarAlCreador(diagrama, TipoEventoDiagrama.DIAGRAMA_OBSERVADO, comentarios);
        } catch (SQLException e) {
            throw new ServiceException("Error al observar el diagrama", e);
        }
    }

    /** Elimina un borrador (las jornadas caen en cascada). */
    public void eliminarBorrador(DiagramaDeServicio diagrama, Usuario actor) throws ServiceException {
        if (diagrama == null || diagrama.getId() == null) {
            throw new ServiceException("Diagrama inválido.");
        }
        validarPuedeGestionar(actor, diagrama.getServicioId());
        DiagramaStateMachine.validarEliminar(diagrama.getEstado());

        try {
            diagramaRepository.delete(diagrama);
        } catch (SQLException e) {
            throw new ServiceException("Error al eliminar el borrador del diagrama", e);
        }
    }

    // ============================================================
    // Consultas
    // ============================================================

    /** Diagramas de un servicio, del más reciente al más viejo. */
    public List<DiagramaDeServicio> diagramasDeServicio(UUID servicioId) throws ServiceException {
        try {
            return diagramaRepository.findByServicio(servicioId);
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los diagramas del servicio", e);
        }
    }

    /** Bandeja de OP: diagramas pendientes de aprobación de todos los servicios. */
    public List<DiagramaDeServicio> pendientesDeAprobacion() throws ServiceException {
        try {
            return diagramaRepository.findByEstado(EstadoDiagrama.PENDIENTE_APROBACION);
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los diagramas pendientes de aprobación", e);
        }
    }

    /** Consulta histórica: todos los diagramas de todos los servicios
     *  (el filtrado por estado/servicio lo hace la bandeja en memoria). */
    public List<DiagramaDeServicio> todos() throws ServiceException {
        try {
            return diagramaRepository.readAll();
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los diagramas", e);
        }
    }

    /** Jornadas persistidas de un diagrama (para poblar la grilla). */
    public List<JornadaLaboral> jornadasDe(UUID diagramaId) throws ServiceException {
        try {
            return diagramaRepository.findJornadasByDiagramaId(diagramaId);
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar las jornadas del diagrama", e);
        }
    }

    /**
     * Calendario del rango (feriados vigentes) para las plantillas de
     * precarga de la pantalla de diagramación.
     */
    public ContextoDiagramacion contextoPara(LocalDate desde, LocalDate hasta) throws ServiceException {
        try {
            return new ContextoDiagramacion(feriadoRepository.findFechasVigentesEnRango(desde, hasta));
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los feriados del período", e);
        }
    }

    // ============================================================
    // Alertas de presentación (RFS09)
    // ============================================================

    /**
     * Estado de presentación del diagrama de un servicio para un mes:
     * mira todos los diagramas que solapan el mes y devuelve el mejor
     * estado alcanzado (PRESENTADO > OBSERVADO > BORRADOR > nada).
     */
    public EstadoPresentacion estadoPresentacion(UUID servicioId, java.time.YearMonth mes)
            throws ServiceException {
        try {
            LocalDate desde = mes.atDay(1);
            LocalDate hasta = mes.atEndOfMonth();
            EstadoPresentacion resultado = EstadoPresentacion.SIN_DIAGRAMA;
            for (DiagramaDeServicio d : diagramaRepository.findSolapados(servicioId, desde, hasta)) {
                switch (d.getEstado()) {
                    case PENDIENTE_APROBACION, APROBADO -> {
                        return EstadoPresentacion.PRESENTADO;
                    }
                    case OBSERVADO -> resultado = EstadoPresentacion.OBSERVADO_SIN_CORREGIR;
                    case BORRADOR -> {
                        if (resultado == EstadoPresentacion.SIN_DIAGRAMA) {
                            resultado = EstadoPresentacion.BORRADOR_SIN_ENVIAR;
                        }
                    }
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar el estado de presentación del diagrama", e);
        }
    }

    /**
     * Servicios con empleados activos cuyo diagrama del mes indicado NO
     * está presentado (para el resumen de OP al login). Los servicios sin
     * empleados no requieren diagrama y se saltean.
     */
    public List<AlertaPresentacion> serviciosSinPresentar(java.time.YearMonth mes)
            throws ServiceException {
        try {
            Map<UUID, Integer> empleadosPorServicio = usuarioRepository.countUsuariosActivosPorServicio();
            List<AlertaPresentacion> alertas = new java.util.ArrayList<>();
            for (Servicio s : servicioRepository.readAll()) {
                if (empleadosPorServicio.getOrDefault(s.getId(), 0) == 0) {
                    continue;
                }
                EstadoPresentacion estado = estadoPresentacion(s.getId(), mes);
                if (estado != EstadoPresentacion.PRESENTADO) {
                    alertas.add(new AlertaPresentacion(s, estado));
                }
            }
            alertas.sort(java.util.Comparator.comparing(a -> a.servicio().getNombre(),
                    String.CASE_INSENSITIVE_ORDER));
            return alertas;
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar los servicios sin diagrama presentado", e);
        }
    }

    // ============================================================
    // Permiso de vista del diagrama por empleado
    // ============================================================

    /**
     * ¿El empleado ve la grilla completa de su servicio en "Mi Diagrama"
     * (true, la "cartelera") o sólo sus propias jornadas (false)?
     */
    public boolean puedeVerContexto(UUID empleadoId) throws ServiceException {
        try {
            return usuarioRepository.findVeDiagramaCompleto(empleadoId);
        } catch (SQLException e) {
            throw new ServiceException("Error al consultar el permiso de vista del diagrama", e);
        }
    }

    /**
     * Setea el permiso de vista del diagrama de un empleado. Pueden hacerlo
     * la jefatura del servicio del empleado, OP y Dirección.
     */
    public void setPermisoVistaCompleta(UUID empleadoId, boolean valor, Usuario actor)
            throws ServiceException {
        if (empleadoId == null) {
            throw new ServiceException("Empleado inválido.");
        }
        try {
            Usuario empleado = usuarioRepository.readByUUID(empleadoId);
            if (empleado == null) {
                throw new ServiceException("El empleado no existe.");
            }
            validarPuedeGestionar(actor, empleado.getServicioId());
            usuarioRepository.setVeDiagramaCompleto(empleadoId, valor);
        } catch (SQLException e) {
            throw new ServiceException("Error al actualizar el permiso de vista del diagrama", e);
        }
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    /** Gestionan el diagrama: OP y Dirección siempre; la jefatura sólo la
     *  de su propio servicio. */
    private void validarPuedeGestionar(Usuario actor, UUID servicioId) throws ServiceException {
        if (actor == null || actor.getId() == null) {
            throw new ServiceException("Usuario inválido.");
        }
        if (actor.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION)) {
            return;
        }
        boolean esJefeDelServicio = actor.hasRole(TipoUsuario.JEFATURADESERVICIO)
                && servicioId != null && servicioId.equals(actor.getServicioId());
        if (!esJefeDelServicio) {
            throw new ServiceException("No tenés permisos para gestionar el diagrama de este servicio.");
        }
    }

    private void validarEsOficinaDePersonal(Usuario actor) throws ServiceException {
        if (actor == null || actor.getId() == null) {
            throw new ServiceException("Usuario inválido.");
        }
        if (!actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal puede aprobar u observar diagramas.");
        }
    }

    private void notificarAlCreador(DiagramaDeServicio diagrama, TipoEventoDiagrama evento,
                                    String comentarios) throws SQLException {
        if (diagrama.getCreadoPorId() == null) {
            return;
        }
        Usuario creador = usuarioRepository.readByUUID(diagrama.getCreadoPorId());
        if (creador != null) {
            notificationService.notify(creador, evento, diagrama,
                    nombreServicio(diagrama.getServicioId()), comentarios);
        }
    }

    private String nombreServicio(UUID servicioId) {
        if (servicioId == null) {
            return null;
        }
        try {
            Servicio s = servicioRepository.readByUUID(servicioId);
            return (s != null) ? s.getNombre() : null;
        } catch (SQLException e) {
            return null; // el nombre es cosmético en la notificación: no propaga
        }
    }
}
