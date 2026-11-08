package ar.com.hmu.model;


import ar.com.hmu.roles.impl.EmpleadoRoleImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Clase para representar Servicios (esto es: áreas, oficinas)
 *
 */
public class Servicio {

	private UUID id;
	private String nombre;
	private Agrupacion agrupacion;
	private Usuario direccion;

	private UUID direccionId;

	/**
	 * Usuario actualmente a cargo del papeleo del servicio: aprobaciones,
	 * rechazos y observaciones de memos. Normalmente coincide con la jefatura
	 * del servicio; durante una ausencia (licencia, vacaciones) puede ser otro
	 * empleado del servicio designado por Oficina de Personal, Dirección o el
	 * propio jefe antes de salir.
	 */
	private UUID encargadoUsuarioId;
	/** Dotación mínima del servicio (CU07, CU13); null = sin control. */
	private Integer dotacionMinima;

	private List<Usuario> empleados;
	private List<DiagramaDeServicio> diagramas;

	public Servicio() {
	}

	public Servicio(UUID id, String nombre, Agrupacion agrupacion) {
		this.id = id;
		this.nombre = nombre;
		this.agrupacion = agrupacion;
		this.direccion = null;
	}

	public Servicio(UUID id, String nombre, Agrupacion agrupacion, UUID direccionId) {
		this.id = id;
		this.nombre = nombre;
		this.agrupacion = agrupacion;
		this.direccionId = direccionId;

	}

	public Servicio(UUID id, String nombre, Agrupacion agrupacion, List<Usuario> empleados, List<DiagramaDeServicio> diagramas) {
		this.id = id;
		this.nombre = nombre;
		this.agrupacion = agrupacion;
		this.direccionId = null;
		this.empleados = empleados;
		this.diagramas = diagramas;
	}

	@Override
	public String toString() {
		return this.getNombre();
	}

	// Setters

	public void setId(UUID id) {
		this.id = id;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public void setAgrupacion(Agrupacion agrupacion) {
		this.agrupacion = agrupacion;
	}

	public void setDireccion(Usuario direccion) {
		this.direccion = direccion;
	}

	public void setDireccionId(UUID direccionId) {
		this.direccionId = direccionId;
	}

	public void setEncargadoUsuarioId(UUID encargadoUsuarioId) {
		this.encargadoUsuarioId = encargadoUsuarioId;
	}

	public void setEmpleados(List<Usuario> empleados) {
		this.empleados = empleados;
	}

	public void setDiagramas(List<DiagramaDeServicio> diagramas) {
		this.diagramas = diagramas;
	}

	// Getters

	public UUID getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public Agrupacion getAgrupacion() {
		return agrupacion;
	}

	public List<DiagramaDeServicio> getDiagramas() {
		return diagramas;
	}

	public Usuario getDireccion() {
		return direccion;
	}

	public UUID getDireccionId() {
		return direccionId;
	}

	public UUID getEncargadoUsuarioId() {
		return encargadoUsuarioId;
	}

	public List<Usuario> getEmpleados(){
		return null;
	}


	/**
	 * Método para agregar empleados a un servicio
	 */
	public void addEmpleado(Usuario empleado){
		if (empleado.hasRoleBehavior(EmpleadoRoleImpl.class)) {
			this.empleados.add(empleado);
		} else {
			throw new IllegalArgumentException("El usuario no tiene el rol de Empleado.");
		}
	}

	public void delEmpleado(Usuario empleado){
		this.empleados.remove(empleado);
	}

	/**
	 * 
	 * @param fechaActual
	 */
	public DiagramaDeServicio getDiagramaActual(LocalDateTime fechaActual){
		return null;
	}

	/**
	 * 
	 * @param fechaEspecifica
	 */
	public DiagramaDeServicio getDiagramaFechaEspecifica(LocalDateTime fechaEspecifica){
		return null;
	}

	/**
	 * 
	 * @param diagrama
	 */
	public void setDiagrama(DiagramaDeServicio diagrama){

	}


	public Integer getDotacionMinima() {
		return dotacionMinima;
	}

	public void setDotacionMinima(Integer dotacionMinima) {
		this.dotacionMinima = dotacionMinima;
	}
}
