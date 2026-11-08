package ar.com.hmu.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del POJO {@link Memorandum} y enums asociados.
 *
 * Foco en la mecánica básica del modelo (constructores, getters/setters,
 * mutadores de listas, conversión de estado). La lógica de negocio se
 * testea en {@code MemorandumServiceTest} (próximo commit).
 */
class MemorandumTest {

    @Test
    void constructor_inicializaCamposBasicos() {
        UUID id = UUID.randomUUID();
        UUID remitenteId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();

        Memorandum m = new Memorandum(id, "Asunto X", "Contenido Y", remitenteId, estadoId);

        assertThat(m.getId()).isEqualTo(id);
        assertThat(m.getAsunto()).isEqualTo("Asunto X");
        assertThat(m.getContenido()).isEqualTo("Contenido Y");
        assertThat(m.getRemitenteId()).isEqualTo(remitenteId);
        assertThat(m.getEstadoTramiteId()).isEqualTo(estadoId);
        assertThat(m.getDestinatarios()).isEmpty();
        assertThat(m.getAutorizaciones()).isEmpty();
    }

    @Test
    void agregarDestinatario_sumaALaLista() {
        Memorandum m = new Memorandum();
        UUID memoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        m.agregarDestinatario(new MemorandumDestinatario(memoId, usuarioId));

        assertThat(m.getDestinatarios()).hasSize(1);
        assertThat(m.getDestinatarios().get(0).getUsuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void agregarDestinatarioNull_lanzaIllegalArgumentException() {
        Memorandum m = new Memorandum();

        assertThatThrownBy(() -> m.agregarDestinatario(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agregarAutorizacion_sumaALaLista() {
        Memorandum m = new Memorandum();
        MemorandumAutorizacion auth = new MemorandumAutorizacion(
                UUID.randomUUID(), UUID.randomUUID(),
                TipoRolMemoAutorizacion.JEFATURADESERVICIO,
                EstadoMemorandumAutorizacion.PENDIENTE);

        m.agregarAutorizacion(auth);

        assertThat(m.getAutorizaciones()).hasSize(1);
        assertThat(m.getAutorizaciones().get(0).getEstado())
                .isEqualTo(EstadoMemorandumAutorizacion.PENDIENTE);
    }

    @Test
    void destinatario_estaLeido_falsoSiSinFecha_verdaderoConFecha() {
        MemorandumDestinatario d = new MemorandumDestinatario(UUID.randomUUID(), UUID.randomUUID());
        assertThat(d.estaLeido()).isFalse();

        d.setFechaRecepcion(LocalDateTime.now());
        assertThat(d.estaLeido()).isTrue();
    }

    @Test
    void estadoTramite_fromDbName_traduceNombresConEspacios() {
        assertThat(EstadoTramite.fromDbName("PENDIENTE DE AUTORIZACION"))
                .isEqualTo(EstadoTramite.PENDIENTE_DE_AUTORIZACION);
        assertThat(EstadoTramite.fromDbName("BORRADOR"))
                .isEqualTo(EstadoTramite.BORRADOR);
        assertThat(EstadoTramite.OBSERVADO.toDbName()).isEqualTo("OBSERVADO");
        assertThat(EstadoTramite.PENDIENTE_DE_FIRMA.toDbName())
                .isEqualTo("PENDIENTE DE FIRMA");
    }

    @Test
    void estadoTramite_fromDbName_nombreInvalido_lanzaIllegalArgument() {
        assertThatThrownBy(() -> EstadoTramite.fromDbName("INVALIDO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALIDO");
    }

    @Test
    void enumsDeMemorandum_valoresEsperados() {
        assertThat(EstadoMemorandumAutorizacion.values())
                .containsExactly(
                        EstadoMemorandumAutorizacion.PENDIENTE,
                        EstadoMemorandumAutorizacion.AUTORIZADO,
                        EstadoMemorandumAutorizacion.RECHAZADO,
                        EstadoMemorandumAutorizacion.OBSERVADO);
        assertThat(TipoRolMemoAutorizacion.values()).hasSize(4);
    }
}
