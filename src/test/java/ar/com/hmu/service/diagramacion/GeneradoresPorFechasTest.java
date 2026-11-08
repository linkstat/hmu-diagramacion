package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioAbierto;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioGuardiaMedica;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de los generadores por fechas programadas: {@link GeneradorNocturno},
 * {@link GeneradorFeriante}, {@link GeneradorGuardiaMedica},
 * {@link GeneradorGuardiaEnfermeria}; y de {@link GeneradorAbierto}
 * (sin patrón).
 *
 * Semana ancla: lunes 2026-07-06 a domingo 2026-07-12.
 */
class GeneradoresPorFechasTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 7, 12);

    // ============================================================
    // GeneradorNocturno
    // ============================================================

    @Test
    void nocturno_fechaProgramada_generaTurnoQueCruzaMedianoche() {
        HorarioNocturno h = new HorarioNocturno();
        h.setDiasProgramados(List.of(LocalDateTime.of(2026, 7, 6, 21, 0)));
        h.setDuracionJornadaHoras(10);

        List<JornadaLaboral> jornadas = new GeneradorNocturno().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        JornadaLaboral lunes = jornadas.get(0);
        assertThat(lunes.getTipo()).isEqualTo(TipoJornada.TURNO_NORMAL);
        assertThat(lunes.getFechaIngreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 21, 0));
        assertThat(lunes.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 7, 7, 0));
        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.FRANCO).hasSize(6);
    }

    @Test
    void nocturno_templateSinFechas_grillaTodaFrancoParaEdicionManual() {
        HorarioNocturno h = new HorarioNocturno();
        h.setDuracionJornadaHoras(10); // sin diasProgramados: los coloca la jefatura

        List<JornadaLaboral> jornadas = new GeneradorNocturno().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).hasSize(7)
                .allSatisfy(j -> assertThat(j.getTipo()).isEqualTo(TipoJornada.FRANCO));
    }

    @Test
    void nocturno_fechasFueraDelRango_seIgnoran() {
        HorarioNocturno h = new HorarioNocturno();
        h.setDiasProgramados(List.of(
                LocalDateTime.of(2026, 6, 30, 21, 0),   // antes del rango
                LocalDateTime.of(2026, 7, 8, 21, 0),    // dentro
                LocalDateTime.of(2026, 8, 1, 21, 0)));  // después
        h.setDuracionJornadaHoras(10);

        List<JornadaLaboral> jornadas = new GeneradorNocturno().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).hasSize(7);
        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.TURNO_NORMAL)
                .hasSize(1)
                .first()
                .satisfies(j -> assertThat(j.getFecha()).isEqualTo(LocalDate.of(2026, 7, 8)));
    }

    @Test
    void nocturno_fechaProgramadaEnFeriado_noSeSaltea() {
        // Las modalidades por fechas cubren el hospital 24/7: el feriado no anula la jornada.
        LocalDate jueves = LocalDate.of(2026, 7, 9);
        HorarioNocturno h = new HorarioNocturno();
        h.setDiasProgramados(List.of(jueves.atTime(21, 0)));
        h.setDuracionJornadaHoras(10);

        List<JornadaLaboral> jornadas = new GeneradorNocturno().generar(
                h, LUNES, DOMINGO, new ContextoDiagramacion(Set.of(jueves)));

        JornadaLaboral feriado = jornadas.stream()
                .filter(j -> j.getFecha().equals(jueves)).findFirst().orElseThrow();
        assertThat(feriado.getTipo()).isEqualTo(TipoJornada.TURNO_NORMAL);
    }

    // ============================================================
    // GeneradorFeriante
    // ============================================================

    @Test
    void feriante_generaGuardiaActivaEnFechaProgramada() {
        HorarioFeriante h = new HorarioFeriante();
        h.setDiasNoLaborables(List.of(LocalDateTime.of(2026, 7, 11, 8, 0))); // sábado
        h.setDuracionGuardiaHoras(12);

        List<JornadaLaboral> jornadas = new GeneradorFeriante().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        JornadaLaboral sabado = jornadas.stream()
                .filter(j -> j.getTipo() == TipoJornada.GUARDIA_ACTIVA).findFirst().orElseThrow();
        assertThat(sabado.getFecha()).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(sabado.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 11, 20, 0));
    }

    // ============================================================
    // GeneradorGuardiaMedica
    // ============================================================

    @Test
    void guardiaMedica_generaGuardiaActivaConSuDuracion() {
        HorarioGuardiaMedica h = new HorarioGuardiaMedica();
        h.setFechasGuardias(List.of(LocalDateTime.of(2026, 7, 7, 8, 0)));
        h.setDuracionGuardiaHoras(12);

        List<JornadaLaboral> jornadas = new GeneradorGuardiaMedica().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.GUARDIA_ACTIVA)
                .hasSize(1)
                .first()
                .satisfies(j -> assertThat(j.getFechaEgreso())
                        .isEqualTo(LocalDateTime.of(2026, 7, 7, 20, 0)));
    }

    // ============================================================
    // GeneradorGuardiaEnfermeria
    // ============================================================

    @Test
    void guardiaEnfermeria_usaLaDuracionDe12HorasComoDefecto() {
        HorarioGuardiaEnfermeria h = new HorarioGuardiaEnfermeria();
        h.setFechasGuardias(List.of(LocalDateTime.of(2026, 7, 8, 7, 0)));
        h.setDuracionGuardia10Horas(10);
        h.setDuracionGuardia12Horas(12);

        List<JornadaLaboral> jornadas = new GeneradorGuardiaEnfermeria().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        JornadaLaboral guardia = jornadas.stream()
                .filter(j -> j.getTipo() == TipoJornada.GUARDIA_ACTIVA).findFirst().orElseThrow();
        // Las dos guardias de 10 hs del mes las ajusta la jefatura en la grilla.
        assertThat(guardia.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 8, 19, 0));
    }

    // ============================================================
    // GeneradorAbierto
    // ============================================================

    @Test
    void abierto_sinPatronDerivable_grillaTodaFranco() {
        HorarioAbierto h = new HorarioAbierto();
        h.setHorasSemanales(35);

        List<JornadaLaboral> jornadas = new GeneradorAbierto().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).hasSize(7)
                .allSatisfy(j -> assertThat(j.getTipo()).isEqualTo(TipoJornada.FRANCO));
    }
}
