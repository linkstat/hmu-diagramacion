package ar.com.hmu.service.diagramacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Colaboradores de calendario que los generadores necesitan para expandir
 * un patrón de horario a fechas concretas.
 *
 * <p><b>Feriados (deuda explícita):</b> Aromito todavía no modela feriados
 * (no hay tabla {@code Feriado} ni carga anual). El contexto recibe el set
 * por constructor: los tests y la futura tabla lo poblarán; producción usa
 * {@link #sinFeriados()} hasta que exista la carga. Esto afecta la exactitud
 * de las modalidades que dependen de días hábiles/no laborables, pero no
 * bloquea el flujo (el jefe corrige a mano en la grilla).</p>
 */
public class ContextoDiagramacion {

    private final Set<LocalDate> feriados;

    public ContextoDiagramacion(Set<LocalDate> feriados) {
        this.feriados = (feriados != null)
                ? Collections.unmodifiableSet(new HashSet<>(feriados))
                : Collections.emptySet();
    }

    /** Contexto sin feriados cargados (estado actual de producción). */
    public static ContextoDiagramacion sinFeriados() {
        return new ContextoDiagramacion(Collections.emptySet());
    }

    public boolean esFeriado(LocalDate fecha) {
        return feriados.contains(fecha);
    }

    public boolean esFinDeSemana(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }

    /** Día hábil = ni fin de semana ni feriado. */
    public boolean esDiaHabil(LocalDate fecha) {
        return !esFinDeSemana(fecha) && !esFeriado(fecha);
    }
}
