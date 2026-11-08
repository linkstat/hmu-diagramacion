package ar.com.hmu.service.novedades;

import java.util.List;

/**
 * Resultado del motor de validación de novedades (CU21 paso 7).
 *
 * @param violaciones             violaciones con su severidad.
 * @param requiereSecretariaSalud se superó el tope mensual de faltas
 *                                justificadas (CU21 6a): la novedad suma la
 *                                instancia de la Secretaría de Salud.
 */
public record ResultadoValidacion(List<ViolacionNovedad> violaciones, boolean requiereSecretariaSalud) {

    public boolean hayBloqueantes() {
        return violaciones.stream().anyMatch(ViolacionNovedad::esBloqueante);
    }

    public List<ViolacionNovedad> bloqueantes() {
        return violaciones.stream().filter(ViolacionNovedad::esBloqueante).toList();
    }
}
