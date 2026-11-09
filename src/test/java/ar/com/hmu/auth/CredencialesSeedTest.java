package ar.com.hmu.auth;

import ar.com.hmu.util.PasswordUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que cada credencial documentada en el README coincida con el hash
 * Argon2id del seed correspondiente de {@code sql/}. Si cambia una contraseña
 * de prueba o un hash, este test obliga a actualizar ambos lados.
 *
 * <p>Usuarios documentados: Medina (OP), García (DH) y Valdez (agente, con
 * contraseña = CUIL para demostrar el cambio obligatorio del primer ingreso)
 * se insertan en {@code 02_datos_prueba.sql}; Paz (JS) y Moreno (agente)
 * reciben el hash de Medina por subconsulta en el escenario de demostración,
 * por lo que basta verificar que esas copias sigan presentes.</p>
 */
class CredencialesSeedTest {

    private static final String PASSWORD_COMUN = "HMU.2026";
    private static final String COPIA_DE_MEDINA = "(SELECT passwordHash FROM Usuario WHERE cuil = 27111122277)";

    private static String leer(String archivo) throws IOException {
        return Files.readString(Path.of("sql", archivo));
    }

    /** Extrae el hash Argon2id de la línea del seed que contiene el CUIL dado. */
    private static String hashDe(String sql, long cuil) {
        Pattern p = Pattern.compile(".*" + cuil + ".*?(\\$argon2id\\$[^']+)'.*");
        for (String linea : sql.split("\n")) {
            Matcher m = p.matcher(linea);
            if (m.matches()) {
                return m.group(1);
            }
        }
        throw new AssertionError("No se encontró un hash Argon2id en la línea del CUIL " + cuil);
    }

    @Test
    void medinaYGarciaEntranConLaPasswordComun() throws IOException {
        String sql = leer("02_datos_prueba.sql");
        for (long cuil : new long[]{27111122277L, 20111111122L}) {
            String hash = hashDe(sql, cuil);
            assertThat(PasswordUtils.validatePassword(PASSWORD_COMUN.toCharArray(), hash))
                    .as("la contraseña documentada debe validar contra el hash del CUIL " + cuil)
                    .isTrue();
        }
    }

    @Test
    void valdezEntraConSuCuilYNoConLaPasswordComun() throws IOException {
        String hash = hashDe(leer("02_datos_prueba.sql"), 24111144499L);
        assertThat(PasswordUtils.validatePassword("24111144499".toCharArray(), hash))
                .as("Valdez entra con su CUIL (dispara el cambio obligatorio)")
                .isTrue();
        assertThat(PasswordUtils.validatePassword(PASSWORD_COMUN.toCharArray(), hash))
                .as("Valdez no debe tener la contraseña común")
                .isFalse();
    }

    @Test
    void pazYMorenoCopianElHashDeMedinaEnElEscenario() throws IOException {
        assertThat(leer("03_escenario_demo_base.sql"))
                .as("Paz (JS de Guardia Central) copia el hash de Medina")
                .contains(COPIA_DE_MEDINA);
        assertThat(leer("03_escenario_demo.sql"))
                .as("Moreno (agente demo) copia el hash de Medina")
                .contains(COPIA_DE_MEDINA);
    }
}
