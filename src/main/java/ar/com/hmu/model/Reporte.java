package ar.com.hmu.model;


import java.io.File;
import java.time.Period;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
  */
public abstract class Reporte {

	private Period periodo;

	public Reporte(){

	}

	
	public File exportarCSV(){
		return null;
	}

	public File exportarPDF(){
		return null;
	}

	public File exportarXLSX(){
		return null;
	}
}//end Reporte