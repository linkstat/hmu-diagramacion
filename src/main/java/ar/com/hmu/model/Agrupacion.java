package ar.com.hmu.model;

/**
 * Agrupación escalafonaria de cargos y servicios. La BD guarda {@link #name()};
 * {@link #toString()} es el texto legible que muestran las pantallas.
 */
public enum Agrupacion {
	INDEFINIDO("Sin definir"),
	PLANTAPOLITICA("Planta política"),
	JEFATURA("Jefatura"),
	TECNICO("Técnico"),
	ADMINISTRATIVO("Administrativo"),
	SERVICIO("Servicio"),
	ENFERMERIA("Enfermería"),
	MEDICO("Médico"),
	PROFESIONAL("Profesional");

	private final String etiqueta;

	Agrupacion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	@Override
	public String toString() {
		return etiqueta;
	}
}
