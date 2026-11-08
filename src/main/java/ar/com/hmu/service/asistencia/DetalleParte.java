package ar.com.hmu.service.asistencia;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Detalle del parte diario de un agente (E4, Tabla 57): el resultado del
 * cotejo (CU31/CU32) o una carga manual de la OP (CU34). Los textos de
 * jornada y diferencia se calculan para la pantalla.
 */
public class DetalleParte {

    public enum TipoDiferencia {
        INASISTENCIA, LLEGADA_TARDE, RETIRO_ANTICIPADO, OMISION_INGRESO, OMISION_EGRESO,
        GUARDIA_NO_CUBIERTA, PRESENTE_SIN_DIAGRAMA
    }

    public enum Origen {
        COTEJO("Cotejo"), NOVEDAD_APROBADA("Novedad aprobada"), LICENCIA_EXTERNA("Licencia externa"),
        CARGA_MANUAL("Carga manual");

        private final String etiqueta;

        Origen(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Código con el que se informa una diferencia todavía sin justificar (666: revisar marcación). */
    public static final int SIN_JUSTIFICAR = ar.com.hmu.model.CodigoNovedad.SIN_JUSTIFICAR;

    private UUID id;
    private UUID agenteId;
    private String agenteNombre;
    private UUID servicioId;
    private String servicioNombre;
    private boolean relacionDeDependencia = true;
    private UUID tipoNovedadId;
    private TipoDiferencia tipoDiferencia;
    private UUID novedadId;
    private UUID jornadaLaboralId;
    private UUID jornadaRegistradaId;
    private Origen origen = Origen.COTEJO;
    /** Código numérico de novedad ({@link ar.com.hmu.model.CodigoNovedad}). */
    private Integer codigo;
    private String observaciones;
    private UUID cargadoPorId;
    private LocalDateTime createdAt;
    // Para mostrar
    private String diagramada;
    private String registrada;
    private Integer diferenciaMinutos;

    /** Diferencia que nadie justificó todavía: se resalta y puede pedirse su justificación (CU35). */
    public boolean sinJustificar() {
        return codigo != null && codigo == SIN_JUSTIFICAR;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAgenteId() { return agenteId; }
    public void setAgenteId(UUID agenteId) { this.agenteId = agenteId; }
    public String getAgenteNombre() { return agenteNombre; }
    public void setAgenteNombre(String agenteNombre) { this.agenteNombre = agenteNombre; }
    public UUID getServicioId() { return servicioId; }
    public void setServicioId(UUID servicioId) { this.servicioId = servicioId; }
    public String getServicioNombre() { return servicioNombre; }
    public void setServicioNombre(String servicioNombre) { this.servicioNombre = servicioNombre; }
    public boolean isRelacionDeDependencia() { return relacionDeDependencia; }
    public void setRelacionDeDependencia(boolean relacionDeDependencia) { this.relacionDeDependencia = relacionDeDependencia; }
    public UUID getTipoNovedadId() { return tipoNovedadId; }
    public void setTipoNovedadId(UUID tipoNovedadId) { this.tipoNovedadId = tipoNovedadId; }
    public TipoDiferencia getTipoDiferencia() { return tipoDiferencia; }
    public void setTipoDiferencia(TipoDiferencia tipoDiferencia) { this.tipoDiferencia = tipoDiferencia; }
    public UUID getNovedadId() { return novedadId; }
    public void setNovedadId(UUID novedadId) { this.novedadId = novedadId; }
    public UUID getJornadaLaboralId() { return jornadaLaboralId; }
    public void setJornadaLaboralId(UUID jornadaLaboralId) { this.jornadaLaboralId = jornadaLaboralId; }
    public UUID getJornadaRegistradaId() { return jornadaRegistradaId; }
    public void setJornadaRegistradaId(UUID jornadaRegistradaId) { this.jornadaRegistradaId = jornadaRegistradaId; }
    public Origen getOrigen() { return origen; }
    public void setOrigen(Origen origen) { this.origen = origen; }
    public Integer getCodigo() { return codigo; }
    public void setCodigo(Integer codigo) { this.codigo = codigo; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public UUID getCargadoPorId() { return cargadoPorId; }
    public void setCargadoPorId(UUID cargadoPorId) { this.cargadoPorId = cargadoPorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDiagramada() { return diagramada; }
    public void setDiagramada(String diagramada) { this.diagramada = diagramada; }
    public String getRegistrada() { return registrada; }
    public void setRegistrada(String registrada) { this.registrada = registrada; }
    public Integer getDiferenciaMinutos() { return diferenciaMinutos; }
    public void setDiferenciaMinutos(Integer diferenciaMinutos) { this.diferenciaMinutos = diferenciaMinutos; }
}
