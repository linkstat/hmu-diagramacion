package ar.com.hmu.model;

/**
 * Instancia del circuito de autorización de una novedad (E4, Tabla 53).
 * Mapea al ENUM PG {@code instancia_novedad}.
 */
public enum InstanciaNovedad {

    JEFATURA("Jefatura (JS)"),
    OFICINA_PERSONAL("Personal (OP)"),
    DIRECCION("Dirección (DH)"),
    SECRETARIA_SALUD("Secretaría de Salud");

    private final String etiqueta;

    InstanciaNovedad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
