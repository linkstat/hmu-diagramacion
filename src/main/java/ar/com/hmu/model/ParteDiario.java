package ar.com.hmu.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Parte diario de una fecha (E4, DER Fig. 20b; CU33–CU34): se genera desde
 * el cotejo, la OP lo revisa y completa con cargas manuales y lo cierra.
 * Concurrencia optimista por {@code version}.
 */
public class ParteDiario {

    public enum Estado { ABIERTO, CERRADO }

    private UUID id;
    private LocalDate fecha;
    private Estado estado = Estado.ABIERTO;
    private LocalDateTime generadoEn;
    private UUID generadoPorId;
    private UUID cerradoPorId;
    private String cerradoPorNombre;
    private LocalDateTime fechaCierre;
    private int version;

    public boolean estaCerrado() {
        return estado == Estado.CERRADO;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public LocalDateTime getGeneradoEn() { return generadoEn; }
    public void setGeneradoEn(LocalDateTime generadoEn) { this.generadoEn = generadoEn; }
    public UUID getGeneradoPorId() { return generadoPorId; }
    public void setGeneradoPorId(UUID generadoPorId) { this.generadoPorId = generadoPorId; }
    public UUID getCerradoPorId() { return cerradoPorId; }
    public void setCerradoPorId(UUID cerradoPorId) { this.cerradoPorId = cerradoPorId; }
    public String getCerradoPorNombre() { return cerradoPorNombre; }
    public void setCerradoPorNombre(String cerradoPorNombre) { this.cerradoPorNombre = cerradoPorNombre; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
