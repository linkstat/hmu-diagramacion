package ar.com.hmu.service.novedades;

import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.Severidad;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.service.diagramacion.CatalogoReglas;
import ar.com.hmu.service.diagramacion.DiagramaValidator;
import ar.com.hmu.service.diagramacion.Violacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Motor de validación de novedades (CU21): verifica la novedad contra el
 * diagrama vigente, el saldo de horas y las reglas aplicables, y devuelve
 * las violaciones con su severidad.
 *
 * <ol>
 *   <li>Datos completos según el tipo (contraparte, horas, respaldo).</li>
 *   <li>Diagrama vigente del agente para las fechas (precondición del CU20).</li>
 *   <li>Compatibilidad del cambio de guardia u horario entre las partes.</li>
 *   <li>Descanso mínimo y superposición resultantes (reglas de
 *       {@link DiagramaValidator} sobre las jornadas que quedarían).</li>
 *   <li>Banco de horas para los francos compensatorios (saldo vigente y fecha).</li>
 *   <li>Pases de salida: duración, anticipo del PSSR, tope mensual y devolución.</li>
 *   <li>Superposición con licencias y con otras novedades en trámite.</li>
 *   <li>Tope mensual de faltas justificadas (Secretaría de Salud).</li>
 * </ol>
 */
public final class ValidadorNovedad {

    public static final String RV_DATOS = "RV-NOV-DATOS";
    public static final String RV_DIAGRAMA_VIGENTE = "RV-NOV-DIAGRAMA";
    public static final String RV_COMPATIBILIDAD = "RV-NOV-CG-CH";
    public static final String RV_DESCANSO = "RV-DESCANSO-10H";
    public static final String RV_SALDO = "RV-NOV-SALDO-FC";
    public static final String RV_SUPERPOSICION = "RV-NOV-SUPERPOS";
    public static final String RV_TOPE_FALTAS = "RV-NOV-TOPE-FJ";
    public static final String RV_FC_FECHA = "RV-NOV-FC-FECHA";
    public static final String RV_PASE_MAX = "RV-NOV-PASE-MAX";
    public static final String RV_PSSR = "RV-NOV-PSSR";
    public static final String RV_PASE_TOPE = "RV-NOV-PASE-TOPE";
    public static final String RV_DEVOLUCION = "RV-NOV-DEVOL";
    /** Superposición con otra novedad en trámite: siempre advertencia. */
    public static final String RV_EN_TRAMITE = "RV-NOV-TRAMITE";

    /** Tope mensual de faltas justificadas sin autorización de la Secretaría de Salud. */
    public static final int TOPE_FALTAS_JUSTIFICADAS_MES = 2;

    /** Días alrededor de la novedad que se miran para descanso y superposición. */
    private static final int MARGEN_DIAS = 2;

    private static final DateTimeFormatter DDMM = DateTimeFormatter.ofPattern("dd/MM");
    private static final Set<EstadoNovedad> EN_TRAMITE_O_VIGENTE = EnumSet.of(
            EstadoNovedad.PENDIENTE_JEFATURA, EstadoNovedad.PENDIENTE_OFICINA_PERSONAL,
            EstadoNovedad.PENDIENTE_DIRECCION, EstadoNovedad.APROBADA, EstadoNovedad.APLICADA);

    private final FuenteDatosNovedad datos;
    private CatalogoReglas reglas = CatalogoReglas.PREDETERMINADO;

    public ValidadorNovedad(FuenteDatosNovedad datos) {
        this.datos = datos;
    }

    /**
     * @param n               la novedad a validar (tipo, agente y fechas cargados).
     * @param tieneRespaldo   la solicitud trae al menos un adjunto.
     */
    public ResultadoValidacion validar(Novedad n, boolean tieneRespaldo) {
        List<ViolacionNovedad> v = new ArrayList<>();
        TipoNovedad tipo = n.getTipo();

        if (!validarDatos(n, tieneRespaldo, v)) {
            return new ResultadoValidacion(v, false);
        }
        reglas = datos.reglas(n.getFechaDesde());

        List<JornadaLaboral> propias = datos.jornadasVigentes(n.getAgenteId(),
                n.getFechaDesde().minusDays(MARGEN_DIAS), n.getFechaHasta().plusDays(MARGEN_DIAS));

        if (!tipo.esExterna() && jornadaDel(propias, n.getFechaDesde()).isEmpty()) {
            v.add(bloq(RV_DIAGRAMA_VIGENTE, "El agente no tiene un diagrama aprobado para el "
                    + n.getFechaDesde().format(DDMM) + "."));
            return new ResultadoValidacion(v, false);
        }

        switch (tipo.codigo()) {
            case TipoNovedad.CAMBIO_GUARDIA -> validarCambioGuardia(n, propias, v);
            case TipoNovedad.CAMBIO_HORARIO -> validarCambioHorario(n, propias, v);
            case TipoNovedad.FRANCO_COMPENSATORIO -> validarFrancoCompensatorio(n, propias, v);
            case TipoNovedad.PASE_SALIDA_OFICIAL, TipoNovedad.PASE_SALIDA_PARTICULAR -> validarPase(n, propias, v);
            case TipoNovedad.PASE_SIN_RETORNO -> validarPaseSinRetorno(n, propias, v);
            case TipoNovedad.DEVOLUCION_PASE -> validarDevolucion(n, propias, v);
            default -> { /* FJ, OM, LIC, HE: sin reglas de jornada propias (las HE se computan al cotejar) */ }
        }
        if (n.getTipo().esPaseADevolver() && v.stream().noneMatch(ViolacionNovedad::esBloqueante)) {
            validarTopePases(n, v);
        }

        validarSuperposiciones(n, v);
        boolean requiereSS = reglas.activa(RV_TOPE_FALTAS) && validarTopeFaltas(n, v);
        return new ResultadoValidacion(segunCatalogo(v), requiereSS);
    }

    /** Severidad y activación de cada regla según el catálogo vigente (CU08). */
    private List<ViolacionNovedad> segunCatalogo(List<ViolacionNovedad> v) {
        List<ViolacionNovedad> r = new ArrayList<>();
        for (ViolacionNovedad x : v) {
            if (!reglas.activa(x.regla())) {
                continue;
            }
            Severidad s = reglas.severidad(x.regla(), x.severidad());
            String mensaje = reglas.conFundamento(x.regla(), x.mensaje());
            r.add(s == x.severidad() && mensaje.equals(x.mensaje()) ? x : new ViolacionNovedad(x.regla(), s, mensaje));
        }
        return r;
    }

    // ============================================================
    // Reglas
    // ============================================================

    private boolean validarDatos(Novedad n, boolean tieneRespaldo, List<ViolacionNovedad> v) {
        TipoNovedad tipo = n.getTipo();
        if (tipo == null || n.getAgenteId() == null || n.getFechaDesde() == null || n.getFechaHasta() == null) {
            v.add(bloq(RV_DATOS, "Complete el tipo de novedad, el agente y las fechas."));
            return false;
        }
        if (n.getFechaHasta().isBefore(n.getFechaDesde())) {
            v.add(bloq(RV_DATOS, "La fecha hasta no puede ser anterior a la fecha desde."));
            return false;
        }
        if (tipo.requiereContraparte()) {
            if (n.getContraparteAgenteId() == null) {
                v.add(bloq(RV_DATOS, "El " + tipo.nombre().toLowerCase() + " requiere un agente contraparte."));
                return false;
            }
            if (n.getContraparteAgenteId().equals(n.getAgenteId())) {
                v.add(bloq(RV_DATOS, "La contraparte debe ser otro agente."));
                return false;
            }
        }
        if (requiereHoras(tipo) && (n.getHoraDesde() == null || n.getHoraHasta() == null)) {
            v.add(bloq(RV_DATOS, "Indique la hora desde y la hora hasta."));
            return false;
        }
        if (tipo.es(TipoNovedad.PASE_SIN_RETORNO) && n.getHoraDesde() == null) {
            v.add(bloq(RV_DATOS, "Indique la hora de salida."));
            return false;
        }
        if (tipo.es(TipoNovedad.DEVOLUCION_PASE) && n.getNovedadOrigenId() == null) {
            v.add(bloq(RV_DATOS, "Elija el pase de salida que se devuelve."));
            return false;
        }
        if (tipo.requiereDocumentacion() && !tieneRespaldo) {
            v.add(bloq(RV_DATOS, "La " + tipo.nombre().toLowerCase()
                    + " requiere adjuntar la documentación respaldatoria."));
        }
        if (n.getMotivo() == null || n.getMotivo().isBlank()) {
            v.add(bloq(RV_DATOS, "Indique el motivo de la solicitud."));
        }
        if (n.getJustificacion() != null && n.getJustificacion().strip().length() > Novedad.JUSTIFICACION_MAX) {
            v.add(bloq(RV_DATOS, "La justificación no puede superar los " + Novedad.JUSTIFICACION_MAX
                    + " caracteres."));
        }
        return true;
    }

    static boolean requiereHoras(TipoNovedad tipo) {
        return tipo.es(TipoNovedad.CAMBIO_HORARIO) || tipo.es(TipoNovedad.PASE_SALIDA_OFICIAL)
                || tipo.es(TipoNovedad.PASE_SALIDA_PARTICULAR) || tipo.es(TipoNovedad.DEVOLUCION_PASE);
    }

    /**
     * Cambio de guardia: el agente cede su guardia del {@code fechaDesde} y
     * recibe la de la contraparte del {@code fechaHasta}. Se arman las
     * jornadas que le quedarían a cada uno y se les aplican las reglas
     * estructurales del diagrama (superposición y descanso de 10 h).
     */
    private void validarCambioGuardia(Novedad n, List<JornadaLaboral> propias, List<ViolacionNovedad> v) {
        UUID servA = datos.servicioDe(n.getAgenteId());
        UUID servB = datos.servicioDe(n.getContraparteAgenteId());
        if (!Objects.equals(servA, servB)) {
            v.add(bloq(RV_COMPATIBILIDAD, "La contraparte pertenece a otro servicio: el cambio de guardia "
                    + "sólo puede hacerse entre agentes del mismo servicio."));
            return;
        }
        if (n.getFechaDesde().equals(n.getFechaHasta())) {
            v.add(bloq(RV_COMPATIBILIDAD, "Ambas guardias son del mismo día: no hay intercambio posible."));
            return;
        }
        List<JornadaLaboral> ajenas = datos.jornadasVigentes(n.getContraparteAgenteId(),
                n.getFechaDesde().minusDays(MARGEN_DIAS), n.getFechaHasta().plusDays(MARGEN_DIAS));
        LocalDate dCedida = fechaCedida(n, propias, ajenas);
        LocalDate dRecibida = dCedida.equals(n.getFechaDesde()) ? n.getFechaHasta() : n.getFechaDesde();
        Optional<JornadaLaboral> cedida = jornadaConHorario(propias, dCedida);
        if (cedida.isEmpty()) {
            v.add(bloq(RV_COMPATIBILIDAD, "El agente no tiene una guardia diagramada el "
                    + dCedida.format(DDMM) + " para ceder."));
            return;
        }
        Optional<JornadaLaboral> recibida = jornadaConHorario(ajenas, dRecibida);
        if (recibida.isEmpty()) {
            v.add(bloq(RV_COMPATIBILIDAD, "La contraparte no tiene una guardia diagramada el "
                    + dRecibida.format(DDMM) + " para entregar."));
            return;
        }

        List<JornadaLaboral> resultanteA = reemplazar(propias, cedida.get(), copiaPara(recibida.get(), n.getAgenteId()));
        List<JornadaLaboral> resultanteB = reemplazar(ajenas, recibida.get(), copiaPara(cedida.get(), n.getContraparteAgenteId()));
        agregarReglasDiagrama(resultanteA, "al agente", v);
        agregarReglasDiagrama(resultanteB, "a la contraparte", v);
    }

    /**
     * En un cambio de guardia la novedad guarda el rango ordenado
     * (fechaDesde ≤ fechaHasta, Tabla 52); la guardia que cede el agente es la
     * del día en que él la tiene diagramada (y la contraparte no). Ante la
     * duda, la del {@code fechaDesde}.
     */
    public static LocalDate fechaCedida(Novedad n, List<JornadaLaboral> delAgente, List<JornadaLaboral> deLaContraparte) {
        boolean agenteEnDesde = jornadaConHorario(delAgente, n.getFechaDesde()).isPresent();
        boolean agenteEnHasta = jornadaConHorario(delAgente, n.getFechaHasta()).isPresent();
        boolean otraEnDesde = jornadaConHorario(deLaContraparte, n.getFechaDesde()).isPresent();
        boolean otraEnHasta = jornadaConHorario(deLaContraparte, n.getFechaHasta()).isPresent();
        if (!(agenteEnDesde && otraEnHasta) && agenteEnHasta && otraEnDesde) {
            return n.getFechaHasta();
        }
        if (!agenteEnDesde && agenteEnHasta && !otraEnHasta) {
            return n.getFechaHasta();
        }
        return n.getFechaDesde();
    }

    /** Cambio de horario: nueva franja para la jornada del {@code fechaDesde}. */
    private void validarCambioHorario(Novedad n, List<JornadaLaboral> propias, List<ViolacionNovedad> v) {
        Optional<JornadaLaboral> original = jornadaConHorario(propias, n.getFechaDesde());
        if (original.isEmpty()) {
            v.add(bloq(RV_COMPATIBILIDAD, "El agente no tiene una jornada diagramada el "
                    + n.getFechaDesde().format(DDMM) + ": corresponde un cambio de guardia."));
            return;
        }
        JornadaLaboral nueva = copiaPara(original.get(), n.getAgenteId());
        nueva.setFechaIngreso(n.getFechaDesde().atTime(n.getHoraDesde()));
        nueva.setFechaEgreso(egreso(n.getFechaDesde(), n.getHoraDesde(), n.getHoraHasta()));
        agregarReglasDiagrama(reemplazar(propias, original.get(), nueva), "al agente", v);
    }

    private void validarFrancoCompensatorio(Novedad n, List<JornadaLaboral> propias, List<ViolacionNovedad> v) {
        BigDecimal horas = n.getCantHoras();
        if (horas == null) {
            long minutos = propias.stream()
                    .filter(j -> j.tieneHorario() && !j.getFecha().isBefore(n.getFechaDesde())
                            && !j.getFecha().isAfter(n.getFechaHasta()))
                    .mapToLong(j -> j.calcularDuracion().toMinutes()).sum();
            horas = BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            n.setCantHoras(horas);
        }
        if (horas.signum() == 0) {
            v.add(bloq(RV_SALDO, "El agente no tiene jornadas diagramadas en el período: no hay horas que compensar."));
            return;
        }
        BigDecimal saldo = datos.saldoHoras(n.getAgenteId());
        if (saldo.compareTo(horas) < 0) {
            v.add(bloq(RV_SALDO, "Saldo vigente insuficiente en el banco de horas: " + fmt(saldo)
                    + " h disponibles (sin contar lo vencido), se requieren " + fmt(horas) + " h."));
        }
        // Fecha: normalmente futura; retroactiva dentro del mismo mes o, si cambió el mes, hasta el día N.
        LocalDate hoy = datos.hoy();
        if (n.getFechaDesde().isBefore(hoy)) {
            int limite = reglas.entero(RV_FC_FECHA, "diaLimiteMesSiguiente", 7);
            YearMonth mesFc = YearMonth.from(n.getFechaDesde());
            YearMonth mesHoy = YearMonth.from(hoy);
            boolean admitido = mesFc.equals(mesHoy)
                    || (mesFc.plusMonths(1).equals(mesHoy) && hoy.getDayOfMonth() <= limite);
            if (!admitido) {
                v.add(bloq(RV_FC_FECHA, "Un franco compensatorio retroactivo sólo puede cargarse dentro del mismo "
                        + "mes o, si cambió el mes, hasta el día " + limite + " del mes siguiente."));
            }
        }
    }

    /** Pase de salida: la franja debe caer dentro de la jornada diagramada. */
    private void validarPase(Novedad n, List<JornadaLaboral> propias, List<ViolacionNovedad> v) {
        Optional<JornadaLaboral> j = jornadaConHorario(propias, n.getFechaDesde());
        LocalDateTime desde = n.getFechaDesde().atTime(n.getHoraDesde());
        LocalDateTime hasta = egreso(n.getFechaDesde(), n.getHoraDesde(), n.getHoraHasta());
        n.setCantHoras(horasEntre(desde, hasta));
        if (j.isEmpty() || desde.isBefore(j.get().getFechaIngreso()) || hasta.isAfter(j.get().getFechaEgreso())) {
            v.add(bloq(RV_COMPATIBILIDAD, "El pase de salida debe estar dentro de la jornada diagramada del "
                    + n.getFechaDesde().format(DDMM) + "."));
            return;
        }
        // Con retorno: máximo de horas fuera por pase (el oficial no tiene tope).
        if (n.getTipo().es(TipoNovedad.PASE_SALIDA_PARTICULAR)) {
            int max = reglas.entero(RV_PASE_MAX, "horasMaximas", 3);
            if (Duration.between(desde, hasta).toMinutes() > max * 60L) {
                v.add(bloq(RV_PASE_MAX, "El pase de salida con retorno no puede superar las " + max
                        + " h fuera de la jornada."));
            }
        }
    }

    /**
     * Pase de salida sin retorno: sale a la hora indicada y no vuelve; puede
     * iniciarse hasta N horas antes del egreso diagramado (RV-NOV-PSSR). Las
     * horas a devolver van de la salida al egreso.
     */
    private void validarPaseSinRetorno(Novedad n, List<JornadaLaboral> propias, List<ViolacionNovedad> v) {
        Optional<JornadaLaboral> j = jornadaConHorario(propias, n.getFechaDesde());
        if (j.isEmpty()) {
            v.add(bloq(RV_COMPATIBILIDAD, "El agente no tiene una jornada diagramada el "
                    + n.getFechaDesde().format(DDMM) + "."));
            return;
        }
        LocalDateTime egreso = j.get().getFechaEgreso();
        LocalDateTime salida = n.getFechaDesde().atTime(n.getHoraDesde());
        if (salida.isBefore(j.get().getFechaIngreso())) {
            salida = salida.plusDays(1);   // jornada que cruza la medianoche
        }
        n.setHoraHasta(egreso.toLocalTime());
        if (!salida.isAfter(j.get().getFechaIngreso()) || !salida.isBefore(egreso)) {
            v.add(bloq(RV_COMPATIBILIDAD, "La salida debe estar dentro de la jornada diagramada ("
                    + j.get().getFechaIngreso().toLocalTime() + " a " + egreso.toLocalTime() + ")."));
            return;
        }
        n.setCantHoras(horasEntre(salida, egreso));
        int anticipo = reglas.entero(RV_PSSR, "anticipoMaxHoras", 3);
        if (Duration.between(salida, egreso).toMinutes() > anticipo * 60L) {
            v.add(bloq(RV_PSSR, "El pase sin retorno puede iniciarse hasta " + anticipo + " h antes del egreso ("
                    + egreso.toLocalTime() + "): desde las " + egreso.minusHours(anticipo).toLocalTime() + "."));
        }
    }

    /** Tope mensual de horas sumando los pases con y sin retorno (RV-NOV-PASE-TOPE). */
    private void validarTopePases(Novedad n, List<ViolacionNovedad> v) {
        if (n.getCantHoras() == null) {
            return;
        }
        int tope = reglas.entero(RV_PASE_TOPE, "topeMensualHoras", 5);
        YearMonth mes = YearMonth.from(n.getFechaDesde());
        BigDecimal total = n.getCantHoras();
        for (Novedad o : datos.novedadesEnRango(n.getAgenteId(), mes.atDay(1), mes.atEndOfMonth(), EN_TRAMITE_O_VIGENTE)) {
            if (o.getTipo().esPaseADevolver() && !Objects.equals(o.getId(), n.getId())
                    && n.getAgenteId().equals(o.getAgenteId()) && o.getCantHoras() != null) {
                total = total.add(o.getCantHoras());
            }
        }
        if (total.compareTo(BigDecimal.valueOf(tope)) > 0) {
            v.add(bloq(RV_PASE_TOPE, "Con este pase el agente suma " + fmt(total) + " h de pases de salida en el mes "
                    + "(tope " + tope + " h, con y sin retorno)."));
        }
    }

    /**
     * Devolución de un pase de salida: referencia al pase de origen del mismo
     * agente, dentro del plazo (con retorno, el mismo día; PSSR, N días
     * corridos), sin superar las horas pendientes y fuera de la jornada
     * diagramada.
     */
    private void validarDevolucion(Novedad n, List<JornadaLaboral> propias, List<ViolacionNovedad> v) {
        Novedad pase = datos.novedad(n.getNovedadOrigenId());
        if (pase == null || !pase.getTipo().esPaseADevolver() || !n.getAgenteId().equals(pase.getAgenteId())) {
            v.add(bloq(RV_DEVOLUCION, "La devolución debe referirse a un pase de salida del mismo agente."));
            return;
        }
        if (pase.getEstado() != EstadoNovedad.APROBADA && pase.getEstado() != EstadoNovedad.APLICADA) {
            v.add(bloq(RV_DEVOLUCION, "El pase de salida del " + pase.getFechaDesde().format(DDMM)
                    + " todavía no está aprobado."));
            return;
        }
        LocalDateTime desde = n.getFechaDesde().atTime(n.getHoraDesde());
        LocalDateTime hasta = egreso(n.getFechaDesde(), n.getHoraDesde(), n.getHoraHasta());
        n.setCantHoras(horasEntre(desde, hasta));
        boolean sinRetorno = pase.getTipo().es(TipoNovedad.PASE_SIN_RETORNO);
        int plazo = sinRetorno ? reglas.entero(RV_DEVOLUCION, "plazoSinRetornoDias", 7)
                : reglas.entero(RV_DEVOLUCION, "plazoConRetornoDias", 0);
        long dias = ChronoUnit.DAYS.between(pase.getFechaDesde(), n.getFechaDesde());
        if (dias < 0 || dias > plazo) {
            v.add(bloq(RV_DEVOLUCION, sinRetorno
                    ? "El pase sin retorno se devuelve dentro de los " + plazo + " días corridos siguientes ("
                            + "hasta el " + pase.getFechaDesde().plusDays(plazo).format(DDMM) + ")."
                    : plazo == 0 ? "El pase con retorno se devuelve el mismo día ("
                            + pase.getFechaDesde().format(DDMM) + ")."
                            : "El pase con retorno se devuelve dentro de los " + plazo + " días siguientes."));
        }
        BigDecimal devuelto = BigDecimal.ZERO;
        for (Novedad d : datos.devolucionesDe(pase.getId())) {
            if (!Objects.equals(d.getId(), n.getId()) && d.getCantHoras() != null) {
                devuelto = devuelto.add(d.getCantHoras());
            }
        }
        BigDecimal pendiente = (pase.getCantHoras() != null ? pase.getCantHoras() : BigDecimal.ZERO).subtract(devuelto);
        if (n.getCantHoras().compareTo(pendiente) > 0) {
            v.add(bloq(RV_DEVOLUCION, "Del pase del " + pase.getFechaDesde().format(DDMM) + " quedan "
                    + fmt(pendiente.max(BigDecimal.ZERO)) + " h por devolver."));
        }
        for (JornadaLaboral j : propias) {
            if (j.tieneHorario() && desde.isBefore(j.getFechaEgreso()) && hasta.isAfter(j.getFechaIngreso())) {
                v.add(bloq(RV_COMPATIBILIDAD, "La devolución debe hacerse fuera de la jornada diagramada del "
                        + j.getFecha().format(DDMM) + "."));
                return;
            }
        }
    }

    private void validarSuperposiciones(Novedad n, List<ViolacionNovedad> v) {
        List<Novedad> otras = datos.novedadesEnRango(n.getAgenteId(), n.getFechaDesde(), n.getFechaHasta(),
                EN_TRAMITE_O_VIGENTE);
        for (Novedad o : otras) {
            if (o.getId() != null && o.getId().equals(n.getId())) {
                continue;
            }
            String rango = rango(o);
            if (o.getTipo().esExterna() && o.getEstado() != null
                    && (o.getEstado() == EstadoNovedad.APROBADA || o.getEstado() == EstadoNovedad.APLICADA)) {
                if (n.getTipo().esExterna()) {
                    v.add(adv(RV_EN_TRAMITE, "Se superpone con otra licencia registrada " + rango + "."));
                } else {
                    v.add(bloq(RV_SUPERPOSICION, "Se superpone con la licencia del agente " + rango
                            + (o.getReferenciaExterna() != null ? " (" + o.getReferenciaExterna() + ")" : "") + "."));
                }
            } else if (o.getTipo().es(n.getTipo().codigo())) {
                v.add(adv(RV_EN_TRAMITE, "Ya hay un " + o.getTipo().nombre().toLowerCase() + " "
                        + o.getEstado().enFrase() + " " + rango + "."));
            } else if (n.getTipo().esExterna()) {
                v.add(adv(RV_EN_TRAMITE, "Se superpone con un " + o.getTipo().nombre().toLowerCase() + " "
                        + rango + ", " + o.getEstado().enFrase() + "."));
            }
        }
        // Jornadas ya marcadas como licencia en el diagrama vigente.
        if (!n.getTipo().esExterna()) {
            boolean enLicencia = datos.jornadasVigentes(n.getAgenteId(), n.getFechaDesde(), n.getFechaHasta())
                    .stream().anyMatch(j -> j.getTipo() == TipoJornada.LICENCIA);
            if (enLicencia && v.stream().noneMatch(x -> x.regla().equals(RV_SUPERPOSICION) && x.esBloqueante())) {
                v.add(bloq(RV_SUPERPOSICION, "El agente está de licencia en esas fechas."));
            }
        }
    }

    /** @return true si se supera el tope y hace falta la Secretaría de Salud. */
    private boolean validarTopeFaltas(Novedad n, List<ViolacionNovedad> v) {
        if (!n.getTipo().es(TipoNovedad.INASISTENCIA_JUSTIFICADA)) {
            return false;
        }
        int tope = reglas.entero(RV_TOPE_FALTAS, "topeMensual", TOPE_FALTAS_JUSTIFICADAS_MES);
        YearMonth mes = YearMonth.from(n.getFechaDesde());
        long dias = diasEnMes(n, mes);
        for (Novedad o : datos.novedadesEnRango(n.getAgenteId(), mes.atDay(1), mes.atEndOfMonth(), EN_TRAMITE_O_VIGENTE)) {
            if (o.getTipo().es(TipoNovedad.INASISTENCIA_JUSTIFICADA) && !Objects.equals(o.getId(), n.getId())
                    && n.getAgenteId().equals(o.getAgenteId())) {
                dias += diasEnMes(o, mes);
            }
        }
        if (dias > tope) {
            v.add(adv(RV_TOPE_FALTAS, "Con esta solicitud el agente suma " + dias
                    + " faltas justificadas en el mes (tope " + tope
                    + "): requiere la autorización de la Secretaría de Salud."));
            return true;
        }
        return false;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void agregarReglasDiagrama(List<JornadaLaboral> jornadas, String aQuien, List<ViolacionNovedad> v) {
        for (Violacion violacion : DiagramaValidator.validar(jornadas, reglas)) {
            String regla = violacion.mensaje().startsWith("Descanso") ? RV_DESCANSO : RV_COMPATIBILIDAD;
            v.add(bloq(regla, "Tras el cambio, " + aQuien + " le queda: " + violacion.mensaje()));
        }
    }

    private static Optional<JornadaLaboral> jornadaDel(List<JornadaLaboral> jornadas, LocalDate fecha) {
        return jornadas.stream().filter(j -> fecha.equals(j.getFecha())).findFirst();
    }

    private static Optional<JornadaLaboral> jornadaConHorario(List<JornadaLaboral> jornadas, LocalDate fecha) {
        return jornadaDel(jornadas, fecha).filter(JornadaLaboral::tieneHorario);
    }

    private static List<JornadaLaboral> reemplazar(List<JornadaLaboral> jornadas, JornadaLaboral sale,
                                                   JornadaLaboral entra) {
        List<JornadaLaboral> r = new ArrayList<>();
        for (JornadaLaboral j : jornadas) {
            if (j == sale) {
                continue;
            }
            if (j.getFecha().equals(entra.getFecha()) && !j.tieneHorario()) {
                continue;   // el franco del día que recibe la guardia deja de serlo
            }
            r.add(j);
        }
        r.add(entra);
        return r;
    }

    private static JornadaLaboral copiaPara(JornadaLaboral j, UUID empleadoId) {
        return new JornadaLaboral(null, j.getDiagramaId(), empleadoId, j.getFecha(), j.getTipo(),
                j.getFechaIngreso(), j.getFechaEgreso(), j.getObservaciones());
    }

    static LocalDateTime egreso(LocalDate fecha, LocalTime desde, LocalTime hasta) {
        return hasta.isAfter(desde) ? fecha.atTime(hasta) : fecha.plusDays(1).atTime(hasta);
    }

    private static BigDecimal horasEntre(LocalDateTime desde, LocalDateTime hasta) {
        return BigDecimal.valueOf(Duration.between(desde, hasta).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static long diasEnMes(Novedad n, YearMonth mes) {
        LocalDate d = n.getFechaDesde().isBefore(mes.atDay(1)) ? mes.atDay(1) : n.getFechaDesde();
        LocalDate h = n.getFechaHasta().isAfter(mes.atEndOfMonth()) ? mes.atEndOfMonth() : n.getFechaHasta();
        return h.isBefore(d) ? 0 : ChronoUnit.DAYS.between(d, h) + 1;
    }

    private static String rango(Novedad o) {
        return o.getFechaDesde().equals(o.getFechaHasta())
                ? "el " + o.getFechaDesde().format(DDMM)
                : "del " + o.getFechaDesde().format(DDMM) + " al " + o.getFechaHasta().format(DDMM);
    }

    private static String fmt(BigDecimal horas) {
        return horas.stripTrailingZeros().toPlainString();
    }

    private static ViolacionNovedad bloq(String regla, String mensaje) {
        return new ViolacionNovedad(regla, Severidad.BLOQUEANTE, mensaje);
    }

    private static ViolacionNovedad adv(String regla, String mensaje) {
        return new ViolacionNovedad(regla, Severidad.ADVERTENCIA, mensaje);
    }
}
