package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aviso en la aplicación (E4, DER Fig. 20b; CU27). Se registra siempre; el
 * correo es un canal adicional y {@code enviadaEmail} indica si ya salió
 * (si falla, el aviso sigue disponible y el envío se reintenta).
 */
public class Notificacion {

    private UUID id;
    private UUID usuarioId;
    private String tipo;
    private String asunto;
    private String cuerpo;
    private String entidad;
    private UUID entidadId;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLectura;
    private boolean enviadaEmail;

    public Notificacion() {
    }

    public Notificacion(UUID usuarioId, String tipo, String asunto, String cuerpo, String entidad, UUID entidadId) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.entidad = entidad;
        this.entidadId = entidadId;
    }

    public boolean estaLeida() {
        return fechaLectura != null;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }
    public UUID getEntidadId() { return entidadId; }
    public void setEntidadId(UUID entidadId) { this.entidadId = entidadId; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaLectura() { return fechaLectura; }
    public void setFechaLectura(LocalDateTime fechaLectura) { this.fechaLectura = fechaLectura; }
    public boolean isEnviadaEmail() { return enviadaEmail; }
    public void setEnviadaEmail(boolean enviadaEmail) { this.enviadaEmail = enviadaEmail; }
}
