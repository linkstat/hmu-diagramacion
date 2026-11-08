package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fila de la tabla intermedia {@code Memorandum_Destinatario} que asocia
 * un memorándum con cada uno de sus destinatarios.
 *
 * <p>{@code fechaRecepcion} se reinterpretó respecto al DDL original como
 * "fecha de lectura": es {@code null} mientras el destinatario no abrió el
 * memo, y se setea con la fecha y hora del primer acceso. Esta convención
 * evita un cambio de DDL para soportar marcado de leído/no leído.</p>
 */
public class MemorandumDestinatario {

    private UUID memorandumId;
    private UUID usuarioId;
    private LocalDateTime fechaRecepcion;

    public MemorandumDestinatario() {
    }

    public MemorandumDestinatario(UUID memorandumId, UUID usuarioId) {
        this.memorandumId = memorandumId;
        this.usuarioId = usuarioId;
    }

    public MemorandumDestinatario(UUID memorandumId, UUID usuarioId, LocalDateTime fechaRecepcion) {
        this.memorandumId = memorandumId;
        this.usuarioId = usuarioId;
        this.fechaRecepcion = fechaRecepcion;
    }

    public UUID getMemorandumId() {
        return memorandumId;
    }

    public void setMemorandumId(UUID memorandumId) {
        this.memorandumId = memorandumId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDateTime fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    /** {@code true} cuando el destinatario ya abrió el memo. */
    public boolean estaLeido() {
        return fechaRecepcion != null;
    }
}
