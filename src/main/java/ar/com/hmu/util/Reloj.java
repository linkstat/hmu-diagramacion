package ar.com.hmu.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Fecha de referencia de la aplicación. Por defecto es la del sistema; con
 * {@code -Dhmu.hoy=AAAA-MM-DD} se fija otra (modo de capturas y demos:
 * el escenario del seed transcurre en octubre de 2026).
 */
public final class Reloj {

    private static LocalDate fija;
    private static LocalTime horaFija;

    static {
        String p = System.getProperty("hmu.hoy");
        if (p != null && !p.isBlank()) {
            fija = LocalDate.parse(p.trim());
        }
    }

    private Reloj() {
    }

    public static void fijar(LocalDate fecha) {
        fija = fecha;
    }

    /** Fecha y hora fijas (modo de capturas): {@link #ahora()} devuelve siempre ese instante. */
    public static void fijar(LocalDate fecha, LocalTime hora) {
        fija = fecha;
        horaFija = hora;
    }

    public static LocalDate hoy() {
        return fija != null ? fija : LocalDate.now();
    }

    /** Con fecha fija conserva la hora actual, salvo que también se haya fijado la hora. */
    public static LocalDateTime ahora() {
        if (fija == null) return LocalDateTime.now();
        return LocalDateTime.of(fija, horaFija != null ? horaFija : LocalTime.now());
    }
}
