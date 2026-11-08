package ar.com.hmu.service;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javafx.scene.image.Image;

import ar.com.hmu.model.*;

/**
 * Servicio que gestiona la lógica del menú principal en mosaico.
 *
 * Esta clase maneja la lógica relacionada con la personalización del menú
 * según el tipo de usuario, proporcionando datos específicos como los módulos
 * habilitados o la información de la sesión actual.
 */
public class MainMenuMosaicoService {

    private Usuario usuarioActual;

    /**
     * Constructor que inicializa el servicio con el usuario actual.
     *
     * @param usuario El usuario que ha iniciado sesión.
     */
    public MainMenuMosaicoService(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    /**
     * Devuelve el nombre completo del usuario actual, en formato "Apellidos, Nombres".
     *
     * @return El/los apellido/s y nombre/s del usuario.
     */
    public String getUsuarioFullName() {
        return usuarioActual.getApellidos() + ", " + usuarioActual.getNombres();
    }

    /**
     * Devuelve el nombre del servicio al que pertenece el usuario actual.
     *
     * @return El nombre del servicio del usuario.
     */
    public String getServicioNombre() {
        Servicio servicio = usuarioActual.getServicio();
        return servicio != null ? servicio.getNombre() : "Sin servicio asignado";
    }

    /**
     * Obtiene la imagen de perfil del usuario.
     * <p>
     * Este método devuelve la imagen de perfil del usuario en forma de una instancia de {@link Image}.
     * Si el usuario no tiene una imagen de perfil almacenada, se devuelve una imagen predeterminada.
     * Esta lógica asegura que siempre haya una imagen visible en la interfaz, incluso si el usuario
     * no ha configurado una imagen personalizada.
     *
     * @return una instancia de {@link Image} con la imagen de perfil del usuario si existe, o una imagen por defecto si no la hay.
     */
    public Image getProfileImage() {
        // Solo actualizar la imagen si el usuario tiene una imagen de perfil personalizada
        byte[] profileImageBytes = usuarioActual.getProfileImage();
        if (profileImageBytes != null && profileImageBytes.length > 0) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(profileImageBytes);
            Image profileImage = new Image(inputStream);
            return new Image(inputStream);
        }
        //return null; // Si no tiene imagen personalizada, retornamos null.
        return new Image(getClass().getResourceAsStream("/images/loginImage.png"));
    }

    // Métodos adicionales para obtener la información de la conexión
    /**
     * Obtiene la información de conexión actual, como la fecha y hora de inicio de sesión.
     */
    public String getCurrentConnDateTime() {
        return ar.com.hmu.util.Reloj.ahora().format(java.time.format.DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss"));
    }

    /**
     * Obtiene la información de conexión actual, como el nombre del host.
     */
    public String getCurrentConnHostname() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            return inetAddress.getHostName();
        } catch (UnknownHostException e) {
            System.err.println("No se pudo obtener el nombre del host: " + e.getMessage());
            return "Desconocido";
        }
    }

    /**
     * Obtiene la información de conexión actual, como la dirección IP.
     */
    public String getCurrentConnIPAddress() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            return inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("No se pudo obtener la dirección IP: " + e.getMessage());
            return "Desconocida";
        }
    }


    /**
     * Proporciona el cargo del usuario actual.
     *
     * @return El cargo del usuario actual como String.
     */
    public String getCargoUsuario() {
        Cargo cargo = usuarioActual.getCargo();
        if (cargo == null) {
            return "0000";
        } else {
            return usuarioActual.getCargo().getNumero().toString();
        }
    }


}
