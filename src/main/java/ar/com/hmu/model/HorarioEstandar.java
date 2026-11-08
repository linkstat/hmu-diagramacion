package ar.com.hmu.model;


import java.util.List;
import ar.com.hmu.util.DiaSemana;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioEstandar extends Horario {

	private List<DiaSemana> diasLaborables;
	private int horasPorDia;

	public HorarioEstandar(){

	}


	// Getters / Setters

	public List<DiaSemana> getDiasLaborables() {
		return diasLaborables;
	}

	public void setDiasLaborables(List<DiaSemana> diasLaborables) {
		this.diasLaborables = diasLaborables;
	}

	public int getHorasPorDia() {
		return horasPorDia;
	}

	public void setHorasPorDia(int horasPorDia) {
		this.horasPorDia = horasPorDia;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioEstandar