package ar.com.hmu.service.notification;

import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.Usuario;

/**
 * Abstracción para notificaciones disparadas por eventos de los módulos
 * con workflow (memorándums, diagramación de servicios). La implementación
 * concreta es por email ({@code EmailNotificationService}); a futuro pueden
 * sumarse otras (in-app, push, etc.) sin tocar a los services de dominio.
 */
public interface NotificationService {

    /**
     * Envía una notificación al destinatario indicado para el evento dado.
     * La invocación debe ser fire-and-forget: no bloquea ni propaga errores
     * de transporte. La política de reintentos (si la hubiera) es
     * responsabilidad de la implementación.
     *
     * @param destinatario  usuario al que se le notifica.
     * @param evento        tipo de evento que disparó la notificación.
     * @param memo          memo asociado al evento (no debe ser null).
     * @param comentarios   contexto adicional (motivo de rechazo, observación);
     *                      puede ser null si el evento no aplica.
     */
    void notify(Usuario destinatario, TipoEventoMemorandum evento, Memorandum memo, String comentarios);

    /**
     * Envía una notificación por un evento del ciclo de vida de un diagrama
     * de servicio. Mismo contrato fire-and-forget que la sobrecarga de
     * memorándums.
     *
     * @param destinatario    usuario al que se le notifica.
     * @param evento          tipo de evento que disparó la notificación.
     * @param diagrama        diagrama asociado al evento (no debe ser null).
     * @param nombreServicio  nombre del servicio del diagrama, para que el
     *                        mensaje sea legible sin otra consulta.
     * @param comentarios     contexto adicional (motivo de la observación);
     *                        puede ser null si el evento no aplica.
     */
    void notify(Usuario destinatario, TipoEventoDiagrama evento, DiagramaDeServicio diagrama,
                String nombreServicio, String comentarios);

    /**
     * Envía un correo de texto libre (módulos sin plantilla propia, como
     * novedades). Mismo contrato fire-and-forget; {@code alEnviar} se
     * ejecuta sólo si el correo salió, para que el llamador registre el
     * envío (CU27: si falla, se reintenta).
     *
     * @param destinatario usuario al que se le notifica.
     * @param asunto       asunto del correo.
     * @param cuerpo       cuerpo en texto plano.
     * @param alEnviar     callback opcional tras un envío exitoso (puede ser null).
     */
    void notify(Usuario destinatario, String asunto, String cuerpo, Runnable alEnviar);
}
