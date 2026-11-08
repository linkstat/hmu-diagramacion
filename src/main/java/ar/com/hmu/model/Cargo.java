package ar.com.hmu.model;

import java.util.UUID;

/**
 * Clase Cargo
 */
public class Cargo {

	private UUID id;
	private Integer numero;
	private String descripcion;
	private Agrupacion agrupacion;

	public Cargo() {
	}

	public Cargo(UUID id, Integer numero, String descripcion, Agrupacion agrupacion) {
		this.id = id;
		this.numero = numero;
		this.descripcion = descripcion;
		this.agrupacion = agrupacion;
	}

	@Override
	public String toString() {
		return this.numero +" - " + this.getDescripcion();
	}

	// Getters

	public UUID getId() {
		return id;
	}

	public Integer getNumero() {
		return numero;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public Agrupacion getAgrupacion() {
		return agrupacion;
	}

	// Setters

	public void setId(UUID id) {
		this.id = id;
	}

	public void setNumero(Integer numero) {
		this.numero = numero;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public void setAgrupacion(Agrupacion agrupacion) {
		this.agrupacion = agrupacion;
	}

}