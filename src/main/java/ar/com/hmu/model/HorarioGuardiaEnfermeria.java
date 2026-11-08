package ar.com.hmu.model;


import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public class HorarioGuardiaEnfermeria extends Horario {

	private int duracionGuardia10Horas;
	private int duracionGuardia12Horas;
	private List<LocalDateTime> fechasGuardias;
	private int numeroGuardias10Horas;
	private int numeroGuardias12Horas;

	public HorarioGuardiaEnfermeria(){

	}


	// Getters / Setters

	public int getDuracionGuardia10Horas() {
		return duracionGuardia10Horas;
	}

	public void setDuracionGuardia10Horas(int duracionGuardia10Horas) {
		this.duracionGuardia10Horas = duracionGuardia10Horas;
	}

	public int getDuracionGuardia12Horas() {
		return duracionGuardia12Horas;
	}

	public void setDuracionGuardia12Horas(int duracionGuardia12Horas) {
		this.duracionGuardia12Horas = duracionGuardia12Horas;
	}

	public List<LocalDateTime> getFechasGuardias() {
		return fechasGuardias;
	}

	public void setFechasGuardias(List<LocalDateTime> fechasGuardias) {
		this.fechasGuardias = fechasGuardias;
	}

	public int getNumeroGuardias10Horas() {
		return numeroGuardias10Horas;
	}

	public void setNumeroGuardias10Horas(int numeroGuardias10Horas) {
		this.numeroGuardias10Horas = numeroGuardias10Horas;
	}

	public int getNumeroGuardias12Horas() {
		return numeroGuardias12Horas;
	}

	public void setNumeroGuardias12Horas(int numeroGuardias12Horas) {
		this.numeroGuardias12Horas = numeroGuardias12Horas;
	}


	public List<JornadaLaboral> calcularJornadas(){
		return null;
	}

	public boolean verificarCondicionesEspecificas(){
		return false;
	}
}//end HorarioGuardiaEnfermeria