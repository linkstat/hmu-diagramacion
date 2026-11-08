package ar.com.hmu.model;


import java.util.List;
import java.util.UUID;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public interface HorarioBase {

	/** Identidad persistida (PK de la tabla HorarioBase). */
	public UUID getId();

	public void setId(UUID id);

	public List<JornadaLaboral> calcularJornadas();

	public boolean verificarCondicionesGenerales();

}