package ar.com.hmu.service.diagramacion;

/**
 * Estado de presentación del diagrama de un servicio para un mes dado,
 * usado por las alertas de RFS09 (recordatorio de fecha límite).
 *
 * <p>"Presentado" = elevado a la Oficina de Personal (PENDIENTE_APROBACION)
 * o ya APROBADO. Un BORRADOR no está presentado; un OBSERVADO fue devuelto
 * y está pendiente de corrección.</p>
 */
public enum EstadoPresentacion {
    /** El servicio no tiene ningún diagrama que cubra el mes. */
    SIN_DIAGRAMA,
    /** Hay un borrador que todavía no se envió a aprobación. */
    BORRADOR_SIN_ENVIAR,
    /** La OP lo observó y la jefatura aún no lo corrigió/reenvió. */
    OBSERVADO_SIN_CORREGIR,
    /** Elevado a OP o aprobado: no requiere alerta. */
    PRESENTADO
}
