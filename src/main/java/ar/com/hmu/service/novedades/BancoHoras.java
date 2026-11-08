package ar.com.hmu.service.novedades;

import ar.com.hmu.model.MovimientoHoras;
import ar.com.hmu.repository.NovedadRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Banco de horas del agente (E4, Tabla 54): las horas extras para FC se
 * acreditan con vencimiento (regla RV-BANCO-VENC, 180 días) y los francos
 * compensatorios las consumen por orden de vencimiento (FIFO). Al vencer, un
 * movimiento VENCIMIENTO da de baja las horas sin usar. El saldo del agente
 * es la suma de las horas restantes de los créditos vigentes.
 *
 * <p>Todos los métodos corren dentro de la transacción del llamador.</p>
 */
public final class BancoHoras {

    public static final String REGLA_VENCIMIENTO = "RV-BANCO-VENC";
    public static final int VENCIMIENTO_DIAS = 180;

    private final NovedadRepository repo;

    public BancoHoras(NovedadRepository repo) {
        this.repo = repo;
    }

    /**
     * Acredita las horas extras de una novedad. Si ya estaban acreditadas y no se
     * usaron, se recalculan (las marcaciones pudieron corregirse); si ya se
     * consumieron en parte, se conserva el crédito original.
     *
     * @return el saldo vigente resultante.
     */
    public BigDecimal acreditar(Connection c, UUID agenteId, UUID novedadId, LocalDate fecha, BigDecimal horas,
                                String concepto, int venceDias, LocalDate hoy, UUID registradoPor)
            throws SQLException {
        MovimientoHoras previo = novedadId != null ? repo.creditoDeNovedad(c, novedadId) : null;
        if (previo != null) {
            boolean intacto = previo.horasRestantes() != null && previo.horasRestantes().compareTo(previo.horas()) == 0;
            if (intacto && previo.horas().compareTo(horas) != 0) {
                repo.actualizarCredito(c, previo.id(), horas, horas, concepto);
            }
            return repo.recalcularSaldo(c, agenteId, hoy);
        }
        if (horas == null || horas.signum() <= 0) {
            return repo.recalcularSaldo(c, agenteId, hoy);
        }
        BigDecimal saldo = repo.saldoVigenteEn(c, agenteId, hoy).add(horas);
        repo.insertarMovimiento(c, new MovimientoHoras(UUID.randomUUID(), agenteId, novedadId, fecha,
                MovimientoHoras.Tipo.CREDITO, horas, concepto, saldo, fecha.plusDays(venceDias), horas, null),
                registradoPor);
        return repo.recalcularSaldo(c, agenteId, hoy);
    }

    /**
     * Consume horas por orden de vencimiento (FIFO): un movimiento de débito por
     * cada crédito afectado.
     *
     * @throws SQLException si el saldo vigente no alcanza (la transacción se deshace).
     */
    public BigDecimal debitar(Connection c, UUID agenteId, UUID novedadId, LocalDate fecha, BigDecimal horas,
                              String concepto, LocalDate hoy, UUID registradoPor) throws SQLException {
        vencer(c, hoy, registradoPor);
        List<MovimientoHoras> creditos = repo.creditosVigentes(c, agenteId, hoy);
        BigDecimal disponible = creditos.stream().map(MovimientoHoras::horasRestantes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (disponible.compareTo(horas) < 0) {
            throw new SQLException("Saldo vigente insuficiente en el banco de horas: " + fmt(disponible)
                    + " h disponibles, se requieren " + fmt(horas) + " h.");
        }
        BigDecimal pendiente = horas;
        BigDecimal saldo = disponible;
        for (MovimientoHoras cr : creditos) {
            if (pendiente.signum() <= 0) break;
            BigDecimal toma = pendiente.min(cr.horasRestantes());
            repo.actualizarRestantes(c, cr.id(), cr.horasRestantes().subtract(toma));
            saldo = saldo.subtract(toma);
            repo.insertarMovimiento(c, new MovimientoHoras(UUID.randomUUID(), agenteId, novedadId, fecha,
                    MovimientoHoras.Tipo.DEBITO, toma.negate(), concepto + " (crédito del "
                    + cr.fecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")",
                    saldo, null, null, cr.id()), registradoPor);
            pendiente = pendiente.subtract(toma);
        }
        return repo.recalcularSaldo(c, agenteId, hoy);
    }

    /** Da de baja las horas sin usar de los créditos vencidos antes de hoy (todos los agentes). */
    public void vencer(Connection c, LocalDate hoy, UUID registradoPor) throws SQLException {
        List<MovimientoHoras> vencidos = repo.creditosVencidos(c, hoy);
        for (MovimientoHoras cr : vencidos) {
            BigDecimal restantes = cr.horasRestantes();
            repo.actualizarRestantes(c, cr.id(), BigDecimal.ZERO);
            BigDecimal saldo = repo.saldoVigenteEn(c, cr.agenteId(), hoy);
            repo.insertarMovimiento(c, new MovimientoHoras(UUID.randomUUID(), cr.agenteId(), null,
                    cr.fechaVencimiento().plusDays(1), MovimientoHoras.Tipo.VENCIMIENTO, restantes.negate(),
                    "Vencimiento del crédito del " + cr.fecha().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), saldo, null, null, cr.id()),
                    registradoPor);
            repo.recalcularSaldo(c, cr.agenteId(), hoy);
        }
    }

    static String fmt(BigDecimal horas) {
        return horas.stripTrailingZeros().toPlainString();
    }
}
