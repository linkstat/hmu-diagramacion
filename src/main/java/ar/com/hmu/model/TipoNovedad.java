package ar.com.hmu.model;

import java.util.UUID;

/**
 * Catálogo de tipos de novedad (E4, DER Fig. 20c): cambio de guardia,
 * franco compensatorio, pase de salida, etc.
 *
 * @param afectaSaldoHoras +1 acredita horas, -1 las descuenta, 0 no afecta.
 * @param codigoNovedad    código numérico con el que el tipo llega al parte
 *                         ({@link CodigoNovedad}); null si no llega (HE) o
 *                         depende de cada novedad (licencia externa).
 */
public record TipoNovedad(UUID id, String codigo, String nombre, boolean requiereContraparte,
                          int afectaSaldoHoras, boolean requiereDocumentacion, boolean esExterna,
                          boolean activo, Integer codigoNovedad) {

    public static final String CAMBIO_GUARDIA = "CG";
    public static final String CAMBIO_HORARIO = "CH";
    public static final String FRANCO_COMPENSATORIO = "FC";
    public static final String PASE_SALIDA_OFICIAL = "PSO";
    /** Pase de salida con retorno (particular): no se informa en el parte; se devuelve el mismo día. */
    public static final String PASE_SALIDA_PARTICULAR = "PSP";
    /** Pase de salida sin retorno: código 333 en el parte; se devuelve dentro del plazo. */
    public static final String PASE_SIN_RETORNO = "PSSR";
    /** Devolución del tiempo de un pase de salida (Novedad.novedadOrigenID). */
    public static final String DEVOLUCION_PASE = "DEV";
    public static final String INASISTENCIA_JUSTIFICADA = "FJ";
    public static final String HORAS_EXTRA = "HE";
    public static final String OMISION_MARCACION = "OM";
    public static final String LICENCIA_EXTERNA = "LIC";

    public boolean es(String codigoTipo) {
        return codigo.equals(codigoTipo);
    }

    /**
     * Lleva justificación (Novedad.justificacion, además del motivo): las horas
     * extras. Hoy el único tipo de horas extras es el que acredita al banco
     * (HE); no hay un tipo de horas extras pagas.
     */
    public boolean llevaJustificacion() {
        return es(HORAS_EXTRA);
    }

    /** Pase de salida que se devuelve (con o sin retorno); el oficial no se devuelve. */
    public boolean esPaseADevolver() {
        return es(PASE_SALIDA_PARTICULAR) || es(PASE_SIN_RETORNO);
    }

    /** "Cambio de guardia (CG)": el título del detalle en P03. */
    public String nombreConCodigo() {
        return nombre + " (" + codigo + ")";
    }

    @Override
    public String toString() {
        return nombre;
    }
}
