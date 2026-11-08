package ar.com.hmu.model;

/** Dónde se evalúa una regla de validación (E4, Tabla 49). ENUM PG {@code ambito_regla}. */
public enum AmbitoRegla {
    DIAGRAMA("Diagrama"),
    NOVEDAD("Novedad"),
    AMBOS("Diagrama y novedad"),
    ASISTENCIA("Asistencia");

    private final String etiqueta;

    AmbitoRegla(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
