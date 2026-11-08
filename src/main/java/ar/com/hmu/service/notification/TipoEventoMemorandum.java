package ar.com.hmu.service.notification;

/**
 * Eventos del ciclo de vida de un memorándum que disparan una notificación.
 * Cada evento corresponde a una transición de estado o acción que afecta a
 * un actor (remitente, destinatario, autorizador).
 */
public enum TipoEventoMemorandum {
    /** El memo fue enviado: notificar a cada destinatario. */
    MEMO_RECIBIDO,
    /** El memo fue enviado: confirmación al remitente. */
    MEMO_ENVIADO_CONFIRMACION,
    /** Se requiere autorización: notificar al encargado. */
    AUTORIZACION_REQUERIDA,
    /** El memo fue autorizado: notificar al remitente. */
    MEMO_AUTORIZADO,
    /** El memo fue rechazado: notificar al remitente con comentarios. */
    MEMO_RECHAZADO,
    /** El memo requiere correcciones: notificar al remitente con comentarios. */
    MEMO_OBSERVADO
}
