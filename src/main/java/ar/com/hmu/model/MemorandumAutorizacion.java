package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fila de la tabla {@code Memorandum_Autorizacion} que representa una
 * solicitud de autorización individual sobre un memorándum.
 *
 * <p>Un memo puede tener una o más filas. Cuando el autorizador observa el
 * memo (solicita correcciones), su fila queda en estado {@code OBSERVADO};
 * cuando el remitente reenvía, se crea una NUEVA fila {@code PENDIENTE}
 * (audit trail de la iteración).</p>
 *
 * <p>{@code comentarios} acompaña a los estados RECHAZADO y OBSERVADO para
 * que el autorizador deje el motivo del rechazo o las correcciones a
 * realizar.</p>
 */
public class MemorandumAutorizacion {

    private UUID id;
    private UUID memorandumId;
    private TipoRolMemoAutorizacion tipoRol;
    private UUID autorizadoPorId;
    private LocalDateTime fechaAutorizacion;
    private EstadoMemorandumAutorizacion estado;
    private String comentarios;

    public MemorandumAutorizacion() {
    }

    public MemorandumAutorizacion(UUID id, UUID memorandumId, TipoRolMemoAutorizacion tipoRol,
                                  EstadoMemorandumAutorizacion estado) {
        this.id = id;
        this.memorandumId = memorandumId;
        this.tipoRol = tipoRol;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMemorandumId() {
        return memorandumId;
    }

    public void setMemorandumId(UUID memorandumId) {
        this.memorandumId = memorandumId;
    }

    public TipoRolMemoAutorizacion getTipoRol() {
        return tipoRol;
    }

    public void setTipoRol(TipoRolMemoAutorizacion tipoRol) {
        this.tipoRol = tipoRol;
    }

    public UUID getAutorizadoPorId() {
        return autorizadoPorId;
    }

    public void setAutorizadoPorId(UUID autorizadoPorId) {
        this.autorizadoPorId = autorizadoPorId;
    }

    public LocalDateTime getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public void setFechaAutorizacion(LocalDateTime fechaAutorizacion) {
        this.fechaAutorizacion = fechaAutorizacion;
    }

    public EstadoMemorandumAutorizacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoMemorandumAutorizacion estado) {
        this.estado = estado;
    }

    public String getComentarios() {
        return comentarios;
    }

    public void setComentarios(String comentarios) {
        this.comentarios = comentarios;
    }
}
