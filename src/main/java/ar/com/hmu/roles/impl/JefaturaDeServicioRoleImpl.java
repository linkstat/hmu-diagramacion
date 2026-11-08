package ar.com.hmu.roles.impl;

import ar.com.hmu.roles.JefaturaDeServicioRole;
import ar.com.hmu.model.Usuario;

public class JefaturaDeServicioRoleImpl implements JefaturaDeServicioRole {
    private Usuario usuario;

    public JefaturaDeServicioRoleImpl(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public void aprobarFaltas() {
        System.out.println(usuario.getNombres() + " está aprobando faltas.");
        // Implementar la lógica necesaria aquí
    }
}
