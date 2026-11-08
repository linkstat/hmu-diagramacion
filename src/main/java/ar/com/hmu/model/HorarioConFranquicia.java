package ar.com.hmu.model;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioConFranquicia implements HorarioBase {

	private UUID id;
	private LocalDateTime fechaEgreso;
	private LocalDateTime fechaIngreso;
	private HorarioBase horarioDecorado;
	private int horasFranquicia;

	public HorarioConFranquicia(){

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

	public HorarioBase getHorarioDecorado() {
		return horarioDecorado;
	}

	public void setHorarioDecorado(HorarioBase horarioDecorado) {
		this.horarioDecorado = horarioDecorado;
	}

	public int getHorasFranquicia() {
		return horasFranquicia;
	}

	public void setHorasFranquicia(int horasFranquicia) {
		this.horasFranquicia = horasFranquicia;
	}


	public int calcularHorasMensuales(){
		return 0;
	}

	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesGenerales(){
		return false;
	}
}//end HorarioConFranquicia