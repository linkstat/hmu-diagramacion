package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioAbierto;
import ar.com.hmu.model.HorarioConFranquicia;
import ar.com.hmu.model.HorarioDXI;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioGuardiaMedica;
import ar.com.hmu.model.HorarioJefeServicioGuardiaPasiva;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.HorarioSemanal;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.util.DiaSemana;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link GeneradorJornadasFactory} (selección de estrategia por
 * modalidad), del {@link GeneradorFranquiciaDecorator} y del helper
 * {@link GeneradorJornadas#estampar}.
 */
class GeneradorJornadasFactoryTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 7, 12);

    // ============================================================
    // Selección de estrategia
    // ============================================================

    @Test
    void paraHorario_mapeaCadaModalidadASuGenerador() {
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioEstandar()))
                .isInstanceOf(GeneradorEstandar.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioSemanal()))
                .isInstanceOf(GeneradorSemanal.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioNocturno()))
                .isInstanceOf(GeneradorNocturno.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioFeriante()))
                .isInstanceOf(GeneradorFeriante.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioDXI()))
                .isInstanceOf(GeneradorDXI.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioGuardiaMedica()))
                .isInstanceOf(GeneradorGuardiaMedica.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioGuardiaEnfermeria()))
                .isInstanceOf(GeneradorGuardiaEnfermeria.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioJefeServicioGuardiaPasiva()))
                .isInstanceOf(GeneradorJefeServicioGuardiaPasiva.class);
        assertThat(GeneradorJornadasFactory.paraHorario(new HorarioAbierto()))
                .isInstanceOf(GeneradorAbierto.class);
    }

    @Test
    void paraHorario_franquicia_devuelveElDecorator() {
        HorarioConFranquicia f = new HorarioConFranquicia();
        f.setHorarioDecorado(new HorarioEstandar());

        assertThat(GeneradorJornadasFactory.paraHorario(f))
                .isInstanceOf(GeneradorFranquiciaDecorator.class);
    }

    // ============================================================
    // GeneradorFranquiciaDecorator (integrado vía factory)
    // ============================================================

    private HorarioConFranquicia estandarConFranquicia(int horasFranquicia) {
        HorarioEstandar base = new HorarioEstandar();
        base.setDiasLaborables(List.of(DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES,
                DiaSemana.JUEVES, DiaSemana.VIERNES));
        base.setHorasPorDia(7);
        HorarioConFranquicia f = new HorarioConFranquicia();
        f.setHorarioDecorado(base);
        f.setHorasFranquicia(horasFranquicia);
        return f;
    }

    @Test
    void franquicia_descuentaHorasDelEgresoYAnotaObservacion() {
        HorarioConFranquicia f = estandarConFranquicia(1);

        List<JornadaLaboral> jornadas = GeneradorJornadasFactory.paraHorario(f)
                .generar(f, LUNES, LUNES, ContextoDiagramacion.sinFeriados());

        JornadaLaboral lunes = jornadas.get(0);
        assertThat(lunes.getFechaIngreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 7, 0));
        assertThat(lunes.getFechaEgreso()).isEqualTo(LocalDateTime.of(2026, 7, 6, 13, 0));
        assertThat(lunes.getObservaciones()).isEqualTo("Franquicia: -1 h");
    }

    @Test
    void franquicia_noTocaLosFrancos() {
        HorarioConFranquicia f = estandarConFranquicia(1);

        List<JornadaLaboral> jornadas = GeneradorJornadasFactory.paraHorario(f)
                .generar(f, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas).filteredOn(j -> j.getTipo() == TipoJornada.FRANCO)
                .hasSize(2)
                .allSatisfy(j -> {
                    assertThat(j.tieneHorario()).isFalse();
                    assertThat(j.getObservaciones()).isNull();
                });
    }

    @Test
    void franquicia_enCero_dejaLasJornadasIntactas() {
        HorarioConFranquicia f = estandarConFranquicia(0);

        List<JornadaLaboral> jornadas = GeneradorJornadasFactory.paraHorario(f)
                .generar(f, LUNES, LUNES, ContextoDiagramacion.sinFeriados());

        assertThat(jornadas.get(0).getFechaEgreso())
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 14, 0));
        assertThat(jornadas.get(0).getObservaciones()).isNull();
    }

    // ============================================================
    // estampar
    // ============================================================

    @Test
    void estampar_asignaIdsDiagramaYEmpleadoATodasLasJornadas() {
        HorarioEstandar h = new HorarioEstandar();
        h.setDiasLaborables(List.of(DiaSemana.LUNES));
        h.setHorasPorDia(7);
        List<JornadaLaboral> jornadas = new GeneradorEstandar().generar(
                h, LUNES, DOMINGO, ContextoDiagramacion.sinFeriados());
        UUID diagramaId = UUID.randomUUID();
        UUID empleadoId = UUID.randomUUID();

        GeneradorJornadas.estampar(jornadas, diagramaId, empleadoId);

        assertThat(jornadas).allSatisfy(j -> {
            assertThat(j.getId()).isNotNull();
            assertThat(j.getDiagramaId()).isEqualTo(diagramaId);
            assertThat(j.getEmpleadoId()).isEqualTo(empleadoId);
        });
        assertThat(jornadas).extracting(JornadaLaboral::getId).doesNotHaveDuplicates();
    }
}
