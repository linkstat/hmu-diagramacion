package ar.com.hmu.model;

/**
 * Estado de un {@link DiagramaDeServicio}, persistido como ENUM PostgreSQL
 * {@code estado_diagrama}.
 *
 * <p>Los valores coinciden exactamente con los del ENUM en la base (mismo
 * nombre en MAYÚSCULAS), por lo que la traducción de/hacia la BD se hace con
 * {@link #name()} y {@link #valueOf(String)} sin necesidad de un mapeo
 * explícito, igual que {@link EstadoMemorandumAutorizacion}.</p>
 *
 * <p>Transiciones válidas (la state machine concreta vive en el service del
 * módulo, no acá):</p>
 * <pre>
 *   BORRADOR ──────────────► PENDIENTE_APROBACION
 *   PENDIENTE_APROBACION ──► APROBADO
 *   PENDIENTE_APROBACION ──► OBSERVADO
 *   OBSERVADO ─────────────► PENDIENTE_APROBACION   (corrección y reenvío)
 * </pre>
 *
 * <p>Un diagrama {@code APROBADO} es inmutable: los cambios posteriores se
 * gestionan como novedades (CH/CG) del empleado, no editando el diagrama.</p>
 */
public enum EstadoDiagrama {
    BORRADOR,
    PENDIENTE_APROBACION,
    APROBADO,
    OBSERVADO
}
