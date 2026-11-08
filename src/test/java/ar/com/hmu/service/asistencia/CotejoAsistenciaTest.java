package ar.com.hmu.service.asistencia;

import ar.com.hmu.model.CodigoNovedad;
import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.repository.MarcacionRepository.Marcacion;
import ar.com.hmu.service.asistencia.DetalleParte.TipoDiferencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** CU30 (conformación de jornadas) y CU31/CU32 (cotejo y diferencias). */
class CotejoAsistenciaTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 6);
    private final UUID a = UUID.randomUUID();
    private final CotejoAsistencia.Agente agente = new CotejoAsistencia.Agente(a, "Prueba, Ana", UUID.randomUUID(),
            "Guardia Central", true);

    private DetalleParte cotejar(JornadaLaboral d, JornadaRegistrada r, List<Novedad> novedades) {
        List<DetalleParte> det = CotejoAsistencia.cotejar(DIA, List.of(agente),
                d != null ? Map.of(a, d) : Map.of(), r != null ? Map.of(a, r) : Map.of(),
                Map.of(a, novedades), 5);
        assertThat(det).hasSize(1);
        return det.get(0);
    }

    @Test
    void presenteDentroDeLaTolerancia() {
        DetalleParte d = cotejar(turno(7, 7), registrada(7, 4, 14, 2), List.of());
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.PRESENTE);
        assertThat(d.getTipoDiferencia()).isNull();
        assertThat(d.getObservaciones()).contains("4 min tarde");
    }

    @Test
    void llegadaTardeFueraDeLaTolerancia() {
        DetalleParte d = cotejar(turno(8, 12), registrada(8, 10, 20, 5), List.of());
        // Sin novedad aprobada: 666 hasta que la OP elija el código en la carga manual.
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.SIN_JUSTIFICAR);
        assertThat(d.sinJustificar()).isTrue();
        assertThat(d.getTipoDiferencia()).isEqualTo(TipoDiferencia.LLEGADA_TARDE);
        assertThat(CotejoAsistencia.formatearDiferencia(d.getDiferenciaMinutos())).isEqualTo("−0:05");
    }

    @Test
    void inasistenciaSinNovedadQuedaSinJustificar() {
        DetalleParte d = cotejar(turno(7, 7), null, List.of());
        assertThat(d.sinJustificar()).isTrue();
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.SIN_JUSTIFICAR);
        assertThat(d.getTipoDiferencia()).isEqualTo(TipoDiferencia.INASISTENCIA);
    }

    @Test
    void omisionDeEgreso() {
        JornadaRegistrada r = new JornadaRegistrada(UUID.randomUUID(), a, DIA, DIA.atTime(6, 57), null,
                UUID.randomUUID(), null);
        DetalleParte d = cotejar(turno(7, 7), r, List.of());
        assertThat(d.getTipoDiferencia()).isEqualTo(TipoDiferencia.OMISION_EGRESO);
        assertThat(d.getRegistrada()).isEqualTo("06:57 – sin egreso");
    }

    @Test
    void laNovedadAprobadaJustificaConSuCodigo() {
        Novedad ji = novedad("FJ", EstadoNovedad.APLICADA, false);
        DetalleParte d = cotejar(turno(7, 7), null, List.of(ji));
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.FALTA_JUSTIFICADA);
        assertThat(d.getOrigen()).isEqualTo(DetalleParte.Origen.NOVEDAD_APROBADA);
        assertThat(d.sinJustificar()).isFalse();
    }

    @Test
    void laNovedadEnTramiteNoJustificaPeroSeInforma() {
        Novedad ji = novedad("FJ", EstadoNovedad.PENDIENTE_DIRECCION, false);
        DetalleParte d = cotejar(turno(21, 10), null, List.of(ji));
        assertThat(d.sinJustificar()).isTrue();
        assertThat(d.getObservaciones()).contains("pendiente de la Dirección");
    }

    @Test
    void licenciaExterna() {
        Novedad lic = novedad("LIC", EstadoNovedad.APLICADA, true);
        lic.setCodigoNovedad(11);
        DetalleParte d = cotejar(turno(14, 7), null, List.of(lic));
        assertThat(d.getCodigo()).isEqualTo(11);
        assertThat(d.getOrigen()).isEqualTo(DetalleParte.Origen.LICENCIA_EXTERNA);
    }

    @Test
    void retiroAnticipadoSinNovedadQuedaCon666() {
        DetalleParte d = cotejar(turno(7, 7), registrada(7, 0, 12, 0), List.of());
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.SIN_JUSTIFICAR);
        assertThat(d.getTipoDiferencia()).isEqualTo(TipoDiferencia.RETIRO_ANTICIPADO);
    }

    @Test
    void pssrSeInformaCon333YLaHoraDeSalidaMarcada() {
        Novedad pssr = novedad("PSSR", EstadoNovedad.APLICADA, false);
        pssr.setHoraDesde(LocalTime.of(11, 0));
        pssr.setHoraHasta(LocalTime.of(14, 0));
        DetalleParte d = cotejar(turno(7, 7), registrada(7, 0, 11, 4), List.of(pssr));
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.PSSR).isEqualTo(333);
        assertThat(d.getObservaciones()).isEqualTo("PSSR 11:04 Hs");
    }

    @Test
    void elPaseConRetornoNoSeInforma() {
        Novedad con = novedad("PSP", EstadoNovedad.APLICADA, false);
        con.setHoraDesde(LocalTime.of(10, 0));
        con.setHoraHasta(LocalTime.of(11, 0));
        DetalleParte d = cotejar(turno(7, 7), registrada(7, 0, 14, 0), List.of(con));
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.PRESENTE);
        assertThat(d.getNovedadId()).isNull();
        // Con retorno y retiro anticipado: el pase no lo justifica.
        assertThat(cotejar(turno(7, 7), registrada(7, 0, 12, 0), List.of(con)).getCodigo())
                .isEqualTo(CodigoNovedad.SIN_JUSTIFICAR);
    }

    @Test
    void elFrancoDiagramadoNoSeInformaYElCompensatorioVaCon6() {
        JornadaLaboral franco = new JornadaLaboral(UUID.randomUUID(), null, a, DIA, TipoJornada.FRANCO, null, null, null);
        assertThat(CotejoAsistencia.cotejar(DIA, List.of(agente), Map.of(a, franco), Map.of(), Map.of(), 5)).isEmpty();
        JornadaLaboral fc = new JornadaLaboral(UUID.randomUUID(), null, a, DIA, TipoJornada.FRANCO_COMPENSATORIO,
                null, null, null);
        assertThat(cotejar(fc, null, List.of()).getCodigo()).isEqualTo(CodigoNovedad.FRANCO_COMPENSATORIO);
    }

    @Test
    void elCodigoEfectivoDeLaNovedadPrevaleceSobreElDeSuTipo() {
        Novedad fuerzaMayor = novedad("FJ", EstadoNovedad.APLICADA, false);
        fuerzaMayor.setCodigoNovedad(41);
        assertThat(cotejar(turno(7, 7), null, List.of(fuerzaMayor)).getCodigo()).isEqualTo(41);
    }

    @Test
    void horasExtraEnUnDiaSinJornadaEsPresente() {
        JornadaLaboral franco = new JornadaLaboral(UUID.randomUUID(), null, a, DIA, TipoJornada.FRANCO, null, null, null);
        DetalleParte d = cotejar(franco, registrada(8, 0, 12, 0), List.of(novedad("HE", EstadoNovedad.APLICADA, false)));
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.PRESENTE);
    }

    @Test
    void elCambioDeGuardiaAplicadoSeInformaConEl222() {
        Novedad cg = novedad("CG", EstadoNovedad.APLICADA, false);
        DetalleParte d = cotejar(turno(8, 12), registrada(8, 0, 20, 0), List.of(cg));
        assertThat(d.getCodigo()).isEqualTo(CodigoNovedad.CAMBIO_HORARIO_GUARDIA);
    }

    @Test
    void presenteSinDiagrama() {
        JornadaLaboral franco = new JornadaLaboral(UUID.randomUUID(), null, a, DIA, TipoJornada.FRANCO, null, null, null);
        DetalleParte d = cotejar(franco, registrada(8, 0, 12, 0), List.of());
        assertThat(d.getTipoDiferencia()).isEqualTo(TipoDiferencia.PRESENTE_SIN_DIAGRAMA);
        assertThat(CotejoAsistencia.formatearDiferencia(d.getDiferenciaMinutos())).isEqualTo("+4:00");
    }

    @Test
    void conformarEmparejaIngresoYEgresoConCruceDeMedianoche() {
        LocalDateTime noche = DIA.atTime(20, 0);
        List<JornadaRegistrada> j = ImportadorMarcaciones.conformar(a, List.of(
                new Marcacion(UUID.randomUUID(), a, noche.plusHours(12).plusMinutes(5), false, null),
                new Marcacion(UUID.randomUUID(), a, noche.minusMinutes(8), true, null),
                new Marcacion(UUID.randomUUID(), a, noche.plusDays(1).plusHours(10), true, null)));
        assertThat(j).hasSize(2);
        assertThat(j.get(0).fecha()).isEqualTo(DIA);
        assertThat(j.get(0).duracionMinutos()).isEqualTo(12 * 60 + 13);
        assertThat(j.get(1).omisionEgreso()).isTrue();
    }

    // ------------------------------------------------------------

    private JornadaLaboral turno(int hora, int horas) {
        LocalDateTime in = DIA.atTime(hora, 0);
        return new JornadaLaboral(UUID.randomUUID(), null, a, DIA,
                horas >= 12 ? TipoJornada.GUARDIA_ACTIVA : TipoJornada.TURNO_NORMAL, in, in.plusHours(horas), null);
    }

    private JornadaRegistrada registrada(int hIn, int mIn, int hOut, int mOut) {
        return new JornadaRegistrada(UUID.randomUUID(), a, DIA, DIA.atTime(hIn, mIn), DIA.atTime(hOut, mOut),
                UUID.randomUUID(), UUID.randomUUID());
    }

    private Novedad novedad(String codigo, EstadoNovedad estado, boolean externa) {
        Novedad n = new Novedad();
        n.setId(UUID.randomUUID());
        n.setTipo(new TipoNovedad(UUID.randomUUID(), codigo, codigo, false, 0, false, externa, true,
                switch (codigo) {
                    case "FJ" -> CodigoNovedad.FALTA_JUSTIFICADA;
                    case "CG", "CH" -> CodigoNovedad.CAMBIO_HORARIO_GUARDIA;
                    case "PSSR" -> CodigoNovedad.PSSR;
                    default -> null;
                }));
        n.setAgenteId(a);
        n.setEstado(estado);
        n.setFechaDesde(DIA);
        n.setFechaHasta(DIA);
        n.setHoraDesde(LocalTime.of(11, 0));
        n.setHoraHasta(LocalTime.of(13, 0));
        return n;
    }
}
