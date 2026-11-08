package ar.com.hmu.repository;

import java.util.UUID;

/**
 * Usuario de la sesión para la auditoría por disparadores (RNF03). La
 * aplicación de escritorio tiene un único usuario por proceso: se fija al
 * iniciar sesión y se borra al cerrarla. {@link ConexionAuditada} lo copia a
 * la variable {@code app.usuario_id} de cada transacción de escritura.
 */
public final class ContextoAuditoria {

    private static volatile UUID usuarioId;

    private ContextoAuditoria() {
    }

    public static void iniciar(UUID usuario) {
        usuarioId = usuario;
    }

    public static void limpiar() {
        usuarioId = null;
    }

    public static UUID usuarioActual() {
        return usuarioId;
    }
}
