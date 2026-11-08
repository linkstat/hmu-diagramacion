package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioEstandar}: 35 hs/semana, 7 hs/día en días
 * hábiles. TURNO_NORMAL en los días laborables del template; FRANCO el
 * resto (y los feriados). Hora de inicio:
 * {@link SoporteGeneracion#HORA_INICIO_DEFECTO} (el template no la define).
 */
public class GeneradorEstandar implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioEstandar h = (HorarioEstandar) horario;
        return SoporteGeneracion.porDiasSemana(h.getDiasLaborables(), h.getHorasPorDia(),
                SoporteGeneracion.HORA_INICIO_DEFECTO, desde, hasta, contexto);
    }
}
