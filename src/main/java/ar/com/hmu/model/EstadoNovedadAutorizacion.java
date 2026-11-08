package ar.com.hmu.model;

/**
 * Resultado de una instancia del circuito de una novedad (E4, Tabla 53).
 * Mapea al ENUM PG {@code estado_novedad_autorizacion}.
 */
public enum EstadoNovedadAutorizacion {
    PENDIENTE,
    AUTORIZADO,
    RECHAZADO,
    OBSERVADO
}
