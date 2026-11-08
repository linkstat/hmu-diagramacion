package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioNocturno}: 140 hs/mes, 14 jornadas de 10 hs
 * (típicamente 21:00-07:00, cruzando medianoche). Las fechas concretas del
 * mes NO son derivables del template: las decide la jefatura. Si
 * {@code diasProgramados} trae fechas dentro del rango se usan (TURNO_NORMAL
 * desde la hora de cada entrada); si no, la grilla sale toda FRANCO y se
 * colocan las jornadas a mano.
 */
public class GeneradorNocturno implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioNocturno h = (HorarioNocturno) horario;
        return SoporteGeneracion.porFechasProgramadas(h.getDiasProgramados(),
                h.getDuracionJornadaHoras(), TipoJornada.TURNO_NORMAL, desde, hasta);
    }
}
