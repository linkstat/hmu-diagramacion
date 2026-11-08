package ar.com.hmu.repository;

/**
 * Único lugar donde se traduce el código numérico de novedad
 * ({@code CodigoNovedad.codigo}: la clave natural, única, con la que trabaja
 * el dominio) a su clave primaria UUID ({@code codigoNovedadID}, la que
 * guardan TipoNovedad, Novedad y ParteDiarioDetalle), y viceversa.
 *
 * <p>Los repositorios arman sus consultas con estas expresiones; ninguna otra
 * clase menciona {@code CodigoNovedad.id}. Ver DIVERGENCIAS.md.</p>
 */
public final class TraduccionCodigoNovedad {

    /** Subconsulta que traduce el código numérico (parámetro {@code ?}) a su {@code codigoNovedadID}. */
    public static final String ID_DEL_CODIGO = "(SELECT id FROM CodigoNovedad WHERE codigo = ?)";

    private TraduccionCodigoNovedad() {
    }

    /** Expresión SQL con el código numérico de la columna {@code codigoNovedadID} indicada (nulo si es nula). */
    public static String codigoDe(String columnaId) {
        return "(SELECT codigo FROM CodigoNovedad WHERE id = " + columnaId + ")";
    }
}
