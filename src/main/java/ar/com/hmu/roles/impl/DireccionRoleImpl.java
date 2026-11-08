package ar.com.hmu.roles.impl;

import ar.com.hmu.roles.DireccionRole;
import ar.com.hmu.model.Usuario;

public class DireccionRoleImpl implements DireccionRole {
    private Usuario usuario;

    public DireccionRoleImpl(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public void tomarDecisionesEstrategicas() {
        System.out.println(usuario.getNombres() + " está tomando decisiones estratégicas.");
        // Implementar la lógica necesaria aquí
    }
}
