package ar.com.hmu.service.notification;

import ar.com.hmu.model.Notificacion;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.NotificacionRepository;
import ar.com.hmu.repository.UsuarioRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Notificar resultado (CU27): cada aviso se registra en la aplicación y,
 * además, se envía por el correo institucional. Si el correo falla, el
 * aviso sigue disponible en la aplicación y {@link #reintentarPendientes}
 * lo vuelve a enviar.
 */
public class NotificacionService {

    private static final String PIE = "\n\n---\nHospital Municipal de Urgencias - Córdoba\n"
            + "Este es un mensaje automático. Por favor no respondas a esta dirección.\n";

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificationService email;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               UsuarioRepository usuarioRepository,
                               NotificationService email) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.email = email;
    }

    /**
     * Registra el aviso y dispara el correo. Nunca propaga errores: una
     * falla al notificar no debe deshacer la decisión que la originó.
     */
    public void notificar(Usuario destinatario, String tipo, String asunto, String cuerpo,
                          String entidad, UUID entidadId) {
        if (destinatario == null || destinatario.getId() == null) {
            return;
        }
        try {
            Notificacion n = new Notificacion(destinatario.getId(), tipo, asunto, cuerpo, entidad, entidadId);
            notificacionRepository.insertar(n);
            enviarCorreo(destinatario, n);
        } catch (SQLException e) {
            System.err.println("NotificacionService: no se pudo registrar el aviso \"" + asunto + "\": "
                    + e.getMessage());
        }
    }

    public void notificar(UUID destinatarioId, String tipo, String asunto, String cuerpo,
                          String entidad, UUID entidadId) {
        if (destinatarioId == null) {
            return;
        }
        try {
            notificar(usuarioRepository.readByUUID(destinatarioId), tipo, asunto, cuerpo, entidad, entidadId);
        } catch (SQLException e) {
            System.err.println("NotificacionService: destinatario inexistente " + destinatarioId);
        }
    }

    /**
     * Recordatorio con tope diario (CU19): se registra y se envía por correo
     * sólo si el usuario no recibió hoy otro del mismo tipo.
     * @return true si se envió.
     */
    public boolean recordarUnaVezPorDia(UUID destinatarioId, String tipo, String asunto, String cuerpo,
                                        String entidad, UUID entidadId) {
        if (destinatarioId == null) return false;
        try {
            if (notificacionRepository.existeHoy(destinatarioId, tipo)) {
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
        notificar(destinatarioId, tipo, asunto, cuerpo, entidad, entidadId);
        return true;
    }

    public List<Notificacion> recientes(Usuario usuario) throws SQLException {
        return notificacionRepository.findByUsuario(usuario.getId(), 50);
    }

    public int contarNoLeidas(Usuario usuario) {
        try {
            return notificacionRepository.contarNoLeidas(usuario.getId());
        } catch (SQLException e) {
            return 0;
        }
    }

    public void marcarLeidas(Usuario usuario) throws SQLException {
        notificacionRepository.marcarLeidas(usuario.getId());
    }

    /** Reenvía los correos que no salieron en los últimos días (CU27 3a). */
    public void reintentarPendientes() {
        try {
            for (Notificacion n : notificacionRepository.findSinEmailEnviado()) {
                Usuario u = usuarioRepository.readByUUID(n.getUsuarioId());
                if (u != null) {
                    enviarCorreo(u, n);
                }
            }
        } catch (SQLException e) {
            System.err.println("NotificacionService: falla al reintentar correos: " + e.getMessage());
        }
    }

    private void enviarCorreo(Usuario destinatario, Notificacion n) {
        email.notify(destinatario, n.getAsunto(), (n.getCuerpo() != null ? n.getCuerpo() : "") + PIE, () -> {
            try {
                notificacionRepository.marcarEnviadaEmail(n.getId());
            } catch (SQLException e) {
                System.err.println("NotificacionService: no se pudo marcar el envío: " + e.getMessage());
            }
        });
    }
}
