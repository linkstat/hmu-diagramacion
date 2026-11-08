package ar.com.hmu.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Hashing y verificación de contraseñas.
 * <p>
 * Algoritmo por defecto para nuevos hashes: <b>Argon2id</b> con el perfil
 * recomendado por OWASP (m=19456 KiB, t=2 iteraciones, p=1).
 * <p>
 * Compatibilidad: durante la migración se aceptan también hashes BCrypt
 * legacy ($2a$/$2b$/$2y$). El método {@link #needsRehash(String)} permite
 * al caller decidir si debe re-hashear el password con Argon2id luego de
 * un login exitoso (migración transparente).
 * <p>
 * Una vez que todos los hashes en BD sean Argon2id, conviene retirar la
 * dependencia spring-security-core.
 */
public final class PasswordUtils {

    // Perfil recomendado por OWASP para Argon2id (vigente sept 2024)
    private static final int ARGON2_ITERATIONS = 2;
    private static final int ARGON2_MEMORY_KIB = 19456;
    private static final int ARGON2_PARALLELISM = 1;

    private static final Argon2 argon2 =
            Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final BCryptPasswordEncoder bcryptEncoder = new BCryptPasswordEncoder();

    private PasswordUtils() {
        // utility class
    }

    /**
     * Genera un hash Argon2id de la contraseña proporcionada.
     * <p>
     * El array de caracteres se limpia (zero-fill) tras el hashing.
     *
     * @param rawPasswordArray contraseña en claro. Se sobrescribe con ceros antes de retornar.
     * @return hash en formato PHC ({@code $argon2id$v=19$m=...,t=...,p=...$<salt>$<hash>}).
     */
    public static String hashPassword(char[] rawPasswordArray) {
        try {
            return argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KIB, ARGON2_PARALLELISM, rawPasswordArray);
        } finally {
            argon2.wipeArray(rawPasswordArray);
        }
    }

    /**
     * Verifica una contraseña contra un hash almacenado.
     * <p>
     * Detecta automáticamente el algoritmo por el prefijo del hash:
     * <ul>
     *   <li>{@code $argon2id$}, {@code $argon2i$}, {@code $argon2d$} → Argon2.</li>
     *   <li>{@code $2a$}, {@code $2b$}, {@code $2y$} → BCrypt (legacy).</li>
     * </ul>
     * Cualquier otro formato devuelve {@code false}.
     * <p>
     * El array se limpia tras la verificación.
     *
     * @param rawPasswordArray contraseña en claro. Se sobrescribe con ceros antes de retornar.
     * @param storedHash hash previamente almacenado.
     * @return {@code true} si la contraseña coincide.
     */
    public static boolean validatePassword(char[] rawPasswordArray, String storedHash) {
        try {
            if (storedHash == null || storedHash.isEmpty()) {
                return false;
            }
            if (storedHash.startsWith("$argon2id$")
                    || storedHash.startsWith("$argon2i$")
                    || storedHash.startsWith("$argon2d$")) {
                return argon2.verify(storedHash, rawPasswordArray);
            }
            if (storedHash.startsWith("$2a$")
                    || storedHash.startsWith("$2b$")
                    || storedHash.startsWith("$2y$")) {
                // BCrypt requiere String; mejor esfuerzo de limpieza (el GC se encarga del intermedio)
                String rawPassword = new String(rawPasswordArray);
                return bcryptEncoder.matches(rawPassword, storedHash);
            }
            return false;
        } finally {
            argon2.wipeArray(rawPasswordArray);
        }
    }

    /**
     * Indica si un hash debería migrarse al algoritmo actual (Argon2id).
     * <p>
     * Útil para implementar migración silenciosa en el flujo de login: si el
     * hash es BCrypt legacy y el usuario se autentica correctamente, regenerar
     * el hash con Argon2id usando el password en claro y persistirlo.
     *
     * @param storedHash hash existente en la base de datos.
     * @return {@code true} si el hash NO es Argon2id.
     */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        return !storedHash.startsWith("$argon2id$");
    }
}
