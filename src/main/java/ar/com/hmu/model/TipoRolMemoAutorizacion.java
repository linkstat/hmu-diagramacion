package ar.com.hmu.model;

/**
 * Tipo de rol asociado a una fila de {@code Memorandum_Autorizacion},
 * persistido como ENUM PostgreSQL {@code tipo_rol_memo_autorizacion}.
 *
 * Indica desde qué rol institucional se requiere o se otorgó la
 * autorización del memorándum. Se distingue de {@link ar.com.hmu.constants.TipoUsuario}
 * porque este enum incluye el valor genérico USUARIO (cuando la autorización
 * recae sobre un usuario específico sin importar su rol concreto, p.ej. el
 * encargado actual del servicio del remitente).
 */
public enum TipoRolMemoAutorizacion {
    JEFATURADESERVICIO,
    OFICINADEPERSONAL,
    DIRECCION,
    USUARIO
}
