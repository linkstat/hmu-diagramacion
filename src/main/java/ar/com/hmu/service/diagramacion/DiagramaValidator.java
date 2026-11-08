package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Severidad;
import ar.com.hmu.model.TipoJornada;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validador de las jornadas de un diagrama de servicio.
 *
 * <p><b>Reglas estructurales</b> ({@link #validar}) — bloquean el envío a
 * aprobación:</p>
 * <ol>
 *   <li>Jornada de tipo con horario ({@code requiereHorario()}) sin
 *       ingreso/egreso cargados, o con egreso anterior o igual al ingreso.</li>
 *   <li>Superposición de jornadas del mismo empleado (los intervalos
 *       [ingreso, egreso] no pueden pisarse; el nocturno que cruza
 *       medianoche se compara por sus timestamps completos).</li>
 *   <li>Descanso mínimo: no pueden transcurrir menos de
 *       {@value #DESCANSO_MINIMO_HORAS} horas corridas entre el egreso de
 *       una jornada y el ingreso de la siguiente (condicionalidad general
 *       del HMU).</li>
 * </ol>
 *
 * <p><b>Advertencias de carga mensual</b> ({@link #advertenciasCargaMensual})
 * — no bloquean, se muestran en el panel de validación: comparan las horas
 * totales de cada empleado contra lo esperado por su modalidad, en las
 * modalidades donde el número mensual es directo (Nocturno: exacto;
 * Feriante y GuardiaEnfermeria: mínimo). Las modalidades semanales
 * (Estandar, Semanal, DXI, GuardiaMedica) requieren lógica de ventanas por
 * semana — deuda para un pase posterior.</p>
 *
 * <p><b>Parametrización (CU08):</b> los parámetros, la severidad y la
 * activación de cada regla vienen de {@link CatalogoReglas} (tabla
 * ReglaValidacion, versión vigente a la fecha del diagrama). Las firmas sin
 * catálogo usan los valores predeterminados de las constantes.</p>
 */
public final class DiagramaValidator {

    static final int DESCANSO_MINIMO_HORAS = 10;

    /** Duración mínima de toda jornada con horario (caso "viene 1 hora a
     *  completar la semana"). */
    public static final int DURACION_MINIMA_HORAS = 1;
    /** Máximo de un turno normal (definición del usuario 2026-07-08). */
    public static final int MAX_TURNO_HORAS = 12;
    /** Máximo PLANIFICADO de una guardia: 24 h exactas — la extensión del
     *  egreso real pertenece al registro de marcaciones, no al diagrama. */
    public static final int MAX_GUARDIA_HORAS = 24;

    private DiagramaValidator() {
        // Sólo métodos estáticos.
    }

    /** Resultado del motor: lo que impide presentar y lo que sólo se informa. */
    public record Resultado(List<Violacion> bloqueantes, List<Violacion> advertencias) {
    }

    /**
     * Reglas estructurales sobre las jornadas del diagrama, con los valores
     * predeterminados. Lista vacía = diagrama válido para enviar a aprobación.
     */
    public static List<Violacion> validar(List<JornadaLaboral> jornadas) {
        return validar(jornadas, CatalogoReglas.PREDETERMINADO);
    }

    /** Reglas estructurales BLOQUEANTES según el catálogo vigente (CU08). */
    public static List<Violacion> validar(List<JornadaLaboral> jornadas, CatalogoReglas reglas) {
        return evaluar(jornadas, Map.of(), reglas).bloqueantes();
    }

    /**
     * Motor completo (CU13): reglas estructurales y de carga mensual, cada
     * violación con su regla; la severidad de cada regla (catálogo) decide si
     * bloquea o advierte, y las reglas inactivas no se evalúan.
     */
    public static Resultado evaluar(List<JornadaLaboral> jornadas, Map<UUID, HorarioBase> horariosPorEmpleado,
                                    CatalogoReglas reglas) {
        return evaluar(jornadas, horariosPorEmpleado, reglas, 0);
    }

    /** Ídem, con la dotación mínima del servicio (0 = no se controla). */
    public static Resultado evaluar(List<JornadaLaboral> jornadas, Map<UUID, HorarioBase> horariosPorEmpleado,
                                    CatalogoReglas reglas, int dotacionMinima) {
        List<Violacion> todas = new ArrayList<>(estructurales(jornadas, reglas));
        todas.addAll(cargaMensual(jornadas, horariosPorEmpleado, reglas));
        todas.addAll(dotacion(jornadas, dotacionMinima, reglas));
        List<Violacion> bloqueantes = new ArrayList<>();
        List<Violacion> advertencias = new ArrayList<>();
        for (Violacion v : todas) {
            v = conFundamento(v, reglas);
            Severidad porDefecto = esDeCarga(v.regla()) ? Severidad.ADVERTENCIA : Severidad.BLOQUEANTE;
            if (reglas.severidad(v.regla(), porDefecto) == Severidad.BLOQUEANTE) {
                bloqueantes.add(v);
            } else {
                advertencias.add(v);
            }
        }
        return new Resultado(bloqueantes, advertencias);
    }

    /** Franjas horarias del control de dotación: nombre, hora de inicio y fin (parámetros de RV-DOTACION-MIN). */
    private record Franja(String nombre, int desde, int hasta, int porcentaje) {
        /** Instante de referencia: la mitad de la franja (la noche cruza la medianoche). */
        java.time.LocalDateTime referencia(java.time.LocalDate dia) {
            int duracion = ((hasta - desde) + 24) % 24;
            return dia.atTime(desde, 0).plusMinutes(duracion * 30L);
        }
    }

    /**
     * Dotación mínima del servicio (CU13, RF10): para cada día y franja
     * horaria, los agentes con jornada en la mitad de la franja deben ser al
     * menos el porcentaje de la dotación mínima que indica la regla.
     */
    static List<Violacion> dotacion(List<JornadaLaboral> jornadas, int dotacionMinima, CatalogoReglas reglas) {
        List<Violacion> v = new ArrayList<>();
        if (dotacionMinima <= 0 || jornadas == null || jornadas.isEmpty()
                || !reglas.activa(CatalogoReglas.DOTACION_MIN)) {
            return v;
        }
        String r = CatalogoReglas.DOTACION_MIN;
        int m = reglas.entero(r, "mananaDesde", 7);
        int t = reglas.entero(r, "tardeDesde", 14);
        int n = reglas.entero(r, "nocheDesde", 21);
        List<Franja> franjas = List.of(
                new Franja("mañana", m, t, reglas.entero(r, "porcentajeManiana", 100)),
                new Franja("tarde", t, n, reglas.entero(r, "porcentajeTarde", 50)),
                new Franja("noche", n, m, reglas.entero(r, "porcentajeNoche", 50)));
        java.util.TreeSet<java.time.LocalDate> dias = new java.util.TreeSet<>();
        jornadas.forEach(j -> dias.add(j.getFecha()));
        for (java.time.LocalDate dia : dias) {
            for (Franja f : franjas) {
                int requeridos = (int) Math.ceil(dotacionMinima * f.porcentaje() / 100.0);
                if (requeridos <= 0) continue;
                java.time.LocalDateTime ref = f.referencia(dia);
                long presentes = jornadas.stream()
                        .filter(j -> j.tieneHorario() && !j.getFechaIngreso().isAfter(ref) && j.getFechaEgreso().isAfter(ref))
                        .map(JornadaLaboral::getEmpleadoId).distinct().count();
                if (presentes < requeridos) {
                    v.add(new Violacion(null, dia, "Dotación insuficiente el " + f(dia) + ", franja " + f.nombre()
                            + " (" + f.desde() + " a " + f.hasta() + " h): " + presentes
                            + (presentes == 1 ? " agente diagramado" : " agentes diagramados")
                            + ", mínimo " + requeridos + ".", r));
                }
            }
        }
        return v;
    }

    private static final java.time.format.DateTimeFormatter DDMM =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM");

    private static String f(java.time.LocalDate d) {
        return d.format(DDMM);
    }

    private static boolean esDeCarga(String regla) {
        return CatalogoReglas.CARGA_NOCTURNO.equals(regla) || CatalogoReglas.CARGA_FERIANTE.equals(regla)
                || CatalogoReglas.CARGA_ENFERMERIA.equals(regla) || CatalogoReglas.DOTACION_MIN.equals(regla);
    }

    private static List<Violacion> estructurales(List<JornadaLaboral> jornadas, CatalogoReglas reglas) {
        List<Violacion> violaciones = new ArrayList<>();
        if (jornadas == null || jornadas.isEmpty()) {
            return violaciones;
        }
        int descansoMinimo = reglas.entero(CatalogoReglas.DESCANSO, "horasMinimas", DESCANSO_MINIMO_HORAS);

        Map<UUID, List<JornadaLaboral>> porEmpleado = agruparConHorario(jornadas, violaciones, reglas);

        for (Map.Entry<UUID, List<JornadaLaboral>> entry : porEmpleado.entrySet()) {
            List<JornadaLaboral> delEmpleado = entry.getValue();
            delEmpleado.sort(Comparator.comparing(JornadaLaboral::getFechaIngreso));

            for (int i = 1; i < delEmpleado.size(); i++) {
                JornadaLaboral previa = delEmpleado.get(i - 1);
                JornadaLaboral actual = delEmpleado.get(i);

                if (actual.getFechaIngreso().isBefore(previa.getFechaEgreso())) {
                    if (reglas.activa(CatalogoReglas.SUPERPOSICION)) {
                        violaciones.add(new Violacion(entry.getKey(), actual.getFecha(),
                                "Superposición de jornadas: la jornada del " + f(actual.getFecha())
                                        + " comienza antes de que termine la del " + f(previa.getFecha()) + ".",
                                CatalogoReglas.SUPERPOSICION));
                    }
                } else if (reglas.activa(CatalogoReglas.DESCANSO)) {
                    Duration descanso = Duration.between(previa.getFechaEgreso(), actual.getFechaIngreso());
                    if (descanso.toHours() < descansoMinimo) {
                        violaciones.add(new Violacion(entry.getKey(), actual.getFecha(),
                                "Descanso insuficiente: " + descanso.toHours() + " h entre el egreso del "
                                        + f(previa.getFecha()) + " y el ingreso del " + f(actual.getFecha())
                                        + " (mínimo " + descansoMinimo + " h corridas).",
                                CatalogoReglas.DESCANSO));
                    }
                }
            }
        }
        return violaciones;
    }

    /**
     * Advertencias de carga horaria mensual por empleado, para las
     * modalidades con número mensual directo. No bloquean el envío.
     *
     * @param jornadas            jornadas del diagrama.
     * @param horariosPorEmpleado horario vigente de cada empleado; los
     *                            empleados ausentes del mapa se saltean.
     */
    public static List<Violacion> advertenciasCargaMensual(List<JornadaLaboral> jornadas,
                                                           Map<UUID, HorarioBase> horariosPorEmpleado) {
        return cargaMensual(jornadas, horariosPorEmpleado, CatalogoReglas.PREDETERMINADO);
    }

    private static List<Violacion> cargaMensual(List<JornadaLaboral> jornadas,
                                                Map<UUID, HorarioBase> horariosPorEmpleado,
                                                CatalogoReglas reglas) {
        List<Violacion> advertencias = new ArrayList<>();
        if (jornadas == null || jornadas.isEmpty() || horariosPorEmpleado == null) {
            return advertencias;
        }

        Map<UUID, Long> horasPorEmpleado = new HashMap<>();
        for (JornadaLaboral j : jornadas) {
            if (j.tieneHorario() && j.getEmpleadoId() != null) {
                Duration d = j.calcularDuracion();
                if (d != null) {
                    horasPorEmpleado.merge(j.getEmpleadoId(), d.toHours(), Long::sum);
                }
            }
        }

        for (Map.Entry<UUID, HorarioBase> entry : horariosPorEmpleado.entrySet()) {
            UUID empleadoId = entry.getKey();
            long horas = horasPorEmpleado.getOrDefault(empleadoId, 0L);

            switch (entry.getValue()) {
                case HorarioNocturno h -> {
                    long esperadas = (long) h.getDuracionJornadaHoras() * h.getNumeroJornadasMensuales();
                    int tolerancia = reglas.entero(CatalogoReglas.CARGA_NOCTURNO, "toleranciaHoras", 0);
                    if (reglas.activa(CatalogoReglas.CARGA_NOCTURNO) && esperadas > 0
                            && Math.abs(horas - esperadas) > tolerancia) {
                        advertencias.add(new Violacion(empleadoId, null,
                                "Carga mensual nocturna: " + horas + " h planificadas, se esperan "
                                        + esperadas + " h.", CatalogoReglas.CARGA_NOCTURNO));
                    }
                }
                case HorarioFeriante h -> {
                    int tolerancia = reglas.entero(CatalogoReglas.CARGA_FERIANTE, "toleranciaHoras", 0);
                    if (reglas.activa(CatalogoReglas.CARGA_FERIANTE) && h.getHorasMinimasMensuales() > 0
                            && horas + tolerancia < h.getHorasMinimasMensuales()) {
                        advertencias.add(new Violacion(empleadoId, null,
                                "Carga mensual feriante: " + horas + " h planificadas, mínimo "
                                        + h.getHorasMinimasMensuales() + " h.", CatalogoReglas.CARGA_FERIANTE));
                    }
                }
                case HorarioGuardiaEnfermeria h -> {
                    long esperadas = (long) h.getNumeroGuardias12Horas() * h.getDuracionGuardia12Horas()
                            + (long) h.getNumeroGuardias10Horas() * h.getDuracionGuardia10Horas();
                    int tolerancia = reglas.entero(CatalogoReglas.CARGA_ENFERMERIA, "toleranciaHoras", 0);
                    if (reglas.activa(CatalogoReglas.CARGA_ENFERMERIA) && esperadas > 0
                            && horas + tolerancia < esperadas) {
                        advertencias.add(new Violacion(empleadoId, null,
                                "Carga mensual guardia enfermería: " + horas + " h planificadas, se esperan "
                                        + esperadas + " h.", CatalogoReglas.CARGA_ENFERMERIA));
                    }
                }
                default -> {
                    // Modalidades semanales: validación por ventanas de semana,
                    // diferida a un pase posterior.
                }
            }
        }
        return advertencias;
    }

    /**
     * Límites de duración por tipo de jornada (regla estructural): mínimo
     * {@value #DURACION_MINIMA_HORAS} h para todas; máximo
     * {@value #MAX_TURNO_HORAS} h para turno normal y
     * {@value #MAX_GUARDIA_HORAS} h para guardias (activa o pasiva).
     *
     * @return la violación, o {@code null} si la duración es válida.
     */
    public static Violacion validarDuracion(JornadaLaboral j) {
        return validarDuracion(j, CatalogoReglas.PREDETERMINADO);
    }

    /** Límites de duración según el catálogo vigente (CU08), con el fundamento normativo. */
    public static Violacion validarDuracion(JornadaLaboral j, CatalogoReglas reglas) {
        Violacion v = duracion(j, reglas);
        return v != null ? conFundamento(v, reglas) : null;
    }

    private static Violacion conFundamento(Violacion v, CatalogoReglas reglas) {
        String mensaje = reglas.conFundamento(v.regla(), v.mensaje());
        return mensaje.equals(v.mensaje()) ? v : new Violacion(v.empleadoId(), v.fecha(), mensaje, v.regla());
    }

    private static Violacion duracion(JornadaLaboral j, CatalogoReglas reglas) {
        Duration d = Duration.between(j.getFechaIngreso(), j.getFechaEgreso());
        int minimo = reglas.entero(CatalogoReglas.DURACION_MIN, "horasMinimas", DURACION_MINIMA_HORAS);
        if (reglas.activa(CatalogoReglas.DURACION_MIN) && d.toMinutes() < minimo * 60L) {
            return new Violacion(j.getEmpleadoId(), j.getFecha(),
                    "Duración insuficiente: toda jornada debe durar al menos "
                            + minimo + " h.", CatalogoReglas.DURACION_MIN);
        }
        boolean turno = j.getTipo() == TipoJornada.TURNO_NORMAL;
        String regla = turno ? CatalogoReglas.TURNO_MAX : CatalogoReglas.GUARDIA_MAX;
        int maximo = reglas.entero(regla, "horasMaximas", turno ? MAX_TURNO_HORAS : MAX_GUARDIA_HORAS);
        if (reglas.activa(regla) && d.toMinutes() > maximo * 60L) {
            return new Violacion(j.getEmpleadoId(), j.getFecha(),
                    "Duración excesiva: " + (d.toMinutes() / 60.0) + " h supera el máximo de "
                            + maximo + " h para " + j.getTipo() + ".", regla);
        }
        return null;
    }

    // ============================================================
    // Helpers privados
    // ============================================================

    /**
     * Agrupa por empleado las jornadas que tienen horario, registrando de
     * paso las violaciones estructurales de la regla 1 (horario faltante o
     * invertido en tipos que lo requieren).
     */
    private static Map<UUID, List<JornadaLaboral>> agruparConHorario(List<JornadaLaboral> jornadas,
                                                                     List<Violacion> violaciones,
                                                                     CatalogoReglas reglas) {
        Map<UUID, List<JornadaLaboral>> porEmpleado = new HashMap<>();
        for (JornadaLaboral j : jornadas) {
            if (j.getTipo() != null && j.getTipo().requiereHorario() && !j.tieneHorario()) {
                violaciones.add(new Violacion(j.getEmpleadoId(), j.getFecha(),
                        "Jornada " + j.getTipo() + " sin horario de ingreso/egreso.", CatalogoReglas.HORARIO));
                continue;
            }
            if (j.tieneHorario()) {
                if (!j.getFechaEgreso().isAfter(j.getFechaIngreso())) {
                    violaciones.add(new Violacion(j.getEmpleadoId(), j.getFecha(),
                            "Horario inválido: el egreso debe ser posterior al ingreso.", CatalogoReglas.HORARIO));
                    continue;
                }
                Violacion duracion = duracion(j, reglas);
                if (duracion != null) {
                    violaciones.add(duracion);
                    continue;
                }
                porEmpleado.computeIfAbsent(j.getEmpleadoId(), k -> new ArrayList<>()).add(j);
            }
        }
        return porEmpleado;
    }
}
