package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.ReglaValidacion;
import ar.com.hmu.model.Severidad;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Las reglas de validación vigentes a una fecha (CU08), tal como las lee el
 * motor de validación. Si una regla no está en el catálogo se usan los
 * valores predeterminados que trae el código ({@link #PREDETERMINADO}): así
 * el motor funciona igual sin base de datos (tests) o con la tabla vacía.
 */
public final class CatalogoReglas {

    // Códigos del catálogo (ReglaValidacion.codigo).
    public static final String DESCANSO = "RV-DESCANSO-10H";
    public static final String SUPERPOSICION = "RV-SUPERPOSICION";
    public static final String HORARIO = "RV-HORARIO";
    public static final String DURACION_MIN = "RV-DURACION-MIN";
    public static final String TURNO_MAX = "RV-TURNO-MAX";
    public static final String GUARDIA_MAX = "RV-GUARDIA-MAX";
    public static final String CARGA_NOCTURNO = "RV-CARGA-NOCT";
    public static final String CARGA_FERIANTE = "RV-CARGA-FER";
    public static final String CARGA_ENFERMERIA = "RV-CARGA-ENF";
    public static final String DOTACION_MIN = "RV-DOTACION-MIN";

    /** Sin reglas en BD: severidad y parámetros predeterminados del código. */
    public static final CatalogoReglas PREDETERMINADO = new CatalogoReglas(Map.of());

    private final Map<String, ReglaValidacion> reglas;

    private CatalogoReglas(Map<String, ReglaValidacion> reglas) {
        this.reglas = reglas;
    }

    public static CatalogoReglas de(Collection<ReglaValidacion> vigentes) {
        Map<String, ReglaValidacion> m = new HashMap<>();
        for (ReglaValidacion r : vigentes) {
            m.put(r.getCodigo(), r);
        }
        return new CatalogoReglas(Map.copyOf(m));
    }

    /** La regla se evalúa (inactiva = el motor la omite). */
    public boolean activa(String codigo) {
        ReglaValidacion r = reglas.get(codigo);
        return r == null || r.isActiva();
    }

    public Severidad severidad(String codigo, Severidad porDefecto) {
        ReglaValidacion r = reglas.get(codigo);
        return r != null && r.getSeveridad() != null ? r.getSeveridad() : porDefecto;
    }

    /**
     * El mensaje con el fundamento normativo de la regla: dentro del
     * paréntesis final si lo hay ("…(mínimo 10 h corridas; Decreto 1605/2018).")
     * o en uno nuevo al final ("…140 h (Ordenanza 7974)."). Sin cambios si la
     * regla no tiene fundamento o el mensaje ya lo trae.
     */
    public String conFundamento(String codigo, String mensaje) {
        ReglaValidacion r = codigo != null ? reglas.get(codigo) : null;
        String f = r != null ? r.getFundamentoNormativo() : null;
        if (f == null || f.isBlank() || mensaje.contains(f.trim())) return mensaje;
        boolean punto = mensaje.endsWith(".");
        String cuerpo = punto ? mensaje.substring(0, mensaje.length() - 1) : mensaje;
        cuerpo = cuerpo.endsWith(")")
                ? cuerpo.substring(0, cuerpo.length() - 1) + "; " + f.trim() + ")"
                : cuerpo + " (" + f.trim() + ")";
        return punto ? cuerpo + "." : cuerpo;
    }

    public int entero(String codigo, String clave, int porDefecto) {
        ReglaValidacion r = reglas.get(codigo);
        if (r == null) return porDefecto;
        Double v = r.getParametros().get(clave);
        return v != null ? (int) Math.round(v) : porDefecto;
    }
}
