package ar.com.hmu.roles.impl;

import ar.com.hmu.roles.OficinaDePersonalRole;
import ar.com.hmu.model.Usuario;

public class OficinaDePersonalRoleImpl implements OficinaDePersonalRole {
    private Usuario usuario;

    public OficinaDePersonalRoleImpl(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public void gestionarContrataciones() {
        System.out.println(usuario.getNombres() + " está gestionando contrataciones.");
        // Implementar la lógica necesaria aquí
    }
}
