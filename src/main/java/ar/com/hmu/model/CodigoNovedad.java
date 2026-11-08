package ar.com.hmu.model;

/**
 * Código numérico de novedad: el que usan las planillas de la Oficina de
 * Personal y Capital Humano (tabla de códigos de novedades), con la
 * denominación del HMU y la de Capital Humano, que a menudo coinciden, y la
 * sigla interna del HMU (P, FJ, FI, T, LIC…). 222 (CH/CG) y 333 (PSSR) son
 * códigos internos del HMU.
 *
 * <p>La tabla tiene, como todas, clave primaria {@code id} UUID; las demás
 * entidades la referencian por {@code codigoNovedadID}. El dominio trabaja con
 * el código numérico, y los repositorios hacen la traducción.</p>
 *
 * @param id         clave primaria.
 * @param codigo     código numérico (único).
 * @param esLicencia se ofrece como tipo de licencia de origen externo (CU29).
 * @param sigla      sigla interna del HMU; null si no tiene.
 */
public record CodigoNovedad(java.util.UUID id, int codigo, String denominacionHmu, String denominacionCapitalHumano,
                            boolean esLicencia, String sigla) {

    // Códigos con los que el cotejo (CU31/CU32) informa cada situación en el parte.
    public static final int FALTA_INJUSTIFICADA = 1;
    public static final int FALTA_JUSTIFICADA = 2;
    public static final int FRANCO_COMPENSATORIO = 6;
    public static final int COMISION = 8;
    public static final int CARPETA_MEDICA = 10;
    public static final int CAPACITACION = 52;
    public static final int OMISION_JUSTIFICADA = 71;
    public static final int PRESENTE = 99;
    public static final int CAMBIO_HORARIO_GUARDIA = 222;
    public static final int PSSR = 333;
    public static final int SIN_JUSTIFICAR = 666;

    /** "11 · Carpeta médica prolongada". */
    @Override
    public String toString() {
        return codigo + " · " + denominacionHmu;
    }
}
