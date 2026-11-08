package ar.com.hmu.service.novedades;

import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.service.diagramacion.CatalogoReglas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Datos que consulta el motor de validación de novedades. La implementación
 * real lee los repositorios; los tests usan una en memoria.
 */
public interface FuenteDatosNovedad {

    /** Jornadas de los diagramas aprobados (vigentes) del agente en el rango. */
    List<JornadaLaboral> jornadasVigentes(UUID agenteId, LocalDate desde, LocalDate hasta);

    /** Novedades del agente (titular o contraparte) que tocan el rango, en esos estados. */
    List<Novedad> novedadesEnRango(UUID agenteId, LocalDate desde, LocalDate hasta,
                                   Collection<EstadoNovedad> estados);

    /** Saldo vigente del banco de horas del agente (créditos no vencidos). */
    BigDecimal saldoHoras(UUID agenteId);

    /** Una novedad por su id (el pase de origen de una devolución); null si no existe. */
    default Novedad novedad(UUID id) {
        return null;
    }

    /** Devoluciones (DEV) en trámite o vigentes de un pase de salida. */
    default List<Novedad> devolucionesDe(UUID paseId) {
        return List.of();
    }

    /** Servicio del agente (para la compatibilidad del cambio de guardia). */
    UUID servicioDe(UUID agenteId);

    /** Fecha de referencia (franco compensatorio retroactivo, vencimientos). */
    default LocalDate hoy() {
        return ar.com.hmu.util.Reloj.hoy();
    }

    /** Reglas de validación vigentes a la fecha (CU08); predeterminadas si no hay catálogo. */
    default CatalogoReglas reglas(LocalDate fecha) {
        return CatalogoReglas.PREDETERMINADO;
    }
}
