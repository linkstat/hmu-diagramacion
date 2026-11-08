package ar.com.hmu.model;


import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioGuardiaMedica extends Horario {

	private int duracionGuardiaHoras;
	private List<LocalDateTime> fechasGuardias;
	private int numeroGuardiasSemanal;
	private boolean permitirGuardiasContinuas;
	private int tiempoDescansoMinimoHoras;

	public HorarioGuardiaMedica(){

	}


	// Getters / Setters

	public int getDuracionGuardiaHoras() {
		return duracionGuardiaHoras;
	}

	public void setDuracionGuardiaHoras(int duracionGuardiaHoras) {
		this.duracionGuardiaHoras = duracionGuardiaHoras;
	}

	public List<LocalDateTime> getFechasGuardias() {
		return fechasGuardias;
	}

	public void setFechasGuardias(List<LocalDateTime> fechasGuardias) {
		this.fechasGuardias = fechasGuardias;
	}

	public int getNumeroGuardiasSemanal() {
		return numeroGuardiasSemanal;
	}

	public void setNumeroGuardiasSemanal(int numeroGuardiasSemanal) {
		this.numeroGuardiasSemanal = numeroGuardiasSemanal;
	}

	public boolean isPermitirGuardiasContinuas() {
		return permitirGuardiasContinuas;
	}

	public void setPermitirGuardiasContinuas(boolean permitirGuardiasContinuas) {
		this.permitirGuardiasContinuas = permitirGuardiasContinuas;
	}

	public int getTiempoDescansoMinimoHoras() {
		return tiempoDescansoMinimoHoras;
	}

	public void setTiempoDescansoMinimoHoras(int tiempoDescansoMinimoHoras) {
		this.tiempoDescansoMinimoHoras = tiempoDescansoMinimoHoras;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioGuardiaMedica