package ar.com.hmu.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NovedadTest {

    private static TipoNovedad tipo(String codigo) {
        return new TipoNovedad(UUID.randomUUID(), codigo, codigo, false, 0, false, false, true, null);
    }

    private static Novedad conJustificacion(TipoNovedad tipo, String justificacion) {
        Novedad n = new Novedad();
        n.setTipo(tipo);
        n.setJustificacion(justificacion);
        n.normalizarJustificacion();
        return n;
    }

    @Test
    void lasHorasExtrasConservanLaJustificacionSinEspaciosSobrantes() {
        Novedad n = conJustificacion(tipo(TipoNovedad.HORAS_EXTRA),
                "  Necesidad de realizar tareas de mantenimiento eléctrico con corte programado ");
        assertThat(n.getJustificacion())
                .isEqualTo("Necesidad de realizar tareas de mantenimiento eléctrico con corte programado");
    }

    @Test
    void laJustificacionEnBlancoQuedaNula() {
        assertThat(conJustificacion(tipo(TipoNovedad.HORAS_EXTRA), "   ").getJustificacion()).isNull();
    }

    @Test
    void losDemasTiposNoLlevanJustificacion() {
        for (String codigo : new String[] {"CG", "CH", "FC", "PSO", "PSP", "PSSR", "DEV", "FJ", "OM", "CS", "LIC"}) {
            assertThat(tipo(codigo).llevaJustificacion()).as(codigo).isFalse();
            assertThat(conJustificacion(tipo(codigo), Novedad.JUSTIFICACION_USUAL).getJustificacion())
                    .as(codigo).isNull();
        }
        assertThat(tipo(TipoNovedad.HORAS_EXTRA).llevaJustificacion()).isTrue();
    }

    @Test
    void laFormulaUsualEsNecesidadDelServicio() {
        assertThat(Novedad.JUSTIFICACION_USUAL).isEqualTo("Necesidad del servicio");
        assertThat(Novedad.JUSTIFICACION_MAX).isEqualTo(255);
    }
}
