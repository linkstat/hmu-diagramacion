package ar.com.hmu.model;

/**
 * Estado de una solicitud de autorización individual sobre un memorándum,
 * persistido como ENUM PostgreSQL {@code estado_memo_autorizacion}.
 *
 * Cada memo puede tener una o más filas en {@code Memorandum_Autorizacion}.
 * Cuando un autorizador OBSERVA el memo, el remitente corrige y reenvía
 * generando una NUEVA fila en estado PENDIENTE; la fila vieja queda
 * OBSERVADO como audit trail de la iteración de revisión.
 */
public enum EstadoMemorandumAutorizacion {
    PENDIENTE,
    AUTORIZADO,
    RECHAZADO,
    OBSERVADO
}
