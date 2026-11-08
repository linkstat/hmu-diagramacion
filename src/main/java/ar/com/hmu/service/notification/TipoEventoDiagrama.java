package ar.com.hmu.service.notification;

/**
 * Eventos del ciclo de vida de un diagrama de servicio que disparan una
 * notificación. Cada evento corresponde a una transición de la state
 * machine del módulo de diagramación.
 */
public enum TipoEventoDiagrama {
    /** El diagrama fue enviado a aprobación: notificar a Oficina de Personal. */
    DIAGRAMA_PENDIENTE_APROBACION,
    /** El diagrama fue aprobado: notificar a la jefatura que lo creó. */
    DIAGRAMA_APROBADO,
    /** El diagrama fue observado: notificar a la jefatura con los comentarios. */
    DIAGRAMA_OBSERVADO
}
