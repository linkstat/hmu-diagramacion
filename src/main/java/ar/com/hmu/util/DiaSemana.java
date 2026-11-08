package ar.com.hmu.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

public enum DiaSemana {
    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO;

    /**
     * Traduce el {@link DayOfWeek} de java.time (MONDAY=1..SUNDAY=7) al
     * enum del dominio. El orden de las constantes coincide, por lo que el
     * mapeo es por posición.
     */
    public static DiaSemana from(DayOfWeek dia) {
        return values()[dia.getValue() - 1];
    }

    /** Día de la semana de una fecha concreta. */
    public static DiaSemana from(LocalDate fecha) {
        return from(fecha.getDayOfWeek());
    }
}
