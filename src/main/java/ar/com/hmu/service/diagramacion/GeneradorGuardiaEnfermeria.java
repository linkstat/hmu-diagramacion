package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioGuardiaEnfermeria}: 140 hs/mes, 10 guardias
 * de 12 hs + 2 de 10 hs. GUARDIA_ACTIVA en las {@code fechasGuardias}
 * programadas. El template no indica qué fecha lleva 10 y cuál 12: se
 * genera todo con la duración de 12 hs (la mayoría) y la jefatura ajusta
 * las dos de 10 en la grilla.
 */
public class GeneradorGuardiaEnfermeria implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioGuardiaEnfermeria h = (HorarioGuardiaEnfermeria) horario;
        return SoporteGeneracion.porFechasProgramadas(h.getFechasGuardias(),
                h.getDuracionGuardia12Horas(), TipoJornada.GUARDIA_ACTIVA, desde, hasta);
    }
}
