package ar.com.hmu.model;

/**
 * Estado de trámite genérico que comparten varios flujos del dominio:
 * memorándums, novedades, francos compensatorios, diagramas de servicio.
 *
 * Los valores se persisten en la tabla {@code EstadoTramite} (FK por UUID
 * desde las entidades que lo usan). El seed inserta los nombres con espacios
 * y mayúsculas (p.ej. "PENDIENTE DE AUTORIZACION"); este enum traduce de y
 * hacia esa representación con {@link #fromDbName(String)} y
 * {@link #toDbName()}.
 *
 * No todos los valores aplican a todos los flujos. Para memorándums en pase
 * 1 se usan: BORRADOR, ENVIADO, PENDIENTE_DE_AUTORIZACION, AUTORIZADO,
 * RECHAZADO, OBSERVADO, LEIDO. Los demás (PENDIENTE_DE_FIRMA, COMPLETADO)
 * quedan reservados para flujos futuros.
 */
public enum EstadoTramite {
    BORRADOR("BORRADOR"),
    ENVIADO("ENVIADO"),
    PENDIENTE_DE_FIRMA("PENDIENTE DE FIRMA"),
    PENDIENTE_DE_AUTORIZACION("PENDIENTE DE AUTORIZACION"),
    AUTORIZADO("AUTORIZADO"),
    RECHAZADO("RECHAZADO"),
    OBSERVADO("OBSERVADO"),
    LEIDO("LEIDO"),
    COMPLETADO("COMPLETADO");

    private final String dbName;

    EstadoTramite(String dbName) {
        this.dbName = dbName;
    }

    /** Nombre tal como está persistido en la tabla EstadoTramite. */
    public String toDbName() {
        return dbName;
    }

    /**
     * Resuelve el enum a partir del nombre persistido.
     *
     * @throws IllegalArgumentException si el nombre no corresponde a ningún
     *                                  valor del enum.
     */
    public static EstadoTramite fromDbName(String dbName) {
        for (EstadoTramite e : values()) {
            if (e.dbName.equals(dbName)) {
                return e;
            }
        }
        throw new IllegalArgumentException("EstadoTramite desconocido: " + dbName);
    }
}
