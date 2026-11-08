package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link ar.com.hmu.model.HorarioAbierto}: 35 hs/semana en
 * cualquier horario (modalidad no usada actualmente). Sin patrón derivable:
 * la grilla sale toda FRANCO y se planifica a mano.
 */
public class GeneradorAbierto implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        return SoporteGeneracion.todoFranco(desde, hasta);
    }
}
