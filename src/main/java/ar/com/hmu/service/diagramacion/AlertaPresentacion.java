package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.Servicio;

/**
 * Un servicio con su estado de presentación del diagrama para el mes
 * consultado (sólo se generan para estados que requieren alerta).
 */
public record AlertaPresentacion(Servicio servicio, EstadoPresentacion estado) {
}
