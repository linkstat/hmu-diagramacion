package ar.com.hmu.model;

/**
 * Estado del trámite de una novedad (E4, Tabla 52). Mapea al ENUM PG
 * {@code estado_novedad}. La etiqueta es la que se muestra en la bandeja
 * de novedades (P03).
 */
public enum EstadoNovedad {

    BORRADOR("Borrador"),
    PENDIENTE_JEFATURA("Pendiente JS"),
    PENDIENTE_OFICINA_PERSONAL("Pendiente OP"),
    PENDIENTE_DIRECCION("Pendiente DH"),
    OBSERVADA("Observada"),
    APROBADA("Aprobada"),
    APLICADA("Aplicada"),
    RECHAZADA("Rechazada"),
    ANULADA("Anulada");

    private final String etiqueta;

    EstadoNovedad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** "pendiente de Personal (OP)", para los avisos. */
    public String enFrase() {
        return switch (this) {
            case PENDIENTE_JEFATURA -> "pendiente de la Jefatura (JS)";
            case PENDIENTE_OFICINA_PERSONAL -> "pendiente de Personal (OP)";
            case PENDIENTE_DIRECCION -> "pendiente de la Dirección (DH)";
            default -> etiqueta.toLowerCase();
        };
    }

    /** La novedad espera la resolución de alguna instancia. */
    public boolean esPendiente() {
        return this == PENDIENTE_JEFATURA || this == PENDIENTE_OFICINA_PERSONAL || this == PENDIENTE_DIRECCION;
    }

    /** Estado que corresponde a la instancia que está por resolver. */
    public static EstadoNovedad pendienteDe(InstanciaNovedad instancia) {
        return switch (instancia) {
            case JEFATURA -> PENDIENTE_JEFATURA;
            case OFICINA_PERSONAL -> PENDIENTE_OFICINA_PERSONAL;
            case DIRECCION -> PENDIENTE_DIRECCION;
            // La Secretaría de Salud resuelve después de la DH: la novedad ya
            // está APROBADA y su aplicación espera ese registro (CU24 5a).
            case SECRETARIA_SALUD -> APROBADA;
        };
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
