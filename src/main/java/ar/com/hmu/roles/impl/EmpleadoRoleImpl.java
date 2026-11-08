package ar.com.hmu.roles.impl;

import ar.com.hmu.roles.EmpleadoRole;
import ar.com.hmu.model.Usuario;

public class EmpleadoRoleImpl implements EmpleadoRole {
    private Usuario usuario;

    public EmpleadoRoleImpl(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public void pedirFaltaJustificada() {
        System.out.println(usuario.getNombres() + " está pidiendo una falta justificada.");
        // Implementar la lógica necesaria aquí
    }
}
