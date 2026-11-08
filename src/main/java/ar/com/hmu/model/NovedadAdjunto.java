package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Respaldo documental de una novedad (E4, DER Fig. 20c). El contenido se
 * carga sólo al descargarlo; las bandejas trabajan con el tamaño.
 */
public class NovedadAdjunto {

    private UUID id;
    private UUID novedadId;
    private String nombreArchivo;
    private String tipoMime;
    private byte[] contenido;
    private long tamanio;
    private UUID cargadoPorId;
    private LocalDateTime fechaCarga;

    public NovedadAdjunto() {
    }

    public NovedadAdjunto(String nombreArchivo, String tipoMime, byte[] contenido) {
        this.nombreArchivo = nombreArchivo;
        this.tipoMime = tipoMime;
        this.contenido = contenido;
        this.tamanio = contenido != null ? contenido.length : 0;
    }

    /** "constancia_examen.pdf (126 KB)". */
    public String descripcion() {
        long kb = Math.max(1, Math.round(tamanio / 1024.0));
        return nombreArchivo + " (" + kb + " KB)";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getNovedadId() { return novedadId; }
    public void setNovedadId(UUID novedadId) { this.novedadId = novedadId; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getTipoMime() { return tipoMime; }
    public void setTipoMime(String tipoMime) { this.tipoMime = tipoMime; }
    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }
    public long getTamanio() { return tamanio; }
    public void setTamanio(long tamanio) { this.tamanio = tamanio; }
    public UUID getCargadoPorId() { return cargadoPorId; }
    public void setCargadoPorId(UUID cargadoPorId) { this.cargadoPorId = cargadoPorId; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDateTime fechaCarga) { this.fechaCarga = fechaCarga; }
}
