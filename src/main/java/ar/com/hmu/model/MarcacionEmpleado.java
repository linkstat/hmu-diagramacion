package ar.com.hmu.model;

import java.time.LocalDateTime;
import java.util.UUID;

import ar.com.hmu.roles.impl.EmpleadoRoleImpl;

public class MarcacionEmpleado {

	private Usuario empleado; // Usuario con rol de Empleado
	private LocalDateTime fechaMarcacion;
	private UUID id;
	private String observaciones;
	private TipoMarcacion tipoMarcacion;
	private boolean validada;

	public MarcacionEmpleado() {
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

	public boolean esEgreso() {
		// Implementar lógica para determinar si es egreso
		return false;
	}

	public boolean esIngreso() {
		// Implementar lógica para determinar si es ingreso
		return false;
	}

	public void validarMarcacion() {
		// Implementar lógica para validar marcación
	}

}
