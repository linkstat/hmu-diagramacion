package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoMemorandumAutorizacion;
import ar.com.hmu.model.EstadoTramite;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.MemorandumAutorizacion;
import ar.com.hmu.model.MemorandumDestinatario;
import ar.com.hmu.model.TipoRolMemoAutorizacion;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.EstadoTramiteRepository;
import ar.com.hmu.repository.MemorandumRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.notification.NotificationService;
import ar.com.hmu.service.notification.TipoEventoMemorandum;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de negocio del módulo de memorándums.
 *
 * <p>Casos de uso (pase 1):</p>
 * <ul>
 *   <li>Redactar/guardar borrador.</li>
 *   <li>Enviar (con o sin autorización previa según rol del remitente y destinatarios).</li>
 *   <li>Autorizar / rechazar / observar (encargado del servicio del remitente).</li>
 *   <li>Reenviar tras observación (nueva fila de autorización, audit trail).</li>
 *   <li>Marcar como leído.</li>
 *   <li>Eliminar borrador.</li>
 *   <li>Bandejas: entrada / salida / pendientes de autorizar.</li>
 *   <li>Contador de no leídos.</li>
 *   <li>Lista de destinatarios válidos según rol del remitente.</li>
 * </ul>
 *
 * <p>Las transiciones de estado se delegan a {@link MemorandumStateMachine}.
 * Las notificaciones por email se delegan a un {@link NotificationService}
 * inyectado por constructor.</p>
 */
public class MemorandumService {

    private static final int CONTENIDO_MAX = 8000;

    private final MemorandumRepository memorandumRepository;
    private final EstadoTramiteRepository estadoTramiteRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificationService notificationService;

    public MemorandumService(MemorandumRepository memorandumRepository,
                             EstadoTramiteRepository estadoTramiteRepository,
                             ServicioRepository servicioRepository,
                             UsuarioRepository usuarioRepository,
                             NotificationService notificationService) {
        this.memorandumRepository = memorandumRepository;
        this.estadoTramiteRepository = estadoTramiteRepository;
        this.servicioRepository = servicioRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificationService = notificationService;
    }

    // ============================================================
    // Casos de uso
    // ============================================================

    /**
     * Crea un memorándum nuevo en estado BORRADOR. Valida campos mínimos
     * (asunto y contenido no vacíos, contenido bajo límite, al menos un
     * destinatario). El memo retornado tiene id asignado y estadoTramiteId
     * apuntando al UUID de "BORRADOR".
     */
    public Memorandum guardarBorrador(Memorandum borrador, Usuario remitente) throws ServiceException {
        if (borrador == null) {
            throw new ServiceException("El memorándum no puede ser null.");
        }
        if (remitente == null || remitente.getId() == null) {
            throw new ServiceException("Remitente inválido.");
        }
        if (borrador.getAsunto() == null || borrador.getAsunto().isBlank()) {
            throw new ServiceException("El asunto del memorándum no puede estar vacío.");
        }
        if (borrador.getContenido() == null || borrador.getContenido().isBlank()) {
            throw new ServiceException("El contenido del memorándum no puede estar vacío.");
        }
        if (borrador.getContenido().length() > CONTENIDO_MAX) {
            throw new ServiceException("El contenido excede el máximo permitido (" + CONTENIDO_MAX + " caracteres).");
        }
        // Borradores admiten lista de destinatarios vacía: la validación de
        // "al menos uno" se aplica recién al enviar.

        try {
            if (borrador.getId() == null) {
                borrador.setId(UUID.randomUUID());
            }
            borrador.setRemitenteId(remitente.getId());
            borrador.setEstadoTramiteId(estadoTramiteRepository.getId(EstadoTramite.BORRADOR));

            if (borrador.getDestinatarios() == null) {
                borrador.setDestinatarios(new java.util.ArrayList<>());
            }
            // Cada destinatario apunta al memo recién generado.
            for (MemorandumDestinatario d : borrador.getDestinatarios()) {
                d.setMemorandumId(borrador.getId());
            }
            // Las autorizaciones se generan en enviar(); en BORRADOR no aplican.
            borrador.getAutorizaciones().clear();

            memorandumRepository.create(borrador);
            return borrador;
        } catch (SQLException e) {
            throw new ServiceException("Error al guardar el borrador del memorándum", e);
        }
    }

    /**
     * Actualiza un borrador existente del propio remitente. Sólo permite
     * cambiar asunto, contenido y destinatarios. El estado debe seguir
     * siendo BORRADOR; cualquier otra cosa lanza ServiceException.
     */
    public Memorandum actualizarBorrador(Memorandum memo, Usuario remitente) throws ServiceException {
        if (memo == null || memo.getId() == null) {
            throw new ServiceException("Borrador inválido (sin id).");
        }
        if (remitente == null || remitente.getId() == null) {
            throw new ServiceException("Remitente inválido.");
        }
        if (memo.getAsunto() == null || memo.getAsunto().isBlank()) {
            throw new ServiceException("El asunto del memorándum no puede estar vacío.");
        }
        if (memo.getContenido() == null || memo.getContenido().isBlank()) {
            throw new ServiceException("El contenido del memorándum no puede estar vacío.");
        }
        if (memo.getContenido().length() > CONTENIDO_MAX) {
            throw new ServiceException("El contenido excede el máximo permitido (" + CONTENIDO_MAX + " caracteres).");
        }
        // Borradores admiten destinatarios vacíos en pase 1.
        if (memo.getDestinatarios() == null) {
            memo.setDestinatarios(new java.util.ArrayList<>());
        }

        try {
            Memorandum existente = memorandumRepository.readByUUID(memo.getId());
            if (existente == null) {
                throw new ServiceException("El borrador no existe.");
            }
            if (!remitente.getId().equals(existente.getRemitenteId())) {
                throw new ServiceException("Sólo el remitente puede modificar el borrador.");
            }
            EstadoTramite estadoActual = resolverEstado(existente);
            if (estadoActual != EstadoTramite.BORRADOR) {
                throw new ServiceException("Sólo se pueden modificar memos en estado BORRADOR.");
            }

            // Asegurar que cada destinatario apunta al memo correcto.
            for (MemorandumDestinatario d : memo.getDestinatarios()) {
                d.setMemorandumId(memo.getId());
            }

            memorandumRepository.actualizarBorrador(memo);
            return memo;
        } catch (SQLException e) {
            throw new ServiceException("Error al actualizar el borrador", e);
        }
    }

    /**
     * Envía el memorándum: transiciona a ENVIADO si no requiere autorización,
     * o a PENDIENTE_DE_AUTORIZACION si la requiere. Notifica a destinatarios,
     * remitente y eventual encargado autorizador.
     */
    public Memorandum enviar(UUID memoId, Usuario remitente) throws ServiceException {
        if (memoId == null) {
            throw new ServiceException("ID del memorándum requerido.");
        }
        if (remitente == null || remitente.getId() == null) {
            throw new ServiceException("Remitente inválido.");
        }

        try {
            Memorandum memo = memorandumRepository.readByUUID(memoId);
            if (memo == null) {
                throw new ServiceException("El memorándum no existe.");
            }
            if (!remitente.getId().equals(memo.getRemitenteId())) {
                throw new ServiceException("Sólo el remitente puede enviar el memorándum.");
            }

            // Para enviar es obligatorio tener al menos 1 destinatario.
            List<MemorandumDestinatario> destsPersistidos =
                    memorandumRepository.findDestinatariosByMemoId(memoId);
            if (destsPersistidos.isEmpty()) {
                throw new ServiceException("El memorándum debe tener al menos un destinatario para ser enviado.");
            }

            EstadoTramite estadoActual = resolverEstado(memo);
            boolean requiereAuth = requiereAutorizacion(remitente, memo);

            if (requiereAuth) {
                MemorandumStateMachine.validarEnviarConAutorizacion(estadoActual);
                transicionarA(memo, EstadoTramite.PENDIENTE_DE_AUTORIZACION);
                memo.setFechaEnvio(LocalDateTime.now());
                memorandumRepository.actualizarFechaEnvio(memo.getId(), memo.getFechaEnvio());

                // Crea la fila de autorización pendiente para el encargado del
                // servicio del remitente.
                MemorandumAutorizacion auth = new MemorandumAutorizacion(
                        UUID.randomUUID(), memo.getId(),
                        TipoRolMemoAutorizacion.JEFATURADESERVICIO,
                        EstadoMemorandumAutorizacion.PENDIENTE);
                memorandumRepository.agregarAutorizacion(auth);
                memo.agregarAutorizacion(auth);

                notificarAutorizadorRequerido(remitente, memo);
            } else {
                MemorandumStateMachine.validarEnviarSinAutorizacion(estadoActual);
                transicionarA(memo, EstadoTramite.ENVIADO);
                memo.setFechaEnvio(LocalDateTime.now());
                memorandumRepository.actualizarFechaEnvio(memo.getId(), memo.getFechaEnvio());

                notificarDestinatarios(memo);
            }

            // Confirmación al remitente en cualquier caso.
            notificationService.notify(remitente, TipoEventoMemorandum.MEMO_ENVIADO_CONFIRMACION, memo, null);

            return memo;
        } catch (SQLException e) {
            throw new ServiceException("Error al enviar el memorándum", e);
        }
    }

    /**
     * Autoriza el memorándum. Sólo el encargado del servicio del remitente
     * puede hacerlo. Transiciona a ENVIADO y notifica a destinatarios y
     * remitente.
     */
    public Memorandum autorizar(UUID memoId, Usuario autorizador) throws ServiceException {
        return resolverAutorizacionConTransicion(memoId, autorizador, EstadoMemorandumAutorizacion.AUTORIZADO,
                EstadoTramite.ENVIADO, null, TipoEventoMemorandum.MEMO_AUTORIZADO);
    }

    /**
     * Rechaza el memorándum. Comentarios opcionales pero recomendados.
     * Transiciona a RECHAZADO (terminal). Notifica al remitente.
     */
    public Memorandum rechazar(UUID memoId, Usuario autorizador, String motivo) throws ServiceException {
        return resolverAutorizacionConTransicion(memoId, autorizador, EstadoMemorandumAutorizacion.RECHAZADO,
                EstadoTramite.RECHAZADO, motivo, TipoEventoMemorandum.MEMO_RECHAZADO);
    }

    /**
     * Observa el memorándum: solicita correcciones al remitente sin aprobar
     * ni rechazar. Transiciona a OBSERVADO. Notifica al remitente con los
     * comentarios.
     */
    public Memorandum observar(UUID memoId, Usuario autorizador, String comentarios) throws ServiceException {
        if (comentarios == null || comentarios.isBlank()) {
            throw new ServiceException("Las observaciones requieren comentarios para que el remitente sepa qué corregir.");
        }
        return resolverAutorizacionConTransicion(memoId, autorizador, EstadoMemorandumAutorizacion.OBSERVADO,
                EstadoTramite.OBSERVADO, comentarios, TipoEventoMemorandum.MEMO_OBSERVADO);
    }

    /**
     * Reenvía un memorándum observado tras correcciones. Crea una NUEVA fila
     * en Memorandum_Autorizacion (la vieja queda OBSERVADO como audit trail)
     * y transiciona el memo a PENDIENTE_DE_AUTORIZACION.
     */
    public Memorandum reenviarPostObservacion(UUID memoId, Usuario remitente) throws ServiceException {
        if (memoId == null || remitente == null) {
            throw new ServiceException("Parámetros inválidos.");
        }
        try {
            Memorandum memo = memorandumRepository.readByUUID(memoId);
            if (memo == null) {
                throw new ServiceException("El memorándum no existe.");
            }
            if (!remitente.getId().equals(memo.getRemitenteId())) {
                throw new ServiceException("Sólo el remitente puede reenviar un memo observado.");
            }

            EstadoTramite estadoActual = resolverEstado(memo);
            MemorandumStateMachine.validarReenviar(estadoActual);

            // Nueva fila pendiente.
            MemorandumAutorizacion auth = new MemorandumAutorizacion(
                    UUID.randomUUID(), memo.getId(),
                    TipoRolMemoAutorizacion.JEFATURADESERVICIO,
                    EstadoMemorandumAutorizacion.PENDIENTE);
            memorandumRepository.agregarAutorizacion(auth);
            memo.agregarAutorizacion(auth);

            transicionarA(memo, EstadoTramite.PENDIENTE_DE_AUTORIZACION);
            notificarAutorizadorRequerido(remitente, memo);

            return memo;
        } catch (SQLException e) {
            throw new ServiceException("Error al reenviar el memorándum observado", e);
        }
    }

    /**
     * Marca el memo como leído por el destinatario indicado. Si todos los
     * destinatarios ya leyeron, el memo transiciona a LEIDO.
     */
    public void marcarLeido(UUID memoId, Usuario destinatario) throws ServiceException {
        if (memoId == null || destinatario == null) {
            throw new ServiceException("Parámetros inválidos.");
        }
        try {
            Memorandum memo = memorandumRepository.readByUUID(memoId);
            if (memo == null) {
                throw new ServiceException("El memorándum no existe.");
            }

            EstadoTramite estadoActual = resolverEstado(memo);
            MemorandumStateMachine.validarMarcarLeido(estadoActual);

            memorandumRepository.marcarLeido(memoId, destinatario.getId(), LocalDateTime.now());

            // Si el memo estaba en ENVIADO y todos leyeron, pasa a LEIDO.
            if (estadoActual == EstadoTramite.ENVIADO) {
                List<MemorandumDestinatario> dests = memorandumRepository.findDestinatariosByMemoId(memoId);
                boolean todosLeyeron = !dests.isEmpty() && dests.stream().allMatch(MemorandumDestinatario::estaLeido);
                if (todosLeyeron) {
                    transicionarA(memo, EstadoTramite.LEIDO);
                }
            }
        } catch (SQLException e) {
            throw new ServiceException("Error al marcar el memorándum como leído", e);
        }
    }

    /**
     * Elimina un memorándum en estado BORRADOR del propio remitente.
     * Físico: borra Memorandum y filas hijas.
     */
    public void eliminarBorrador(UUID memoId, Usuario remitente) throws ServiceException {
        if (memoId == null || remitente == null) {
            throw new ServiceException("Parámetros inválidos.");
        }
        try {
            Memorandum memo = memorandumRepository.readByUUID(memoId);
            if (memo == null) {
                throw new ServiceException("El memorándum no existe.");
            }
            if (!remitente.getId().equals(memo.getRemitenteId())) {
                throw new ServiceException("Sólo el remitente puede eliminar el borrador.");
            }
            EstadoTramite estadoActual = resolverEstado(memo);
            MemorandumStateMachine.validarEliminarBorrador(estadoActual);

            memorandumRepository.delete(memo);
        } catch (SQLException e) {
            throw new ServiceException("Error al eliminar el borrador", e);
        }
    }

    public List<Memorandum> bandejaEntrada(Usuario usuario) throws ServiceException {
        try {
            return memorandumRepository.findRecibidosPorDestinatario(usuario.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener la bandeja de entrada", e);
        }
    }

    public List<Memorandum> bandejaSalida(Usuario usuario) throws ServiceException {
        try {
            return memorandumRepository.findEnviadosPorRemitente(usuario.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener la bandeja de salida", e);
        }
    }

    public List<Memorandum> pendientesDeAutorizar(Usuario usuario) throws ServiceException {
        try {
            return memorandumRepository.findPendientesAutorizacionPor(usuario.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener los memorándums pendientes de autorizar", e);
        }
    }

    public int contarNoLeidos(Usuario usuario) throws ServiceException {
        try {
            return memorandumRepository.countNoLeidos(usuario.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al contar memorándums no leídos", e);
        }
    }

    /**
     * Devuelve el memo con sus listas de destinatarios y autorizaciones
     * pobladas. Útil para la pantalla de detalle.
     */
    public Memorandum findDetalleCompleto(UUID memoId) throws ServiceException {
        try {
            Memorandum memo = memorandumRepository.readByUUID(memoId);
            if (memo == null) return null;
            memo.setDestinatarios(memorandumRepository.findDestinatariosByMemoId(memoId));
            memo.setAutorizaciones(memorandumRepository.findAutorizacionesByMemoId(memoId));
            return memo;
        } catch (SQLException e) {
            throw new ServiceException("Error al cargar el detalle del memorándum", e);
        }
    }

    /**
     * Indica si el usuario tiene permiso para resolver la autorización
     * pendiente del memo (autorizar/rechazar/observar). Es decir, si es el
     * encargado actual del servicio del remitente del memo.
     */
    public boolean puedeResolverAutorizacion(Memorandum memo, Usuario usuario) throws ServiceException {
        if (memo == null || usuario == null) return false;
        try {
            Usuario remitente = usuarioRepository.readByUUID(memo.getRemitenteId());
            if (remitente == null || remitente.getServicioId() == null) return false;
            UUID encargadoId = servicioRepository.findEncargadoByServicio(remitente.getServicioId());
            return encargadoId != null && encargadoId.equals(usuario.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al verificar permisos de autorización", e);
        }
    }

    /**
     * Devuelve los usuarios a los que el remitente puede enviar un memo
     * según las reglas de destinatarios:
     * <ul>
     *   <li>EMPLEADO: jefe propio + todos los OP + todos los DIRECCION.</li>
     *   <li>JEFATURA: empleados del propio servicio + otros jefes + OP + DIRECCION.</li>
     *   <li>OP / DIRECCION: cualquier usuario activo.</li>
     * </ul>
     */
    public List<Usuario> destinatariosValidosPara(Usuario remitente) throws ServiceException {
        try {
            List<Usuario> todos = usuarioRepository.readAll();
            List<Usuario> resultado = new ArrayList<>();
            UUID servicioRemitente = remitente.getServicioId();

            boolean esEmpleadoSimple = remitente.hasRole(TipoUsuario.EMPLEADO)
                    && !remitente.hasRole(TipoUsuario.JEFATURADESERVICIO,
                                           TipoUsuario.OFICINADEPERSONAL,
                                           TipoUsuario.DIRECCION);
            boolean esJefe = remitente.hasRole(TipoUsuario.JEFATURADESERVICIO)
                    && !remitente.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION);
            boolean esOpODireccion = remitente.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION);

            for (Usuario u : todos) {
                if (u.getId().equals(remitente.getId())) {
                    continue; // No autoenvío.
                }
                if (esOpODireccion) {
                    resultado.add(u);
                } else if (esJefe) {
                    boolean mismoServicio = servicioRemitente != null && servicioRemitente.equals(u.getServicioId());
                    if (mismoServicio
                            || u.hasRole(TipoUsuario.JEFATURADESERVICIO,
                                          TipoUsuario.OFICINADEPERSONAL,
                                          TipoUsuario.DIRECCION)) {
                        resultado.add(u);
                    }
                } else if (esEmpleadoSimple) {
                    boolean esJefeDelMismoServicio = u.hasRole(TipoUsuario.JEFATURADESERVICIO)
                            && servicioRemitente != null && servicioRemitente.equals(u.getServicioId());
                    boolean esOpODir = u.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION);
                    if (esJefeDelMismoServicio || esOpODir) {
                        resultado.add(u);
                    }
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new ServiceException("Error al obtener destinatarios válidos", e);
        }
    }

    // ============================================================
    // Helpers internos
    // ============================================================

    /**
     * Determina si el envío de este memo requiere autorización del encargado
     * del servicio del remitente. Regla:
     *  - Si remitente es EMPLEADO simple y al menos un destinatario está
     *    fuera de su propia jefatura → requiere autorización.
     *  - Resto → no.
     */
    private boolean requiereAutorizacion(Usuario remitente, Memorandum memo) throws SQLException, ServiceException {
        boolean esEmpleadoSimple = remitente.hasRole(TipoUsuario.EMPLEADO)
                && !remitente.hasRole(TipoUsuario.JEFATURADESERVICIO,
                                       TipoUsuario.OFICINADEPERSONAL,
                                       TipoUsuario.DIRECCION);
        if (!esEmpleadoSimple) {
            return false;
        }

        UUID servicioRemitente = remitente.getServicioId();
        // Resolver destinatarios reales (lista que viene en el memo o la
        // persistida). Para enviar() ya están persistidos: leer de BD.
        List<MemorandumDestinatario> dests = memorandumRepository.findDestinatariosByMemoId(memo.getId());
        for (MemorandumDestinatario d : dests) {
            Usuario u = usuarioRepository.readByUUID(d.getUsuarioId());
            if (u == null) continue;
            boolean esJefeMismoServicio = u.hasRole(TipoUsuario.JEFATURADESERVICIO)
                    && servicioRemitente != null && servicioRemitente.equals(u.getServicioId());
            if (!esJefeMismoServicio) {
                return true; // hay al menos un destinatario fuera de la jefatura propia
            }
        }
        return false;
    }

    private Memorandum resolverAutorizacionConTransicion(UUID memoId, Usuario autorizador,
                                                          EstadoMemorandumAutorizacion estadoAuth,
                                                          EstadoTramite estadoMemo,
                                                          String comentarios,
                                                          TipoEventoMemorandum eventoRemitente) throws ServiceException {
        if (memoId == null || autorizador == null) {
            throw new ServiceException("Parámetros inválidos.");
        }
        try {
            Memorandum memo = memorandumRepository.readByUUID(memoId);
            if (memo == null) {
                throw new ServiceException("El memorándum no existe.");
            }
            EstadoTramite estadoActual = resolverEstado(memo);

            // Validar transición de estado del memo.
            switch (estadoAuth) {
                case AUTORIZADO -> MemorandumStateMachine.validarAutorizar(estadoActual);
                case RECHAZADO -> MemorandumStateMachine.validarRechazar(estadoActual);
                case OBSERVADO -> MemorandumStateMachine.validarObservar(estadoActual);
                default -> throw new ServiceException("Transición no soportada para autorización: " + estadoAuth);
            }

            // Validar permisos del autorizador: debe ser el encargado del
            // servicio del remitente.
            Usuario remitente = usuarioRepository.readByUUID(memo.getRemitenteId());
            if (remitente == null) {
                throw new ServiceException("Remitente del memorándum no encontrado.");
            }
            UUID encargadoId = servicioRepository.findEncargadoByServicio(remitente.getServicioId());
            if (encargadoId == null) {
                throw new ServiceException("El servicio del remitente no tiene un encargado actual definido.");
            }
            if (!encargadoId.equals(autorizador.getId())) {
                throw new ServiceException("Sólo el encargado del servicio del remitente puede autorizar este memorándum.");
            }

            // Resolver la fila pendiente.
            List<MemorandumAutorizacion> auths = memorandumRepository.findAutorizacionesByMemoId(memoId);
            MemorandumAutorizacion pendiente = auths.stream()
                    .filter(a -> a.getEstado() == EstadoMemorandumAutorizacion.PENDIENTE)
                    .findFirst()
                    .orElseThrow(() -> new ServiceException("No hay autorización pendiente para este memorándum."));

            memorandumRepository.resolverAutorizacion(pendiente.getId(), estadoAuth, autorizador.getId(),
                    LocalDateTime.now(), comentarios);

            transicionarA(memo, estadoMemo);

            // Si fue autorizado, ahora notificamos a los destinatarios reales.
            if (estadoAuth == EstadoMemorandumAutorizacion.AUTORIZADO) {
                notificarDestinatarios(memo);
            }
            // Notificación al remitente (autorizado / rechazado / observado).
            notificationService.notify(remitente, eventoRemitente, memo, comentarios);

            return memo;
        } catch (SQLException e) {
            throw new ServiceException("Error al resolver la autorización del memorándum", e);
        }
    }

    private EstadoTramite resolverEstado(Memorandum memo) throws SQLException, ServiceException {
        EstadoTramite estado = estadoTramiteRepository.getEstadoTramite(memo.getEstadoTramiteId());
        if (estado == null) {
            throw new ServiceException("Estado de trámite del memorándum no reconocido.");
        }
        return estado;
    }

    private void transicionarA(Memorandum memo, EstadoTramite nuevo) throws SQLException {
        UUID nuevoEstadoId = estadoTramiteRepository.getId(nuevo);
        memorandumRepository.actualizarEstadoTramite(memo.getId(), nuevoEstadoId);
        memo.setEstadoTramiteId(nuevoEstadoId);
    }

    private void notificarDestinatarios(Memorandum memo) throws SQLException {
        List<MemorandumDestinatario> dests = memorandumRepository.findDestinatariosByMemoId(memo.getId());
        for (MemorandumDestinatario d : dests) {
            Usuario u = usuarioRepository.readByUUID(d.getUsuarioId());
            if (u != null) {
                notificationService.notify(u, TipoEventoMemorandum.MEMO_RECIBIDO, memo, null);
            }
        }
    }

    private void notificarAutorizadorRequerido(Usuario remitente, Memorandum memo) throws SQLException {
        UUID servicioId = remitente.getServicioId();
        if (servicioId == null) return;
        UUID encargadoId = servicioRepository.findEncargadoByServicio(servicioId);
        if (encargadoId == null) return;
        Usuario encargado = usuarioRepository.readByUUID(encargadoId);
        if (encargado != null) {
            notificationService.notify(encargado, TipoEventoMemorandum.AUTORIZACION_REQUERIDA, memo, null);
        }
    }
}
