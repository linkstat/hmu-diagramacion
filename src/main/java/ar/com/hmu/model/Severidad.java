package ar.com.hmu.model;

/**
 * Severidad de una regla de validación (E4, Tabla 49): una violación
 * BLOQUEANTE impide presentar el diagrama o la novedad; una ADVERTENCIA se
 * informa, pero no lo impide.
 */
public enum Severidad {
    BLOQUEANTE,
    ADVERTENCIA
}
