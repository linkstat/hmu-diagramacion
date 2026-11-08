package ar.com.hmu.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feriado del calendario institucional. Modelo POJO: el workflow de carga
 * anual y de propuestas con autorización de la Dirección vive en
 * {@code FeriadoService}.
 */
public class Feriado {

    private UUID id;
    private LocalDate fecha;
    private String descripcion;
    private EstadoFeriado estado;
    private UUID creadoPorId;
    private UUID resueltoPorId;           // Dirección que autorizó/rechazó
    private LocalDateTime fechaResolucion;
    private LocalDateTime createdAt;

    public Feriado() {
    }

    public Feriado(UUID id, LocalDate fecha, String descripcion,
                   EstadoFeriado estado, UUID creadoPorId) {
        this.id = id;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.estado = estado;
        this.creadoPorId = creadoPorId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoFeriado getEstado() {
        return estado;
    }

    public void setEstado(EstadoFeriado estado) {
        this.estado = estado;
    }

    public UUID getCreadoPorId() {
        return creadoPorId;
    }

    public void setCreadoPorId(UUID creadoPorId) {
        this.creadoPorId = creadoPorId;
    }

    public UUID getResueltoPorId() {
        return resueltoPorId;
    }

    public void setResueltoPorId(UUID resueltoPorId) {
        this.resueltoPorId = resueltoPorId;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
