package ar.com.hmu.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Novedad (E4, Tabla 52): solicitud de un agente (cambio de guardia,
 * franco compensatorio, pase de salida, justificación de inasistencia...)
 * que recorre el circuito de autorización por instancias (RF17) hasta
 * aplicarse sobre el diagrama vigente (RF20).
 *
 * <p>Las instancias ({@link NovedadAutorizacion}) y los respaldos
 * ({@link NovedadAdjunto}) se cargan aparte; los nombres del agente, la
 * contraparte y el servicio se denormalizan desde el repositorio para que la
 * bandeja no requiera otra consulta.</p>
 */
public class Novedad {

    private UUID id;
    private TipoNovedad tipo;
    private UUID agenteId;
    private UUID contraparteAgenteId;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private LocalTime horaDesde;
    private LocalTime horaHasta;
    private BigDecimal cantHoras;
    private String motivo;
    /** Justificación de las horas extras (distinta del motivo); nula en los demás tipos. */
    private String justificacion;
    private EstadoNovedad estado = EstadoNovedad.BORRADOR;
    private OrigenNovedad origen = OrigenNovedad.SOLICITUD;
    private UUID solicitadoPorId;
    private LocalDateTime fechaSolicitud;
    private String referenciaExterna;
    /** Tipo de licencia (código numérico) de una licencia de origen externo. */
    private Integer codigoNovedad;
    /** Pase de salida que devuelve una devolución (DEV). */
    private UUID novedadOrigenId;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Denormalizados (sólo lectura)
    private String agenteNombre;
    private String contraparteNombre;
    private String solicitadoPorNombre;
    private UUID servicioId;
    private String servicioNombre;

    private List<NovedadAutorizacion> autorizaciones = new ArrayList<>();
    private List<NovedadAdjunto> adjuntos = new ArrayList<>();

    public Novedad() {
    }

    /** La instancia que está por resolver (la de menor orden pendiente). */
    public Optional<NovedadAutorizacion> instanciaPendiente() {
        return autorizaciones.stream()
                .filter(NovedadAutorizacion::estaPendiente)
                .min(Comparator.comparingInt(NovedadAutorizacion::getOrden));
    }

    /** El agente que pidió la novedad la cargó a su nombre. */
    public boolean esSolicitadaPorElAgente() {
        return agenteId != null && agenteId.equals(solicitadoPorId);
    }

    /** El usuario participa de la novedad (agente, contraparte o quien la cargó). */
    public boolean involucraA(UUID usuarioId) {
        return usuarioId != null && (usuarioId.equals(agenteId) || usuarioId.equals(contraparteAgenteId)
                || usuarioId.equals(solicitadoPorId));
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public TipoNovedad getTipo() { return tipo; }
    public void setTipo(TipoNovedad tipo) { this.tipo = tipo; }
    public UUID getAgenteId() { return agenteId; }
    public void setAgenteId(UUID agenteId) { this.agenteId = agenteId; }
    public UUID getContraparteAgenteId() { return contraparteAgenteId; }
    public void setContraparteAgenteId(UUID contraparteAgenteId) { this.contraparteAgenteId = contraparteAgenteId; }
    public LocalDate getFechaDesde() { return fechaDesde; }
    public void setFechaDesde(LocalDate fechaDesde) { this.fechaDesde = fechaDesde; }
    public LocalDate getFechaHasta() { return fechaHasta; }
    public void setFechaHasta(LocalDate fechaHasta) { this.fechaHasta = fechaHasta; }
    public LocalTime getHoraDesde() { return horaDesde; }
    public void setHoraDesde(LocalTime horaDesde) { this.horaDesde = horaDesde; }
    public LocalTime getHoraHasta() { return horaHasta; }
    public void setHoraHasta(LocalTime horaHasta) { this.horaHasta = horaHasta; }
    public BigDecimal getCantHoras() { return cantHoras; }
    public void setCantHoras(BigDecimal cantHoras) { this.cantHoras = cantHoras; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getJustificacion() { return justificacion; }
    public void setJustificacion(String justificacion) { this.justificacion = justificacion; }

    /** Fórmula usual de la justificación de las horas extras (precarga del formulario). */
    public static final String JUSTIFICACION_USUAL = "Necesidad del servicio";
    /** Longitud de Novedad.justificacion (VARCHAR). */
    public static final int JUSTIFICACION_MAX = 255;

    /**
     * Sólo los tipos que llevan justificación (horas extras) la conservan,
     * sin espacios sobrantes; en los demás tipos, o en blanco, queda nula.
     */
    public void normalizarJustificacion() {
        if (tipo == null || !tipo.llevaJustificacion() || justificacion == null || justificacion.isBlank()) {
            justificacion = null;
        } else {
            justificacion = justificacion.strip();
        }
    }
    public EstadoNovedad getEstado() { return estado; }
    public void setEstado(EstadoNovedad estado) { this.estado = estado; }
    public OrigenNovedad getOrigen() { return origen; }
    public void setOrigen(OrigenNovedad origen) { this.origen = origen; }
    public UUID getSolicitadoPorId() { return solicitadoPorId; }
    public void setSolicitadoPorId(UUID solicitadoPorId) { this.solicitadoPorId = solicitadoPorId; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) { this.referenciaExterna = referenciaExterna; }
    public Integer getCodigoNovedad() { return codigoNovedad; }
    public void setCodigoNovedad(Integer codigoNovedad) { this.codigoNovedad = codigoNovedad; }
    public UUID getNovedadOrigenId() { return novedadOrigenId; }
    public void setNovedadOrigenId(UUID novedadOrigenId) { this.novedadOrigenId = novedadOrigenId; }

    /** Código con el que la novedad llega al parte: el propio o, por defecto, el de su tipo. */
    public Integer codigoEfectivo() {
        return codigoNovedad != null ? codigoNovedad : tipo != null ? tipo.codigoNovedad() : null;
    }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getAgenteNombre() { return agenteNombre; }
    public void setAgenteNombre(String agenteNombre) { this.agenteNombre = agenteNombre; }
    public String getContraparteNombre() { return contraparteNombre; }
    public void setContraparteNombre(String contraparteNombre) { this.contraparteNombre = contraparteNombre; }
    public String getSolicitadoPorNombre() { return solicitadoPorNombre; }
    public void setSolicitadoPorNombre(String solicitadoPorNombre) { this.solicitadoPorNombre = solicitadoPorNombre; }
    public UUID getServicioId() { return servicioId; }
    public void setServicioId(UUID servicioId) { this.servicioId = servicioId; }
    public String getServicioNombre() { return servicioNombre; }
    public void setServicioNombre(String servicioNombre) { this.servicioNombre = servicioNombre; }
    public List<NovedadAutorizacion> getAutorizaciones() { return autorizaciones; }
    public void setAutorizaciones(List<NovedadAutorizacion> autorizaciones) { this.autorizaciones = autorizaciones; }
    public List<NovedadAdjunto> getAdjuntos() { return adjuntos; }
    public void setAdjuntos(List<NovedadAdjunto> adjuntos) { this.adjuntos = adjuntos; }
}
