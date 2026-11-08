package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.util.DiaSemana;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plantillas de precarga para la jefatura: llenan la fila de un agente en
 * todo el período del diagrama con los esquemas horarios más comunes del HMU
 * (definidos por el usuario, 2026-07-08). El resultado es un punto de partida
 * editable, no una regla: la jefatura ajusta a mano y el validador controla.
 *
 * <p>Mismo invariante que {@link GeneradorJornadas}: una jornada por día del
 * rango, con {@code FRANCO} donde no hay trabajo. Las jornadas vuelven sin
 * {@code id}/{@code diagramaId}/{@code empleadoId}; se estampan con
 * {@link GeneradorJornadas#estampar}.</p>
 *
 * <p>Para la plantilla de feriante el rango completo se toma como "el mes"
 * (los diagramas son mensuales en la práctica).</p>
 */
public final class PlantillasJornada {

    public static final int HORAS_TURNO_ESTANDAR = 7;
    public static final int GUARDIAS_FERIANTE_MINIMAS = 10;
    public static final int HORAS_GUARDIA_FERIANTE = 12;
    /** Horas mínimas de feriante: 10 guardias de 12 h. */
    public static final int HORAS_FERIANTE_MINIMAS = GUARDIAS_FERIANTE_MINIMAS * HORAS_GUARDIA_FERIANTE;

    static final String OBS_COMPLETA_FERIANTE = "Completa horas mínimas de feriante";

    private static final List<DiaSemana> LUNES_A_VIERNES = List.of(
            DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES, DiaSemana.JUEVES, DiaSemana.VIERNES);

    private PlantillasJornada() {
        // Sólo métodos estáticos.
    }

    /**
     * Semana estándar: turno de 7 h de lunes a viernes desde la hora de
     * ingreso elegida (las habituales son 07:00 y 08:00). Fines de semana y
     * feriados quedan FRANCO (el feriado, con observación).
     */
    public static List<JornadaLaboral> semanaEstandar(LocalTime ingreso, LocalDate desde, LocalDate hasta,
                                                      ContextoDiagramacion ctx) {
        return SoporteGeneracion.porDiasSemana(LUNES_A_VIERNES, HORAS_TURNO_ESTANDAR, ingreso,
                desde, hasta, ctx);
    }

    /**
     * Guardia médica semanal: cada semana, una guardia de 12 h y otra de 24 h
     * en el día y la hora de ingreso que define la jefatura. Las guardias no
     * se saltean en feriados (cobertura 24/7).
     *
     * @throws IllegalArgumentException si ambas guardias caen el mismo día.
     */
    public static List<JornadaLaboral> guardiaMedicaSemanal(DayOfWeek dia12, LocalTime ingreso12,
                                                            DayOfWeek dia24, LocalTime ingreso24,
                                                            LocalDate desde, LocalDate hasta) {
        if (dia12 == dia24) {
            throw new IllegalArgumentException("La guardia de 12 h y la de 24 h deben caer en días distintos.");
        }
        List<JornadaLaboral> jornadas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            DayOfWeek dia = fecha.getDayOfWeek();
            if (dia == dia12) {
                jornadas.add(SoporteGeneracion.conHorario(fecha, TipoJornada.GUARDIA_ACTIVA, ingreso12, 12, null));
            } else if (dia == dia24) {
                jornadas.add(SoporteGeneracion.conHorario(fecha, TipoJornada.GUARDIA_ACTIVA, ingreso24, 24, null));
            } else {
                jornadas.add(SoporteGeneracion.franco(fecha, null));
            }
        }
        return jornadas;
    }

    /**
     * Feriante: como mínimo {@value #GUARDIAS_FERIANTE_MINIMAS} guardias de
     * {@value #HORAS_GUARDIA_FERIANTE} h en días no laborables (sábados,
     * domingos y feriados), repartidas a lo largo del período. Si el período
     * no tiene suficientes días no laborables, las horas que faltan para
     * llegar a {@value #HORAS_FERIANTE_MINIMAS} h se reparten en días hábiles,
     * en jornadas de 1 a 12 h.
     *
     * <p>Todas las jornadas arrancan a la misma hora y duran como máximo
     * 12 h, así que entre dos días seguidos siempre quedan al menos 12 h de
     * descanso (el mínimo del HMU es 10).</p>
     */
    public static List<JornadaLaboral> feriante(LocalTime ingreso, LocalDate desde, LocalDate hasta,
                                                ContextoDiagramacion ctx) {
        List<LocalDate> noLaborables = new ArrayList<>();
        List<LocalDate> habiles = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (ctx.esFinDeSemana(fecha) || ctx.esFeriado(fecha)) {
                noLaborables.add(fecha);
            } else {
                habiles.add(fecha);
            }
        }

        Map<LocalDate, JornadaLaboral> trabajo = new HashMap<>();
        List<LocalDate> guardias = repartir(noLaborables, Math.min(GUARDIAS_FERIANTE_MINIMAS, noLaborables.size()));
        for (LocalDate fecha : guardias) {
            trabajo.put(fecha, SoporteGeneracion.conHorario(fecha, TipoJornada.GUARDIA_ACTIVA, ingreso,
                    HORAS_GUARDIA_FERIANTE, null));
        }

        int faltan = HORAS_FERIANTE_MINIMAS - guardias.size() * HORAS_GUARDIA_FERIANTE;
        if (faltan > 0) {
            int jornadasExtra = (faltan + HORAS_GUARDIA_FERIANTE - 1) / HORAS_GUARDIA_FERIANTE;
            if (jornadasExtra > habiles.size()) {
                throw new IllegalArgumentException("El período es demasiado corto para completar las "
                        + HORAS_FERIANTE_MINIMAS + " h mínimas de feriante.");
            }
            for (LocalDate fecha : repartir(habiles, jornadasExtra)) {
                int horas = Math.min(HORAS_GUARDIA_FERIANTE, faltan);
                trabajo.put(fecha, SoporteGeneracion.conHorario(fecha, TipoJornada.GUARDIA_ACTIVA, ingreso,
                        horas, OBS_COMPLETA_FERIANTE));
                faltan -= horas;
            }
        }

        List<JornadaLaboral> jornadas = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            JornadaLaboral j = trabajo.get(fecha);
            jornadas.add(j != null ? j : SoporteGeneracion.franco(fecha, null));
        }
        return jornadas;
    }

    /**
     * Elige {@code n} elementos repartidos de forma pareja a lo largo de la
     * lista (centrados en cada tramo), en orden. {@code n} no puede superar
     * el tamaño de la lista.
     */
    static <T> List<T> repartir(List<T> elementos, int n) {
        List<T> elegidos = new ArrayList<>();
        int total = elementos.size();
        for (int i = 0; i < n; i++) {
            elegidos.add(elementos.get((int) (((2L * i + 1) * total) / (2L * n))));
        }
        return elegidos;
    }
}
