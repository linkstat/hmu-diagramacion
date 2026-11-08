package ar.com.hmu.service.novedades;

import ar.com.hmu.model.MovimientoHoras;
import ar.com.hmu.repository.NovedadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Banco de horas: créditos con vencimiento, débitos FIFO y vencimientos. */
class BancoHorasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);
    private final UUID agente = UUID.randomUUID();
    private final UUID op = UUID.randomUUID();
    private final Connection c = mock(Connection.class);
    private NovedadRepository repo;
    private BancoHoras banco;

    @BeforeEach
    void setUp() throws SQLException {
        repo = mock(NovedadRepository.class);
        banco = new BancoHoras(repo);
        when(repo.creditosVencidos(any(), any())).thenReturn(List.of());
        when(repo.saldoVigenteEn(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repo.recalcularSaldo(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void elFrancoCompensatorioConsumeElCreditoQueVenceAntes() throws SQLException {
        MovimientoHoras vencePronto = credito(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 28), "3");
        MovimientoHoras venceDespues = credito(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 2, 28), "5");
        when(repo.creditosVigentes(c, agente, HOY)).thenReturn(List.of(vencePronto, venceDespues));

        banco.debitar(c, agente, UUID.randomUUID(), HOY, new BigDecimal("4"), "Franco compensatorio", HOY, op);

        verify(repo).actualizarRestantes(c, vencePronto.id(), BigDecimal.ZERO);
        verify(repo).actualizarRestantes(c, venceDespues.id(), new BigDecimal("4"));
        ArgumentCaptor<MovimientoHoras> mov = ArgumentCaptor.forClass(MovimientoHoras.class);
        verify(repo, times(2)).insertarMovimiento(eq(c), mov.capture(), eq(op));
        assertThat(mov.getAllValues()).extracting(MovimientoHoras::tipo)
                .containsOnly(MovimientoHoras.Tipo.DEBITO);
        assertThat(mov.getAllValues()).extracting(MovimientoHoras::horas)
                .containsExactly(new BigDecimal("-3"), new BigDecimal("-1"));
        assertThat(mov.getAllValues()).extracting(MovimientoHoras::creditoId)
                .containsExactly(vencePronto.id(), venceDespues.id());
    }

    @Test
    void sinSaldoVigenteSuficienteNoSeDebita() throws SQLException {
        when(repo.creditosVigentes(c, agente, HOY)).thenReturn(List.of(
                credito(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 2, 28), "2")));
        assertThatThrownBy(() -> banco.debitar(c, agente, null, HOY, new BigDecimal("6"), "FC", HOY, op))
                .isInstanceOf(SQLException.class).hasMessageContaining("insuficiente");
        verify(repo, never()).insertarMovimiento(any(), any(), any());
    }

    @Test
    void elCreditoVenceALos180Dias() throws SQLException {
        LocalDate fecha = LocalDate.of(2026, 10, 3);
        banco.acreditar(c, agente, UUID.randomUUID(), fecha, new BigDecimal("2"), "Horas extras", 180, HOY, op);
        ArgumentCaptor<MovimientoHoras> mov = ArgumentCaptor.forClass(MovimientoHoras.class);
        verify(repo).insertarMovimiento(eq(c), mov.capture(), eq(op));
        assertThat(mov.getValue().tipo()).isEqualTo(MovimientoHoras.Tipo.CREDITO);
        assertThat(mov.getValue().fechaVencimiento()).isEqualTo(fecha.plusDays(180));
        assertThat(mov.getValue().horasRestantes()).isEqualByComparingTo("2");
    }

    @Test
    void alVencerLasHorasSinUsarQuedanEnCero() throws SQLException {
        MovimientoHoras vencido = credito(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 28), "1.5");
        when(repo.creditosVencidos(c, HOY)).thenReturn(List.of(vencido));
        banco.vencer(c, HOY, op);
        verify(repo).actualizarRestantes(c, vencido.id(), BigDecimal.ZERO);
        ArgumentCaptor<MovimientoHoras> mov = ArgumentCaptor.forClass(MovimientoHoras.class);
        verify(repo).insertarMovimiento(eq(c), mov.capture(), eq(op));
        assertThat(mov.getValue().tipo()).isEqualTo(MovimientoHoras.Tipo.VENCIMIENTO);
        assertThat(mov.getValue().horas()).isEqualByComparingTo("-1.5");
        assertThat(mov.getValue().creditoId()).isEqualTo(vencido.id());
    }

    private MovimientoHoras credito(LocalDate fecha, LocalDate vence, String restantes) {
        BigDecimal h = new BigDecimal(restantes);
        return new MovimientoHoras(UUID.randomUUID(), agente, UUID.randomUUID(), fecha, MovimientoHoras.Tipo.CREDITO,
                h, "Horas extras", h, vence, h, null);
    }
}
