package ar.com.hmu.model;


import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioNocturno extends Horario {

	private List<LocalDateTime> diasProgramados;
	private int duracionJornadaHoras;
	private int numeroJornadasMensuales;

	public HorarioNocturno(){

	}


	// Getters / Setters

	public List<LocalDateTime> getDiasProgramados() {
		return diasProgramados;
	}

	public void setDiasProgramados(List<LocalDateTime> diasProgramados) {
		this.diasProgramados = diasProgramados;
	}

	public int getDuracionJornadaHoras() {
		return duracionJornadaHoras;
	}

	public void setDuracionJornadaHoras(int duracionJornadaHoras) {
		this.duracionJornadaHoras = duracionJornadaHoras;
	}

	public int getNumeroJornadasMensuales() {
		return numeroJornadasMensuales;
	}

	public void setNumeroJornadasMensuales(int numeroJornadasMensuales) {
		this.numeroJornadasMensuales = numeroJornadasMensuales;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioNocturno