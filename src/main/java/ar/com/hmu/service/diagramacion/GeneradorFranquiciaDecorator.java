package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioConFranquicia;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;

/**
 * Decorator para {@link HorarioConFranquicia}: genera las jornadas con la
 * estrategia del horario decorado y les descuenta las horas de franquicia.
 *
 * <p><b>Semántica asumida (a confirmar con la Oficina de Personal):</b>
 * {@code horasFranquicia} se interpreta como horas <i>por día trabajado</i>
 * (el caso típico del HMU: franquicia de lactancia = 1 hora menos por día),
 * descontadas adelantando el egreso. Sólo afecta jornadas con horario; los
 * francos y licencias pasan intactos. Cada jornada afectada queda anotada
 * en {@code observaciones} para que sea visible en la grilla.</p>
 */
public class GeneradorFranquiciaDecorator implements GeneradorJornadas {

    private final GeneradorJornadas delegado;

    public GeneradorFranquiciaDecorator(GeneradorJornadas delegado) {
        this.delegado = delegado;
    }

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioConFranquicia franquicia = (HorarioConFranquicia) horario;
        List<JornadaLaboral> jornadas = delegado.generar(franquicia.getHorarioDecorado(),
                desde, hasta, contexto);

        int horas = franquicia.getHorasFranquicia();
        if (horas <= 0) {
            return jornadas;
        }
        String nota = "Franquicia: -" + horas + " h";
        for (JornadaLaboral j : jornadas) {
            if (j.tieneHorario()) {
                j.setFechaEgreso(j.getFechaEgreso().minusHours(horas));
                j.setObservaciones(j.getObservaciones() == null
                        ? nota
                        : j.getObservaciones() + " | " + nota);
            }
        }
        return jornadas;
    }
}
