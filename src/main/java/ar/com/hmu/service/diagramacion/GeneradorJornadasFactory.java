package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.Horario;
import ar.com.hmu.model.HorarioAbierto;
import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioConFranquicia;
import ar.com.hmu.model.HorarioDXI;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioGuardiaMedica;
import ar.com.hmu.model.HorarioJefeServicioGuardiaPasiva;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.HorarioSemanal;

/**
 * Selecciona la estrategia de generación según la modalidad concreta del
 * horario. Para {@link HorarioConFranquicia} devuelve el decorator armado
 * sobre la estrategia del horario decorado.
 *
 * <p>Los generadores son stateless: se comparten instancias únicas.</p>
 */
public final class GeneradorJornadasFactory {

    private static final GeneradorEstandar ESTANDAR = new GeneradorEstandar();
    private static final GeneradorSemanal SEMANAL = new GeneradorSemanal();
    private static final GeneradorNocturno NOCTURNO = new GeneradorNocturno();
    private static final GeneradorFeriante FERIANTE = new GeneradorFeriante();
    private static final GeneradorDXI DXI = new GeneradorDXI();
    private static final GeneradorGuardiaMedica GUARDIA_MEDICA = new GeneradorGuardiaMedica();
    private static final GeneradorGuardiaEnfermeria GUARDIA_ENFERMERIA = new GeneradorGuardiaEnfermeria();
    private static final GeneradorJefeServicioGuardiaPasiva JEFE_GUARDIA_PASIVA =
            new GeneradorJefeServicioGuardiaPasiva();
    private static final GeneradorAbierto ABIERTO = new GeneradorAbierto();

    private GeneradorJornadasFactory() {
        // Sólo métodos estáticos.
    }

    /**
     * Devuelve la estrategia para el horario dado.
     *
     * @throws IllegalArgumentException si la modalidad no tiene generador
     *                                  (jerarquía extendida sin actualizar
     *                                  esta factory).
     */
    public static GeneradorJornadas paraHorario(HorarioBase horario) {
        return switch (horario) {
            case HorarioConFranquicia f ->
                    new GeneradorFranquiciaDecorator(paraHorario(f.getHorarioDecorado()));
            case HorarioEstandar h -> ESTANDAR;
            case HorarioSemanal h -> SEMANAL;
            case HorarioNocturno h -> NOCTURNO;
            case HorarioFeriante h -> FERIANTE;
            case HorarioDXI h -> DXI;
            case HorarioGuardiaMedica h -> GUARDIA_MEDICA;
            case HorarioGuardiaEnfermeria h -> GUARDIA_ENFERMERIA;
            case HorarioJefeServicioGuardiaPasiva h -> JEFE_GUARDIA_PASIVA;
            case HorarioAbierto h -> ABIERTO;
            case Horario h -> throw new IllegalArgumentException(
                    "Modalidad de Horario sin generador: " + h.getClass().getName());
            default -> throw new IllegalArgumentException(
                    "Rama de HorarioBase desconocida: " + horario.getClass().getName());
        };
    }
}
