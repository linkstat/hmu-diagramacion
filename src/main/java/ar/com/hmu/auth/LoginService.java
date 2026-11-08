package ar.com.hmu.auth;

import java.sql.SQLException;
import java.util.Arrays;

import ar.com.hmu.repository.UsuarioRepository;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.UsuarioService;
import ar.com.hmu.util.PasswordUtils;

/**
 * Servicio que gestiona la lógica de negocio relacionada con el inicio de sesión de usuarios.
 */
public class LoginService {

    private UsuarioService usuarioService;

    public LoginService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Valida las credenciales de un usuario.
     * <p>
     * Si la validación es exitosa y el hash almacenado pertenece a un algoritmo
     * legacy (BCrypt), se re-hashea con Argon2id y se persiste de forma
     * transparente. Una falla en esa persistencia no bloquea el login.
     *
     * @param cuil el CUIL del usuario que intenta iniciar sesión.
     * @param passwordCharArray la contraseña ingresada por el usuario, almacenada como char[].
     * @return true si las credenciales son válidas, false de lo contrario.
     * @throws SQLException si ocurre un error con la conexión a la base de datos.
     */
    public boolean validateUser(long cuil, char[] passwordCharArray) throws SQLException {
        char[] passwordCopy = null;
        try {
            String storedHash = usuarioService.findPasswordByCuil(cuil);
            if (storedHash == null) {
                return false; // Usuario no encontrado
            }

            // PasswordUtils.validatePassword wipea el char[] al terminar; clonamos
            // antes para poder re-hashear sin pedirle de nuevo el password al usuario.
            passwordCopy = passwordCharArray.clone();
            boolean passwordMatches = PasswordUtils.validatePassword(passwordCharArray, storedHash);

            if (passwordMatches && PasswordUtils.needsRehash(storedHash)) {
                try {
                    String newHash = PasswordUtils.hashPassword(passwordCopy);
                    passwordCopy = null; // hashPassword también limpió el clon
                    usuarioService.updatePasswordHash(cuil, newHash);
                } catch (ServiceException e) {
                    // Migración silenciosa: si falla la persistencia del nuevo hash,
                    // el login es igual exitoso. Reintentaremos en el próximo login.
                    System.err.println("Falla en el re-hash transparente para CUIL " + cuil
                            + ": " + e.getMessage());
                }
            }

            return passwordMatches;

        } catch (ServiceException e) {
            throw new RuntimeException("Error al validar el usuario contra la base de datos", e);
        } finally {
            // Garantizar que ambos arrays queden limpios incluso ante errores
            if (passwordCharArray != null) {
                Arrays.fill(passwordCharArray, '\0');
            }
            if (passwordCopy != null) {
                Arrays.fill(passwordCopy, '\0');
            }
        }
    }

    /** Intentos fallidos consecutivos que bloquean la cuenta (RNF01). */
    public static final int MAX_INTENTOS = 5;

    /** Resultado del inicio de sesión (CU01). */
    public enum ResultadoLogin {
        /** Credenciales válidas. */
        CORRECTO,
        /** CUIL inexistente o contraseña incorrecta: mismo mensaje genérico (2a, 2c). */
        CREDENCIALES_INVALIDAS,
        /** Este intento alcanzó el máximo y bloqueó la cuenta (2b). */
        BLOQUEADA_AHORA,
        /** La cuenta ya estaba bloqueada; la desbloquea la OP. */
        BLOQUEADA
    }

    /**
     * CU01 completo: verifica la contraseña, cuenta los intentos fallidos
     * (sólo de cuentas existentes, 2c) y bloquea al quinto consecutivo; un
     * ingreso correcto reinicia el contador. Una cuenta bloqueada no ingresa
     * aunque la contraseña sea correcta.
     */
    public ResultadoLogin autenticar(long cuil, char[] password) throws SQLException, ServiceException {
        UsuarioRepository.EstadoAcceso estado = usuarioService.findEstadoAcceso(cuil);
        if (estado == null) {
            Arrays.fill(password, '\0');
            return ResultadoLogin.CREDENCIALES_INVALIDAS;
        }
        if (estado.bloqueada()) {
            Arrays.fill(password, '\0');
            return ResultadoLogin.BLOQUEADA;
        }
        if (validateUser(cuil, password)) {
            if (estado.intentosFallidos() > 0) {
                usuarioService.reiniciarIntentos(cuil);
            }
            return ResultadoLogin.CORRECTO;
        }
        UsuarioRepository.EstadoAcceso nuevo = usuarioService.registrarIntentoFallido(cuil, MAX_INTENTOS);
        return nuevo != null && nuevo.bloqueada() ? ResultadoLogin.BLOQUEADA_AHORA : ResultadoLogin.CREDENCIALES_INVALIDAS;
    }

    /** La cuenta debe cambiar la contraseña antes de continuar (CU01 3a). */
    public boolean requiereCambioPassword(long cuil) throws ServiceException {
        UsuarioRepository.EstadoAcceso estado = usuarioService.findEstadoAcceso(cuil);
        return estado != null && estado.cambioPasswordRequerido();
    }

    public Usuario getUsuarioByCuil(long cuil) throws ServiceException {
        return usuarioService.findUsuarioByCuil(cuil);
    }

}
