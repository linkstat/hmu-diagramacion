package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link PlantillasJornada}: semana estándar, guardia médica
 * semanal y feriante (incluido el caso de un mes con pocos días no
 * laborables, donde las horas faltantes pasan a días hábiles).
 */
class PlantillasJornadaTest {

    // Octubre 2026: arranca jueves 01; 9 sábados/domingos.
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);
    // Febrero 2026: arranca domingo 01; sólo 8 sábados/domingos.
    private static final LocalDate FEB_1 = LocalDate.of(2026, 2, 1);
    private static final LocalDate FEB_28 = LocalDate.of(2026, 2, 28);
    private static final java.util.UUID EMPLEADO = java.util.UUID.randomUUID();

    private static long horas(List<JornadaLaboral> jornadas) {
        return jornadas.stream().filter(JornadaLaboral::tieneHorario)
                .mapToLong(j -> j.calcularDuracion().toHours()).sum();
    }

    private static boolean esNoLaborable(LocalDate f, ContextoDiagramacion ctx) {
        return ctx.esFinDeSemana(f) || ctx.esFeriado(f);
    }

    // ============================================================
    // Semana estándar
    // ============================================================

    @Test
    void semanaEstandar_turno7hDeLunesAViernes_desdeLaHoraElegida() {
        List<JornadaLaboral> j = PlantillasJornada.semanaEstandar(LocalTime.of(8, 0),
                OCT_1, OCT_31, ContextoDiagramacion.sinFeriados());

        assertThat(j).hasSize(31);
        assertThat(j).filteredOn(x -> x.getTipo() == TipoJornada.TURNO_NORMAL).hasSize(22)
                .allSatisfy(x -> {
                    assertThat(x.getFechaIngreso().toLocalTime()).isEqualTo(LocalTime.of(8, 0));
                    assertThat(x.calcularDuracion().toHours()).isEqualTo(7);
                });
    }

    @Test
    void semanaEstandar_feriadoEnDiaHabil_quedaFranco() {
        LocalDate feriado = LocalDate.of(2026, 10, 12); // lunes
        List<JornadaLaboral> j = PlantillasJornada.semanaEstandar(LocalTime.of(7, 0),
                OCT_1, OCT_31, new ContextoDiagramacion(Set.of(feriado)));

        JornadaLaboral doce = j.stream().filter(x -> x.getFecha().equals(feriado)).findFirst().orElseThrow();
        assertThat(doce.getTipo()).isEqualTo(TipoJornada.FRANCO);
        assertThat(j).filteredOn(x -> x.getTipo() == TipoJornada.TURNO_NORMAL).hasSize(21);
    }

    // ============================================================
    // Guardia médica semanal
    // ============================================================

    @Test
    void guardiaMedica_unaDe12YUnaDe24PorSemana_enLosDiasElegidos() {
        List<JornadaLaboral> j = PlantillasJornada.guardiaMedicaSemanal(
                DayOfWeek.TUESDAY, LocalTime.of(8, 0), DayOfWeek.FRIDAY, LocalTime.of(20, 0), OCT_1, OCT_31);

        // Octubre 2026: 4 martes (6, 13, 20, 27) y 5 viernes (2, 9, 16, 23, 30).
        assertThat(j).filteredOn(x -> x.tieneHorario() && x.calcularDuracion().toHours() == 12)
                .hasSize(4)
                .allSatisfy(x -> assertThat(x.getFecha().getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY));
        assertThat(j).filteredOn(x -> x.tieneHorario() && x.calcularDuracion().toHours() == 24)
                .hasSize(5)
                .allSatisfy(x -> {
                    assertThat(x.getFecha().getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
                    assertThat(x.getFechaIngreso().toLocalTime()).isEqualTo(LocalTime.of(20, 0));
                });
        assertThat(j).filteredOn(JornadaLaboral::tieneHorario)
                .allSatisfy(x -> assertThat(x.getTipo()).isEqualTo(TipoJornada.GUARDIA_ACTIVA));
    }

    @Test
    void guardiaMedica_resultadoPasaElValidador() {
        List<JornadaLaboral> j = PlantillasJornada.guardiaMedicaSemanal(
                DayOfWeek.TUESDAY, LocalTime.of(8, 0), DayOfWeek.FRIDAY, LocalTime.of(8, 0), OCT_1, OCT_31);
        j.forEach(x -> x.setEmpleadoId(EMPLEADO));

        assertThat(DiagramaValidator.validar(j)).isEmpty();
    }

    @Test
    void guardiaMedica_mismoDiaParaAmbas_lanza() {
        assertThatThrownBy(() -> PlantillasJornada.guardiaMedicaSemanal(
                DayOfWeek.MONDAY, LocalTime.of(8, 0), DayOfWeek.MONDAY, LocalTime.of(20, 0), OCT_1, OCT_31))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("días distintos");
    }

    // ============================================================
    // Feriante
    // ============================================================

    @Test
    void feriante_octubreSinFeriados_nueveGuardiasDeFinDeSemanaYUnaEnDiaHabil() {
        ContextoDiagramacion ctx = ContextoDiagramacion.sinFeriados();
        List<JornadaLaboral> j = PlantillasJornada.feriante(LocalTime.of(8, 0), OCT_1, OCT_31, ctx);

        // Octubre tiene 9 sábados/domingos: no alcanzan para 10 → 9 guardias + 1 en día hábil.
        List<JornadaLaboral> conHorario = j.stream().filter(JornadaLaboral::tieneHorario).toList();
        assertThat(horas(j)).isEqualTo(120);
        assertThat(conHorario).filteredOn(x -> esNoLaborable(x.getFecha(), ctx)).hasSize(9);
        assertThat(conHorario).filteredOn(x -> !esNoLaborable(x.getFecha(), ctx)).hasSize(1)
                .allSatisfy(x -> assertThat(x.getObservaciones()).isEqualTo(PlantillasJornada.OBS_COMPLETA_FERIANTE));
    }

    @Test
    void feriante_conFeriadosQueSumanDiasNoLaborables_noUsaDiasHabiles() {
        // Octubre + feriados 12/10 y 30/10 → 11 días no laborables: alcanzan los 10.
        ContextoDiagramacion ctx = new ContextoDiagramacion(Set.of(
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 30)));
        List<JornadaLaboral> j = PlantillasJornada.feriante(LocalTime.of(8, 0), OCT_1, OCT_31, ctx);

        assertThat(j).filteredOn(JornadaLaboral::tieneHorario).hasSize(10)
                .allSatisfy(x -> {
                    assertThat(esNoLaborable(x.getFecha(), ctx)).isTrue();
                    assertThat(x.calcularDuracion().toHours()).isEqualTo(12);
                });
        assertThat(horas(j)).isEqualTo(120);
    }

    @Test
    void feriante_mesConPocosDiasNoLaborables_completaLas120hEnDiasHabilesDe1a12h() {
        ContextoDiagramacion ctx = ContextoDiagramacion.sinFeriados();
        List<JornadaLaboral> j = PlantillasJornada.feriante(LocalTime.of(8, 0), FEB_1, FEB_28, ctx);

        // Febrero 2026: 8 sábados/domingos → 8 × 12 h = 96 h; faltan 24 h → 2 jornadas hábiles de 12 h.
        assertThat(j).hasSize(28);
        assertThat(horas(j)).isEqualTo(120);
        assertThat(j).filteredOn(x -> x.tieneHorario() && !esNoLaborable(x.getFecha(), ctx))
                .hasSize(2)
                .allSatisfy(x -> assertThat(x.calcularDuracion().toHours()).isBetween(1L, 12L));
    }

    @Test
    void feriante_resultadoPasaElValidador() {
        List<JornadaLaboral> j = PlantillasJornada.feriante(LocalTime.of(20, 0), FEB_1, FEB_28,
                ContextoDiagramacion.sinFeriados());
        j.forEach(x -> x.setEmpleadoId(EMPLEADO));

        // Misma hora de ingreso y ≤ 12 h: nunca menos de 12 h de descanso entre días seguidos.
        assertThat(DiagramaValidator.validar(j)).isEmpty();
    }

    @Test
    void feriante_guardiasRepartidasNoAmontonadasAlPrincipio() {
        ContextoDiagramacion ctx = new ContextoDiagramacion(Set.of(
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 30)));
        List<LocalDateTime> ingresos = PlantillasJornada.feriante(LocalTime.of(8, 0), OCT_1, OCT_31, ctx)
                .stream().filter(JornadaLaboral::tieneHorario).map(JornadaLaboral::getFechaIngreso).toList();

        assertThat(ingresos.get(0).toLocalDate()).isBeforeOrEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(ingresos.get(ingresos.size() - 1).toLocalDate()).isAfterOrEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void repartir_eligeElementosDistintosYEnOrden() {
        List<Integer> elegidos = PlantillasJornada.repartir(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), 10);

        assertThat(elegidos).hasSize(10).doesNotHaveDuplicates().isSorted();
    }
}
