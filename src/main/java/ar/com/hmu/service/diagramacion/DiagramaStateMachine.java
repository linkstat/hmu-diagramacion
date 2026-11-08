package ar.com.hmu.service.diagramacion;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoDiagrama;

/**
 * State machine del módulo de diagramación de servicios. Centraliza las
 * transiciones válidas para que {@link DiagramaService} no tenga que
 * conocerlas dispersas en varios métodos. Mismo criterio que
 * {@code MemorandumStateMachine}: concreta por dominio, sin abstracción
 * genérica todavía (YAGNI).
 *
 * <pre>
 *   BORRADOR ── enviar ──────────► PENDIENTE_APROBACION
 *
 *   PENDIENTE_APROBACION ─┬─ aprobar ──► APROBADO  (terminal e inmutable)
 *                         └─ observar ─► OBSERVADO
 *
 *   OBSERVADO ── corregir y reenviar ──► PENDIENTE_APROBACION
 * </pre>
 *
 * <p>APROBADO es terminal: el diagrama no se puede modificar ni volver a
 * transicionar (decisión de diseño). Los cambios reales posteriores se
 * gestionan como novedades (CH/CG) de cada agente, no editando el
 * diagrama. La edición de jornadas sólo es válida en BORRADOR y OBSERVADO.</p>
 */
final class DiagramaStateMachine {

    private DiagramaStateMachine() {
        // Sólo métodos estáticos.
    }

    /** La edición de jornadas (y del rango) sólo aplica antes de aprobar. */
    static void validarEditar(EstadoDiagrama estadoActual) throws ServiceException {
        if (estadoActual != EstadoDiagrama.BORRADOR && estadoActual != EstadoDiagrama.OBSERVADO) {
            throw new ServiceException("Sólo se puede editar un diagrama en estado BORRADOR u OBSERVADO. "
                    + "Estado actual: " + estadoActual);
        }
    }

    static void validarEnviarParaAprobacion(EstadoDiagrama estadoActual) throws ServiceException {
        if (estadoActual != EstadoDiagrama.BORRADOR && estadoActual != EstadoDiagrama.OBSERVADO) {
            throw new ServiceException("Sólo se puede enviar a aprobación un diagrama en estado BORRADOR "
                    + "u OBSERVADO. Estado actual: " + estadoActual);
        }
    }

    static void validarAprobar(EstadoDiagrama estadoActual) throws ServiceException {
        if (estadoActual != EstadoDiagrama.PENDIENTE_APROBACION) {
            throw new ServiceException("Sólo se puede aprobar un diagrama PENDIENTE_APROBACION. "
                    + "Estado actual: " + estadoActual);
        }
    }

    static void validarObservar(EstadoDiagrama estadoActual) throws ServiceException {
        if (estadoActual != EstadoDiagrama.PENDIENTE_APROBACION) {
            throw new ServiceException("Sólo se puede observar un diagrama PENDIENTE_APROBACION. "
                    + "Estado actual: " + estadoActual);
        }
    }

    static void validarEliminar(EstadoDiagrama estadoActual) throws ServiceException {
        if (estadoActual != EstadoDiagrama.BORRADOR) {
            throw new ServiceException("Sólo se pueden eliminar diagramas en estado BORRADOR. "
                    + "Estado actual: " + estadoActual);
        }
    }
}
