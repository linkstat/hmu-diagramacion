package ar.com.hmu.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Diagrama de servicio: la planificación mensual de un servicio, con las
 * jornadas de cada empleado para el rango {@code [fechaInicio, fechaFin]}.
 *
 * <p>Modelo POJO. Las transiciones de estado (state machine), la generación
 * de jornadas desde los horarios y las validaciones viven en los servicios
 * del módulo; esta clase sólo expone datos.</p>
 *
 * <p>Las relaciones se referencian por UUID (mismo criterio que
 * {@link Memorandum}): {@code servicioId}, {@code creadoPorId},
 * {@code aprobadoPorId}. El {@code estado} usa el ENUM {@link EstadoDiagrama}
 * (columna {@code estado_diagrama}).</p>
 *
 * <p>{@code aprobadoPorId}, {@code fechaAprobacion} y
 * {@code comentariosObservacion} se completan en la transición a
 * {@code APROBADO} / {@code OBSERVADO}. {@code version} soporta control de
 * concurrencia optimista.</p>
 */
public class DiagramaDeServicio {

	private UUID id;
	private UUID servicioId;
	private EstadoDiagrama estado;
	private LocalDate fechaInicio;
	private LocalDate fechaFin;
	private int version;
	private UUID creadoPorId;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private UUID aprobadoPorId;               // null hasta APROBADO/OBSERVADO
	private LocalDateTime fechaAprobacion;    // null hasta APROBADO/OBSERVADO
	private String comentariosObservacion;    // motivo de OBSERVADO

	private List<JornadaLaboral> jornadas = new ArrayList<>();

	public DiagramaDeServicio() {
	}

	public DiagramaDeServicio(UUID id, UUID servicioId, EstadoDiagrama estado,
	                          LocalDate fechaInicio, LocalDate fechaFin,
	                          UUID creadoPorId) {
		this.id = id;
		this.servicioId = servicioId;
		this.estado = estado;
		this.fechaInicio = fechaInicio;
		this.fechaFin = fechaFin;
		this.creadoPorId = creadoPorId;
	}


	// Getters / Setters

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getServicioId() {
		return servicioId;
	}

	public void setServicioId(UUID servicioId) {
		this.servicioId = servicioId;
	}

	public EstadoDiagrama getEstado() {
		return estado;
	}

	public void setEstado(EstadoDiagrama estado) {
		this.estado = estado;
	}

	public LocalDate getFechaInicio() {
		return fechaInicio;
	}

	public void setFechaInicio(LocalDate fechaInicio) {
		this.fechaInicio = fechaInicio;
	}

	public LocalDate getFechaFin() {
		return fechaFin;
	}

	public void setFechaFin(LocalDate fechaFin) {
		this.fechaFin = fechaFin;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public UUID getCreadoPorId() {
		return creadoPorId;
	}

	public void setCreadoPorId(UUID creadoPorId) {
		this.creadoPorId = creadoPorId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public UUID getAprobadoPorId() {
		return aprobadoPorId;
	}

	public void setAprobadoPorId(UUID aprobadoPorId) {
		this.aprobadoPorId = aprobadoPorId;
	}

	public LocalDateTime getFechaAprobacion() {
		return fechaAprobacion;
	}

	public void setFechaAprobacion(LocalDateTime fechaAprobacion) {
		this.fechaAprobacion = fechaAprobacion;
	}

	public String getComentariosObservacion() {
		return comentariosObservacion;
	}

	public void setComentariosObservacion(String comentariosObservacion) {
		this.comentariosObservacion = comentariosObservacion;
	}

	public List<JornadaLaboral> getJornadas() {
		return jornadas;
	}

	public void setJornadas(List<JornadaLaboral> jornadas) {
		this.jornadas = (jornadas != null) ? jornadas : new ArrayList<>();
	}


	// Otros métodos

	/**
	 * Agrega una jornada a la planificación del diagrama.
	 *
	 * @param jornada la jornada a agregar.
	 */
	public void addJornada(JornadaLaboral jornada) {
		this.jornadas.add(jornada);
	}

	/**
	 * Devuelve las jornadas planificadas para un empleado dado.
	 *
	 * @param empleadoId el UUID del empleado.
	 * @return lista de jornadas del empleado (posiblemente vacía).
	 */
	public List<JornadaLaboral> getJornadasDeEmpleado(UUID empleadoId) {
		List<JornadaLaboral> resultado = new ArrayList<>();
		for (JornadaLaboral j : jornadas) {
			if (empleadoId != null && empleadoId.equals(j.getEmpleadoId())) {
				resultado.add(j);
			}
		}
		return resultado;
	}

}
