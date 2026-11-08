package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioGuardiaMedica;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioGuardiaMedica}: 36 hs/semana, 3 guardias de
 * 12 hs. GUARDIA_ACTIVA en las {@code fechasGuardias} programadas dentro
 * del rango; FRANCO el resto (la colocación fina es de la jefatura).
 */
public class GeneradorGuardiaMedica implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioGuardiaMedica h = (HorarioGuardiaMedica) horario;
        return SoporteGeneracion.porFechasProgramadas(h.getFechasGuardias(),
                h.getDuracionGuardiaHoras(), TipoJornada.GUARDIA_ACTIVA, desde, hasta);
    }
}
