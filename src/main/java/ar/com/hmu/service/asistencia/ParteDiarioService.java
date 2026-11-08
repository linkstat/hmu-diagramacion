package ar.com.hmu.service.asistencia;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.ParteDiario;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.MarcacionRepository;
import ar.com.hmu.repository.NovedadRepository;
import ar.com.hmu.repository.ParteDiarioRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.service.notification.NotificacionService;
import ar.com.hmu.util.Reloj;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parte diario (CU33–CU35): se genera desde el cotejo (CU31/CU32) separado
 * por vinculación; la OP lo revisa, lo completa con cargas manuales
 * fundamentadas, pide la justificación de las diferencias y lo cierra.
 */
public class ParteDiarioService {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** El parte de una fecha con sus detalles y el último lote importado. */
    public record Vista(ParteDiario parte, List<DetalleParte> detalles, LoteMarcaciones ultimoLote,
                        boolean cotejoParcial) {

        public long sinJustificar() {
            return detalles.stream().filter(DetalleParte::sinJustificar).count();
        }

        public long cargasManuales() {
            return detalles.stream().filter(d -> d.getOrigen() == DetalleParte.Origen.CARGA_MANUAL).count();
        }
    }

    private final ParteDiarioRepository parteRepository;
    private final MarcacionRepository marcacionRepository;
    private final NovedadRepository novedadRepository;
    private final ServicioRepository servicioRepository;
    private final NotificacionService notificaciones;
    private final ImportadorMarcaciones importador;
    private final ar.com.hmu.service.novedades.BancoHoras banco;
    /** Feriados y asuetos vigentes (cómputo doble de las horas extras); opcional. */
    private ar.com.hmu.repository.FeriadoRepository feriadoRepository;
    /** Reglas vigentes a una fecha (CU08). */
    private java.util.function.Function<LocalDate, ar.com.hmu.service.diagramacion.CatalogoReglas> reglas =
            f -> ar.com.hmu.service.diagramacion.CatalogoReglas.PREDETERMINADO;

    public ParteDiarioService(ParteDiarioRepository parteRepository, MarcacionRepository marcacionRepository,
                              NovedadRepository novedadRepository, ServicioRepository servicioRepository,
                              NotificacionService notificaciones) {
        this.parteRepository = parteRepository;
        this.marcacionRepository = marcacionRepository;
        this.novedadRepository = novedadRepository;
        this.servicioRepository = servicioRepository;
        this.notificaciones = notificaciones;
        this.importador = new ImportadorMarcaciones(marcacionRepository);
        this.banco = new ar.com.hmu.service.novedades.BancoHoras(novedadRepository);
    }

    public void setFeriadoRepository(ar.com.hmu.repository.FeriadoRepository feriadoRepository) {
        this.feriadoRepository = feriadoRepository;
    }

    public void setReglas(java.util.function.Function<LocalDate, ar.com.hmu.service.diagramacion.CatalogoReglas> reglas) {
        this.reglas = reglas;
    }

    /**
     * Acredita en el banco de horas las horas extras para FC aplicadas de la
     * fecha, según las marcaciones de la jornada (regla RV-HE-COMPUTO; vencen
     * según RV-BANCO-VENC). Es repetible: si el crédito no se usó, se recalcula.
     *
     * @return por agente, el texto de lo acreditado (observación del parte).
     */
    public Map<UUID, String> acreditarHorasExtra(LocalDate fecha, Usuario actor) throws SQLException {
        List<Novedad> horasExtra = novedadRepository.findHorasExtraAplicadas(fecha);
        if (horasExtra.isEmpty()) {
            return Map.of();
        }
        Map<UUID, JornadaLaboral> diagramadas = marcacionRepository.diagramadasDelDia(fecha);
        Map<UUID, JornadaRegistrada> registradas = marcacionRepository.registradasDelDia(fecha);
        ar.com.hmu.service.diagramacion.CatalogoReglas catalogo = reglas.apply(fecha);
        CalculoHorasExtra.Parametros p = CalculoHorasExtra.Parametros.de(catalogo);
        int venceDias = catalogo.entero(ar.com.hmu.service.novedades.BancoHoras.REGLA_VENCIMIENTO, "vencimientoDias",
                ar.com.hmu.service.novedades.BancoHoras.VENCIMIENTO_DIAS);
        Set<LocalDate> noLaborables = feriadoRepository != null
                ? feriadoRepository.findFechasVigentesEnRango(fecha, fecha.plusDays(1)) : Set.of();
        Map<UUID, String> textos = new HashMap<>();
        try (Connection c = parteRepository.conexion()) {
            c.setAutoCommit(false);
            try {
                for (Novedad he : horasExtra) {
                    JornadaRegistrada r = registradas.get(he.getAgenteId());
                    if (r == null) {
                        continue;   // todavía sin marcaciones: se acreditará al importarlas
                    }
                    CalculoHorasExtra.Resultado res = CalculoHorasExtra.calcular(diagramadas.get(he.getAgenteId()), r,
                            noLaborables::contains, p);
                    String detalle = res.descripcion(p.fraccionMinutos());
                    banco.acreditar(c, he.getAgenteId(), he.getId(), fecha, res.horasComputadas(),
                            "Horas extras para FC del " + fecha.format(DDMMYYYY) + ": " + detalle, venceDias,
                            Reloj.hoy(), actor.getId());
                    novedadRepository.actualizarCantHoras(c, he.getId(), res.horasComputadas());
                    textos.put(he.getAgenteId(), res.minutosComputados() > 0
                            ? "Horas extras para FC: " + detalle + " acreditadas en el banco de horas."
                            : "Horas extras para FC: sin tiempo posterior al egreso para acreditar.");
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        return textos;
    }

    /** Horas extras autorizadas después de la jornada (las acredita al aprobarse, si hay marcaciones). */
    public void acreditarHorasExtraDeNovedad(Novedad horasExtra, Usuario actor) {
        try {
            acreditarHorasExtra(horasExtra.getFechaDesde(), actor);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron acreditar las horas extras: " + e.getMessage(), e);
        }
    }

    public ImportadorMarcaciones.Resumen importar(String nombreArchivo, byte[] contenido, Usuario actor)
            throws ServiceException {
        exigirOP(actor);
        return importador.importar(nombreArchivo, contenido, actor);
    }

    /** Vista previa de un archivo de marcaciones, sin importarlo. */
    public ImportadorMarcaciones.Resumen analizar(String nombreArchivo, byte[] contenido, Usuario actor)
            throws ServiceException {
        return importador.analizar(nombreArchivo, contenido, actor);
    }

    /** El parte de la fecha (o null si todavía no se generó), con el cotejo fresco para los textos de jornada. */
    public Vista consultar(LocalDate fecha) throws ServiceException {
        try {
            ParteDiario p = parteRepository.findByFecha(fecha);
            LoteMarcaciones lote = marcacionRepository.ultimoLoteQueCubre(fecha);
            if (p == null) {
                return new Vista(null, List.of(), lote, lote == null);
            }
            List<DetalleParte> guardados = parteRepository.detalles(p.getId());
            completarTextos(fecha, guardados);
            return new Vista(p, guardados, lote, lote == null);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer el parte diario: " + e.getMessage(), e);
        }
    }

    /**
     * CU33: genera el parte de la fecha desde el cotejo. Si ya existe y está
     * abierto, actualiza el cotejo conservando las cargas manuales (1a, y
     * CU35 paso 4 cuando se aprueba la novedad presentada); si está cerrado,
     * lo informa.
     */
    public Vista generar(LocalDate fecha, Usuario actor) throws ServiceException {
        exigirOP(actor);
        try {
            ParteDiario existente = parteRepository.findByFecha(fecha);
            if (existente != null && existente.estaCerrado()) {
                throw new ServiceException("El parte del " + fecha.format(DDMMYYYY) + " está cerrado.");
            }
            List<DetalleParte> cotejo = new ArrayList<>(cotejar(fecha));
            // Horas extras para FC de la fecha: se acreditan y quedan en la observación del agente.
            Map<UUID, String> horasExtra = acreditarHorasExtra(fecha, actor);
            for (DetalleParte d : cotejo) {
                String he = horasExtra.get(d.getAgenteId());
                if (he != null) {
                    d.setObservaciones(d.getObservaciones() == null || d.getObservaciones().isBlank() ? he
                            : d.getObservaciones() + " " + he);
                }
            }
            if (existente != null) {
                // Las cargas manuales prevalecen sobre la diferencia sin justificar del mismo agente.
                Set<UUID> conCargaManual = new LinkedHashSet<>();
                for (DetalleParte d : parteRepository.detalles(existente.getId())) {
                    if (d.getOrigen() == DetalleParte.Origen.CARGA_MANUAL) conCargaManual.add(d.getAgenteId());
                }
                cotejo.removeIf(d -> d.sinJustificar() && conCargaManual.contains(d.getAgenteId()));
            }
            try (Connection c = parteRepository.conexion()) {
                c.setAutoCommit(false);
                try {
                    UUID parteId;
                    if (existente == null) {
                        ParteDiario p = new ParteDiario();
                        p.setId(UUID.randomUUID());
                        p.setFecha(fecha);
                        p.setGeneradoEn(Reloj.ahora());
                        p.setGeneradoPorId(actor.getId());
                        parteRepository.insertar(c, p);
                        parteId = p.getId();
                    } else {
                        parteId = existente.getId();
                        if (!parteRepository.tocar(c, parteId, existente.getVersion(), actor.getId())) {
                            throw new SQLException(CONFLICTO);
                        }
                        parteRepository.borrarDetallesAutomaticos(c, parteId);
                    }
                    parteRepository.insertarDetalles(c, parteId, cotejo);
                    banco.vencer(c, Reloj.hoy(), actor.getId());
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                }
            }
            return consultar(fecha);
        } catch (SQLException e) {
            throw new ServiceException(e.getMessage().equals(CONFLICTO) ? CONFLICTO
                    : "Error al generar el parte diario: " + e.getMessage(), e);
        }
    }

    private static final String CONFLICTO = "Otro usuario modificó el parte. Se recargará: revise y vuelva a intentar.";

    /** CU31/CU32 sobre los datos guardados. */
    public List<DetalleParte> cotejar(LocalDate fecha) throws SQLException {
        Map<UUID, JornadaLaboral> diagramadas = marcacionRepository.diagramadasDelDia(fecha);
        Map<UUID, JornadaRegistrada> registradas = marcacionRepository.registradasDelDia(fecha);
        Map<UUID, List<Novedad>> novedades = new HashMap<>();
        List<Novedad> delDia = novedadRepository.findQueTocanFecha(fecha);
        for (Novedad n : delDia) {
            novedades.computeIfAbsent(n.getAgenteId(), k -> new ArrayList<>()).add(n);
        }
        // Un cambio de guardia aplicado también modifica la jornada de la contraparte (código 222):
        // va al final, para que sus propias novedades se evalúen antes.
        for (Novedad n : delDia) {
            if (n.getContraparteAgenteId() != null && n.getEstado() == ar.com.hmu.model.EstadoNovedad.APLICADA) {
                novedades.computeIfAbsent(n.getContraparteAgenteId(), k -> new ArrayList<>()).add(n);
            }
        }
        Set<UUID> ids = CotejoAsistencia.involucrados(diagramadas, registradas);
        return CotejoAsistencia.cotejar(fecha, marcacionRepository.agentes(ids), diagramadas, registradas,
                novedades, marcacionRepository.toleranciaMinutos(fecha));
    }

    /** Recalcula los textos de jornada y la diferencia de los detalles guardados. */
    private void completarTextos(LocalDate fecha, List<DetalleParte> detalles) throws SQLException {
        Map<UUID, JornadaLaboral> diagramadas = marcacionRepository.diagramadasDelDia(fecha);
        Map<UUID, JornadaRegistrada> registradas = marcacionRepository.registradasDelDia(fecha);
        for (DetalleParte d : detalles) {
            JornadaLaboral jl = diagramadas.get(d.getAgenteId());
            JornadaRegistrada jr = registradas.get(d.getAgenteId());
            d.setDiagramada(CotejoAsistencia.textoDiagramada(jl));
            d.setRegistrada(CotejoAsistencia.textoRegistrada(jr, jl));
            d.setDiferenciaMinutos(CotejoAsistencia.diferencia(jl, jr));
        }
    }

    /** Catálogo de códigos numéricos de novedad, para la carga manual. */
    public List<ar.com.hmu.model.CodigoNovedad> codigos() throws ServiceException {
        try {
            return novedadRepository.findCodigos();
        } catch (SQLException e) {
            throw new ServiceException("Error al leer los códigos de novedad: " + e.getMessage(), e);
        }
    }

    /** CU34 pasos 2 y 3: carga manual con código y fundamento obligatorio. */
    public void cargaManual(ParteDiario parte, UUID agenteId, Integer codigo, String observacion, Usuario actor)
            throws ServiceException {
        exigirOP(actor);
        if (parte == null || parte.estaCerrado()) {
            throw new ServiceException("El parte no está abierto.");
        }
        if (agenteId == null || codigo == null) {
            throw new ServiceException("Seleccione el agente y el código.");
        }
        if (observacion == null || observacion.isBlank()) {
            throw new ServiceException("La observación es obligatoria: indique el motivo y el respaldo de la carga.");
        }
        try {
            List<CotejoAsistencia.Agente> ag = marcacionRepository.agentes(List.of(agenteId));
            DetalleParte d = new DetalleParte();
            d.setAgenteId(agenteId);
            d.setServicioId(ag.isEmpty() ? null : ag.get(0).servicioId());
            d.setOrigen(DetalleParte.Origen.CARGA_MANUAL);
            d.setCodigo(codigo);
            // Conserva el vínculo con la jornada de la diferencia que reemplaza (indicadores).
            for (DetalleParte previo : parteRepository.detalles(parte.getId())) {
                if (agenteId.equals(previo.getAgenteId()) && previo.getOrigen() == DetalleParte.Origen.COTEJO
                        && previo.sinJustificar()) {
                    d.setJornadaLaboralId(previo.getJornadaLaboralId());
                    d.setJornadaRegistradaId(previo.getJornadaRegistradaId());
                    d.setTipoDiferencia(previo.getTipoDiferencia());
                }
            }
            d.setObservaciones(observacion.trim());
            d.setCargadoPorId(actor.getId());
            try (Connection c = parteRepository.conexion()) {
                c.setAutoCommit(false);
                try {
                    if (!parteRepository.tocar(c, parte.getId(), parte.getVersion(), actor.getId())) {
                        throw new SQLException(CONFLICTO);
                    }
                    // La carga manual reemplaza la diferencia sin justificar del mismo agente.
                    parteRepository.borrarDetallesDelCotejo(c, parte.getId(), agenteId, DetalleParte.SIN_JUSTIFICAR);
                    parteRepository.insertarDetalles(c, parte.getId(), List.of(d));
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new ServiceException(CONFLICTO.equals(e.getMessage()) ? CONFLICTO
                    : "Error al registrar la carga manual: " + e.getMessage(), e);
        }
    }

    /** CU35: pide a la JS y al agente que presenten la novedad que justifique la diferencia. */
    public void solicitarJustificacion(ParteDiario parte, DetalleParte detalle, Usuario actor) throws ServiceException {
        exigirOP(actor);
        if (detalle == null || !detalle.sinJustificar()) {
            throw new ServiceException("Seleccione una diferencia sin justificar.");
        }
        String asunto = "Justificación requerida: " + detalle.getAgenteNombre() + " ("
                + parte.getFecha().format(DDMMYYYY) + ")";
        String cuerpo = "La Oficina de Personal solicita presentar la novedad que justifique la diferencia del "
                + parte.getFecha().format(DDMMYYYY) + ":\n" + detalle.getObservaciones()
                + "\nSi no se presenta, la diferencia se informará como " + descripcion(detalle) + ".";
        notificaciones.notificar(detalle.getAgenteId(), "PARTE_JUSTIFICACION", asunto, cuerpo, "ParteDiario", parte.getId());
        try {
            UUID js = servicioRepository.findEncargadoByServicio(detalle.getServicioId());
            if (js != null && !js.equals(detalle.getAgenteId())) {
                notificaciones.notificar(js, "PARTE_JUSTIFICACION", asunto, cuerpo, "ParteDiario", parte.getId());
            }
        } catch (SQLException ignored) {
            // Se notificó al agente.
        }
    }

    /** CU34 pasos 4 a 6: cierra el parte y avisa a las JS con diferencias sin justificar. */
    public void cerrar(ParteDiario parte, Usuario actor) throws ServiceException {
        exigirOP(actor);
        try {
            if (!parteRepository.cerrar(parte.getId(), parte.getVersion(), actor.getId())) {
                throw new ServiceException(CONFLICTO);
            }
            Set<UUID> servicios = new LinkedHashSet<>();
            for (DetalleParte d : parteRepository.detalles(parte.getId())) {
                if (d.sinJustificar() && d.getServicioId() != null) servicios.add(d.getServicioId());
            }
            for (UUID s : servicios) {
                UUID js = servicioRepository.findEncargadoByServicio(s);
                if (js != null) {
                    notificaciones.notificar(js, "PARTE_CERRADO",
                            "Parte diario del " + parte.getFecha().format(DDMMYYYY) + " cerrado con diferencias sin justificar",
                            "El parte diario se cerró con diferencias sin justificar de agentes de su servicio: "
                                    + "se informan con el código de inasistencia o incumplimiento que corresponde.",
                            "ParteDiario", parte.getId());
                }
            }
        } catch (SQLException e) {
            throw new ServiceException("Error al cerrar el parte: " + e.getMessage(), e);
        }
    }

    private static String descripcion(DetalleParte d) {
        return d.getTipoDiferencia() == null ? "sin justificar" : switch (d.getTipoDiferencia()) {
            case INASISTENCIA, GUARDIA_NO_CUBIERTA -> "falta injustificada";
            case OMISION_INGRESO, OMISION_EGRESO -> "omisión de marcación sin justificar";
            default -> "incumplimiento horario";
        };
    }

    private static void exigirOP(Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal gestiona las marcaciones y el parte diario.");
        }
    }
}
