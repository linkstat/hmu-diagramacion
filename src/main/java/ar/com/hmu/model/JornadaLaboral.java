package ar.com.hmu.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Jornada laboral planificada dentro de un {@link DiagramaDeServicio}: qué le
 * toca a un empleado en un día concreto.
 *
 * <p>Modelo POJO. Toda la lógica de negocio (generación desde el horario,
 * validación de superposiciones y carga horaria) vive en los servicios del
 * módulo de diagramación; esta clase sólo expone datos.</p>
 *
 * <p>Absorbe la ex-tabla puente {@code Planificacion}: la jornada referencia
 * por UUID su diagrama ({@code diagramaId}) y su empleado ({@code empleadoId}),
 * en lugar de resolverse a través de una tabla ternaria.</p>
 *
 * <p>Para tipos sin horario ({@code FRANCO}, {@code FRANCO_COMPENSATORIO},
 * {@code LICENCIA}) los campos {@code fechaIngreso} y {@code fechaEgreso}
 * quedan en {@code null} (ver {@link TipoJornada#requiereHorario()}).</p>
 */
public class JornadaLaboral {

	private UUID id;
	private UUID diagramaId;
	private UUID empleadoId;
	private LocalDate fecha;
	private TipoJornada tipo;
	private LocalDateTime fechaIngreso;   // null si el tipo no requiere horario
	private LocalDateTime fechaEgreso;    // null si el tipo no requiere horario
	private String observaciones;

	public JornadaLaboral() {
	}

	public JornadaLaboral(UUID id, UUID diagramaId, UUID empleadoId, LocalDate fecha,
	                      TipoJornada tipo, LocalDateTime fechaIngreso,
	                      LocalDateTime fechaEgreso, String observaciones) {
		this.id = id;
		this.diagramaId = diagramaId;
		this.empleadoId = empleadoId;
		this.fecha = fecha;
		this.tipo = tipo;
		this.fechaIngreso = fechaIngreso;
		this.fechaEgreso = fechaEgreso;
		this.observaciones = observaciones;
	}


	// Getters / Setters

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getDiagramaId() {
		return diagramaId;
	}

	public void setDiagramaId(UUID diagramaId) {
		this.diagramaId = diagramaId;
	}

	public UUID getEmpleadoId() {
		return empleadoId;
	}

	public void setEmpleadoId(UUID empleadoId) {
		this.empleadoId = empleadoId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public void setFecha(LocalDate fecha) {
		this.fecha = fecha;
	}

	public TipoJornada getTipo() {
		return tipo;
	}

	public void setTipo(TipoJornada tipo) {
		this.tipo = tipo;
	}

	public LocalDateTime getFechaIngreso() {
		return fechaIngreso;
	}

	public void setFechaIngreso(LocalDateTime fechaIngreso) {
		this.fechaIngreso = fechaIngreso;
	}

	public LocalDateTime getFechaEgreso() {
		return fechaEgreso;
	}

	public void setFechaEgreso(LocalDateTime fechaEgreso) {
		this.fechaEgreso = fechaEgreso;
	}

	public String getObservaciones() {
		return observaciones;
	}

	public void setObservaciones(String observaciones) {
		this.observaciones = observaciones;
	}


	// Otros métodos

	/**
	 * Indica si esta jornada tiene un horario concreto de ingreso y egreso
	 * (ambos no nulos). Los francos y licencias no lo tienen.
	 *
	 * @return {@code true} si hay horario de ingreso y egreso.
	 */
	public boolean tieneHorario() {
		return fechaIngreso != null && fechaEgreso != null;
	}

	/**
	 * Calcula la duración de la jornada a partir de su horario.
	 *
	 * @return la duración como {@link Duration}, o {@code null} si la jornada
	 *         no tiene horario o el egreso es anterior al ingreso.
	 */
	public Duration calcularDuracion() {
		if (tieneHorario() && !fechaEgreso.isBefore(fechaIngreso)) {
			return Duration.between(fechaIngreso, fechaEgreso);
		}
		return null;
	}

}
