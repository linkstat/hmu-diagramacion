package ar.com.hmu.service.diagramacion;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link ContextoDiagramacion}: calendario básico para los
 * generadores (fines de semana, feriados inyectados, días hábiles).
 *
 * Semana ancla: lunes 2026-07-06 a domingo 2026-07-12.
 */
class ContextoDiagramacionTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);
    private static final LocalDate SABADO = LocalDate.of(2026, 7, 11);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 7, 12);

    @Test
    void esFinDeSemana_distingueSabadoDomingoDeHabiles() {
        ContextoDiagramacion ctx = ContextoDiagramacion.sinFeriados();

        assertThat(ctx.esFinDeSemana(SABADO)).isTrue();
        assertThat(ctx.esFinDeSemana(DOMINGO)).isTrue();
        assertThat(ctx.esFinDeSemana(LUNES)).isFalse();
    }

    @Test
    void esFeriado_respondeSegunElSetInyectado() {
        LocalDate nueveDeJulio = LocalDate.of(2026, 7, 9);
        ContextoDiagramacion ctx = new ContextoDiagramacion(Set.of(nueveDeJulio));

        assertThat(ctx.esFeriado(nueveDeJulio)).isTrue();
        assertThat(ctx.esFeriado(LUNES)).isFalse();
    }

    @Test
    void sinFeriados_noReconoceNingunFeriado() {
        ContextoDiagramacion ctx = ContextoDiagramacion.sinFeriados();

        assertThat(ctx.esFeriado(LocalDate.of(2026, 7, 9))).isFalse();
        assertThat(ctx.esFeriado(LocalDate.of(2026, 12, 25))).isFalse();
    }

    @Test
    void esDiaHabil_excluyeFinesDeSemanaYFeriados() {
        LocalDate nueveDeJulio = LocalDate.of(2026, 7, 9); // jueves feriado
        ContextoDiagramacion ctx = new ContextoDiagramacion(Set.of(nueveDeJulio));

        assertThat(ctx.esDiaHabil(LUNES)).isTrue();
        assertThat(ctx.esDiaHabil(nueveDeJulio)).isFalse();
        assertThat(ctx.esDiaHabil(SABADO)).isFalse();
    }
}
