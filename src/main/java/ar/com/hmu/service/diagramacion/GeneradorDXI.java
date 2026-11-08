package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioDXI;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioDXI} (Diagnóstico por Imágenes): 24 hs/semana
 * por legislación especial. Misma mecánica que el semanal: distribución
 * día → horas con hora de inicio propia de cada día.
 */
public class GeneradorDXI implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioDXI h = (HorarioDXI) horario;
        return SoporteGeneracion.porDistribucion(h.getDistribucionHoraria(),
                h.getHoraInicioPorDia(), desde, hasta, contexto);
    }
}
