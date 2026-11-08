package ar.com.hmu.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa un domicilio con varios atributos como calle, numeración, barrio, ciudad, localidad y provincia.
 * Implementa el patrón de diseño Builder para permitir la creación de objetos con parámetros opcionales.
 */
public class Domicilio {
	private final UUID id;
	private final String calle;
	private final int numeracion;
	private final String barrio;
	private final String ciudad;
	private final String localidad;
	private final String provincia;

	/**
	 * Constructor privado que inicializa un objeto Domicilio con los valores proporcionados por el Builder.
	 *
	 * @param builder la instancia de Builder que contiene los datos para inicializar el objeto Domicilio.
	 */
	private Domicilio(Builder builder) {
		this.id = builder.id;
		this.calle = builder.calle;
		this.numeracion = builder.numeracion;
		this.barrio = builder.barrio;
		this.ciudad = builder.ciudad;
		this.localidad = builder.localidad;
		this.provincia = builder.provincia;
	}

	/**
	 * Builder estático que facilita la construcción de un objeto Domicilio.
	 * Este patrón permite la creación de objetos Domicilio con solo los atributos necesarios, manteniendo
	 * la legibilidad y flexibilidad en el código.
	 */
	public static class Builder {
		private UUID id;
		private String calle;
		private int numeracion;
		private String barrio;
		private String ciudad;
		private String localidad;
		private String provincia;

		/**
		 * Establece el UUID del domicilio.
		 *
		 * @param id el UUID del domicilio.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder id(UUID id) {
			Objects.requireNonNull(id, "El ID de domicilio no puede ser nulo.");
			this.id = id;
			return this;
		}

		/**
		 * Establece la calle del domicilio.
		 *
		 * @param calle el nombre de la calle.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder calle(String calle) {
			this.calle = calle;
			return this;
		}

		/**
		 * Establece la numeración del domicilio.
		 *
		 * @param numeracion el número del domicilio.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder numeracion(int numeracion) {
			this.numeracion = numeracion;
			return this;
		}

		/**
		 * Establece el barrio del domicilio.
		 *
		 * @param barrio el nombre del barrio.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder barrio(String barrio) {
			this.barrio = barrio;
			return this;
		}

		/**
		 * Establece la ciudad del domicilio.
		 *
		 * @param ciudad el nombre de la ciudad.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder ciudad(String ciudad) {
			this.ciudad = ciudad;
			return this;
		}

		/**
		 * Establece la localidad del domicilio.
		 *
		 * @param localidad el nombre de la localidad.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder localidad(String localidad) {
			this.localidad = localidad;
			return this;
		}

		/**
		 * Establece la provincia del domicilio.
		 *
		 * @param provincia el nombre de la provincia.
		 * @return la instancia del Builder para permitir la concatenación de métodos.
		 */
		public Builder provincia(String provincia) {
			this.provincia = provincia;
			return this;
		}

		/**
		 * Construye y devuelve un objeto Domicilio con los valores proporcionados al Builder.
		 *
		 * @return un nuevo objeto Domicilio.
		 */
		public Domicilio build() {
			// Validaciones opcionales antes de construir el objeto
			if (id == null) {
				throw new IllegalStateException("El ID no puede ser null");
			}

			return new Domicilio(this);
		}

	}


	// Setters
	/* No se definen setters para reforzar la inmutabilidad (porque todos los campos se han marcado como final).
	 * Mantenemos Domicilio como inmutable, porque es lo recomendable cuando se usa el patrón Builder.
	 * Esto evita la modificación de objetos después de su creación, mejorando la seguridad y consistencia de los datos.
	 * En lugar de usar el constructor predeterminado y los setters, se usa el Builder para establecer todas las
	 * propiedades necesarias antes de construir el objeto.
	 */


	// Getters

	public UUID getId() {
		return id;
	}

	public String getCalle() {
		return calle;
	}

	public int getNumeracion() {
		return numeracion;
	}

	public String getBarrio() {
		return barrio;
	}

	public String getCiudad() {
		return ciudad;
	}

	public String getLocalidad() {
		return localidad;
	}

	public String getProvincia() {
		return provincia;
	}

	/** Método para obtener un Builder preconfigurado con los valores actuales
	 * <p>
	 * @return un objeto Builder con los datos del domicilio actual
	 */
	public Builder toBuilder() {
		return new Builder()
				.id(this.id)
				.calle(this.calle)
				.numeracion(this.numeracion)
				.barrio(this.barrio)
				.ciudad(this.ciudad)
				.localidad(this.localidad)
				.provincia(this.provincia);
	}

}
