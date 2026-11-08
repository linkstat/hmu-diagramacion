package ar.com.hmu.model;

/**
 * Estado de un {@link Feriado}, persistido como ENUM PostgreSQL
 * {@code estado_feriado}. Mapeo por {@link #name()}/{@link #valueOf(String)}.
 *
 * <pre>
 *   Carga anual (OP, en bloque) ────────────► VIGENTE
 *   Propuesta de alta (OP) ──► ALTA_PENDIENTE ─(Dirección autoriza)─► VIGENTE
 *                                            └─(Dirección rechaza)──► RECHAZADO
 *   Propuesta de baja (OP) ──► BAJA_PENDIENTE ─(Dirección autoriza)─► ANULADO
 *                                            └─(Dirección rechaza)──► VIGENTE
 * </pre>
 *
 * <p>Un feriado en BAJA_PENDIENTE sigue contando como feriado hasta que la
 * Dirección autorice la baja. RECHAZADO y ANULADO quedan como historia.</p>
 */
public enum EstadoFeriado {
    VIGENTE,
    ALTA_PENDIENTE,
    BAJA_PENDIENTE,
    RECHAZADO,
    ANULADO;

    /** ¿Cuenta como feriado a efectos de la diagramación? */
    public boolean cuentaComoFeriado() {
        return this == VIGENTE || this == BAJA_PENDIENTE;
    }
}
