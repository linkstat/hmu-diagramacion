package ar.com.hmu.model;

import java.time.LocalDate;
import java.util.*;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.roles.Role;
import ar.com.hmu.util.PasswordUtils;
import static ar.com.hmu.util.StringUtils.normalizar;

/**
 * Clase base abstracta pra representar usuarios de forma genérica
 */
public class Usuario {

	private UUID id;
	private LocalDate fechaAlta;
	private boolean estado;
	private long cuil;
	private String apellidos;
	private String nombres;
	private Sexo sexo;
	private String mail;
	private long tel;
	private Domicilio domicilio;
	private Cargo cargo;
	private Servicio servicio;
	private String password;
	private byte[] profileImage;

	// Atributos para implementar el patrón «Lazy Loading»
	// Una solución equilibrada: almacenar la referencias de los objetos (además de los propios objetos), evita tener que inicializar los objetos (en este caso Cargo, Servicio, Domicilio)evitando tener que cargar las entidades completas desde la BD
	private UUID domicilioId;
	private UUID cargoId;
	private UUID servicioId;

	// Gestión de roles de usuario
	private Set<RoleData> rolesData = new HashSet<>();    // Roles de datos
	private Set<Role> rolesBehavior = new HashSet<>();    // Roles de comportamiento


	// Constructor por defecto
	public Usuario(){
		this.rolesData = new HashSet<>();
		this.rolesBehavior = new HashSet<>();
	}

	// Constructor con los datos principales
	public Usuario(UUID id, LocalDate fechaAlta, boolean estado, long cuil, String apellidos, String nombres, Sexo sexo, String mail) {
		this.id = id;
		this.fechaAlta = fechaAlta;
		this.estado = estado;
		this.cuil = cuil;
		this.apellidos = apellidos;
		this.nombres = nombres;
		this.sexo = sexo;
		this.mail = mail;
		this.password = PasswordUtils.hashPassword(String.valueOf(cuil).toCharArray());
	}

	// Getters

	public UUID getId() {
		return id;
	}

	public LocalDate getFechaAlta() {
		return fechaAlta;
	}

	public boolean getEstado() {
		return estado;
	}

	public long getCuil() {
		return cuil;
	}

	public String getApellidos() {
		return apellidos;
	}

	public String getNombres() {
		return nombres;
	}

	public String getNombreCompleto() {
		return nombres + " " + apellidos;
	}

	public String getApellidosNombres() {
		return apellidos + ", " + nombres;
	}

	public Sexo getSexo() {
		return sexo;
	}

	public String getMail() {
		return mail;
	}

	public long getTel() {
		return tel;
	}

	public Domicilio getDomicilio() {
		return domicilio;
	}

	public Cargo getCargo() {
		return cargo;
	}

	/**
	 * Obtiene el servicio al que pertenece el usuario.
	 *
	 * @return el servicio asociado al usuario.
	 */
	public Servicio getServicio() {
		return servicio;
	}

	// Método para obtener la contraseña cifrada (solo para uso interno de la base de datos)
	/**
	 * Este método permite al UsuarioRepository acceder a la contraseña ya cifrada
	 * y usarla para persistir en la base de datos.
	 * Este método no expone la contraseña en texto plano, sino la versión hasheada
	 * que ya está lista para ser almacenada.
	 * @return EncryptedPassword
	 */
	public String getEncryptedPassword() {
		return this.password;
	}

	public byte[] getProfileImage() {
		return profileImage;
	}

	public UUID getDomicilioId() {
		return domicilioId;
	}

	public UUID getCargoId() {
		return cargoId;
	}

	public UUID getServicioId() {
		return servicioId;
	}

	public Set<RoleData> getRolesData() {
		return rolesData;
	}

	public Set<Role> getRolesBehavior() {
		return rolesBehavior;
	}


	// Setters

	public void setId(UUID id) {
		this.id = id;
	}

	public void setFechaAlta(LocalDate fechaAlta) {
		this.fechaAlta = fechaAlta;
	}

	public void setEstado(boolean estado) {
		this.estado = estado;
	}

	public void setCuil(long cuil) {
		this.cuil = cuil;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public void setNombres(String nombres) {
		this.nombres = nombres;
	}

	public void setSexo(Sexo sexo) {
		this.sexo = sexo;
	}

	public void setMail(String mail) {
		this.mail = mail;
	}

	public void setTel(long tel) {
		this.tel = tel;
	}

	public void setDomicilio(Domicilio domicilio) {
		this.domicilio = domicilio;
	}

	public void setCargo(Cargo cargo) {
		this.cargo = cargo;
	}

	/**
	 * Establece el servicio al que pertenece el usuario.
	 *
	 * @param servicio el servicio a asociar con el usuario.
	 */
	public void setServicio(Servicio servicio) {
		this.servicio = servicio;
	}

	/**
	 * Establece la contraseña del usuario cifrándola con Argon2id antes de almacenarla.
	 * Los hashes BCrypt legacy se siguen aceptando en {@link #validatePassword(char[])}
	 * y se migran a Argon2id en el flujo de login.
	 *
	 * @param rawPasswordArray La contraseña en texto plano proporcionada por el usuario.
	 *                         Se sobrescribe con ceros tras el hashing.
	 */
	public void setPassword(char[] rawPasswordArray) {
		this.password = PasswordUtils.hashPassword(rawPasswordArray);
	}

	// Método para establecer la contraseña desde la base de datos
	public void setPasswordHash(String passwordHash) {
		this.password = passwordHash;
	}

	public void setProfileImage(byte[] profileImage) {
		this.profileImage = profileImage;
	}

	public void setDomicilioId(UUID domicilioId) {
		this.domicilioId = domicilioId;
	}

	public void setCargoId(UUID cargoId) {
		this.cargoId = cargoId;
	}

	public void setServicioId(UUID servicioId) {
		this.servicioId = servicioId;
	}

	public void setRolesData(Set<RoleData> rolesData) {
		this.rolesData = rolesData;
	}

	public void setRolesBehavior(Set<Role> rolesBehavior) {
		this.rolesBehavior = rolesBehavior;
	}


	// Otros métodos

	public void addRoleBehavior(Role role) {
		this.rolesBehavior.add(role);
	}


	/**
	 * Verifica si la contraseña actual del usuario es la predeterminada.
	 * La contraseña predeterminada es el número de CUIL del usuario; este método
	 * la valida contra el hash almacenado independientemente del algoritmo
	 * (Argon2id o BCrypt legacy).
	 *
	 * @return true si la contraseña es la predeterminada, false en caso contrario.
	 */
	public boolean isDefaultPassword() {
		// Convertimos el CUIL a cadena de caracteres para comprobar
		char[] defaultPasswordArray = String.valueOf(this.cuil).toCharArray();
		try {
			// Validar la contraseña usando el método actualizado de PasswordUtils
			return PasswordUtils.validatePassword(defaultPasswordArray, this.password);
		} finally {
			// Limpiar la memoria del `char[]` después de usarlo
			Arrays.fill(defaultPasswordArray, '\0');
		}
	}

	// Método para validar la contraseña sin exponerla
	/**
	 * Valida si la contraseña proporcionada coincide con la contraseña cifrada almacenada.
	 *
	 * @param rawPasswordArray Contraseña en texto plano proporcionada por el usuario.
	 * @return true si la contraseña coincide, false de lo contrario.
	 */
	public boolean validatePassword(char[] rawPasswordArray) {
		return PasswordUtils.validatePassword(rawPasswordArray, this.password);
	}

	public boolean setDefaultPassword() {
		// Convertir CUIL a cadena de caracteres
		char[] defaultPasswordArray = String.valueOf(this.cuil).toCharArray();
		try {
			// Establecer la contraseña por defecto cifrada (solo actualiza el objeto Usuario)
			setPassword(defaultPasswordArray);
			return true; // Si el cambio es exitoso, retorna true
		} finally {
			// Limpiar los array con la contraseña por defecto para que no permanezca en memoria
			Arrays.fill(defaultPasswordArray, '\0');
		}
	}


	/**
	 * Cambia la contraseña del usuario, asegurando que se validen las credenciales actuales y que las nuevas contraseñas coincidan.
	 * <p>
	 * Este método se encarga de validar la contraseña actual, verificar que la nueva contraseña coincida con la confirmación,
	 * y posteriormente actualizar la contraseña almacenada con su versión hasheada. Se implementa la limpieza de datos sensibles
	 * como los valores en texto plano de las contraseñas, inmediatamente después de ser utilizados, para reducir el riesgo de
	 * exposición de datos.
	 *
	 * @param currentPassword     La contraseña actual del usuario en formato de arreglo de caracteres (char[]).
	 * @param newPassword         La nueva contraseña propuesta por el usuario en formato de arreglo de caracteres (char[]).
	 * @param confirmNewPassword  Confirmación de la nueva contraseña, ingresada nuevamente por el usuario en formato de arreglo de caracteres (char[]).
	 * @return {@code true} si la contraseña se cambió exitosamente, de lo contrario lanza una excepción si alguna validación falla.
	 * @throws IllegalArgumentException Si la contraseña actual no es correcta o si las nuevas contraseñas no coinciden.
	 *
	 * <p><b>Nota:</b> Los arreglos de caracteres se limpian inmediatamente después de ser utilizados para evitar que los datos
	 * en texto plano queden en la memoria.
	 */
	public boolean changePassword(char[] currentPassword, char[] newPassword, char[] confirmNewPassword) {
		try {
			// Paso 1: Validar la contraseña actual
			if (!validatePassword(currentPassword)) {
				throw new IllegalArgumentException("La contraseña actual no es correcta.");
			}

			// Paso 2: Validar que la nueva contraseña coincide con la confirmación
			if (!Arrays.equals(newPassword, confirmNewPassword)) {
				throw new IllegalArgumentException("Las nuevas contraseñas no coinciden.");
			}

			// Paso 3: Establecer la nueva contraseña cifrada (solo actualiza el objeto Usuario)
			setPassword(newPassword);

			return true; // Si el cambio es exitoso, retorna true
		} finally {
			// Paso 4: Limpiar los arrays de contraseñas para evitar que permanezcan en memoria
			Arrays.fill(currentPassword, '\0');
			Arrays.fill(newPassword, '\0');
			Arrays.fill(confirmNewPassword, '\0');
		}
	}

	/**
	 * Método que indica si el usuario posé un determinado rol.
	 * @param roleClass los roles
	 * @return verdadero o falso, según el usuario tenga el rol o no.
	 */
	public <T extends Role> T getRoleBehavior(Class<T> roleClass) {
		return rolesBehavior.stream()
				.filter(roleClass::isInstance)
				.map(roleClass::cast)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Método que indica si el usuario posé al menos un rol de entre varios dados.
	 * @param tiposUsuario uno o más Enum de tipo TipoUsuario, que contiene los valores posibles.
	 * @return verdaero o falso, según el usuario tenga el rol o no.
	 */
	public boolean hasRole(TipoUsuario... tiposUsuario) {
		if (rolesBehavior == null || rolesBehavior.isEmpty()) {
			return false;
		}
		for (TipoUsuario tipoUsuario : tiposUsuario) {
			Class<? extends Role> roleClass = tipoUsuario.getRoleClass();
			for (Role role : rolesBehavior) {
				if (roleClass.isInstance(role)) {
					return true;
				}
			}
		}
		return false;
	}


	public boolean hasRoleBehavior(Class<? extends Role> roleClass) {
		return rolesBehavior.stream().anyMatch(roleClass::isInstance);
	}


	/**
	 * Método que indica si el usuario posé algún rol o no
	 * El objetivo principal, es poder manejar los casos en los cuales el usuario no tiene cargado ningún rol.
	 * También resulta de utilidad cuando se desea mostrar un elemento a cualquier tipo de usuario,
	 * pero cuidando que tenga al menos un rol configurado.
	 * @return verdadero o false, según el usuario tenga el rol o no
	 */
	public boolean hasAnyRoleData() {
		return rolesData != null && !rolesData.isEmpty();
	}

	public void consultarHorario(){

	}

	/**
	 * Genera un tipo de Reporte
	 * @param reporte un objeto reporte
	 */
	public void generarReporte(int reporte){
		//void
	}

	public void registrarHorasExtra(){
		//void
	}

	public void registrarNovedad(){
		//void
	}

	public void solicitarFC(){
		//void
	}

	/**
	 * Método que verifica si el usuario coincide con el texto de búsqueda.
	 * Compara el texto con CUIL, apellidos, nombres, mail y teléfono.
	 *
	 * @param textoBuscado El texto ingresado por el usuario para buscar.
	 * @return true si hay coincidencia en alguno de los campos, false en caso contrario.
	 */
	public boolean coincideCon(String textoBuscado) {
		if (textoBuscado == null || textoBuscado.isEmpty()) {
			return false;
		}

		// Normalizar el texto buscado
		textoBuscado = normalizar(textoBuscado);

		// Normalizar los campos del usuario
		String cuilStr = String.valueOf(cuil);
		String telStr = String.valueOf(tel);
		String apellidosNorm = normalizar(apellidos);
		String nombresNorm = normalizar(nombres);
		String mailNorm = normalizar(mail);

		// Verificar coincidencias
		return cuilStr.contains(textoBuscado)
				|| (apellidos != null && apellidos.toLowerCase().contains(textoBuscado))
				|| (nombres != null && nombres.toLowerCase().contains(textoBuscado))
				|| (mail != null && mail.toLowerCase().contains(textoBuscado))
				|| telStr.contains(textoBuscado);
	}

	public void assignRoleBehaviors() throws ServiceException {
		this.rolesBehavior.clear();
		for (RoleData roleData : this.rolesData) {
			try {
				TipoUsuario tipoUsuario = TipoUsuario.fromInternalName(roleData.getNombre());
				Class<? extends Role> roleClass = tipoUsuario.getRoleClass();
				Role roleBehavior = roleClass.getDeclaredConstructor(Usuario.class).newInstance(this);
				this.addRoleBehavior(roleBehavior);
			} catch (Exception e) {
				throw new ServiceException("Error al crear instancia de role behavior para " + roleData.getNombre(), e);
			}
		}
	}

}