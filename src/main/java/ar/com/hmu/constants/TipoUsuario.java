package ar.com.hmu.constants;

import ar.com.hmu.roles.Role;
import ar.com.hmu.roles.impl.DireccionRoleImpl;
import ar.com.hmu.roles.impl.EmpleadoRoleImpl;
import ar.com.hmu.roles.impl.JefaturaDeServicioRoleImpl;
import ar.com.hmu.roles.impl.OficinaDePersonalRoleImpl;

public enum TipoUsuario {
    EMPLEADO("Empleado", "Empleado", EmpleadoRoleImpl.class),
    JEFATURADESERVICIO("JefaturaDeServicio", "Jefatura de Servicio", JefaturaDeServicioRoleImpl.class),
    OFICINADEPERSONAL("OficinaDePersonal", "Oficina de Personal", OficinaDePersonalRoleImpl.class),
    DIRECCION("Direccion", "Directivo", DireccionRoleImpl.class);

    private final String internalName;
    private final String displayName;
    private final Class<? extends Role> roleClass;

    TipoUsuario(String internalName, String displayName, Class<? extends Role> roleClass) {
        this.internalName = internalName;
        this.displayName = displayName;
        this.roleClass = roleClass;
    }

    public String getInternalName() {
        return internalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Class<? extends Role> getRoleClass() {
        return roleClass;
    }

    // Método de conversión para obtener el Enum a partir de la cadena de la base de datos
    public static TipoUsuario fromInternalName(String internalName) {
        for (TipoUsuario tipo : TipoUsuario.values()) {
            if (tipo.getInternalName().equalsIgnoreCase(internalName)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de usuario desconocido: " + internalName);
    }

    @Override
    public String toString() {
        return displayName;  // Por defecto, toString() mostrará el texto alternativo
    }
}
