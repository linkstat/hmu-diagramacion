package ar.com.hmu.service.novedades;

import java.sql.SQLException;

/**
 * Concurrencia optimista (RNF03): la escritura no encontró la versión
 * esperada porque otro usuario modificó la novedad antes.
 */
class ConflictoVersionException extends SQLException {

    static final String MENSAJE = "Otro usuario modificó la novedad mientras la tenía abierta. "
            + "Se recargarán los datos: revise y vuelva a intentar.";

    ConflictoVersionException() {
        super(MENSAJE);
    }
}
