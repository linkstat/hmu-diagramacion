package ar.com.hmu.model;

import java.time.LocalDateTime;

import ar.com.hmu.roles.impl.EmpleadoRoleImpl;
import ar.com.hmu.roles.impl.JefaturaDeServicioRoleImpl;

public class FrancoCompensatorio {

	private Usuario autorizadaPor; // Usuario con rol de JefaturaDeServicio
	private Usuario jefatura; // Usuario con rol de JefaturaDeServicio
	private double cantHoras;
	private String desc;
	private Usuario empleado; // Usuario con rol de Empleado
	private EstadoTramite estadoTramite;
	private LocalDateTime fechaAutorizacion;
	private LocalDateTime fechaDeAplicacion;

	public FrancoCompensatorio() {
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

	public void setAutorizadaPor(Usuario autorizadaPor) {
		if (autorizadaPor.hasRoleBehavior(JefaturaDeServicioRoleImpl.class)) {
			this.autorizadaPor = autorizadaPor;
		} else {
			throw new IllegalArgumentException("El usuario no tiene el rol de Jefatura de Servicio.");
		}
	}

	public Usuario getAutorizadaPor() {
		return autorizadaPor;
	}

	public void setJefatura(Usuario jefatura) {
		if (jefatura.hasRoleBehavior(JefaturaDeServicioRoleImpl.class)) {
			this.jefatura = jefatura;
		} else {
			throw new IllegalArgumentException("El usuario no tiene el rol de Jefatura de Servicio.");
		}
	}

	public Usuario getJefatura() {
		return jefatura;
	}

	// Resto de getters y setters...

	// Métodos actualizados
	public void autorizar() {
		// Implementar lógica de autorización
	}

	public void descontarHorasExtra() {
		// Implementar lógica para descontar horas extra
	}

	public EstadoTramite getEstado() {
		return estadoTramite;
	}

	public void solicitarFranco() {
		// Implementar lógica para solicitar franco compensatorio
	}

	public boolean verificarHorasSuficientes() {
		// Implementar lógica para verificar horas suficientes
		return false;
	}

}
