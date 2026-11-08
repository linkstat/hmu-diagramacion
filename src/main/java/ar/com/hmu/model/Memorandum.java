package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Memorándum digital: comunicación formal entre actores del HMU.
 *
 * <p>Modelo POJO. Todas las decisiones de negocio (estados válidos,
 * transiciones, reglas de destinatarios, autorización) viven en
 * {@code MemorandumService}; esta clase sólo expone datos.</p>
 *
 * <p>El campo {@code estadoTramiteId} apunta por UUID a la fila de
 * {@code EstadoTramite}. Para uso en código se traduce con
 * {@link EstadoTramite#fromDbName(String)} a través de
 * {@code EstadoTramiteRepository}.</p>
 *
 * <p>{@code fechaRecepcion} a nivel cabecera se mantiene NULL en pase 1:
 * la recepción real vive en {@link MemorandumDestinatario#getFechaRecepcion()}
 * (reinterpretada como "fecha de lectura" por destinatario). El campo de
 * cabecera queda reservado para usos futuros.</p>
 */
public class Memorandum {

    private UUID id;
    private String asunto;
    private String contenido;
    private LocalDateTime fechaEnvio;
    private LocalDateTime fechaRecepcion;
    private UUID estadoTramiteId;
    private UUID remitenteId;

    private List<MemorandumDestinatario> destinatarios = new ArrayList<>();
    private List<MemorandumAutorizacion> autorizaciones = new ArrayList<>();

    public Memorandum() {
    }

    public Memorandum(UUID id, String asunto, String contenido, UUID remitenteId, UUID estadoTramiteId) {
        this.id = id;
        this.asunto = asunto;
        this.contenido = contenido;
        this.remitenteId = remitenteId;
        this.estadoTramiteId = estadoTramiteId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public void setFechaEnvio(LocalDateTime fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDateTime fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public UUID getEstadoTramiteId() {
        return estadoTramiteId;
    }

    public void setEstadoTramiteId(UUID estadoTramiteId) {
        this.estadoTramiteId = estadoTramiteId;
    }

    public UUID getRemitenteId() {
        return remitenteId;
    }

    public void setRemitenteId(UUID remitenteId) {
        this.remitenteId = remitenteId;
    }

    public List<MemorandumDestinatario> getDestinatarios() {
        return destinatarios;
    }

    public void setDestinatarios(List<MemorandumDestinatario> destinatarios) {
        this.destinatarios = destinatarios != null ? destinatarios : new ArrayList<>();
    }

    public List<MemorandumAutorizacion> getAutorizaciones() {
        return autorizaciones;
    }

    public void setAutorizaciones(List<MemorandumAutorizacion> autorizaciones) {
        this.autorizaciones = autorizaciones != null ? autorizaciones : new ArrayList<>();
    }

    /**
     * Conveniencia: agrega un destinatario al memo. No valida duplicados ni
     * pertenencia institucional — esas reglas las aplica
     * {@code MemorandumService}.
     */
    public void agregarDestinatario(MemorandumDestinatario destinatario) {
        if (destinatario == null) {
            throw new IllegalArgumentException("Destinatario no puede ser null.");
        }
        destinatarios.add(destinatario);
    }

    /**
     * Conveniencia: agrega una solicitud de autorización al memo. No valida
     * estado ni unicidad por rol — esas reglas las aplica
     * {@code MemorandumService}.
     */
    public void agregarAutorizacion(MemorandumAutorizacion autorizacion) {
        if (autorizacion == null) {
            throw new IllegalArgumentException("Autorización no puede ser null.");
        }
        autorizaciones.add(autorizacion);
    }
}
