package ar.com.hmu.service.asistencia;

import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.service.diagramacion.CatalogoReglas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Predicate;

/**
 * Cómputo de las horas extras para FC de una jornada a partir de sus
 * marcaciones (regla RV-HE-COMPUTO, parametrizable por el CU08):
 *
 * <ul>
 *   <li>sólo cuenta el tiempo posterior al egreso diagramado (el ingreso
 *       anticipado no suma); en un día sin jornada diagramada cuenta todo lo
 *       registrado;</li>
 *   <li>se computan bloques completos de la fracción mínima (30 min);</li>
 *   <li>tope de jornada de 12 h desde el ingreso diagramado, salvo guardias: el
 *       excedente se pierde;</li>
 *   <li>cómputo doble (factor 2) en feriados y asuetos, sábados, domingos y en
 *       la franja nocturna (21:00 a 06:00), según el inicio de cada bloque.</li>
 * </ul>
 */
public final class CalculoHorasExtra {

    public static final String REGLA = "RV-HE-COMPUTO";

    /** Parámetros de la regla. */
    public record Parametros(int fraccionMinutos, int topeJornadaHoras, int factorDoble, int nocheDesde,
                             int nocheHasta) {

        public static final Parametros PREDETERMINADOS = new Parametros(30, 12, 2, 21, 6);

        public static Parametros de(CatalogoReglas reglas) {
            Parametros p = PREDETERMINADOS;
            return new Parametros(
                    Math.max(1, reglas.entero(REGLA, "fraccionMinutos", p.fraccionMinutos())),
                    reglas.entero(REGLA, "topeJornadaHoras", p.topeJornadaHoras()),
                    Math.max(1, reglas.entero(REGLA, "factorDoble", p.factorDoble())),
                    reglas.entero(REGLA, "nocheDesde", p.nocheDesde()),
                    reglas.entero(REGLA, "nocheHasta", p.nocheHasta()));
        }
    }

    /**
     * @param minutosExtra      minutos registrados que cuentan (después del egreso y dentro del tope).
     * @param minutosPerdidos   minutos posteriores al tope de jornada (no se acreditan).
     * @param bloques           bloques completos de la fracción mínima.
     * @param bloquesDobles     bloques con cómputo doble.
     * @param minutosComputados minutos que se acreditan (con el cómputo doble aplicado).
     */
    public record Resultado(int minutosExtra, int minutosPerdidos, int bloques, int bloquesDobles,
                            int minutosComputados) {

        public static final Resultado NADA = new Resultado(0, 0, 0, 0, 0);

        public BigDecimal horasComputadas() {
            return BigDecimal.valueOf(minutosComputados).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        /** "3 h (2 h trabajadas en bloques de 30 min; 2 bloques dobles)". */
        public String descripcion(int fraccionMinutos) {
            StringBuilder sb = new StringBuilder(horas(minutosComputados)).append(" (")
                    .append(horas(bloques * fraccionMinutos)).append(" trabajadas en bloques de ")
                    .append(fraccionMinutos).append(" min");
            if (bloquesDobles > 0) {
                sb.append("; ").append(bloquesDobles).append(bloquesDobles == 1 ? " bloque doble" : " bloques dobles");
            }
            if (minutosPerdidos > 0) {
                sb.append("; ").append(horas(minutosPerdidos)).append(" sobre el tope, no acreditadas");
            }
            return sb.append(")").toString();
        }
    }

    private CalculoHorasExtra() {
    }

    /**
     * @param diagramada  jornada diagramada del día (null o sin horario: día sin jornada).
     * @param registrada  jornada registrada (marcaciones); sin egreso no hay horas extra.
     * @param noLaborable feriados y asuetos vigentes.
     */
    public static Resultado calcular(JornadaLaboral diagramada, JornadaRegistrada registrada,
                                     Predicate<LocalDate> noLaborable, Parametros p) {
        if (registrada == null || registrada.ingreso() == null || registrada.egreso() == null) {
            return Resultado.NADA;
        }
        boolean conHorario = diagramada != null && diagramada.tieneHorario();
        LocalDateTime inicio = conHorario ? diagramada.getFechaEgreso() : registrada.ingreso();
        LocalDateTime referenciaTope = conHorario ? diagramada.getFechaIngreso() : registrada.ingreso();
        boolean guardia = conHorario && (diagramada.getTipo() == TipoJornada.GUARDIA_ACTIVA
                || diagramada.getTipo() == TipoJornada.GUARDIA_PASIVA);
        LocalDateTime fin = registrada.egreso();
        int perdidos = 0;
        if (!guardia && p.topeJornadaHoras() > 0) {
            LocalDateTime tope = referenciaTope.plusHours(p.topeJornadaHoras());
            if (fin.isAfter(tope)) {
                LocalDateTime desdePerdida = inicio.isAfter(tope) ? inicio : tope;
                perdidos = (int) Math.max(0, Duration.between(desdePerdida, fin).toMinutes());
                fin = tope;
            }
        }
        if (!fin.isAfter(inicio)) {
            return new Resultado(0, perdidos, 0, 0, 0);
        }
        int extra = (int) Duration.between(inicio, fin).toMinutes();
        int bloques = extra / p.fraccionMinutos();
        int dobles = 0;
        for (int k = 0; k < bloques; k++) {
            if (esDoble(inicio.plusMinutes((long) k * p.fraccionMinutos()), noLaborable, p)) {
                dobles++;
            }
        }
        int computados = (bloques - dobles) * p.fraccionMinutos() + dobles * p.fraccionMinutos() * p.factorDoble();
        return new Resultado(extra, perdidos, bloques, dobles, computados);
    }

    static boolean esDoble(LocalDateTime t, Predicate<LocalDate> noLaborable, Parametros p) {
        DayOfWeek dia = t.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY || noLaborable.test(t.toLocalDate())) {
            return true;
        }
        int h = t.getHour();
        return p.nocheDesde() > p.nocheHasta()
                ? h >= p.nocheDesde() || h < p.nocheHasta()
                : h >= p.nocheDesde() && h < p.nocheHasta();
    }

    private static String horas(int minutos) {
        int h = minutos / 60;
        int m = minutos % 60;
        return m == 0 ? h + " h" : h > 0 ? h + " h " + m + " min" : m + " min";
    }
}
