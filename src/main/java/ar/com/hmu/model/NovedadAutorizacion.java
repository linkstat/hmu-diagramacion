package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una instancia del circuito de autorización de una novedad (E4, Tabla 53).
 * Cada decisión queda registrada con su autor, su fecha y su fundamento
 * (RF18); una observación reinicia el circuito con filas nuevas, de modo que
 * las anteriores conservan el historial.
 */
public class NovedadAutorizacion {

    private UUID id;
    private UUID novedadId;
    private InstanciaNovedad instancia;
    private int orden;
    private EstadoNovedadAutorizacion estado;
    private UUID resueltoPorId;
    private String resueltoPorNombre;   // denormalizado para el historial
    private LocalDateTime fechaResolucion;
    private String comentarios;

    public NovedadAutorizacion() {
    }

    public NovedadAutorizacion(UUID id, UUID novedadId, InstanciaNovedad instancia, int orden,
                               EstadoNovedadAutorizacion estado) {
        this.id = id;
        this.novedadId = novedadId;
        this.instancia = instancia;
        this.orden = orden;
        this.estado = estado;
    }

    public boolean estaPendiente() {
        return estado == EstadoNovedadAutorizacion.PENDIENTE;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNovedadId() { return novedadId; }
    public void setNovedadId(UUID novedadId) { this.novedadId = novedadId; }
    public InstanciaNovedad getInstancia() { return instancia; }
    public void setInstancia(InstanciaNovedad instancia) { this.instancia = instancia; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public EstadoNovedadAutorizacion getEstado() { return estado; }
    public void setEstado(EstadoNovedadAutorizacion estado) { this.estado = estado; }
    public UUID getResueltoPorId() { return resueltoPorId; }
    public void setResueltoPorId(UUID resueltoPorId) { this.resueltoPorId = resueltoPorId; }
    public String getResueltoPorNombre() { return resueltoPorNombre; }
    public void setResueltoPorNombre(String resueltoPorNombre) { this.resueltoPorNombre = resueltoPorNombre; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }
    public String getComentarios() { return comentarios; }
    public void setComentarios(String comentarios) { this.comentarios = comentarios; }
}
