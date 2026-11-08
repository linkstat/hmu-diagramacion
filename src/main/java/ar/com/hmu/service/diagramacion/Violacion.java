package ar.com.hmu.service.diagramacion;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una violación detectada por {@link DiagramaValidator} sobre las jornadas
 * de un diagrama. Inmutable; apunta al empleado y fecha del problema para
 * que la UI pueda resaltar la celda correspondiente en la grilla.
 *
 * @param empleadoId empleado afectado (no null).
 * @param fecha      día donde se detectó el problema (puede ser null para
 *                   violaciones agregadas, como carga horaria mensual).
 * @param mensaje    descripción legible del problema.
 * @param regla      código de la regla del catálogo (CU08) que la originó;
 *                   null si no corresponde a una regla parametrizable.
 */
public record Violacion(UUID empleadoId, LocalDate fecha, String mensaje, String regla) {

    public Violacion(UUID empleadoId, LocalDate fecha, String mensaje) {
        this(empleadoId, fecha, mensaje, null);
    }

    @Override
    public String toString() {
        return (fecha != null ? fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ": " : "")
                + mensaje;
    }
}
