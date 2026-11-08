package ar.com.hmu.service.novedades;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.EstadoNovedadAutorizacion;
import ar.com.hmu.model.InstanciaNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.NovedadAdjunto;
import ar.com.hmu.model.NovedadAutorizacion;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.DiagramaRepository;
import ar.com.hmu.repository.NovedadRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.notification.NotificacionService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lógica de negocio de las novedades (CU20–CU28).
 *
 * <ul>
 *   <li>CU20 Solicitar novedad: borrador y envío con sus instancias pendientes.</li>
 *   <li>CU21 Validar novedad: {@link ValidadorNovedad}.</li>
 *   <li>CU22–CU24 Autorizar (JS, o DH si el agente es JS), verificar (OP) y
 *       aprobar (DH, también por lotes).</li>
 *   <li>CU25 Devolver para corrección (observar) y rechazar, con fundamento
 *       obligatorio.</li>
 *   <li>CU26 Aplicar la novedad aprobada: {@link AplicadorNovedad}.</li>
 *   <li>CU27 Notificar resultado: aviso en la aplicación + correo.</li>
 *   <li>CU28 Consultar trazabilidad: {@link #detalle} con todas las instancias.</li>
 * </ul>
 *
 * <p>Separación de funciones (RNF01): nadie resuelve una instancia de una
 * novedad en la que participa. Concurrencia optimista por {@code version}.</p>
 */
public class NovedadService {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NovedadRepository novedadRepository;
    private final DiagramaRepository diagramaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final NotificacionService notificaciones;
    private final ValidadorNovedad validador;
    private final AplicadorNovedad aplicador;
    /** Para leer las reglas vigentes (CU08); opcional. */
    private ar.com.hmu.service.diagramacion.DiagramaService diagramaService;
    /**
     * Acredita las horas extras de una novedad recién aplicada si su jornada ya
     * tiene marcaciones (lo provee el parte diario); opcional.
     */
    private java.util.function.BiConsumer<Novedad, Usuario> acreditadorHorasExtra;
    private final BancoHoras banco;

    /** Estados en que una devolución cuenta como hecha o en camino. */
    static final Set<EstadoNovedad> DEVOLUCION_VIGENTE = java.util.EnumSet.of(EstadoNovedad.PENDIENTE_JEFATURA,
            EstadoNovedad.PENDIENTE_OFICINA_PERSONAL, EstadoNovedad.PENDIENTE_DIRECCION, EstadoNovedad.APROBADA,
            EstadoNovedad.APLICADA);

    /** Pase de salida con horas pendientes de devolución (formulario de devolución). */
    public record PaseADevolver(Novedad pase, BigDecimal horasPendientes) {
        @Override
        public String toString() {
            String hora = pase.getHoraDesde() != null ? " " + pase.getHoraDesde() : "";
            return (pase.getTipo().es(TipoNovedad.PASE_SIN_RETORNO) ? "PSSR " : "Pase con retorno ")
                    + pase.getFechaDesde().format(DDMMYYYY) + hora + " · pendientes "
                    + BancoHoras.fmt(horasPendientes) + " h";
        }
    }

    public NovedadService(NovedadRepository novedadRepository, DiagramaRepository diagramaRepository,
                          UsuarioRepository usuarioRepository, ServicioRepository servicioRepository,
                          NotificacionService notificaciones) {
        this.novedadRepository = novedadRepository;
        this.diagramaRepository = diagramaRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicioRepository = servicioRepository;
        this.notificaciones = notificaciones;
        this.validador = new ValidadorNovedad(new FuenteDatosRepositorios());
        this.aplicador = new AplicadorNovedad(novedadRepository);
        this.banco = new BancoHoras(novedadRepository);
    }

    public void setAcreditadorHorasExtra(java.util.function.BiConsumer<Novedad, Usuario> acreditador) {
        this.acreditadorHorasExtra = acreditador;
    }

    public void setDiagramaService(ar.com.hmu.service.diagramacion.DiagramaService diagramaService) {
        this.diagramaService = diagramaService;
    }

    // ============================================================
    // Consultas
    // ============================================================

    /** Tipos de licencia de origen externo: códigos del catálogo marcados como licencia (CU29). */
    public List<ar.com.hmu.model.CodigoNovedad> tiposDeLicencia() throws ServiceException {
        try {
            return novedadRepository.findCodigos().stream().filter(ar.com.hmu.model.CodigoNovedad::esLicencia).toList();
        } catch (SQLException e) {
            throw traducir("Error al leer los tipos de licencia", e);
        }
    }

    public List<TipoNovedad> tiposSolicitables() throws ServiceException {
        try {
            return novedadRepository.findTiposActivos().stream().filter(t -> !t.esExterna()).toList();
        } catch (SQLException e) {
            throw traducir("Error al leer los tipos de novedad", e);
        }
    }

    /**
     * Bandeja según el perfil (RF03): el agente ve las suyas, la JS las de su
     * servicio, la OP y la DH todo el hospital. Los borradores son privados
     * de quien los cargó.
     */
    public List<Novedad> bandeja(Usuario usuario, PerfilNovedades perfil) throws ServiceException {
        try {
            List<Novedad> todas = switch (perfil) {
                case AGENTE -> novedadRepository.findDeUsuario(usuario.getId());
                case JEFATURA -> usuario.getServicioId() != null
                        ? novedadRepository.findByServicio(usuario.getServicioId()) : List.of();
                case OFICINA_PERSONAL, DIRECCION -> novedadRepository.findTodas();
            };
            var autorizaciones = novedadRepository.findAutorizacionesPorNovedad();
            List<Novedad> visibles = todas.stream()
                    .filter(n -> n.getEstado() != EstadoNovedad.BORRADOR
                            || usuario.getId().equals(n.getSolicitadoPorId()))
                    .toList();
            visibles.forEach(n -> n.setAutorizaciones(autorizaciones.getOrDefault(n.getId(), new ArrayList<>())));
            return visibles;
        } catch (SQLException e) {
            throw traducir("Error al obtener las novedades", e);
        }
    }

    /** Novedad con su historial de instancias y sus respaldos (CU28). */
    public Novedad detalle(UUID novedadId) throws ServiceException {
        try {
            Novedad n = novedadRepository.findById(novedadId);
            if (n == null) {
                throw new ServiceException("La novedad no existe.");
            }
            n.setAutorizaciones(novedadRepository.findAutorizaciones(novedadId));
            n.setAdjuntos(novedadRepository.findAdjuntos(novedadId));
            return n;
        } catch (SQLException e) {
            throw traducir("Error al cargar la novedad", e);
        }
    }

    public byte[] contenidoAdjunto(UUID adjuntoId) throws ServiceException {
        try {
            return novedadRepository.leerContenidoAdjunto(adjuntoId);
        } catch (SQLException e) {
            throw traducir("Error al leer el adjunto", e);
        }
    }

    /** Agentes para los que el usuario puede cargar una solicitud. */
    public List<Usuario> agentesSeleccionables(Usuario usuario, PerfilNovedades perfil) throws ServiceException {
        try {
            List<Usuario> lista = switch (perfil) {
                case AGENTE -> List.of(usuario);
                case JEFATURA -> usuario.getServicioId() != null
                        ? usuarioRepository.findUsuariosByServicio(usuario.getServicioId()) : List.of(usuario);
                case OFICINA_PERSONAL, DIRECCION -> usuarioRepository.readAll();
            };
            return ordenar(lista);
        } catch (SQLException e) {
            throw traducir("Error al obtener los agentes", e);
        }
    }

    /** Posibles contrapartes: los demás agentes del servicio del agente. */
    public List<Usuario> contrapartesPosibles(Usuario agente) throws ServiceException {
        if (agente == null || agente.getServicioId() == null) {
            return List.of();
        }
        try {
            return ordenar(usuarioRepository.findUsuariosByServicio(agente.getServicioId()).stream()
                    .filter(u -> !u.getId().equals(agente.getId())).toList());
        } catch (SQLException e) {
            throw traducir("Error al obtener las contrapartes", e);
        }
    }

    /** Jornadas vigentes del agente (para mostrar la guardia cedida/recibida). */
    public Optional<JornadaLaboral> jornadaVigente(UUID agenteId, LocalDate fecha) {
        try {
            return diagramaRepository.findJornadasVigentesDeEmpleadoEnRango(agenteId, fecha, fecha)
                    .stream().findFirst();
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /** Saldo vigente del banco de horas (créditos no vencidos). */
    public BigDecimal saldoHoras(UUID agenteId) throws ServiceException {
        try {
            return novedadRepository.findSaldoHoras(agenteId, ar.com.hmu.util.Reloj.hoy());
        } catch (SQLException e) {
            throw traducir("Error al leer el saldo de horas", e);
        }
    }

    /**
     * Banco de horas del agente: créditos con su vencimiento, débitos FIFO y
     * vencimientos. Antes da de baja lo vencido.
     */
    public List<ar.com.hmu.model.MovimientoHoras> movimientosHoras(UUID agenteId, Usuario actor)
            throws ServiceException {
        try {
            novedadRepository.enTransaccion(c -> {
                banco.vencer(c, ar.com.hmu.util.Reloj.hoy(), actor.getId());
                return null;
            });
            return novedadRepository.findMovimientos(agenteId);
        } catch (SQLException e) {
            throw traducir("Error al leer el banco de horas", e);
        }
    }

    /** Pases de salida (con o sin retorno) aprobados del agente con horas por devolver. */
    public List<PaseADevolver> pasesADevolver(UUID agenteId) throws ServiceException {
        LocalDate hoy = ar.com.hmu.util.Reloj.hoy();
        try {
            List<PaseADevolver> l = new ArrayList<>();
            for (Novedad p : novedadRepository.findDelAgenteEnRango(agenteId, hoy.minusDays(31), hoy.plusDays(31),
                    java.util.EnumSet.of(EstadoNovedad.APROBADA, EstadoNovedad.APLICADA))) {
                if (!p.getTipo().esPaseADevolver() || !agenteId.equals(p.getAgenteId()) || p.getCantHoras() == null) {
                    continue;
                }
                BigDecimal devuelto = novedadRepository.findDevolucionesDe(p.getId(), DEVOLUCION_VIGENTE).stream()
                        .map(d -> d.getCantHoras() != null ? d.getCantHoras() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal pendiente = p.getCantHoras().subtract(devuelto);
                if (pendiente.signum() > 0) {
                    l.add(new PaseADevolver(p, pendiente));
                }
            }
            l.sort(Comparator.comparing(x -> x.pase().getFechaDesde()));
            return l;
        } catch (SQLException e) {
            throw traducir("Error al leer los pases de salida", e);
        }
    }

    // ============================================================
    // CU20 / CU21 — solicitar y validar
    // ============================================================

    public ResultadoValidacion validar(Novedad n, boolean tieneRespaldo) {
        return validador.validar(n, tieneRespaldo);
    }

    /** Guarda (o actualiza) la solicitud en borrador, sin validarla. */
    public Novedad guardarBorrador(Novedad n, List<NovedadAdjunto> adjuntosNuevos, Usuario actor)
            throws ServiceException {
        verificarDatosMinimos(n);
        n.normalizarJustificacion();
        try {
            novedadRepository.enTransaccion(c -> {
                persistirDatos(c, n, actor);
                insertarAdjuntos(c, n, adjuntosNuevos, actor);
                return null;
            });
            return n;
        } catch (SQLException e) {
            throw traducir("Error al guardar la novedad", e);
        }
    }

    /**
     * Envía (o reenvía, si estaba observada) la solicitud: valida (CU21),
     * registra las instancias pendientes y notifica a la primera.
     *
     * @throws ValidacionNovedadException si hay errores bloqueantes (CU20 4a).
     */
    public ResultadoValidacion enviar(Novedad n, List<NovedadAdjunto> adjuntosNuevos, Usuario actor)
            throws ServiceException {
        verificarDatosMinimos(n);
        n.normalizarJustificacion();
        NovedadStateMachine.validarEnviar(n.getEstado());
        boolean tieneRespaldo = !n.getAdjuntos().isEmpty() || (adjuntosNuevos != null && !adjuntosNuevos.isEmpty());
        ResultadoValidacion resultado = validador.validar(n, tieneRespaldo);
        if (resultado.hayBloqueantes()) {
            throw new ValidacionNovedadException(resultado);
        }
        try {
            boolean agenteEsJefatura = esJefatura(n.getAgenteId());
            List<InstanciaNovedad> circuito =
                    NovedadStateMachine.circuito(agenteEsJefatura, resultado.requiereSecretariaSalud());
            EstadoNovedad primerEstado = EstadoNovedad.pendienteDe(circuito.get(0));
            novedadRepository.enTransaccion(c -> {
                persistirDatos(c, n, actor);
                insertarAdjuntos(c, n, adjuntosNuevos, actor);
                int orden = novedadRepository.maxOrden(c, n.getId());
                List<NovedadAutorizacion> filas = new ArrayList<>();
                for (InstanciaNovedad instancia : circuito) {
                    filas.add(new NovedadAutorizacion(UUID.randomUUID(), n.getId(), instancia, ++orden,
                            EstadoNovedadAutorizacion.PENDIENTE));
                }
                novedadRepository.insertarAutorizaciones(c, filas);
                LocalDateTime fechaSolicitud = n.getEstado() == EstadoNovedad.BORRADOR ? ar.com.hmu.util.Reloj.ahora() : null;
                exigir(novedadRepository.cambiarEstado(c, n.getId(), n.getVersion(), primerEstado, fechaSolicitud));
                return null;
            });
            Novedad enviada = detalle(n.getId());
            notificarPendiente(enviada, circuito.get(0), actor);
            notificarInvolucrados(enviada, "Solicitud registrada",
                    "La solicitud quedó " + primerEstado.enFrase() + ".", null, actor);
            return resultado;
        } catch (SQLException e) {
            throw traducir("Error al enviar la novedad", e);
        }
    }

    // ============================================================
    // CU29 — licencia de origen externo
    // ============================================================

    /** Valida una licencia externa antes de registrarla (superposiciones, CU29 paso 3). */
    public ResultadoValidacion validarLicenciaExterna(Novedad licencia) throws ServiceException {
        licencia.setTipo(tipoLicenciaExterna());
        return validador.validar(licencia, true);
    }

    /**
     * CU29: la OP asienta una licencia tramitada en VeDi o MiDocta, sin
     * circuito de autorización; las jornadas del diagrama vigente se marcan
     * como licencia. Si se superpone con otra novedad, el llamador pide
     * confirmación antes (3a).
     */
    public Novedad registrarLicenciaExterna(Novedad licencia, Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal registra licencias de origen externo.");
        }
        verificarDatosMinimos(licencia);
        if (licencia.getReferenciaExterna() == null || licencia.getReferenciaExterna().isBlank()) {
            throw new ServiceException("Indique la referencia del trámite municipal (VeDi o MiDocta).");
        }
        if (licencia.getCodigoNovedad() == null) {
            throw new ServiceException("Elija el tipo de licencia.");
        }
        licencia.setTipo(tipoLicenciaExterna());
        ResultadoValidacion r = validador.validar(licencia, true);
        if (r.hayBloqueantes()) {
            throw new ValidacionNovedadException(r);
        }
        try {
            licencia.setId(UUID.randomUUID());
            licencia.setSolicitadoPorId(actor.getId());
            licencia.setOrigen(ar.com.hmu.model.OrigenNovedad.LICENCIA_EXTERNA);
            licencia.setEstado(EstadoNovedad.APLICADA);
            licencia.setFechaSolicitud(ar.com.hmu.util.Reloj.ahora());
            novedadRepository.enTransaccion(c -> {
                novedadRepository.insertar(c, licencia);
                aplicador.aplicar(c, licencia, actor.getId());
                return null;
            });
            Novedad registrada = detalle(licencia.getId());
            notificarInvolucrados(registrada, "Licencia registrada",
                    "La Oficina de Personal registró la licencia (" + licencia.getReferenciaExterna()
                            + ") y marcó las jornadas del diagrama vigente.", null, actor);
            return registrada;
        } catch (SQLException e) {
            throw traducir("Error al registrar la licencia", e);
        }
    }

    private TipoNovedad tipoLicenciaExterna() throws ServiceException {
        try {
            return novedadRepository.findTiposActivos().stream().filter(TipoNovedad::esExterna).findFirst()
                    .orElseThrow(() -> new ServiceException("No está cargado el tipo de licencia externa."));
        } catch (SQLException e) {
            throw traducir("Error al leer los tipos de novedad", e);
        }
    }

    public void anular(Novedad n, Usuario actor) throws ServiceException {
        NovedadStateMachine.validarAnular(n.getEstado());
        if (!actor.getId().equals(n.getSolicitadoPorId()) && !actor.getId().equals(n.getAgenteId())) {
            throw new ServiceException("Sólo quien cargó la solicitud puede anularla.");
        }
        try {
            novedadRepository.enTransaccion(c -> {
                exigir(novedadRepository.cambiarEstado(c, n.getId(), n.getVersion(), EstadoNovedad.ANULADA, null));
                return null;
            });
        } catch (SQLException e) {
            throw traducir("Error al anular la novedad", e);
        }
    }

    // ============================================================
    // CU22–CU25 — instancias
    // ============================================================

    /**
     * Autoriza (JS), verifica (OP) o aprueba (DH) la instancia pendiente.
     * Si era la última, la novedad queda aprobada y se aplica (CU26).
     */
    public void autorizar(Novedad n, Usuario actor, PerfilNovedades perfil, String comentario)
            throws ServiceException {
        NovedadAutorizacion pendiente = instanciaResoluble(n, actor, perfil);
        if (pendiente.getInstancia() == InstanciaNovedad.SECRETARIA_SALUD) {
            throw new ServiceException("Registre el resultado de la Secretaría de Salud con la referencia del expediente.");
        }
        try {
            Novedad actual = detalle(n.getId());
            Optional<NovedadAutorizacion> siguiente = actual.getAutorizaciones().stream()
                    .filter(a -> a.estaPendiente() && a.getOrden() > pendiente.getOrden())
                    .min(Comparator.comparingInt(NovedadAutorizacion::getOrden));
            EstadoNovedad nuevo = NovedadStateMachine.trasAutorizar(
                    siguiente.map(NovedadAutorizacion::getInstancia).orElse(null));
            boolean aplicar = siguiente.isEmpty();

            novedadRepository.enTransaccion(c -> {
                exigir(novedadRepository.resolverAutorizacion(c, pendiente.getId(),
                        EstadoNovedadAutorizacion.AUTORIZADO, actor.getId(), vacioANull(comentario)));
                exigir(novedadRepository.cambiarEstado(c, n.getId(), n.getVersion(), nuevo, null));
                if (aplicar) {
                    aplicador.aplicar(c, actual, actor.getId());
                    exigir(novedadRepository.cambiarEstado(c, n.getId(), n.getVersion() + 1,
                            EstadoNovedad.APLICADA, null));
                }
                return null;
            });

            Novedad resuelta = detalle(n.getId());
            if (aplicar) {
                acreditarSiCorresponde(resuelta, actor);
            }
            if (aplicar) {
                notificarInvolucrados(resuelta, "Novedad aprobada",
                        "La Dirección aprobó la novedad y quedó aplicada al diagrama vigente.", comentario, actor);
            } else {
                siguiente.ifPresent(s -> notificarPendiente(resuelta, s.getInstancia(), actor));
                notificarInvolucrados(resuelta, verboDe(pendiente.getInstancia()),
                        pendiente.getInstancia().getEtiqueta() + " " + verboDe(pendiente.getInstancia()).toLowerCase()
                                + " la novedad. Ahora está " + nuevo.enFrase() + ".",
                        comentario, actor);
            }
        } catch (SQLException e) {
            throw traducir("Error al registrar la decisión", e);
        }
    }

    /** CU24: la DH aprueba varias novedades verificadas por la OP. @return las que no pudo aprobar. */
    public List<String> aprobarLote(Collection<Novedad> novedades, Usuario actor) {
        List<String> errores = new ArrayList<>();
        for (Novedad n : novedades) {
            try {
                autorizar(detalle(n.getId()), actor, PerfilNovedades.DIRECCION, null);
            } catch (ServiceException e) {
                errores.add(n.getAgenteNombre() + " (" + n.getTipo().nombre() + "): " + e.getMessage());
            }
        }
        return errores;
    }

    /** CU25: devuelve la solicitud al solicitante con el motivo. */
    public void observar(Novedad n, Usuario actor, PerfilNovedades perfil, String motivo) throws ServiceException {
        cortarCircuito(n, actor, perfil, motivo, EstadoNovedadAutorizacion.OBSERVADO, EstadoNovedad.OBSERVADA);
    }

    /** CU22 3a / CU23 4a / CU24 3a: rechazo con fundamento (terminal). */
    public void rechazar(Novedad n, Usuario actor, PerfilNovedades perfil, String motivo) throws ServiceException {
        cortarCircuito(n, actor, perfil, motivo, EstadoNovedadAutorizacion.RECHAZADO, EstadoNovedad.RECHAZADA);
    }

    private void cortarCircuito(Novedad n, Usuario actor, PerfilNovedades perfil, String motivo,
                                EstadoNovedadAutorizacion resultado, EstadoNovedad nuevo) throws ServiceException {
        if (motivo == null || motivo.isBlank()) {
            throw new ServiceException(nuevo == EstadoNovedad.OBSERVADA
                    ? "No se puede devolver la solicitud sin indicar el motivo."
                    : "No se puede rechazar la solicitud sin fundamento.");
        }
        NovedadAutorizacion pendiente = instanciaResoluble(n, actor, perfil);
        try {
            novedadRepository.enTransaccion(c -> {
                exigir(novedadRepository.resolverAutorizacion(c, pendiente.getId(), resultado, actor.getId(),
                        motivo.trim()));
                novedadRepository.borrarPendientes(c, n.getId());
                exigir(novedadRepository.cambiarEstado(c, n.getId(), n.getVersion(), nuevo, null));
                return null;
            });
            Novedad resuelta = detalle(n.getId());
            String que = nuevo == EstadoNovedad.OBSERVADA ? "devolvió para corrección" : "rechazó";
            notificarInvolucrados(resuelta, nuevo == EstadoNovedad.OBSERVADA ? "Novedad observada" : "Novedad rechazada",
                    pendiente.getInstancia().getEtiqueta() + " " + que + " la novedad.", motivo, actor);
        } catch (SQLException e) {
            throw traducir("Error al registrar la decisión", e);
        }
    }

    /**
     * CU24 5a: la OP registra el resultado de la Secretaría de Salud, con la
     * referencia del expediente, como una instancia más.
     */
    public void registrarResultadoSecretaria(Novedad n, Usuario actor, boolean autorizada, String expediente)
            throws ServiceException {
        if (expediente == null || expediente.isBlank()) {
            throw new ServiceException("Indique la referencia del expediente de la Secretaría de Salud.");
        }
        NovedadAutorizacion pendiente = instanciaResoluble(n, actor, PerfilNovedades.OFICINA_PERSONAL);
        try {
            Novedad actual = detalle(n.getId());
            novedadRepository.enTransaccion(c -> {
                exigir(novedadRepository.resolverAutorizacion(c, pendiente.getId(),
                        autorizada ? EstadoNovedadAutorizacion.AUTORIZADO : EstadoNovedadAutorizacion.RECHAZADO,
                        actor.getId(), "Expediente " + expediente.trim()));
                if (autorizada) {
                    aplicador.aplicar(c, actual, actor.getId());
                }
                exigir(novedadRepository.cambiarEstado(c, n.getId(), n.getVersion(),
                        autorizada ? EstadoNovedad.APLICADA : EstadoNovedad.RECHAZADA, null));
                return null;
            });
            if (autorizada) {
                acreditarSiCorresponde(detalle(n.getId()), actor);
            }
            notificarInvolucrados(detalle(n.getId()), "Resultado de la Secretaría de Salud",
                    "La Secretaría de Salud " + (autorizada ? "autorizó" : "no autorizó") + " la novedad.",
                    "Expediente " + expediente.trim(), actor);
        } catch (SQLException e) {
            throw traducir("Error al registrar el resultado", e);
        }
    }

    /** Horas extras autorizadas después de la jornada: se acreditan ya, si hay marcaciones. */
    private void acreditarSiCorresponde(Novedad n, Usuario actor) {
        if (acreditadorHorasExtra != null && n.getTipo().es(TipoNovedad.HORAS_EXTRA)) {
            try {
                acreditadorHorasExtra.accept(n, actor);
            } catch (RuntimeException e) {
                // Se acreditará al cotejar el parte de esa fecha.
            }
        }
    }

    // ============================================================
    // Permisos
    // ============================================================

    /** El usuario, con ese perfil, puede resolver la instancia pendiente. */
    public boolean puedeResolver(Novedad n, Usuario usuario, PerfilNovedades perfil) {
        try {
            instanciaResoluble(n, usuario, perfil);
            return true;
        } catch (ServiceException e) {
            return false;
        }
    }

    private NovedadAutorizacion instanciaResoluble(Novedad n, Usuario actor, PerfilNovedades perfil)
            throws ServiceException {
        if (n.getAutorizaciones().isEmpty() && n.getEstado().esPendiente()) {
            n.setAutorizaciones(detalle(n.getId()).getAutorizaciones());
        }
        NovedadAutorizacion pendiente = n.instanciaPendiente()
                .orElseThrow(() -> new ServiceException("La novedad no tiene instancias pendientes."));
        NovedadStateMachine.validarResolver(n.getEstado(), pendiente.getInstancia());
        if (n.involucraA(actor.getId())) {
            throw new ServiceException("Separación de funciones: no puede resolver una novedad en la que participa.");
        }
        boolean competente = switch (pendiente.getInstancia()) {
            case JEFATURA -> perfil == PerfilNovedades.JEFATURA && esJefaturaDe(actor, n.getServicioId());
            case OFICINA_PERSONAL, SECRETARIA_SALUD ->
                    perfil == PerfilNovedades.OFICINA_PERSONAL && actor.hasRole(TipoUsuario.OFICINADEPERSONAL);
            case DIRECCION -> perfil == PerfilNovedades.DIRECCION && actor.hasRole(TipoUsuario.DIRECCION);
        };
        if (!competente) {
            throw new ServiceException("La instancia pendiente (" + pendiente.getInstancia().getEtiqueta()
                    + ") no corresponde a su perfil.");
        }
        return pendiente;
    }

    private boolean esJefaturaDe(Usuario u, UUID servicioId) {
        if (servicioId == null || !u.hasRole(TipoUsuario.JEFATURADESERVICIO)) {
            return false;
        }
        if (servicioId.equals(u.getServicioId())) {
            return true;
        }
        try {
            return u.getId().equals(servicioRepository.findEncargadoByServicio(servicioId));
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean esJefatura(UUID agenteId) throws SQLException {
        Usuario agente = usuarioRepository.readByUUID(agenteId);
        return agente != null && agente.hasRole(TipoUsuario.JEFATURADESERVICIO);
    }

    // ============================================================
    // Helpers de persistencia
    // ============================================================

    private void verificarDatosMinimos(Novedad n) throws ServiceException {
        if (n.getTipo() == null || n.getAgenteId() == null || n.getFechaDesde() == null
                || n.getFechaHasta() == null) {
            throw new ServiceException("Complete el tipo de novedad, el agente y las fechas.");
        }
        if (n.getFechaHasta().isBefore(n.getFechaDesde())) {
            throw new ServiceException("La fecha hasta no puede ser anterior a la fecha desde.");
        }
    }

    private void persistirDatos(Connection c, Novedad n, Usuario actor) throws SQLException {
        if (n.getId() == null) {
            n.setId(UUID.randomUUID());
            n.setSolicitadoPorId(actor.getId());
            n.setEstado(EstadoNovedad.BORRADOR);
            n.setVersion(0);
            novedadRepository.insertar(c, n);
        } else {
            try {
                NovedadStateMachine.validarEditar(n.getEstado());
            } catch (ServiceException e) {
                throw new SQLException(e.getMessage());
            }
            exigir(novedadRepository.actualizarDatos(c, n));
        }
    }

    private void insertarAdjuntos(Connection c, Novedad n, List<NovedadAdjunto> adjuntos, Usuario actor)
            throws SQLException {
        if (adjuntos == null) {
            return;
        }
        for (NovedadAdjunto a : adjuntos) {
            a.setNovedadId(n.getId());
            a.setCargadoPorId(actor.getId());
            novedadRepository.insertarAdjunto(c, a);
        }
    }

    private static ServiceException traducir(String mensaje, SQLException e) {
        if (e instanceof ConflictoVersionException) {
            return new ServiceException(ConflictoVersionException.MENSAJE, e);
        }
        return new ServiceException(mensaje + ": " + e.getMessage(), e);
    }

    /** Una escritura que no afectó filas = otro usuario cambió la novedad antes. */
    private static void exigir(boolean afectada) throws SQLException {
        if (!afectada) {
            throw new ConflictoVersionException();
        }
    }

    // ============================================================
    // CU27 — notificaciones
    // ============================================================

    private void notificarPendiente(Novedad n, InstanciaNovedad instancia, Usuario actor) {
        Set<UUID> destinatarios = new LinkedHashSet<>();
        try {
            switch (instancia) {
                case JEFATURA -> {
                    UUID encargado = servicioRepository.findEncargadoByServicio(n.getServicioId());
                    if (encargado != null) destinatarios.add(encargado);
                }
                case OFICINA_PERSONAL, SECRETARIA_SALUD -> destinatarios.addAll(conRol(TipoUsuario.OFICINADEPERSONAL));
                case DIRECCION -> destinatarios.addAll(conRol(TipoUsuario.DIRECCION));
            }
        } catch (SQLException e) {
            return;
        }
        destinatarios.remove(actor.getId());
        destinatarios.removeIf(n::involucraA);
        for (UUID d : destinatarios) {
            notificaciones.notificar(d, "NOVEDAD_PENDIENTE",
                    "Novedad pendiente de " + instancia.getEtiqueta() + ": " + resumen(n),
                    resumen(n) + "\nEspera la decisión de " + instancia.getEtiqueta() + ".\n"
                            + "Ingresá al módulo de novedades para resolverla.",
                    "Novedad", n.getId());
        }
    }

    /** Al agente (y quien cargó la solicitud, y la contraparte) y a la JS del servicio (RF19). */
    private void notificarInvolucrados(Novedad n, String titulo, String texto, String comentario, Usuario actor) {
        Set<UUID> destinatarios = new LinkedHashSet<>();
        destinatarios.add(n.getAgenteId());
        destinatarios.add(n.getSolicitadoPorId());
        if (n.getContraparteAgenteId() != null) {
            destinatarios.add(n.getContraparteAgenteId());
        }
        try {
            UUID encargado = servicioRepository.findEncargadoByServicio(n.getServicioId());
            if (encargado != null) destinatarios.add(encargado);
        } catch (SQLException ignored) {
            // Sin JS identificable: se notifica a los demás.
        }
        destinatarios.remove(actor.getId());
        destinatarios.remove(null);
        String cuerpo = texto + "\n\n" + resumen(n)
                + (comentario != null && !comentario.isBlank() ? "\nComentario: " + comentario.trim() : "");
        for (UUID d : destinatarios) {
            notificaciones.notificar(d, "NOVEDAD_" + n.getEstado().name(), titulo + ": " + resumen(n), cuerpo,
                    "Novedad", n.getId());
        }
    }

    private List<UUID> conRol(TipoUsuario rol) throws SQLException {
        return usuarioRepository.readAll().stream().filter(u -> u.hasRole(rol)).map(Usuario::getId).toList();
    }

    private static String resumen(Novedad n) {
        String fechas = n.getFechaDesde().equals(n.getFechaHasta())
                ? n.getFechaDesde().format(DDMMYYYY)
                : n.getFechaDesde().format(DDMMYYYY) + " al " + n.getFechaHasta().format(DDMMYYYY);
        return n.getTipo().nombre() + " de " + n.getAgenteNombre() + " (" + fechas + ")";
    }

    private static String verboDe(InstanciaNovedad instancia) {
        return switch (instancia) {
            case JEFATURA -> "Autorizada";
            case OFICINA_PERSONAL -> "Verificada";
            case DIRECCION -> "Aprobada";
            case SECRETARIA_SALUD -> "Autorizada";
        };
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static List<Usuario> ordenar(List<Usuario> lista) {
        return lista.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Usuario::getApellidos, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Usuario::getNombres, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ============================================================
    // Fuente de datos del validador
    // ============================================================

    private final class FuenteDatosRepositorios implements FuenteDatosNovedad {

        @Override
        public List<JornadaLaboral> jornadasVigentes(UUID agenteId, LocalDate desde, LocalDate hasta) {
            try {
                return diagramaRepository.findJornadasVigentesDeEmpleadoEnRango(agenteId, desde, hasta);
            } catch (SQLException e) {
                throw new IllegalStateException("Error al leer el diagrama vigente", e);
            }
        }

        @Override
        public List<Novedad> novedadesEnRango(UUID agenteId, LocalDate desde, LocalDate hasta,
                                              Collection<EstadoNovedad> estados) {
            try {
                return novedadRepository.findDelAgenteEnRango(agenteId, desde, hasta, estados);
            } catch (SQLException e) {
                throw new IllegalStateException("Error al leer las novedades", e);
            }
        }

        @Override
        public BigDecimal saldoHoras(UUID agenteId) {
            try {
                return novedadRepository.findSaldoHoras(agenteId, ar.com.hmu.util.Reloj.hoy());
            } catch (SQLException e) {
                throw new IllegalStateException("Error al leer el saldo de horas", e);
            }
        }

        @Override
        public Novedad novedad(UUID id) {
            try {
                return id != null ? novedadRepository.findById(id) : null;
            } catch (SQLException e) {
                throw new IllegalStateException("Error al leer la novedad", e);
            }
        }

        @Override
        public List<Novedad> devolucionesDe(UUID paseId) {
            try {
                return novedadRepository.findDevolucionesDe(paseId, DEVOLUCION_VIGENTE);
            } catch (SQLException e) {
                throw new IllegalStateException("Error al leer las devoluciones", e);
            }
        }

        @Override
        public ar.com.hmu.service.diagramacion.CatalogoReglas reglas(LocalDate fecha) {
            return diagramaService != null ? diagramaService.reglasVigentes(fecha)
                    : ar.com.hmu.service.diagramacion.CatalogoReglas.PREDETERMINADO;
        }

        @Override
        public UUID servicioDe(UUID agenteId) {
            try {
                Usuario u = usuarioRepository.readByUUID(agenteId);
                return u != null ? u.getServicioId() : null;
            } catch (SQLException e) {
                throw new IllegalStateException("Error al leer el agente", e);
            }
        }
    }
}
