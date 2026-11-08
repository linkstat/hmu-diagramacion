package ar.com.hmu.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import ar.com.hmu.roles.impl.EmpleadoRoleImpl;

public class RegistroJornadaLaboral {

	private Usuario empleado; // Usuario con rol de Empleado
	private LocalDateTime fecha;
	private LocalDateTime fechaEgreso;
	private LocalDateTime fechaIngreso;
	private List<MarcacionEmpleado> marcacionesEmpleado;

	public RegistroJornadaLaboral() {
		this.marcacionesEmpleado = new ArrayList<>();
	}

	// Getters y Setters con verificación de roles

	public void setEmpleado(Usuario empleado) {
		if (empleado.hasRoleBehavior(EmpleadoRoleImpl.class)) {
			this.empleado = empleado;
		} else {
			throw new IllegalArgumentException("El usuario no tiene el rol de Empleado.");
		}
	}

	public Usuario getEmpleado() {
		return empleado;
	}

	// Métodos actualizados

	public void agregarMarcacion(MarcacionEmpleado marcacion) {
		this.marcacionesEmpleado.add(marcacion);
	}

	public Duration calcularDuracionJornada() {
		// Implementar lógica para calcular la duración de la jornada
		return null;
	}

	public double getHorasExtraDisp() {
		// Implementar lógica para obtener horas extra disponibles
		return 0;
	}

	public boolean tieneOmisiones() {
		// Implementar lógica para verificar omisiones
		return false;
	}

	public boolean tieneTardanza() {
		// Implementar lógica para verificar tardanzas
		return false;
	}

}
