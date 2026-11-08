package ar.com.hmu.model;


import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import ar.com.hmu.util.DiaSemana;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioSemanal extends Horario {

	private Map<DiaSemana, Integer> distribucionSemanal;
	private Map<DiaSemana, LocalTime> horaInicioPorDia;   // hora recurrente del día, no fecha

	public HorarioSemanal(){

	}


	// Getters / Setters

	public Map<DiaSemana, Integer> getDistribucionSemanal() {
		return distribucionSemanal;
	}

	public void setDistribucionSemanal(Map<DiaSemana, Integer> distribucionSemanal) {
		this.distribucionSemanal = distribucionSemanal;
	}

	public Map<DiaSemana, LocalTime> getHoraInicioPorDia() {
		return horaInicioPorDia;
	}

	public void setHoraInicioPorDia(Map<DiaSemana, LocalTime> horaInicioPorDia) {
		this.horaInicioPorDia = horaInicioPorDia;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioSemanal