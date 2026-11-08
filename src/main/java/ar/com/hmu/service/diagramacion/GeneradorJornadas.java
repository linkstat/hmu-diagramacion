package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.JornadaLaboral;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estrategia de generación de jornadas: expande el patrón de un
 * {@link HorarioBase} a las jornadas concretas de un rango de fechas
 * (típicamente el mes del diagrama de servicio).
 *
 * <p>Hay una implementación por modalidad de horario, seleccionada por
 * {@link GeneradorJornadasFactory}, más un decorator para
 * {@code HorarioConFranquicia}. La lógica vive fuera del modelo persistido
 * (Strategy) porque las modalidades divergen en la <i>forma de sus datos</i>
 * (patrón semanal vs. fechas concretas), no en un comportamiento que
 * convenga heredar; y porque la expansión necesita colaboradores externos
 * (calendario de feriados vía {@link ContextoDiagramacion}).</p>
 *
 * <p><b>Invariante:</b> el resultado tiene exactamente una jornada por cada
 * día del rango {@code [desde, hasta]}: los días sin trabajo se materializan
 * como {@code FRANCO}. Así la grilla del diagrama queda completa y las
 * validaciones y el parte diario no tienen que inferir huecos.</p>
 *
 * <p>Las jornadas devueltas vienen <b>sin</b> {@code id}, {@code diagramaId}
 * ni {@code empleadoId}: el generador es puro (patrón → días). El caller las
 * estampa con {@link #estampar(List, UUID, UUID)} antes de persistir.</p>
 */
public interface GeneradorJornadas {

    /**
     * Genera las jornadas del rango aplicando el patrón del horario.
     *
     * @param horario  el horario cuyo patrón se expande; debe ser de la
     *                 modalidad que la implementación espera (lo garantiza
     *                 {@link GeneradorJornadasFactory}).
     * @param desde    primer día del rango (inclusive).
     * @param hasta    último día del rango (inclusive).
     * @param contexto calendario de feriados y demás colaboradores.
     * @return una jornada por día del rango, en orden cronológico.
     */
    List<JornadaLaboral> generar(HorarioBase horario, LocalDate desde, LocalDate hasta,
                                 ContextoDiagramacion contexto);

    /**
     * Estampa las jornadas generadas con su diagrama y empleado, asignando
     * ids nuevos donde falten. Paso previo a persistir vía
     * {@code DiagramaRepository}.
     */
    static void estampar(List<JornadaLaboral> jornadas, UUID diagramaId, UUID empleadoId) {
        for (JornadaLaboral j : jornadas) {
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            j.setDiagramaId(diagramaId);
            j.setEmpleadoId(empleadoId);
        }
    }
}
