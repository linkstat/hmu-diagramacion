package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioSemanal;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioSemanal}: 35 hs/semana distribuidas
 * irregularmente. TURNO_NORMAL según la distribución día → horas del
 * template, con la hora de inicio propia de cada día; FRANCO el resto.
 */
public class GeneradorSemanal implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioSemanal h = (HorarioSemanal) horario;
        return SoporteGeneracion.porDistribucion(h.getDistribucionSemanal(),
                h.getHoraInicioPorDia(), desde, hasta, contexto);
    }
}
