package ar.com.hmu.model;

/**
 * Vinculación laboral del agente (E4, Tabla 48). Mapea al ENUM PG
 * {@code vinculacion}; separa el parte diario que se informa a Capital
 * Humano (relación de dependencia / monotributistas y becarios).
 */
public enum Vinculacion {

    PLANTA_PERMANENTE("Planta permanente"),
    PLANTA_TRANSITORIA("Planta transitoria"),
    CONTRATADO("Contratado"),
    FUNCIONARIO("Funcionario"),
    MONOTRIBUTISTA("Monotributista"),
    BECARIO("Becario");

    private final String etiqueta;

    Vinculacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Monotributistas y becarios van en un parte separado (RF23). */
    public boolean esRelacionDeDependencia() {
        return this != MONOTRIBUTISTA && this != BECARIO;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
