package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.AmbitoRegla;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.ReglaValidacion;
import ar.com.hmu.model.Severidad;
import ar.com.hmu.model.TipoJornada;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** CU08 y CU13: el motor aplica los parámetros y la severidad del catálogo vigente. */
class ReglasParametrizadasTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 2);

    @Test
    void elDescansoMinimoSaleDelCatalogo() {
        UUID a = UUID.randomUUID();
        // 11 h de descanso: válido con 10 h, inválido si la regla pasa a 12 h.
        List<JornadaLaboral> js = List.of(jornada(a, DIA, 8, 12), jornada(a, DIA.plusDays(1), 7, 12));
        assertThat(DiagramaValidator.validar(js)).isEmpty();

        CatalogoReglas doce = CatalogoReglas.de(List.of(regla(CatalogoReglas.DESCANSO, Severidad.BLOQUEANTE,
                Map.of("horasMinimas", 12.0), true)));
        assertThat(DiagramaValidator.validar(js, doce)).singleElement()
                .satisfies(v -> assertThat(v.mensaje()).contains("mínimo 12 h"));
    }

    @Test
    void elMensajeTerminaConElFundamentoNormativo() {
        UUID a = UUID.randomUUID();
        List<JornadaLaboral> js = List.of(jornada(a, DIA, 8, 12), jornada(a, DIA.plusDays(1), 2, 12));
        ReglaValidacion descanso = regla(CatalogoReglas.DESCANSO, Severidad.BLOQUEANTE,
                Map.of("horasMinimas", 10.0), true);
        descanso.setFundamentoNormativo("Decreto 1605/2018");
        CatalogoReglas catalogo = CatalogoReglas.de(List.of(descanso));
        // Se integra al paréntesis final del mensaje, sin ". (" encadenado.
        assertThat(DiagramaValidator.validar(js, catalogo)).singleElement()
                .satisfies(v -> assertThat(v.mensaje())
                        .endsWith("(mínimo 10 h corridas; Decreto 1605/2018).")
                        .doesNotContain(". ("));
        // Sin paréntesis final: uno nuevo antes del punto.
        assertThat(catalogo.conFundamento(CatalogoReglas.DESCANSO, "Se esperan 140 h."))
                .isEqualTo("Se esperan 140 h (Decreto 1605/2018).");
        // Sin fundamento, el mensaje no cambia; y no se duplica si ya lo trae.
        assertThat(DiagramaValidator.validar(js).get(0).mensaje()).doesNotContain("Decreto");
        String yaLoTrae = "x (mínimo 10 h; Decreto 1605/2018).";
        assertThat(catalogo.conFundamento(CatalogoReglas.DESCANSO, yaLoTrae)).isEqualTo(yaLoTrae);
    }

    @Test
    void unaReglaComoAdvertenciaNoBloquea() {
        UUID a = UUID.randomUUID();
        List<JornadaLaboral> js = List.of(jornada(a, DIA, 8, 12), jornada(a, DIA.plusDays(1), 2, 12));
        CatalogoReglas advertencia = CatalogoReglas.de(List.of(regla(CatalogoReglas.DESCANSO, Severidad.ADVERTENCIA,
                Map.of("horasMinimas", 10.0), true)));
        DiagramaValidator.Resultado r = DiagramaValidator.evaluar(js, Map.of(), advertencia);
        assertThat(r.bloqueantes()).isEmpty();
        assertThat(r.advertencias()).hasSize(1);
    }

    @Test
    void unaReglaInactivaNoSeEvalua() {
        UUID a = UUID.randomUUID();
        List<JornadaLaboral> js = List.of(jornada(a, DIA, 8, 12), jornada(a, DIA.plusDays(1), 2, 12));
        CatalogoReglas inactiva = CatalogoReglas.de(List.of(regla(CatalogoReglas.DESCANSO, Severidad.BLOQUEANTE,
                Map.of("horasMinimas", 10.0), false)));
        assertThat(DiagramaValidator.validar(js, inactiva)).isEmpty();
    }

    @Test
    void dotacionMinimaPorFranja() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // Mañana: dos agentes (07-14). Tarde y noche: nadie.
        List<JornadaLaboral> js = List.of(jornada(a, DIA, 7, 7), jornada(b, DIA, 7, 7));
        List<Violacion> v = DiagramaValidator.dotacion(js, 2, CatalogoReglas.PREDETERMINADO);
        assertThat(v).extracting(Violacion::mensaje)
                .anyMatch(m -> m.contains("franja tarde"))
                .anyMatch(m -> m.contains("franja noche"))
                .noneMatch(m -> m.contains("franja mañana"));
        DiagramaValidator.Resultado r = DiagramaValidator.evaluar(js, Map.of(), CatalogoReglas.PREDETERMINADO, 2);
        assertThat(r.bloqueantes()).isEmpty();
        assertThat(r.advertencias()).hasSize(2);
    }

    private static JornadaLaboral jornada(UUID empleado, LocalDate dia, int hora, int horas) {
        LocalDateTime in = dia.atTime(hora, 0);
        return new JornadaLaboral(UUID.randomUUID(), null, empleado, dia, TipoJornada.GUARDIA_ACTIVA, in,
                in.plusHours(horas), null);
    }

    private static ReglaValidacion regla(String codigo, Severidad s, Map<String, Double> p, boolean activa) {
        ReglaValidacion r = new ReglaValidacion();
        r.setCodigo(codigo);
        r.setAmbito(AmbitoRegla.DIAGRAMA);
        r.setSeveridad(s);
        r.setParametros(new java.util.LinkedHashMap<>(p));
        r.setVigenteDesde(LocalDate.of(2026, 1, 1));
        r.setActiva(activa);
        return r;
    }
}
