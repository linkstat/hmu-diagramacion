package ar.com.hmu.service;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoTramite;

/**
 * State machine del módulo de memorándums. Centraliza las transiciones
 * válidas para que {@link MemorandumService} no tenga que conocerlas
 * dispersas en varios métodos.
 *
 * <p>Pase 1 (transiciones implementadas):</p>
 * <pre>
 *   BORRADOR ─┬─ enviar (sin auth) ──────► ENVIADO
 *             └─ enviar (con auth) ──────► PENDIENTE_DE_AUTORIZACION
 *
 *   PENDIENTE_DE_AUTORIZACION ─┬─ autorizar ──► ENVIADO
 *                              ├─ rechazar  ──► RECHAZADO  (terminal)
 *                              └─ observar  ──► OBSERVADO
 *
 *   OBSERVADO ── reenviar ──► PENDIENTE_DE_AUTORIZACION
 *
 *   ENVIADO ── todos leyeron ──► LEIDO  (terminal en pase 1)
 * </pre>
 *
 * <p>{@link EstadoTramite#PENDIENTE_DE_FIRMA} y {@link EstadoTramite#COMPLETADO}
 * existen en el enum pero no se usan en pase 1.</p>
 *
 * <p>Esta clase queda dentro del paquete {@code service} y no se generaliza
 * todavía. Cuando llegue una segunda implementación (licencias, novedades),
 * se evaluará extraer una abstracción común.</p>
 */
final class MemorandumStateMachine {

    private MemorandumStateMachine() {
        // Sólo métodos estáticos.
    }

    static void validarEnviarSinAutorizacion(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.BORRADOR) {
            throw new ServiceException("Sólo se puede enviar un memo en estado BORRADOR. Estado actual: " + estadoActual);
        }
    }

    static void validarEnviarConAutorizacion(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.BORRADOR) {
            throw new ServiceException("Sólo se puede enviar un memo en estado BORRADOR. Estado actual: " + estadoActual);
        }
    }

    static void validarAutorizar(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.PENDIENTE_DE_AUTORIZACION) {
            throw new ServiceException("Sólo se puede autorizar un memo PENDIENTE_DE_AUTORIZACION. Estado actual: " + estadoActual);
        }
    }

    static void validarRechazar(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.PENDIENTE_DE_AUTORIZACION) {
            throw new ServiceException("Sólo se puede rechazar un memo PENDIENTE_DE_AUTORIZACION. Estado actual: " + estadoActual);
        }
    }

    static void validarObservar(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.PENDIENTE_DE_AUTORIZACION) {
            throw new ServiceException("Sólo se puede observar un memo PENDIENTE_DE_AUTORIZACION. Estado actual: " + estadoActual);
        }
    }

    static void validarReenviar(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.OBSERVADO) {
            throw new ServiceException("Sólo se puede reenviar un memo OBSERVADO. Estado actual: " + estadoActual);
        }
    }

    static void validarMarcarLeido(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.ENVIADO && estadoActual != EstadoTramite.LEIDO) {
            throw new ServiceException("El memo no está disponible para lectura. Estado actual: " + estadoActual);
        }
    }

    static void validarEliminarBorrador(EstadoTramite estadoActual) throws ServiceException {
        if (estadoActual != EstadoTramite.BORRADOR) {
            throw new ServiceException("Sólo se pueden eliminar memos en estado BORRADOR. Estado actual: " + estadoActual);
        }
    }
}
