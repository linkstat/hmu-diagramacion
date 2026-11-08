package ar.com.hmu.model;


import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import ar.com.hmu.util.DiaSemana;


/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioDXI extends Horario {

	private Map<DiaSemana, Integer> distribucionHoraria;
	private Map<DiaSemana, LocalTime> horaInicioPorDia;   // hora recurrente del día, no fecha
	private int horasSemanales;

	public HorarioDXI(){

	}


	// Getters / Setters

	public Map<DiaSemana, Integer> getDistribucionHoraria() {
		return distribucionHoraria;
	}

	public void setDistribucionHoraria(Map<DiaSemana, Integer> distribucionHoraria) {
		this.distribucionHoraria = distribucionHoraria;
	}

	public Map<DiaSemana, LocalTime> getHoraInicioPorDia() {
		return horaInicioPorDia;
	}

	public void setHoraInicioPorDia(Map<DiaSemana, LocalTime> horaInicioPorDia) {
		this.horaInicioPorDia = horaInicioPorDia;
	}

	public int getHorasSemanales() {
		return horasSemanales;
	}

	public void setHorasSemanales(int horasSemanales) {
		this.horasSemanales = horasSemanales;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioDXI