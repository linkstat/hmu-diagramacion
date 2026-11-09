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
 * <p><b>Feriados:</b> el contexto recibe el set de fechas por constructor
 * (en producción viene de la tabla {@code Feriado}; los tests lo arman a
 * mano, y {@link #sinFeriados()} sirve para los casos que no los usan).</p>
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
