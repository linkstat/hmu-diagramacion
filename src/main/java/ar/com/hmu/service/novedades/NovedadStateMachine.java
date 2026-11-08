package ar.com.hmu.service.novedades;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.InstanciaNovedad;

import java.util.ArrayList;
import java.util.List;

/**
 * State machine de las novedades (RF17). Centraliza las transiciones
 * válidas para que {@link NovedadService} no las disperse.
 *
 * <pre>
 *   BORRADOR ──enviar──► 1.ª instancia (PENDIENTE_JEFATURA, o PENDIENTE_DIRECCION si el agente es JS)
 *   PENDIENTE_* ──autorizar──► siguiente instancia ... ──► APROBADA ──aplicar──► APLICADA
 *   PENDIENTE_* ──observar──► OBSERVADA ──reenviar──► 1.ª instancia (ronda nueva)
 *   PENDIENTE_* ──rechazar──► RECHAZADA (terminal)
 *   BORRADOR / OBSERVADA ──anular──► ANULADA (terminal)
 *   APROBADA + Secretaría de Salud pendiente ──registrar resultado──► APLICADA / RECHAZADA
 * </pre>
 */
final class NovedadStateMachine {

    private NovedadStateMachine() {
        // Sólo métodos estáticos.
    }

    /** Instancias del circuito, en orden (CU20 paso 5, CU20 5a, CU21 6a). */
    static List<InstanciaNovedad> circuito(boolean agenteEsJefatura, boolean requiereSecretariaSalud) {
        List<InstanciaNovedad> c = new ArrayList<>();
        c.add(agenteEsJefatura ? InstanciaNovedad.DIRECCION : InstanciaNovedad.JEFATURA);
        c.add(InstanciaNovedad.OFICINA_PERSONAL);
        c.add(InstanciaNovedad.DIRECCION);
        if (requiereSecretariaSalud) {
            c.add(InstanciaNovedad.SECRETARIA_SALUD);
        }
        return c;
    }

    static void validarEditar(EstadoNovedad actual) throws ServiceException {
        if (actual != EstadoNovedad.BORRADOR && actual != EstadoNovedad.OBSERVADA) {
            throw new ServiceException("Sólo se puede modificar una novedad en borrador u observada. Estado actual: "
                    + actual.getEtiqueta() + ".");
        }
    }

    static void validarEnviar(EstadoNovedad actual) throws ServiceException {
        if (actual != EstadoNovedad.BORRADOR && actual != EstadoNovedad.OBSERVADA) {
            throw new ServiceException("Sólo se puede enviar una novedad en borrador u observada. Estado actual: "
                    + actual.getEtiqueta() + ".");
        }
    }

    static void validarResolver(EstadoNovedad actual, InstanciaNovedad instancia) throws ServiceException {
        if (EstadoNovedad.pendienteDe(instancia) != actual) {
            throw new ServiceException("La novedad no está pendiente de " + instancia.getEtiqueta()
                    + ". Estado actual: " + actual.getEtiqueta() + ".");
        }
    }

    static void validarAnular(EstadoNovedad actual) throws ServiceException {
        if (actual != EstadoNovedad.BORRADOR && actual != EstadoNovedad.OBSERVADA) {
            throw new ServiceException("Sólo se puede anular una novedad en borrador u observada.");
        }
    }

    /** Estado tras autorizar: el de la próxima instancia pendiente, o APROBADA. */
    static EstadoNovedad trasAutorizar(InstanciaNovedad siguiente) {
        return siguiente == null ? EstadoNovedad.APROBADA : EstadoNovedad.pendienteDe(siguiente);
    }
}
