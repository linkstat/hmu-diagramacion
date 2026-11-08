package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioJefeServicioGuardiaPasiva;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;

/**
 * Generador para {@link HorarioJefeServicioGuardiaPasiva}: como el estándar,
 * con {@code horasPorDia} ya reducido en 1 hora (dato del template, no regla
 * de este generador). La disponibilidad pasiva continua del jefe no se
 * materializa como filas GUARDIA_PASIVA: es una condición del cargo, no una
 * jornada planificable.
 */
public class GeneradorJefeServicioGuardiaPasiva implements GeneradorJornadas {

    @Override
    public List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                        ContextoDiagramacion contexto) {
        HorarioJefeServicioGuardiaPasiva h = (HorarioJefeServicioGuardiaPasiva) horario;
        return SoporteGeneracion.porDiasSemana(h.getDiasLaborables(), h.getHorasPorDia(),
                SoporteGeneracion.HORA_INICIO_DEFECTO, desde, hasta, contexto);
    }
}
