package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioFeriante}: 120 hs/mes mínimas, 10 guardias
 * de 12 hs en días no laborables (fines de semana y feriados). Las fechas
 * concretas las decide la jefatura: {@code diasNoLaborables} del template
 * aporta las programadas (GUARDIA_ACTIVA); el resto sale FRANCO para
 * edición manual.
 */
public class GeneradorFeriante implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioFeriante h = (HorarioFeriante) horario;
        return SoporteGeneracion.porFechasProgramadas(h.getDiasNoLaborables(),
                h.getDuracionGuardiaHoras(), TipoJornada.GUARDIA_ACTIVA, desde, hasta);
    }
}
