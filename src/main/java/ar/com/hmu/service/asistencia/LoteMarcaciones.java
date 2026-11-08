package ar.com.hmu.service.asistencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Archivo importado del sistema de relojes (E4, Tabla 55). */
public record LoteMarcaciones(UUID id, String nombreArchivo, String hashArchivo, LocalDateTime fechaImportacion,
                              UUID importadoPorId, String importadoPorNombre, int cantidadRegistros,
                              LocalDateTime rangoDesde, LocalDateTime rangoHasta) {
}
