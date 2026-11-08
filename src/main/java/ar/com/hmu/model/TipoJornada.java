package ar.com.hmu.model;

/**
 * Tipo de una {@link JornadaLaboral} dentro de un diagrama, persistido como
 * ENUM PostgreSQL {@code tipo_jornada}.
 *
 * <p>Los valores coinciden con los del ENUM en la base (MAYÚSCULAS), por lo
 * que se traducen con {@link #name()} y {@link #valueOf(String)}.</p>
 *
 * <p>Los tipos {@code FRANCO}, {@code FRANCO_COMPENSATORIO} y {@code LICENCIA}
 * no tienen horario: para ellos {@code fechaIngreso}/{@code fechaEgreso} de la
 * jornada quedan en NULL. Ver {@link #requiereHorario()}.</p>
 */
public enum TipoJornada {

    /** Turno de trabajo estándar (día laboral con horario de ingreso/egreso). */
    TURNO_NORMAL,

    /** Guardia con presencia física en el establecimiento. */
    GUARDIA_ACTIVA,

    /** Guardia pasiva / a la orden: disponibilidad en una franja horaria. */
    GUARDIA_PASIVA,

    /** Día de descanso semanal. Sin horario. */
    FRANCO,

    /** Franco generado a partir de horas extras acumuladas. Sin horario. */
    FRANCO_COMPENSATORIO,

    /** Ausencia amparada por licencia (gestionada en VeDi). Sin horario. */
    LICENCIA;

    /**
     * Indica si el tipo de jornada conlleva un horario concreto de ingreso y
     * egreso. Los tipos sin horario (franco, franco compensatorio, licencia)
     * se representan con {@code fechaIngreso}/{@code fechaEgreso} en NULL.
     *
     * @return {@code true} si la jornada tiene horario; {@code false} si es
     *         un día sin marcación de horario.
     */
    public boolean requiereHorario() {
        return this == TURNO_NORMAL
                || this == GUARDIA_ACTIVA
                || this == GUARDIA_PASIVA;
    }
}
