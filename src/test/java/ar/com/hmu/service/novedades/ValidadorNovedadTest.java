package ar.com.hmu.service.novedades;

import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.InstanciaNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.TipoNovedad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorNovedadTest {

    private static final TipoNovedad CG = tipo("CG", true, 0, false, false);
    private static final TipoNovedad FC = tipo("FC", false, -1, false, false);
    private static final TipoNovedad FJ = tipo("FJ", false, 0, true, false);
    private static final TipoNovedad LIC = tipo("LIC", false, 0, false, true);
    private static final TipoNovedad PSO = tipo("PSO", false, 0, false, false);
    private static final TipoNovedad PSP = tipo("PSP", false, 0, false, false);
    private static final TipoNovedad PSSR = tipo("PSSR", false, 0, false, false);
    private static final TipoNovedad DEV = tipo("DEV", false, 0, false, false);
    private static final TipoNovedad HE = tipo("HE", false, 1, false, false);

    private final UUID servicio = UUID.randomUUID();
    private final UUID moreno = UUID.randomUUID();
    private final UUID ledesma = UUID.randomUUID();
    private final UUID otroServicio = UUID.randomUUID();

    private final Map<UUID, List<JornadaLaboral>> jornadas = new HashMap<>();
    private final List<Novedad> novedades = new ArrayList<>();
    private final Map<UUID, BigDecimal> saldos = new HashMap<>();
    private LocalDate hoy = LocalDate.of(2026, 10, 7);
    private ValidadorNovedad validador;

    @BeforeEach
    void setUp() {
        validador = new ValidadorNovedad(new FuenteDatosNovedad() {
            @Override
            public List<JornadaLaboral> jornadasVigentes(UUID agenteId, LocalDate desde, LocalDate hasta) {
                return jornadas.getOrDefault(agenteId, List.of()).stream()
                        .filter(j -> !j.getFecha().isBefore(desde) && !j.getFecha().isAfter(hasta)).toList();
            }

            @Override
            public List<Novedad> novedadesEnRango(UUID agenteId, LocalDate desde, LocalDate hasta,
                                                  Collection<EstadoNovedad> estados) {
                return novedades.stream().filter(n -> n.involucraA(agenteId) && estados.contains(n.getEstado())
                        && !n.getFechaDesde().isAfter(hasta) && !n.getFechaHasta().isBefore(desde)).toList();
            }

            @Override
            public BigDecimal saldoHoras(UUID agenteId) {
                return saldos.getOrDefault(agenteId, BigDecimal.ZERO);
            }

            @Override
            public UUID servicioDe(UUID agenteId) {
                return agenteId.equals(otroServicio) ? UUID.randomUUID() : servicio;
            }

            @Override
            public Novedad novedad(UUID id) {
                return novedades.stream().filter(n -> id.equals(n.getId())).findFirst().orElse(null);
            }

            @Override
            public List<Novedad> devolucionesDe(UUID paseId) {
                return novedades.stream().filter(n -> paseId.equals(n.getNovedadOrigenId())).toList();
            }

            @Override
            public LocalDate hoy() {
                return hoy;
            }
        });
        // Octubre: Moreno guardia 18/10 20:00-08:00 y 22/10 08-20; Ledesma 16/10 y 23/10 08-20.
        for (int d = 1; d <= 31; d++) {
            jornada(moreno, d, null, 0);
            jornada(ledesma, d, null, 0);
            jornada(otroServicio, d, null, 0);
        }
        jornada(moreno, 18, LocalTime.of(20, 0), 12);
        jornada(moreno, 22, LocalTime.of(8, 0), 12);
        jornada(ledesma, 16, LocalTime.of(8, 0), 12);
        jornada(ledesma, 23, LocalTime.of(8, 0), 12);
        jornada(otroServicio, 23, LocalTime.of(8, 0), 12);
    }

    @Test
    void cambioDeGuardiaCompatibleNoTieneViolaciones() {
        ResultadoValidacion r = validador.validar(cg(18, ledesma, 23), false);
        assertThat(r.violaciones()).isEmpty();
    }

    @Test
    void cambioDeGuardiaQueDejaMenosDeDiezHorasDeDescansoEsBloqueante() {
        // Ledesma sale de una guardia el 22/10 a las 02:00 y recibiría la de Moreno del 22/10 a las 08:00.
        jornada(ledesma, 21, LocalTime.of(14, 0), 12);
        ResultadoValidacion r = validador.validar(cg(22, ledesma, 23), false);
        assertThat(r.bloqueantes()).extracting(ViolacionNovedad::regla).containsExactly(ValidadorNovedad.RV_DESCANSO);
        assertThat(r.bloqueantes().get(0).mensaje()).contains("a la contraparte").contains("6 h");
    }

    @Test
    void descansoInsuficienteTrasElCambio() {
        // Ledesma ya tiene guardia el 19/10 06:00: recibir la del 18/10 20:00-08:00 se superpone.
        jornada(ledesma, 19, LocalTime.of(6, 0), 6);
        ResultadoValidacion r = validador.validar(cg(18, ledesma, 23), false);
        assertThat(r.bloqueantes()).anyMatch(v -> v.mensaje().contains("Superposición")
                || v.regla().equals(ValidadorNovedad.RV_DESCANSO));
    }

    @Test
    void contraparteDeOtroServicioEsBloqueante() {
        ResultadoValidacion r = validador.validar(cg(18, otroServicio, 23), false);
        assertThat(r.bloqueantes()).extracting(ViolacionNovedad::regla)
                .containsExactly(ValidadorNovedad.RV_COMPATIBILIDAD);
    }

    @Test
    void contraparteSinGuardiaEseDiaEsBloqueante() {
        ResultadoValidacion r = validador.validar(cg(18, ledesma, 24), false);
        assertThat(r.bloqueantes()).anyMatch(v -> v.mensaje().contains("no tiene una guardia"));
    }

    @Test
    void francoCompensatorioSinSaldoEsBloqueante() {
        saldos.put(moreno, BigDecimal.valueOf(6));
        Novedad n = base(FC, moreno, 18, 18);
        ResultadoValidacion r = validador.validar(n, false);
        assertThat(n.getCantHoras()).isEqualByComparingTo("12");
        assertThat(r.bloqueantes()).extracting(ViolacionNovedad::regla).containsExactly(ValidadorNovedad.RV_SALDO);
    }

    @Test
    void francoCompensatorioConSaldoEsValido() {
        saldos.put(moreno, BigDecimal.valueOf(20));
        assertThat(validador.validar(base(FC, moreno, 18, 18), false).violaciones()).isEmpty();
    }

    @Test
    void inasistenciaJustificadaRequiereRespaldo() {
        ResultadoValidacion r = validador.validar(base(FJ, moreno, 18, 18), false);
        assertThat(r.bloqueantes()).anyMatch(v -> v.mensaje().contains("documentación"));
    }

    @Test
    void superarElTopeDeFaltasRequiereSecretariaDeSalud() {
        ResultadoValidacion r = validador.validar(base(FJ, moreno, 18, 20), true);
        assertThat(r.hayBloqueantes()).isFalse();
        assertThat(r.requiereSecretariaSalud()).isTrue();
        assertThat(r.violaciones()).extracting(ViolacionNovedad::regla).contains(ValidadorNovedad.RV_TOPE_FALTAS);
    }

    @Test
    void superposicionConLicenciaAprobadaEsBloqueante() {
        Novedad lic = base(LIC, moreno, 15, 20);
        lic.setId(UUID.randomUUID());
        lic.setEstado(EstadoNovedad.APLICADA);
        novedades.add(lic);
        ResultadoValidacion r = validador.validar(base(FJ, moreno, 18, 18), true);
        assertThat(r.bloqueantes()).extracting(ViolacionNovedad::regla).contains(ValidadorNovedad.RV_SUPERPOSICION);
    }

    @Test
    void sinDiagramaAprobadoEsBloqueante() {
        Novedad n = base(FJ, moreno, 18, 18);
        n.setFechaDesde(LocalDate.of(2026, 12, 1));
        n.setFechaHasta(LocalDate.of(2026, 12, 1));
        assertThat(validador.validar(n, true).bloqueantes()).extracting(ViolacionNovedad::regla)
                .containsExactly(ValidadorNovedad.RV_DIAGRAMA_VIGENTE);
    }

    @Test
    void elFrancoCompensatorioRetroactivoSoloHastaElDia7DelMesSiguiente() {
        saldos.put(moreno, new BigDecimal("24"));
        Novedad fc = base(FC, moreno, 22, 22);
        hoy = LocalDate.of(2026, 11, 5);
        assertThat(validador.validar(fc, false).bloqueantes()).isEmpty();
        hoy = LocalDate.of(2026, 11, 8);
        Novedad tarde = base(FC, moreno, 22, 22);
        assertThat(validador.validar(tarde, false).bloqueantes()).extracting(ViolacionNovedad::regla)
                .containsExactly(ValidadorNovedad.RV_FC_FECHA);
    }

    @Test
    void laJustificacionDeLasHorasExtrasNoSuperaLos255Caracteres() {
        Novedad he = base(HE, moreno, 22, 22);
        he.setJustificacion(Novedad.JUSTIFICACION_USUAL);
        assertThat(validador.validar(he, false).bloqueantes()).isEmpty();
        he.setJustificacion("x".repeat(Novedad.JUSTIFICACION_MAX + 1));
        assertThat(validador.validar(he, false).bloqueantes()).extracting(ViolacionNovedad::mensaje)
                .containsExactly("La justificación no puede superar los 255 caracteres.");
    }

    @Test
    void elPaseOficialEsSoloUnaConstancia() {
        // Sin tope por pase: 5 h dentro de la guardia del 22/10 (08-20).
        Novedad oficial = pase(PSO, 22, LocalTime.of(9, 0), LocalTime.of(14, 0));
        assertThat(validador.validar(oficial, false).violaciones()).isEmpty();
        assertThat(PSO.esPaseADevolver()).isFalse();
        // No suma al tope mensual: con 5 h de pase oficial aplicado, un pase con retorno de 3 h es válido.
        oficial.setId(UUID.randomUUID());
        oficial.setEstado(EstadoNovedad.APLICADA);
        novedades.add(oficial);
        assertThat(validador.validar(pase(PSP, 22, LocalTime.of(15, 0), LocalTime.of(18, 0)), false)
                .violaciones()).isEmpty();
        // No se devuelve.
        assertThat(validador.validar(devolucion(oficial, 25, 18, 20), false).bloqueantes())
                .extracting(ViolacionNovedad::regla).containsExactly(ValidadorNovedad.RV_DEVOLUCION);
    }

    @Test
    void elPaseConRetornoNoSuperaLas3Horas() {
        Novedad pase = pase(PSP, 22, LocalTime.of(9, 0), LocalTime.of(13, 0));
        assertThat(validador.validar(pase, false).bloqueantes()).extracting(ViolacionNovedad::regla)
                .containsExactly(ValidadorNovedad.RV_PASE_MAX);
    }

    @Test
    void elPssrSeIniciaHasta3HorasAntesDelEgreso() {
        // Guardia 22/10 08:00-20:00.
        Novedad temprano = pase(PSSR, 22, LocalTime.of(16, 0), null);
        assertThat(validador.validar(temprano, false).bloqueantes()).extracting(ViolacionNovedad::regla)
                .containsExactly(ValidadorNovedad.RV_PSSR);
        Novedad ok = pase(PSSR, 22, LocalTime.of(18, 0), null);
        assertThat(validador.validar(ok, false).bloqueantes()).isEmpty();
        assertThat(ok.getHoraHasta()).isEqualTo(LocalTime.of(20, 0));
        assertThat(ok.getCantHoras()).isEqualByComparingTo("2");
    }

    @Test
    void topeMensualDePasesConYSinRetorno() {
        Novedad previo = pase(PSP, 22, LocalTime.of(9, 0), LocalTime.of(12, 0));
        previo.setId(UUID.randomUUID());
        previo.setCantHoras(new BigDecimal("3"));
        previo.setEstado(EstadoNovedad.APLICADA);
        novedades.add(previo);
        Novedad pssr = pase(PSSR, 18, LocalTime.of(5, 30), null);   // guardia 18/10 20:00-08:00: 2 h 30 min
        assertThat(validador.validar(pssr, false).bloqueantes()).extracting(ViolacionNovedad::regla)
                .containsExactly(ValidadorNovedad.RV_PASE_TOPE);
    }

    @Test
    void laDevolucionRespetaPlazoYHorasPendientes() {
        Novedad pssr = pase(PSSR, 22, LocalTime.of(18, 0), LocalTime.of(20, 0));
        pssr.setId(UUID.randomUUID());
        pssr.setCantHoras(new BigDecimal("2"));
        pssr.setEstado(EstadoNovedad.APLICADA);
        novedades.add(pssr);
        // 25/10 (franco), 18-20: dentro de los 7 días y de las 2 h pendientes.
        assertThat(validador.validar(devolucion(pssr, 25, 18, 20), false).bloqueantes()).isEmpty();
        // 30/10: fuera de plazo.
        assertThat(validador.validar(devolucion(pssr, 30, 18, 20), false).bloqueantes())
                .extracting(ViolacionNovedad::regla).containsExactly(ValidadorNovedad.RV_DEVOLUCION);
        // Con 1 h ya devuelta, no se pueden devolver 2 h más.
        Novedad parcial = devolucion(pssr, 24, 18, 19);
        parcial.setId(UUID.randomUUID());
        parcial.setCantHoras(BigDecimal.ONE);
        parcial.setEstado(EstadoNovedad.APLICADA);
        novedades.add(parcial);
        assertThat(validador.validar(devolucion(pssr, 25, 18, 20), false).bloqueantes())
                .extracting(ViolacionNovedad::regla).containsExactly(ValidadorNovedad.RV_DEVOLUCION);
        // Dentro de la jornada diagramada (22/10 08-20) no es devolución.
        assertThat(validador.validar(devolucion(pssr, 22, 10, 11), false).bloqueantes())
                .extracting(ViolacionNovedad::regla).contains(ValidadorNovedad.RV_COMPATIBILIDAD);
    }

    @Test
    void circuitoDeUnAgenteYDeUnaJefatura() {
        assertThat(NovedadStateMachine.circuito(false, false)).containsExactly(
                InstanciaNovedad.JEFATURA, InstanciaNovedad.OFICINA_PERSONAL, InstanciaNovedad.DIRECCION);
        assertThat(NovedadStateMachine.circuito(true, true)).containsExactly(
                InstanciaNovedad.DIRECCION, InstanciaNovedad.OFICINA_PERSONAL, InstanciaNovedad.DIRECCION,
                InstanciaNovedad.SECRETARIA_SALUD);
        assertThat(NovedadStateMachine.trasAutorizar(InstanciaNovedad.OFICINA_PERSONAL))
                .isEqualTo(EstadoNovedad.PENDIENTE_OFICINA_PERSONAL);
        assertThat(NovedadStateMachine.trasAutorizar(null)).isEqualTo(EstadoNovedad.APROBADA);
    }

    // ------------------------------------------------------------

    private Novedad cg(int cede, UUID contraparte, int recibe) {
        Novedad n = base(CG, moreno, cede, recibe);
        n.setContraparteAgenteId(contraparte);
        return n;
    }

    private Novedad base(TipoNovedad tipo, UUID agente, int desde, int hasta) {
        Novedad n = new Novedad();
        n.setTipo(tipo);
        n.setAgenteId(agente);
        n.setSolicitadoPorId(agente);
        n.setFechaDesde(LocalDate.of(2026, 10, desde));
        n.setFechaHasta(LocalDate.of(2026, 10, hasta));
        n.setMotivo("Motivo de prueba");
        return n;
    }

    private Novedad pase(TipoNovedad tipo, int dia, LocalTime desde, LocalTime hasta) {
        Novedad n = base(tipo, moreno, dia, dia);
        n.setHoraDesde(desde);
        n.setHoraHasta(hasta);
        return n;
    }

    private Novedad devolucion(Novedad pase, int dia, int desde, int hasta) {
        Novedad n = base(DEV, moreno, dia, dia);
        n.setNovedadOrigenId(pase.getId());
        n.setHoraDesde(LocalTime.of(desde, 0));
        n.setHoraHasta(LocalTime.of(hasta, 0));
        return n;
    }

    private void jornada(UUID agente, int dia, LocalTime ingreso, int horas) {
        List<JornadaLaboral> lista = jornadas.computeIfAbsent(agente, k -> new ArrayList<>());
        LocalDate fecha = LocalDate.of(2026, 10, dia);
        lista.removeIf(j -> j.getFecha().equals(fecha));
        if (ingreso == null) {
            lista.add(new JornadaLaboral(UUID.randomUUID(), null, agente, fecha, TipoJornada.FRANCO, null, null, null));
        } else {
            lista.add(new JornadaLaboral(UUID.randomUUID(), null, agente, fecha, TipoJornada.GUARDIA_ACTIVA,
                    fecha.atTime(ingreso), fecha.atTime(ingreso).plusHours(horas), null));
        }
    }

    private static TipoNovedad tipo(String codigo, boolean contraparte, int saldo, boolean doc, boolean externa) {
        return new TipoNovedad(UUID.randomUUID(), codigo, codigo, contraparte, saldo, doc, externa, true, null);
    }
}
