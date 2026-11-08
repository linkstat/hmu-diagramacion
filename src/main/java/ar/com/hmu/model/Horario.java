package ar.com.hmu.model;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public abstract class Horario implements HorarioBase {

	private UUID id;
	private LocalDateTime fechaEgreso;
	private LocalDateTime fechaIngreso;
	private List<JornadaLaboral> jornadasPlanificadas;
	private String reglasHorario;

	public Horario(){

	}


	// Getters / Setters

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public void setId(UUID id) {
		this.id = id;
	}

	public LocalDateTime getFechaEgreso() {
		return fechaEgreso;
	}

	public void setFechaEgreso(LocalDateTime fechaEgreso) {
		this.fechaEgreso = fechaEgreso;
	}

	public LocalDateTime getFechaIngreso() {
		return fechaIngreso;
	}

	public void setFechaIngreso(LocalDateTime fechaIngreso) {
		this.fechaIngreso = fechaIngreso;
	}

	public List<JornadaLaboral> getJornadasPlanificadas() {
		return jornadasPlanificadas;
	}

	public void setJornadasPlanificadas(List<JornadaLaboral> jornadasPlanificadas) {
		this.jornadasPlanificadas = jornadasPlanificadas;
	}

	public String getReglasHorario() {
		return reglasHorario;
	}

	public void setReglasHorario(String reglasHorario) {
		this.reglasHorario = reglasHorario;
	}


	public abstract List<JornadaLaboral> calcularJornadas();

	public boolean verificarCondicionesGenerales(){
		return false;
	}
}//end Horario