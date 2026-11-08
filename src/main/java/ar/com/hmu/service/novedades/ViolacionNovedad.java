package ar.com.hmu.service.novedades;

import ar.com.hmu.model.Severidad;

/**
 * Una violación detectada al validar una novedad (CU21), con la regla que la
 * originó (código del catálogo {@code ReglaValidacion}) y su severidad.
 */
public record ViolacionNovedad(String regla, Severidad severidad, String mensaje) {

    public boolean esBloqueante() {
        return severidad == Severidad.BLOQUEANTE;
    }

    @Override
    public String toString() {
        return (esBloqueante() ? "Error: " : "Advertencia: ") + mensaje;
    }
}
