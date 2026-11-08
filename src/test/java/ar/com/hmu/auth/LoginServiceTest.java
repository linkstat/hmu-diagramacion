package ar.com.hmu.auth;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.service.UsuarioService;
import ar.com.hmu.util.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link LoginService}.
 *
 * Usan {@link UsuarioService} mockeado y hashes Argon2id reales generados
 * con {@link PasswordUtils}, para ejercitar la integración con el hashing
 * sin necesidad de base de datos.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final long CUIL_VALIDO = 20123456789L;
    private static final String PASSWORD = "Secreto123!";

    @Mock
    private UsuarioService usuarioService;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(usuarioService);
    }

    @Test
    void validateUser_devuelveFalseSiElCuilNoExiste() throws SQLException, ServiceException {
        when(usuarioService.findPasswordByCuil(CUIL_VALIDO)).thenReturn(null);

        boolean ok = loginService.validateUser(CUIL_VALIDO, PASSWORD.toCharArray());

        assertThat(ok).isFalse();
        verify(usuarioService, never()).updatePasswordHash(anyLong(), anyString());
    }

    @Test
    void validateUser_devuelveTrueConPasswordCorrecto() throws SQLException, ServiceException {
        String hashArgon = PasswordUtils.hashPassword(PASSWORD.toCharArray());
        when(usuarioService.findPasswordByCuil(CUIL_VALIDO)).thenReturn(hashArgon);

        boolean ok = loginService.validateUser(CUIL_VALIDO, PASSWORD.toCharArray());

        assertThat(ok).isTrue();
        // Hash ya en Argon2id: no debe re-hashear
        verify(usuarioService, never()).updatePasswordHash(anyLong(), anyString());
    }

    @Test
    void validateUser_devuelveFalseConPasswordIncorrecto() throws SQLException, ServiceException {
        String hashArgon = PasswordUtils.hashPassword(PASSWORD.toCharArray());
        when(usuarioService.findPasswordByCuil(CUIL_VALIDO)).thenReturn(hashArgon);

        boolean ok = loginService.validateUser(CUIL_VALIDO, "OtroPassword".toCharArray());

        assertThat(ok).isFalse();
        verify(usuarioService, never()).updatePasswordHash(anyLong(), anyString());
    }

    @Test
    void validateUser_limpiaElPasswordCharArrayDespuesDeUsarlo() throws SQLException, ServiceException {
        when(usuarioService.findPasswordByCuil(CUIL_VALIDO)).thenReturn(null);
        char[] password = PASSWORD.toCharArray();

        loginService.validateUser(CUIL_VALIDO, password);

        // El finally de LoginService debe haber wipeado el array por seguridad
        for (char c : password) {
            assertThat(c).isEqualTo('\0');
        }
    }
}
