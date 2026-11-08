package ar.com.hmu.service.asistencia;

import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.CodigoNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.service.asistencia.DetalleParte.Origen;
import ar.com.hmu.service.asistencia.DetalleParte.TipoDiferencia;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cotejo de asistencia de un día (CU31) y detección de diferencias (CU32):
 * para cada agente cruza la jornada diagramada (diagramas aprobados), la
 * jornada registrada (marcaciones) y las novedades aprobadas, y le asigna
 * el código con el que se informa en el parte.
 *
 * <p>Códigos numéricos de Capital Humano ({@link CodigoNovedad}): 99 presente,
 * 222 jornada de un cambio de horario o de guardia aplicado, 6 franco
 * compensatorio, 333 pase sin retorno y el código efectivo de la novedad
 * aprobada que justifica (FJ 2, CS 8, licencias…). Una diferencia sin novedad
 * aprobada (tardanza, retiro anticipado, omisión, inasistencia) queda con 666
 * hasta que la OP elige el código en la carga manual. El franco diagramado y
 * los pases con retorno no se informan.</p>
 */
public final class CotejoAsistencia {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<EstadoNovedad> VIGENTES = Set.of(EstadoNovedad.APROBADA, EstadoNovedad.APLICADA);

    /** Datos de un agente para el parte. */
    public record Agente(UUID id, String nombre, UUID servicioId, String servicioNombre, boolean relacionDeDependencia) {
    }

    private CotejoAsistencia() {
    }

    /**
     * @param fecha          día del parte.
     * @param agentes        agentes a cotejar (los que tienen jornada diagramada o registrada ese día).
     * @param diagramadas    jornada diagramada de cada agente ese día (diagramas aprobados).
     * @param registradas    jornadas registradas del agente que empiezan ese día.
     * @param novedades      novedades del agente que tocan el día (aprobadas o en trámite).
     * @param toleranciaMin  minutos de tolerancia de ingreso y egreso (regla RV-COTEJO-TOLER).
     */
    public static List<DetalleParte> cotejar(LocalDate fecha, List<Agente> agentes,
                                             Map<UUID, JornadaLaboral> diagramadas,
                                             Map<UUID, JornadaRegistrada> registradas,
                                             Map<UUID, List<Novedad>> novedades, int toleranciaMin) {
        List<DetalleParte> detalles = new ArrayList<>();
        for (Agente a : agentes) {
            JornadaLaboral d = diagramadas.get(a.id());
            JornadaRegistrada r = registradas.get(a.id());
            if (d == null && r == null) {
                continue;
            }
            List<Novedad> delAgente = novedades.getOrDefault(a.id(), List.of());
            DetalleParte det = new DetalleParte();
            det.setAgenteId(a.id());
            det.setAgenteNombre(a.nombre());
            det.setServicioId(a.servicioId());
            det.setServicioNombre(a.servicioNombre());
            det.setRelacionDeDependencia(a.relacionDeDependencia());
            det.setJornadaLaboralId(d != null ? d.getId() : null);
            det.setJornadaRegistradaId(r != null ? r.id() : null);
            det.setDiagramada(textoDiagramada(d));
            det.setRegistrada(textoRegistrada(r, d));
            det.setDiferenciaMinutos(diferencia(d, r));
            if (clasificar(det, fecha, d, r, delAgente, toleranciaMin)) {
                detalles.add(det);
            }
        }
        detalles.sort(Comparator.comparing(DetalleParte::getServicioNombre, Comparator.nullsLast(String::compareTo))
                .thenComparing(DetalleParte::getAgenteNombre));
        return detalles;
    }

    /**
     * Asigna el código del parte (numérico de Capital Humano).
     *
     * @return false si el día no se informa en el parte (franco diagramado).
     */
    private static boolean clasificar(DetalleParte det, LocalDate fecha, JornadaLaboral d, JornadaRegistrada r,
                                      List<Novedad> novedades, int tol) {
        boolean conHorario = d != null && d.tieneHorario();

        // 1. Novedades aprobadas: el código efectivo de la novedad (por defecto, el de su tipo).
        Optional<Novedad> licencia = vigente(novedades, n -> n.getTipo().esExterna());
        if (licencia.isPresent()) {
            Novedad n = licencia.get();
            justificar(det, n, Origen.LICENCIA_EXTERNA,
                    n.codigoEfectivo() != null ? n.codigoEfectivo() : CodigoNovedad.CARPETA_MEDICA, textoLicencia(n));
            return true;
        }
        // Una ausencia justificada sólo cuenta los días con jornada (un franco sigue siendo franco).
        Optional<Novedad> ausencia = vigente(novedades, n -> n.getTipo().es(TipoNovedad.INASISTENCIA_JUSTIFICADA)
                || n.getTipo().es("CS") || n.getTipo().es(TipoNovedad.FRANCO_COMPENSATORIO));
        if (ausencia.isPresent() && (conHorario || d == null || d.getTipo() == TipoJornada.FRANCO_COMPENSATORIO)) {
            Novedad n = ausencia.get();
            justificar(det, n, Origen.NOVEDAD_APROBADA, codigoDe(n), n.getTipo().nombre() + textoMotivo(n));
            return true;
        }
        // Pase sin retorno: 333, con la hora de salida marcada. Los pases con retorno no se informan.
        Optional<Novedad> pssr = vigente(novedades, n -> n.getTipo().es(TipoNovedad.PASE_SIN_RETORNO));
        if (pssr.isPresent() && r != null && !r.omisionIngreso()) {
            Novedad n = pssr.get();
            LocalTime salida = r.egreso() != null ? r.egreso().toLocalTime() : n.getHoraDesde();
            justificar(det, n, Origen.NOVEDAD_APROBADA, codigoDe(n), "PSSR " + salida.format(HHMM) + " Hs");
            return true;
        }
        Optional<Novedad> omision = vigente(novedades, n -> n.getTipo().es(TipoNovedad.OMISION_MARCACION));
        if (omision.isPresent() && r != null && (r.omisionIngreso() || r.omisionEgreso())) {
            justificar(det, omision.get(), Origen.NOVEDAD_APROBADA, codigoDe(omision.get()),
                    "Omisión de marcación justificada.");
            return true;
        }
        // Horas extras autorizadas en un día sin jornada diagramada: presente.
        Optional<Novedad> horasExtra = vigente(novedades, n -> n.getTipo().es(TipoNovedad.HORAS_EXTRA));
        if (horasExtra.isPresent() && !conHorario && r != null) {
            justificar(det, horasExtra.get(), Origen.NOVEDAD_APROBADA, CodigoNovedad.PRESENTE,
                    horasExtra.get().getTipo().nombre() + ".");
            return true;
        }

        // 2. Sin jornada con horario diagramada.
        if (!conHorario) {
            if (r != null) {
                diferencia(det, TipoDiferencia.PRESENTE_SIN_DIAGRAMA, "Presente sin jornada diagramada.");
            } else if (d != null && d.getTipo() == TipoJornada.LICENCIA) {
                det.setCodigo(CodigoNovedad.CARPETA_MEDICA);
                det.setObservaciones(d.getObservaciones());
            } else if (d != null && d.getTipo() == TipoJornada.FRANCO_COMPENSATORIO) {
                det.setCodigo(CodigoNovedad.FRANCO_COMPENSATORIO);
            } else {
                return false;   // franco diagramado: no se informa
            }
            return true;
        }

        // 3. Jornada diagramada con horario. Las diferencias sin novedad aprobada quedan con 666
        //    hasta que la OP elige el código en la carga manual.
        if (r == null) {
            boolean guardia = d.getTipo() == TipoJornada.GUARDIA_ACTIVA || d.getTipo() == TipoJornada.GUARDIA_PASIVA;
            diferencia(det, guardia ? TipoDiferencia.GUARDIA_NO_CUBIERTA : TipoDiferencia.INASISTENCIA,
                    "Sin justificar: " + (guardia ? "guardia no cubierta" : "inasistencia") + "." + pendiente(novedades));
            return true;
        }
        if (r.omisionIngreso() || r.omisionEgreso()) {
            diferencia(det, r.omisionIngreso() ? TipoDiferencia.OMISION_INGRESO : TipoDiferencia.OMISION_EGRESO,
                    "Sin justificar. Falta la marcación de " + (r.omisionIngreso() ? "ingreso" : "egreso") + "."
                            + pendiente(novedades));
            return true;
        }
        long tarde = Duration.between(d.getFechaIngreso(), r.ingreso()).toMinutes();
        long antes = Duration.between(r.egreso(), d.getFechaEgreso()).toMinutes();
        if (tarde > tol) {
            diferencia(det, TipoDiferencia.LLEGADA_TARDE, "Sin justificar: tardanza de " + tarde
                    + " min (tolerancia " + tol + " min)." + pendiente(novedades));
        } else if (antes > tol) {
            diferencia(det, TipoDiferencia.RETIRO_ANTICIPADO, "Sin justificar: retiro anticipado de " + antes
                    + " min." + pendiente(novedades));
        } else {
            // Presente; si la jornada salió de un cambio de horario o de guardia aplicado, el 222 interno.
            boolean porCambio = novedades.stream().anyMatch(n -> esCambioAplicadoEl(n, fecha));
            det.setCodigo(porCambio ? CodigoNovedad.CAMBIO_HORARIO_GUARDIA : CodigoNovedad.PRESENTE);
            if (tarde > 0) {
                det.setObservaciones("Ingreso " + tarde + " min tarde, dentro de la tolerancia.");
            }
        }
        return true;
    }

    /** La primera novedad aprobada (o aplicada) del día que cumple la condición. */
    private static Optional<Novedad> vigente(List<Novedad> novedades, java.util.function.Predicate<Novedad> cond) {
        return novedades.stream().filter(n -> VIGENTES.contains(n.getEstado())).filter(cond).findFirst();
    }

    /**
     * Cambio de horario o de guardia aplicado que modificó la jornada del día:
     * el CG intercambia el día cedido y el recibido; el CH, los días de su período.
     */
    private static boolean esCambioAplicadoEl(Novedad n, LocalDate fecha) {
        if (n.getEstado() != EstadoNovedad.APLICADA
                || !Integer.valueOf(CodigoNovedad.CAMBIO_HORARIO_GUARDIA).equals(n.getTipo().codigoNovedad())) {
            return false;
        }
        if (n.getTipo().es(TipoNovedad.CAMBIO_GUARDIA)) {
            return fecha.equals(n.getFechaDesde()) || fecha.equals(n.getFechaHasta());
        }
        return !fecha.isBefore(n.getFechaDesde()) && !fecha.isAfter(n.getFechaHasta());
    }

    /** Código efectivo de la novedad (el propio o el de su tipo). */
    private static int codigoDe(Novedad n) {
        return n.codigoEfectivo() != null ? n.codigoEfectivo() : CodigoNovedad.SIN_JUSTIFICAR;
    }

    private static void justificar(DetalleParte det, Novedad n, Origen origen, int codigo, String obs) {
        det.setNovedadId(n.getId());
        det.setTipoNovedadId(n.getTipo().id());
        det.setOrigen(origen);
        det.setCodigo(codigo);
        det.setObservaciones(obs);
    }

    private static void diferencia(DetalleParte det, TipoDiferencia tipo, String obs) {
        det.setTipoDiferencia(tipo);
        det.setCodigo(CodigoNovedad.SIN_JUSTIFICAR);
        det.setObservaciones(obs);
    }

    /** " Novedad en trámite: falta justificada, pendiente de la DH." cuando corresponde. */
    private static String pendiente(List<Novedad> novedades) {
        return novedades.stream().filter(n -> n.getEstado().esPendiente()).findFirst()
                .map(n -> " Novedad en trámite: " + n.getTipo().nombre().toLowerCase() + ", " + n.getEstado().enFrase() + ".")
                .orElse("");
    }

    private static String textoLicencia(Novedad n) {
        return n.getMotivo() != null ? n.getMotivo() : "Licencia " + (n.getReferenciaExterna() != null
                ? n.getReferenciaExterna() : "");
    }

    private static String textoMotivo(Novedad n) {
        return n.getMotivo() != null && !n.getMotivo().isBlank() ? " (" + n.getMotivo() + ")" : ".";
    }

    static String textoDiagramada(JornadaLaboral d) {
        if (d == null) return "—";
        if (!d.tieneHorario()) {
            return switch (d.getTipo()) {
                case LICENCIA -> "Licencia";
                case FRANCO_COMPENSATORIO -> "Franco comp.";
                default -> "Franco";
            };
        }
        return rango(d.getFechaIngreso(), d.getFechaEgreso());
    }

    static String textoRegistrada(JornadaRegistrada r, JornadaLaboral d) {
        if (r == null) {
            return d != null && d.tieneHorario() ? "Sin marcaciones" : "—";
        }
        if (r.omisionEgreso()) return r.ingreso().format(HHMM) + " – sin egreso";
        if (r.omisionIngreso()) return "sin ingreso – " + r.egreso().format(HHMM);
        return rango(r.ingreso(), r.egreso());
    }

    private static String rango(LocalDateTime in, LocalDateTime out) {
        String s = in.format(HHMM) + "–" + out.format(HHMM);
        return out.toLocalDate().isAfter(in.toLocalDate()) ? s + " (+1)" : s;
    }

    /** Minutos registrados menos minutos diagramados. */
    static Integer diferencia(JornadaLaboral d, JornadaRegistrada r) {
        long diag = d != null && d.tieneHorario() ? Duration.between(d.getFechaIngreso(), d.getFechaEgreso()).toMinutes() : 0;
        if (r == null) {
            return diag > 0 ? (int) -diag : 0;
        }
        Integer reg = r.duracionMinutos();
        return reg == null ? null : (int) (reg - diag);
    }

    /** "+0:05", "−10:00". */
    public static String formatearDiferencia(Integer minutos) {
        if (minutos == null) return "—";
        String signo = minutos > 0 ? "+" : minutos < 0 ? "−" : "";
        int m = Math.abs(minutos);
        return signo + (m / 60) + ":" + String.format("%02d", m % 60);
    }

    /** Agentes de un día: unión de los que tienen jornada diagramada y registrada. */
    public static Set<UUID> involucrados(Map<UUID, JornadaLaboral> diagramadas, Map<UUID, JornadaRegistrada> registradas) {
        Set<UUID> ids = new LinkedHashSet<>(diagramadas.keySet());
        ids.addAll(registradas.keySet());
        return ids;
    }
}
