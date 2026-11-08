package ar.com.hmu.factory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.*;
import ar.com.hmu.service.RoleService;

/**
 * Clase {@code UsuarioFactory} que se encarga de crear instancias de las subclases de {@link Usuario}.
 * <p>
 * Esta clase sigue el <i>Patrón <b>Factory</b></i> para instanciar el objeto adecuado del tipo {@link Usuario}
 * dependiendo de la información proporcionada en un {@link ResultSet}. Facilita la lógica de creación
 * de diferentes tipos de usuarios basados en el tipo especificado en la base de datos.
 * <p>
 * Es utilizada principalmente para convertir los datos almacenados en la base de datos en objetos del
 * modelo que puedan ser manipulados por la aplicación.
 * </p>
 */
public class UsuarioFactory {

    private RoleService roleService;

    public UsuarioFactory(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * Crea una instancia de {@link Usuario} basada en la información proporcionada en un {@link ResultSet}.
     * <p>
     * Asigna los valores comunes de {@code Usuario} desde el {@code ResultSet} a la instancia creada
     * y carga sus roles desde la tabla {@code AsignacionRol} (el polimorfismo se resuelve por roles,
     * no por una columna discriminadora).
     *
     * @param resultSet el {@link ResultSet} que contiene la información del usuario desde la base de datos.
     *                  Se espera que esté posicionado en una fila válida.
     * @return una instancia específica de {@link Usuario} con los valores asignados desde el {@code ResultSet}.
     * @throws SQLException si ocurre un error al acceder al {@code ResultSet}.
     * @throws IllegalArgumentException si el {@code tipoUsuario} no coincide con un tipo conocido.
     */
    public Usuario createUsuario(ResultSet resultSet) throws SQLException, ServiceException {
        Usuario usuario = new Usuario();

        usuario.setId(resultSet.getObject("id", UUID.class));
        usuario.setFechaAlta(resultSet.getDate("fechaAlta").toLocalDate());
        usuario.setEstado(resultSet.getBoolean("estado"));
        usuario.setCuil(resultSet.getLong("cuil"));
        usuario.setApellidos(resultSet.getString("apellidos"));
        usuario.setNombres(resultSet.getString("nombres"));
        usuario.setSexo(Sexo.valueOf(resultSet.getString("sexo").toUpperCase()));
        usuario.setMail(resultSet.getString("mail"));
        usuario.setTel(resultSet.getLong("tel"));

        String passwordHash = resultSet.getString("passwordHash");
        if (passwordHash != null && !passwordHash.isEmpty()) {
            usuario.setPasswordHash(passwordHash);
        }

        // profile_image es BYTEA; getBytes() devuelve byte[] directo
        byte[] profileImage = resultSet.getBytes("profileImage");
        if (profileImage != null && profileImage.length > 0) {
            usuario.setProfileImage(profileImage);
        }

        usuario.setDomicilioId(resultSet.getObject("domicilioID", UUID.class));
        usuario.setCargoId(resultSet.getObject("cargoID", UUID.class));
        usuario.setServicioId(resultSet.getObject("servicioID", UUID.class));

        Set<RoleData> rolesData = obtenerRolesDelUsuario(usuario.getId());
        usuario.setRolesData(rolesData);

        usuario.assignRoleBehaviors();

        return usuario;
    }


    /**
     * Método para obtener los roles del usuario desde la base de datos
     * @param usuarioId id de usuario
     * @return un listado de roles de usuario
     * @throws SQLException excepciones de SQL
     * @throws ServiceException excepciones personalizadas ServiceException
     */
    private Set<RoleData> obtenerRolesDelUsuario(UUID usuarioId) throws SQLException, ServiceException {
        return roleService.findRolesByUsuarioId(usuarioId);
    }

}
