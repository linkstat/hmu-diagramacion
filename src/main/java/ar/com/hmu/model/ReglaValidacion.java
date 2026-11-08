package ar.com.hmu.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Una versión de una regla de validación (E4, Tabla 49; CU08). Los
 * parámetros son valores numéricos (topes, mínimos, tolerancias) que se
 * ajustan sin recompilar; la vigencia permite que un diagrama aprobado
 * conserve la versión con la que se validó.
 */
public class ReglaValidacion {

    private UUID id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private AmbitoRegla ambito;
    private String modalidad;
    private Severidad severidad;
    private Map<String, Double> parametros = new LinkedHashMap<>();
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private boolean activa = true;
    /** Norma que respalda la regla (p. ej. "Decreto 1605/2018"); null si no corresponde. */
    private String fundamentoNormativo;

    public boolean vigenteEl(LocalDate fecha) {
        return !vigenteDesde.isAfter(fecha) && (vigenteHasta == null || !vigenteHasta.isBefore(fecha));
    }

    /** {"horasMinimas": 10} con enteros sin decimales. */
    public String parametrosJson() {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Double> e : parametros.entrySet()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append('"').append(e.getKey()).append("\": ").append(formatear(e.getValue()));
        }
        return sb.append('}').toString();
    }

    /** "horasMinimas = 10; toleranciaHoras = 0" para las tablas. */
    public String parametrosTexto() {
        StringBuilder sb = new StringBuilder();
        parametros.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : "; ").append(k).append(" = ").append(formatear(v)));
        return sb.isEmpty() ? "—" : sb.toString();
    }

    public static String formatear(Double v) {
        if (v == null) return "";
        return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(v);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public AmbitoRegla getAmbito() { return ambito; }
    public void setAmbito(AmbitoRegla ambito) { this.ambito = ambito; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public Severidad getSeveridad() { return severidad; }
    public void setSeveridad(Severidad severidad) { this.severidad = severidad; }
    public Map<String, Double> getParametros() { return parametros; }
    public void setParametros(Map<String, Double> parametros) { this.parametros = parametros; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(LocalDate vigenteDesde) { this.vigenteDesde = vigenteDesde; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public void setVigenteHasta(LocalDate vigenteHasta) { this.vigenteHasta = vigenteHasta; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public String getFundamentoNormativo() { return fundamentoNormativo; }
    public void setFundamentoNormativo(String fundamentoNormativo) { this.fundamentoNormativo = fundamentoNormativo; }
}
