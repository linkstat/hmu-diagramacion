package ar.com.hmu.service.asistencia;

import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RV-HE-COMPUTO: horas extras para FC a partir de las marcaciones. */
class CalculoHorasExtraTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 3);
    private static final CalculoHorasExtra.Parametros P = CalculoHorasExtra.Parametros.PREDETERMINADOS;
    private final UUID a = UUID.randomUUID();

    @Test
    void soloCuentaElTiempoPosteriorAlEgresoEnBloquesDe30() {
        // Turno 07–14; llegó 06:30 (no cuenta) y se fue 15:50: 1 h 50 min -> 3 bloques = 1 h 30 min.
        var r = CalculoHorasExtra.calcular(turno(MARTES, 7, 7, TipoJornada.TURNO_NORMAL),
                registrada(MARTES.atTime(6, 30), MARTES.atTime(15, 50)), d -> false, P);
        assertThat(r.minutosExtra()).isEqualTo(110);
        assertThat(r.bloques()).isEqualTo(3);
        assertThat(r.minutosComputados()).isEqualTo(90);
        assertThat(r.horasComputadas()).isEqualByComparingTo("1.50");
    }

    @Test
    void menosDeLaFraccionMinimaNoSeAcredita() {
        var r = CalculoHorasExtra.calcular(turno(MARTES, 7, 7, TipoJornada.TURNO_NORMAL),
                registrada(MARTES.atTime(7, 0), MARTES.atTime(14, 25)), d -> false, P);
        assertThat(r.minutosComputados()).isZero();
    }

    @Test
    void elExcedenteSobreElTopeDe12HorasSePierde() {
        // Turno 07–14: tope 19:00. Se fue 20:00 -> cuentan 14–19 (5 h), se pierde 1 h.
        var r = CalculoHorasExtra.calcular(turno(MARTES, 7, 7, TipoJornada.TURNO_NORMAL),
                registrada(MARTES.atTime(7, 0), MARTES.atTime(20, 0)), d -> false, P);
        assertThat(r.minutosExtra()).isEqualTo(300);
        assertThat(r.minutosPerdidos()).isEqualTo(60);
        assertThat(r.minutosComputados()).isEqualTo(300);
    }

    @Test
    void lasGuardiasNoTienenTope() {
        // Guardia 08–20 y se quedó hasta 22:00: 20–21 simple, 21–22 doble (nocturno) = 3 h.
        var r = CalculoHorasExtra.calcular(turno(MARTES, 8, 12, TipoJornada.GUARDIA_ACTIVA),
                registrada(MARTES.atTime(8, 0), MARTES.atTime(22, 0)), d -> false, P);
        assertThat(r.minutosPerdidos()).isZero();
        assertThat(r.bloquesDobles()).isEqualTo(2);
        assertThat(r.minutosComputados()).isEqualTo(180);
    }

    @Test
    void computoDobleEnFinDeSemanaYFeriado() {
        var sabado = CalculoHorasExtra.calcular(turno(SABADO, 8, 6, TipoJornada.TURNO_NORMAL),
                registrada(SABADO.atTime(8, 0), SABADO.atTime(16, 0)), d -> false, P);
        assertThat(sabado.minutosComputados()).isEqualTo(240);   // 2 h trabajadas, dobles
        var feriado = CalculoHorasExtra.calcular(turno(MARTES, 7, 7, TipoJornada.TURNO_NORMAL),
                registrada(MARTES.atTime(7, 0), MARTES.atTime(15, 0)), Set.of(MARTES)::contains, P);
        assertThat(feriado.minutosComputados()).isEqualTo(120);
    }

    @Test
    void enUnDiaSinJornadaCuentaTodoLoRegistrado() {
        var r = CalculoHorasExtra.calcular(null, registrada(SABADO.atTime(9, 0), SABADO.atTime(12, 10)), d -> false, P);
        assertThat(r.bloques()).isEqualTo(6);
        assertThat(r.minutosComputados()).isEqualTo(360);
    }

    @Test
    void sinEgresoNoHayHorasExtra() {
        var r = CalculoHorasExtra.calcular(turno(MARTES, 7, 7, TipoJornada.TURNO_NORMAL),
                registrada(MARTES.atTime(7, 0), null), d -> false, P);
        assertThat(r).isEqualTo(CalculoHorasExtra.Resultado.NADA);
    }

    private JornadaLaboral turno(LocalDate dia, int hora, int horas, TipoJornada tipo) {
        LocalDateTime in = dia.atTime(hora, 0);
        return new JornadaLaboral(UUID.randomUUID(), null, a, dia, tipo, in, in.plusHours(horas), null);
    }

    private JornadaRegistrada registrada(LocalDateTime in, LocalDateTime out) {
        return new JornadaRegistrada(UUID.randomUUID(), a, in.toLocalDate(), in, out, UUID.randomUUID(),
                out != null ? UUID.randomUUID() : null);
    }
}
