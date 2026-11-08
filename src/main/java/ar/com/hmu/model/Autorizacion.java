package ar.com.hmu.model;


import java.time.LocalDateTime;

/**
 * @author Pablo Alejandro Hamann
 * @version 1.0
 */
public class Autorizacion {

	private Usuario autorizadoPor;
	private LocalDateTime fechaAutorizacion;
	private TipoAutorizacion tipo;
	public TipoAutorizacion m_TipoAutorizacion;
	public Usuario m_Usuario;

	public Autorizacion(){

	}

	/**
	 * 
	 * @param usuario
	 */
	public boolean esAutorizadoPor(Usuario usuario){
		return false;
	}
}//end Autorizacion