package ar.com.hmu.service.novedades;

import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.repository.NovedadRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aplicar novedad al diagrama (CU26): la novedad aprobada modifica las
 * jornadas afectadas del diagrama vigente (y las vincula con ella mediante
 * {@code JornadaLaboral.novedadID}) y el banco de horas del agente
 * ({@link BancoHoras}): el franco compensatorio consume horas por orden de
 * vencimiento (FIFO). Corre dentro de la transacción de la aprobación.
 *
 * <p>Las novedades que no modifican jornadas (falta justificada, omisión de
 * marcación, pases de salida y su devolución) quedan disponibles sólo para el
 * cotejo (CU26 1a). Las horas extras para FC se acreditan al cotejar las
 * marcaciones de la jornada, no al aprobarse.</p>
 */
class AplicadorNovedad {

    private final NovedadRepository repo;
    private final BancoHoras banco;

    AplicadorNovedad(NovedadRepository repo) {
        this.repo = repo;
        this.banco = new BancoHoras(repo);
    }

    void aplicar(Connection c, Novedad n, UUID registradoPor) throws SQLException {
        switch (n.getTipo().codigo()) {
            case TipoNovedad.CAMBIO_GUARDIA -> aplicarCambioGuardia(c, n);
            case TipoNovedad.CAMBIO_HORARIO -> {
                JornadaLaboral j = jornada(c, n.getAgenteId(), n.getFechaDesde());
                repo.actualizarJornada(c, j.getId(), j.getTipo(), n.getFechaDesde().atTime(n.getHoraDesde()),
                        ValidadorNovedad.egreso(n.getFechaDesde(), n.getHoraDesde(), n.getHoraHasta()), n.getId(),
                        "Cambio de horario");
            }
            case TipoNovedad.FRANCO_COMPENSATORIO -> {
                marcarRango(c, n, TipoJornada.FRANCO_COMPENSATORIO, true, "Franco compensatorio");
                if (n.getCantHoras() != null && n.getCantHoras().signum() > 0) {
                    // El débito se registra al aprobarse; el concepto lleva la fecha del franco.
                    java.time.LocalDate hoy = ar.com.hmu.util.Reloj.hoy();
                    banco.debitar(c, n.getAgenteId(), n.getId(), hoy, n.getCantHoras(), "Franco compensatorio del "
                            + n.getFechaDesde().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            hoy, registradoPor);
                }
            }
            case TipoNovedad.LICENCIA_EXTERNA ->
                    marcarRango(c, n, TipoJornada.LICENCIA, false, "Licencia " + valor(n.getReferenciaExterna()));
            default -> { /* sólo para el cotejo (las horas extras se acreditan al cotejar) */ }
        }
    }

    /** El agente cede su guardia del día 1 y recibe la de la contraparte del día 2. */
    private void aplicarCambioGuardia(Connection c, Novedad n) throws SQLException {
        UUID a = n.getAgenteId();
        UUID b = n.getContraparteAgenteId();
        List<JornadaLaboral> deA = new ArrayList<>();
        List<JornadaLaboral> deB = new ArrayList<>();
        for (LocalDate d : List.of(n.getFechaDesde(), n.getFechaHasta())) {
            deA.add(jornada(c, a, d));
            deB.add(jornada(c, b, d));
        }
        LocalDate d1 = ValidadorNovedad.fechaCedida(n, deA, deB);
        LocalDate d2 = d1.equals(n.getFechaDesde()) ? n.getFechaHasta() : n.getFechaDesde();
        JornadaLaboral cedida = jornada(c, a, d1);
        JornadaLaboral bDia1 = jornada(c, b, d1);
        JornadaLaboral aDia2 = jornada(c, a, d2);
        JornadaLaboral recibida = jornada(c, b, d2);
        String obs = "Cambio de guardia";
        repo.actualizarJornada(c, bDia1.getId(), cedida.getTipo(), cedida.getFechaIngreso(), cedida.getFechaEgreso(),
                n.getId(), obs);
        repo.actualizarJornada(c, cedida.getId(), TipoJornada.FRANCO, null, null, n.getId(), obs);
        repo.actualizarJornada(c, aDia2.getId(), recibida.getTipo(), recibida.getFechaIngreso(),
                recibida.getFechaEgreso(), n.getId(), obs);
        repo.actualizarJornada(c, recibida.getId(), TipoJornada.FRANCO, null, null, n.getId(), obs);
    }

    private void marcarRango(Connection c, Novedad n, TipoJornada tipo, boolean soloConHorario, String obs)
            throws SQLException {
        for (LocalDate d = n.getFechaDesde(); !d.isAfter(n.getFechaHasta()); d = d.plusDays(1)) {
            JornadaLaboral j = repo.jornadaVigente(c, n.getAgenteId(), d);
            if (j != null && (!soloConHorario || j.tieneHorario())) {
                repo.actualizarJornada(c, j.getId(), tipo, null, null, n.getId(), obs);
            }
        }
    }

    private JornadaLaboral jornada(Connection c, UUID agenteId, LocalDate fecha) throws SQLException {
        JornadaLaboral j = repo.jornadaVigente(c, agenteId, fecha);
        if (j == null) {
            throw new SQLException("No hay jornada en un diagrama aprobado para el " + fecha
                    + ": la novedad no puede aplicarse.");
        }
        return j;
    }

    private static String valor(String s) {
        return s != null ? s : "";
    }
}
