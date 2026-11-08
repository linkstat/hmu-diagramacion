package ar.com.hmu.model;


import java.util.List;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioAbierto extends Horario {

	private boolean flexibilidadHoraria;
	private int horasSemanales;
	private List<JornadaLaboral> preferenciasHorarias;

	public HorarioAbierto(){

	}


	// Getters / Setters

	public boolean isFlexibilidadHoraria() {
		return flexibilidadHoraria;
	}

	public void setFlexibilidadHoraria(boolean flexibilidadHoraria) {
		this.flexibilidadHoraria = flexibilidadHoraria;
	}

	public int getHorasSemanales() {
		return horasSemanales;
	}

	public void setHorasSemanales(int horasSemanales) {
		this.horasSemanales = horasSemanales;
	}

	public List<JornadaLaboral> getPreferenciasHorarias() {
		return preferenciasHorarias;
	}

	public void setPreferenciasHorarias(List<JornadaLaboral> preferenciasHorarias) {
		this.preferenciasHorarias = preferenciasHorarias;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioAbierto