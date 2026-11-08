package ar.com.hmu.service.asistencia;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Jornada cumplida, conformada con las marcaciones de ingreso y egreso
 * (E4, Tabla 56; tabla RegistroJornadaLaboral). Una marcación faltante
 * deja la omisión indicada.
 */
public record JornadaRegistrada(UUID id, UUID agenteId, LocalDate fecha, LocalDateTime ingreso,
                                LocalDateTime egreso, UUID marcacionIngresoId, UUID marcacionEgresoId) {

    public boolean omisionIngreso() {
        return ingreso == null;
    }

    public boolean omisionEgreso() {
        return egreso == null;
    }

    public Integer duracionMinutos() {
        return ingreso != null && egreso != null ? (int) Duration.between(ingreso, egreso).toMinutes() : null;
    }
}
