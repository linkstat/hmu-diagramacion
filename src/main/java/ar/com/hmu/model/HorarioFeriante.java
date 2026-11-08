package ar.com.hmu.model;


import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioFeriante extends Horario {

	private List<LocalDateTime> diasNoLaborables;
	private int duracionGuardiaHoras;
	private List<JornadaLaboral> guardiasProgramadas;
	private int horasMinimasMensuales;

	public HorarioFeriante(){

	}


	// Getters / Setters

	public List<LocalDateTime> getDiasNoLaborables() {
		return diasNoLaborables;
	}

	public void setDiasNoLaborables(List<LocalDateTime> diasNoLaborables) {
		this.diasNoLaborables = diasNoLaborables;
	}

	public int getDuracionGuardiaHoras() {
		return duracionGuardiaHoras;
	}

	public void setDuracionGuardiaHoras(int duracionGuardiaHoras) {
		this.duracionGuardiaHoras = duracionGuardiaHoras;
	}

	public List<JornadaLaboral> getGuardiasProgramadas() {
		return guardiasProgramadas;
	}

	public void setGuardiasProgramadas(List<JornadaLaboral> guardiasProgramadas) {
		this.guardiasProgramadas = guardiasProgramadas;
	}

	public int getHorasMinimasMensuales() {
		return horasMinimasMensuales;
	}

	public void setHorasMinimasMensuales(int horasMinimasMensuales) {
		this.horasMinimasMensuales = horasMinimasMensuales;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioFeriante