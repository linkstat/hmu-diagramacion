package ar.com.hmu.service.novedades;

import ar.com.hmu.exceptions.ServiceException;

/** La novedad tiene errores bloqueantes y no puede enviarse (CU20 4a). */
public class ValidacionNovedadException extends ServiceException {

    private final transient ResultadoValidacion resultado;

    public ValidacionNovedadException(ResultadoValidacion resultado) {
        super("La solicitud tiene errores bloqueantes y no se registró.");
        this.resultado = resultado;
    }

    public ResultadoValidacion getResultado() {
        return resultado;
    }
}
