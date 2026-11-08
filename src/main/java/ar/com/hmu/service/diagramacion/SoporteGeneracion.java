package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.util.DiaSemana;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Algoritmos núcleo compartidos por los generadores de jornadas. Las
 * modalidades caen en tres familias según la forma de sus datos:
 *
 * <ul>
 *   <li><b>Por días de semana</b> (Estandar, JefeServicioGuardiaPasiva):
 *       lista de días laborables + horas por día.</li>
 *   <li><b>Por distribución semanal</b> (Semanal, DXI): mapa día → horas +
 *       mapa día → hora de inicio.</li>
 *   <li><b>Por fechas programadas</b> (Nocturno, Feriante, GuardiaMedica,
 *       GuardiaEnfermeria): lista de fechas/hora concretas + duración.</li>
 * </ul>
 *
 * <p>Todas mantienen el invariante de {@link GeneradorJornadas}: una jornada
 * por día del rango, materializando {@code FRANCO} donde no hay trabajo.</p>
 */
final class SoporteGeneracion {

    /**
     * Hora de inicio por defecto cuando el template no la define (las
     * modalidades Estandar y JefeServicioGuardiaPasiva no tienen campo de
     * hora de inicio). 07:00 = arranque típico del turno mañana del HMU;
     * la jefatura la ajusta en la grilla si el servicio arranca distinto.
     */
    static final LocalTime HORA_INICIO_DEFECTO = LocalTime.of(7, 0);

    private SoporteGeneracion() {
        // Sólo métodos estáticos.
    }

    /**
     * Familia "por días de semana": TURNO_NORMAL de {@code horasPorDia} en
     * los días laborables hábiles; FRANCO el resto. Un feriado que cae en
     * día laborable se marca FRANCO con observación.
     */
    static List<JornadaLaboral> porDiasSemana(List<DiaSemana> diasLaborables, int horasPorDia,
                                              LocalTime horaInicio, LocalDate desde,
                                              LocalDate hasta, ContextoDiagramacion ctx) {
        List<JornadaLaboral> jornadas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            boolean laborable = diasLaborables != null
                    && diasLaborables.contains(DiaSemana.from(fecha));
            if (laborable && ctx.esFeriado(fecha)) {
                jornadas.add(franco(fecha, "Feriado"));
            } else if (laborable) {
                jornadas.add(conHorario(fecha, TipoJornada.TURNO_NORMAL,
                        horaInicio, horasPorDia, null));
            } else {
                jornadas.add(franco(fecha, null));
            }
        }
        return jornadas;
    }

    /**
     * Familia "por distribución semanal": TURNO_NORMAL con las horas del
     * mapa en cada día presente (con horas &gt; 0); FRANCO el resto. La hora
     * de inicio sale del mapa correspondiente o de
     * {@link #HORA_INICIO_DEFECTO}.
     */
    static List<JornadaLaboral> porDistribucion(Map<DiaSemana, Integer> horasPorDia,
                                                Map<DiaSemana, LocalTime> inicioPorDia,
                                                LocalDate desde, LocalDate hasta,
                                                ContextoDiagramacion ctx) {
        List<JornadaLaboral> jornadas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            DiaSemana dia = DiaSemana.from(fecha);
            Integer horas = (horasPorDia != null) ? horasPorDia.get(dia) : null;
            boolean trabaja = horas != null && horas > 0;
            if (trabaja && ctx.esFeriado(fecha)) {
                jornadas.add(franco(fecha, "Feriado"));
            } else if (trabaja) {
                LocalTime inicio = (inicioPorDia != null && inicioPorDia.get(dia) != null)
                        ? inicioPorDia.get(dia) : HORA_INICIO_DEFECTO;
                jornadas.add(conHorario(fecha, TipoJornada.TURNO_NORMAL, inicio, horas, null));
            } else {
                jornadas.add(franco(fecha, null));
            }
        }
        return jornadas;
    }

    /**
     * Familia "por fechas programadas": jornada del tipo indicado en cada
     * fecha programada dentro del rango (la hora de inicio viene en el
     * propio {@link LocalDateTime}); FRANCO el resto. Las fechas programadas
     * NO se saltean en feriados: estas modalidades cubren el hospital 24/7.
     *
     * <p>Si el template no trae fechas dentro del rango (caso típico: las
     * guardias del mes se colocan a mano en la grilla), el resultado es todo
     * FRANCO y la jefatura edita encima.</p>
     */
    static List<JornadaLaboral> porFechasProgramadas(List<LocalDateTime> fechasProgramadas,
                                                     int duracionHoras, TipoJornada tipo,
                                                     LocalDate desde, LocalDate hasta) {
        Map<LocalDate, LocalDateTime> programadas = new HashMap<>();
        if (fechasProgramadas != null) {
            for (LocalDateTime f : fechasProgramadas) {
                programadas.put(f.toLocalDate(), f);
            }
        }
        List<JornadaLaboral> jornadas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            LocalDateTime inicio = programadas.get(fecha);
            if (inicio != null) {
                JornadaLaboral j = new JornadaLaboral();
                j.setFecha(fecha);
                j.setTipo(tipo);
                j.setFechaIngreso(inicio);
                j.setFechaEgreso(inicio.plusHours(duracionHoras));
                jornadas.add(j);
            } else {
                jornadas.add(franco(fecha, null));
            }
        }
        return jornadas;
    }

    /** Grilla completa de FRANCO (modalidades sin patrón derivable). */
    static List<JornadaLaboral> todoFranco(LocalDate desde, LocalDate hasta) {
        List<JornadaLaboral> jornadas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            jornadas.add(franco(fecha, null));
        }
        return jornadas;
    }

    // ============================================================
    // Constructores de jornadas individuales
    // ============================================================

    static JornadaLaboral franco(LocalDate fecha, String observaciones) {
        JornadaLaboral j = new JornadaLaboral();
        j.setFecha(fecha);
        j.setTipo(TipoJornada.FRANCO);
        j.setObservaciones(observaciones);
        return j;
    }

    static JornadaLaboral conHorario(LocalDate fecha, TipoJornada tipo, LocalTime horaInicio,
                                     int duracionHoras, String observaciones) {
        JornadaLaboral j = new JornadaLaboral();
        j.setFecha(fecha);
        j.setTipo(tipo);
        LocalDateTime ingreso = fecha.atTime(horaInicio);
        j.setFechaIngreso(ingreso);
        j.setFechaEgreso(ingreso.plusHours(duracionHoras)); // puede cruzar medianoche
        j.setObservaciones(observaciones);
        return j;
    }
}
