package ar.com.hmu.service.novedades;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.model.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * Perfil con el que el usuario opera la pantalla de novedades (E4, Tabla 59):
 * define qué novedades ve y qué instancia resuelve. Un usuario con varios
 * roles elige el perfil.
 */
public enum PerfilNovedades {

    AGENTE("Agente"),
    JEFATURA("Jefatura de Servicio"),
    OFICINA_PERSONAL("Oficina de Personal"),
    DIRECCION("Dirección");

    private final String etiqueta;

    PerfilNovedades(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Perfiles del usuario, del de mayor alcance al de menor. */
    public static List<PerfilNovedades> de(Usuario u) {
        List<PerfilNovedades> perfiles = new ArrayList<>();
        if (u.hasRole(TipoUsuario.OFICINADEPERSONAL)) perfiles.add(OFICINA_PERSONAL);
        if (u.hasRole(TipoUsuario.DIRECCION)) perfiles.add(DIRECCION);
        if (u.hasRole(TipoUsuario.JEFATURADESERVICIO)) perfiles.add(JEFATURA);
        perfiles.add(AGENTE);
        return perfiles;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
