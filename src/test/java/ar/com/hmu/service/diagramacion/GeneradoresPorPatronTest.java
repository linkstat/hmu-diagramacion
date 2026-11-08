package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioDXI;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.HorarioJefeServicioGuardiaPasiva;
import ar.com.hmu.model.HorarioSemanal;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.util.DiaSemana;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de los generadores de patrón semanal: {@link GeneradorEstandar},
 * {@link GeneradorJefeServicioGuardiaPasiva}, {@link GeneradorSemanal} y
 * {@link GeneradorDXI}.
 *
 * Semana ancla: lunes 2026-07-06 a domingo 2026-07-12.
 */
class GeneradoresPorPatronTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 7, 12);
    private static final List<DiaSemana> LUNES_A_VIERNES = List.of(
            DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES,
            DiaSemana.JUEVES, DiaSemana.VIERNES);

    private HorarioEstandar estandar(int horasPorDia) {
        HorarioEstandar h = new HorarioEstandar();
        h.setDiasLaborables(LUNES_A_VIERNES);
        h.setHorasPorDia(horasPorDia);
        return h;
    }

    // ============================================================
    // GeneradorEstandar
    // ============================================================

    @Test
    void estandar_semanaCompleta_cincoTurnosYDosFrancos() {
        List<JornadaLaboral> jornadas = new GeneradorEstandar().generar(
                estandar(7), LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).hasSize(7);
        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.TURNO_NORMAL).hasSize(5);
        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.FRANCO).hasSize(2);
    }

    @Test
    void estandar_turnoArrancaALaHoraDefectoYDuraLasHorasDelTemplate() {
        List<JornadaLaboral> jornadas = new GeneradorEstandar().generar(
                estandar(7), LUNES, LUNES, ContextoDiagramacion.sinFeriados());

        JornadaLaboral lunes = jornadas.get(0);
        assertThat(lunes.getFechaIngreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 7, 0));
        assertThat(lunes.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 14, 0));
    }

    @Test
    void estandar_feriadoEnDiaLaborable_salecomoFrancoConObservacion() {
        LocalDate jueves = LocalDate.of(2026, 7, 9);
        ContextoDiagramacion ctx = new ContextoDiagramacion(Set.of(jueves));

        List<JornadaLaboral> jornadas = new GeneradorEstandar().generar(
                estandar(7), LUNES, DOMINGO, ctx);

        JornadaLaboral feriado = jornadas.stream()
                .filter(j -> j.getFecha().equals(jueves)).findFirst().orElseThrow();
        assertThat(feriado.getTipo()).isEqualTo(TipoJornada.FRANCO);
        assertThat(feriado.getObservaciones()).isEqualTo("Feriado");
        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.TURNO_NORMAL).hasSize(4);
    }

    @Test
    void estandar_invarianteUnaJornadaPorDia_enMesCompleto() {
        LocalDate primero = LocalDate.of(2026, 7, 1);
        LocalDate ultimo = LocalDate.of(2026, 7, 31);

        List<JornadaLaboral> jornadas = new GeneradorEstandar().generar(
                estandar(7), primero, ultimo, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).hasSize(31);
        assertThat(jornadas).extracting(JornadaLaboral::getFecha).doesNotHaveDuplicates();
        assertThat(jornadas).allSatisfy(j -> assertThat(j.getTipo()).isNotNull());
    }

    @Test
    void estandar_sinDiasLaborables_todoFranco() {
        HorarioEstandar h = new HorarioEstandar(); // diasLaborables null

        List<JornadaLaboral> jornadas = new GeneradorEstandar().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).hasSize(7)
                .allSatisfy(j -> assertThat(j.getTipo()).isEqualTo(TipoJornada.FRANCO));
    }

    // ============================================================
    // GeneradorJefeServicioGuardiaPasiva
    // ============================================================

    @Test
    void jefeGuardiaPasiva_usaLasHorasReducidasDelTemplate() {
        HorarioJefeServicioGuardiaPasiva h = new HorarioJefeServicioGuardiaPasiva();
        h.setDiasLaborables(LUNES_A_VIERNES);
        h.setHorasPorDia(6); // 7 - 1 por guardia pasiva, ya reducido en el dato

        List<JornadaLaboral> jornadas = new GeneradorJefeServicioGuardiaPasiva().generar(
                h, LUNES, LUNES, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas.get(0).getFechaEgreso())
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 13, 0));
    }

    // ============================================================
    // GeneradorSemanal
    // ============================================================

    @Test
    void semanal_usaHorasEInicioPropiosDeCadaDia_yCruzaMedianoche() {
        HorarioSemanal h = new HorarioSemanal();
        h.setDistribucionSemanal(Map.of(DiaSemana.VIERNES, 10));
        h.setHoraInicioPorDia(Map.of(DiaSemana.VIERNES, LocalTime.of(20, 0)));

        List<JornadaLaboral> jornadas = new GeneradorSemanal().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        JornadaLaboral viernes = jornadas.stream()
                .filter(j -> j.getTipo() == TipoJornada.TURNO_NORMAL).findFirst().orElseThrow();
        assertThat(viernes.getFecha()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(viernes.getFechaIngreso()).isEqualTo(LocalDateTime.of(2026, 7, 10, 20, 0));
        assertThat(viernes.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 11, 6, 0));
        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.FRANCO).hasSize(6);
    }

    @Test
    void semanal_diaSinHoraDeInicio_usaLaHoraDefecto() {
        HorarioSemanal h = new HorarioSemanal();
        h.setDistribucionSemanal(Map.of(DiaSemana.LUNES, 7));
        h.setHoraInicioPorDia(Map.of()); // sin inicio definido

        List<JornadaLaboral> jornadas = new GeneradorSemanal().generar(
                h, LUNES, LUNES, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas.get(0).getFechaIngreso())
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 7, 0));
    }

    @Test
    void semanal_feriadoEnDiaConDistribucion_saleFranco() {
        LocalDate jueves = LocalDate.of(2026, 7, 9);
        HorarioSemanal h = new HorarioSemanal();
        h.setDistribucionSemanal(Map.of(DiaSemana.JUEVES, 7));

        List<JornadaLaboral> jornadas = new GeneradorSemanal().generar(
                h, LUNES, DOMINGO, new ContextoDiagramacion(Set.of(jueves)));

        assertThat(jornadas)
                .allSatisfy(j -> assertThat(j.getTipo()).isEqualTo(TipoJornada.FRANCO));
    }

    // ============================================================
    // GeneradorDXI
    // ============================================================

    @Test
    void dxi_generaSegunSuDistribucionHoraria() {
        HorarioDXI h = new HorarioDXI();
        h.setDistribucionHoraria(Map.of(
                DiaSemana.LUNES, 8, DiaSemana.MARTES, 8, DiaSemana.MIERCOLES, 8));
        h.setHoraInicioPorDia(Map.of(DiaSemana.LUNES, LocalTime.of(8, 0)));

        List<JornadaLaboral> jornadas = new GeneradorDXI().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.TURNO_NORMAL).hasSize(3);
        JornadaLaboral lunes = jornadas.get(0);
        assertThat(lunes.getFechaIngreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 8, 0));
        assertThat(lunes.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 16, 0));
    }
}
