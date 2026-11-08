package ar.com.hmu.service.notification;

import ar.com.hmu.config.SmtpConfig;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.Usuario;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementación de {@link NotificationService} que envía mails vía SMTP
 * relay. Async fire-and-forget: el envío corre en un pool dedicado de
 * 2 threads y los errores se loguean por stderr sin propagar.
 *
 * <p>Si {@link SmtpConfig#isNoop()} (host vacío, o relay con autenticación
 * sin clave), todas las llamadas a {@link #notify} se ignoran. Útil en dev y
 * tests. Sin clave se avisa una vez por stderr al crear el servicio.</p>
 *
 * <p>Si el destinatario no tiene mail configurado, la notificación se
 * descarta (log a stderr).</p>
 *
 * <p>El cuerpo del mail es texto plano, no HTML. Las plantillas son strings
 * hardcoded; sin engine de templating en pase 1.</p>
 */
public class EmailNotificationService implements NotificationService {

    private final SmtpConfig config;
    private final ExecutorService executor;

    public EmailNotificationService(SmtpConfig config) {
        this.config = config;
        if (config.faltaClave()) {
            System.err.println("EmailNotificationService: smtp.password vacío; no se envía correo "
                    + "(los avisos se ven en la aplicación).");
        }
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "aromito-email");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void notify(Usuario destinatario, TipoEventoMemorandum evento, Memorandum memo, String comentarios) {
        if (config.isNoop()) {
            return;
        }
        if (destinatario == null || destinatario.getMail() == null || destinatario.getMail().isBlank()) {
            System.err.println("EmailNotificationService: destinatario sin mail, salteando notificación de " + evento);
            return;
        }
        if (memo == null) {
            System.err.println("EmailNotificationService: memo null, salteando notificación de " + evento);
            return;
        }

        executor.submit(() -> {
            try {
                enviar(destinatario.getMail(), evento, memo, comentarios);
            } catch (Exception e) {
                System.err.println("EmailNotificationService: falla enviando mail (" + evento
                        + ") a " + destinatario.getMail() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void notify(Usuario destinatario, TipoEventoDiagrama evento, DiagramaDeServicio diagrama,
                       String nombreServicio, String comentarios) {
        if (config.isNoop()) {
            return;
        }
        if (destinatario == null || destinatario.getMail() == null || destinatario.getMail().isBlank()) {
            System.err.println("EmailNotificationService: destinatario sin mail, salteando notificación de " + evento);
            return;
        }
        if (diagrama == null) {
            System.err.println("EmailNotificationService: diagrama null, salteando notificación de " + evento);
            return;
        }

        executor.submit(() -> {
            try {
                enviarMail(destinatario.getMail(),
                        asuntoPara(evento, diagrama, nombreServicio),
                        cuerpoPara(evento, diagrama, nombreServicio, comentarios));
            } catch (Exception e) {
                System.err.println("EmailNotificationService: falla enviando mail (" + evento
                        + ") a " + destinatario.getMail() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void notify(Usuario destinatario, String asunto, String cuerpo, Runnable alEnviar) {
        if (config.isNoop()) {
            return;
        }
        if (destinatario == null || destinatario.getMail() == null || destinatario.getMail().isBlank()) {
            System.err.println("EmailNotificationService: destinatario sin mail, salteando \"" + asunto + "\"");
            return;
        }

        executor.submit(() -> {
            try {
                enviarMail(destinatario.getMail(), asunto, cuerpo);
                if (alEnviar != null) {
                    alEnviar.run();
                }
            } catch (Exception e) {
                System.err.println("EmailNotificationService: falla enviando mail (\"" + asunto
                        + "\") a " + destinatario.getMail() + ": " + e.getMessage());
            }
        });
    }

    /** Cierra el pool de envío. Llamar al apagar la app. */
    public void shutdown() {
        executor.shutdown();
    }

    // ============================================================
    // Internos
    // ============================================================

    private void enviar(String mailTo, TipoEventoMemorandum evento, Memorandum memo, String comentarios)
            throws MessagingException {
        enviarMail(mailTo, asuntoPara(evento, memo), cuerpoPara(evento, memo, comentarios));
    }

    /** Transporte común: arma la sesión SMTP y despacha un mail de texto plano. */
    private void enviarMail(String mailTo, String asunto, String cuerpo) throws MessagingException {
        Properties props = buildProps();

        Session session;
        if (config.isAuth()) {
            session = Session.getInstance(props, new jakarta.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(parseFrom());
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(mailTo));
        msg.setSubject(asunto);
        msg.setText(cuerpo, "UTF-8");

        Transport.send(msg);
    }

    private Properties buildProps() {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        if (config.isAuth()) {
            props.put("mail.smtp.auth", "true");
        }
        if (config.isTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return props;
    }

    private InternetAddress parseFrom() throws MessagingException {
        String from = config.getFrom();
        if (from == null || from.isBlank()) {
            try {
                return new InternetAddress("noreply@aromito.local", "Aromito");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new MessagingException("No se pudo construir la dirección 'from' por defecto", e);
            }
        }
        return new InternetAddress(from);
    }

    private String asuntoPara(TipoEventoMemorandum evento, Memorandum memo) {
        String asuntoMemo = memo.getAsunto() != null ? memo.getAsunto() : "(sin asunto)";
        switch (evento) {
            case MEMO_RECIBIDO:
                return "Recibiste un memorándum: " + asuntoMemo;
            case MEMO_ENVIADO_CONFIRMACION:
                return "Enviaste un memorándum: " + asuntoMemo;
            case AUTORIZACION_REQUERIDA:
                return "Pendiente de autorizar: " + asuntoMemo;
            case MEMO_AUTORIZADO:
                return "Tu memorándum fue autorizado: " + asuntoMemo;
            case MEMO_RECHAZADO:
                return "Tu memorándum fue rechazado: " + asuntoMemo;
            case MEMO_OBSERVADO:
                return "Tu memorándum requiere correcciones: " + asuntoMemo;
            default:
                return "Memorándum: " + asuntoMemo;
        }
    }

    private String cuerpoPara(TipoEventoMemorandum evento, Memorandum memo, String comentarios) {
        StringBuilder sb = new StringBuilder();
        sb.append("Aromito :: Sistema de Gestión de Ausentismo Hospitalario\n");
        sb.append("Hospital Municipal de Urgencias - Córdoba\n");
        sb.append("=========================================================\n\n");

        switch (evento) {
            case MEMO_RECIBIDO:
                sb.append("Recibiste un nuevo memorándum.\n\n");
                break;
            case MEMO_ENVIADO_CONFIRMACION:
                sb.append("Tu memorándum fue registrado en el sistema.\n\n");
                break;
            case AUTORIZACION_REQUERIDA:
                sb.append("Tienes un memorándum esperando tu autorización.\n\n");
                break;
            case MEMO_AUTORIZADO:
                sb.append("Tu memorándum fue autorizado y enviado a sus destinatarios.\n\n");
                break;
            case MEMO_RECHAZADO:
                sb.append("Tu memorándum fue rechazado.\n");
                if (comentarios != null && !comentarios.isBlank()) {
                    sb.append("Motivo: ").append(comentarios).append("\n");
                }
                sb.append("\n");
                break;
            case MEMO_OBSERVADO:
                sb.append("Tu memorándum requiere correcciones antes de poder ser enviado.\n");
                if (comentarios != null && !comentarios.isBlank()) {
                    sb.append("Observaciones: ").append(comentarios).append("\n");
                }
                sb.append("\n");
                break;
        }

        sb.append("Asunto: ").append(memo.getAsunto() != null ? memo.getAsunto() : "(sin asunto)").append("\n");
        sb.append("\n");
        sb.append("Para gestionar este memorándum, ingresá a Aromito.\n");
        sb.append("\n");
        sb.append("---\n");
        sb.append("Este es un mensaje automático. Por favor no respondas a esta dirección.\n");
        return sb.toString();
    }

    // ============================================================
    // Plantillas de diagramas de servicio
    // ============================================================

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String periodoDe(DiagramaDeServicio diagrama) {
        String desde = diagrama.getFechaInicio() != null
                ? diagrama.getFechaInicio().format(FECHA_CORTA) : "?";
        String hasta = diagrama.getFechaFin() != null
                ? diagrama.getFechaFin().format(FECHA_CORTA) : "?";
        return desde + " al " + hasta;
    }

    private String asuntoPara(TipoEventoDiagrama evento, DiagramaDeServicio diagrama, String nombreServicio) {
        String servicio = (nombreServicio != null && !nombreServicio.isBlank())
                ? nombreServicio : "(servicio)";
        switch (evento) {
            case DIAGRAMA_PENDIENTE_APROBACION:
                return "Diagrama pendiente de aprobación: " + servicio + " (" + periodoDe(diagrama) + ")";
            case DIAGRAMA_APROBADO:
                return "Diagrama aprobado: " + servicio + " (" + periodoDe(diagrama) + ")";
            case DIAGRAMA_OBSERVADO:
                return "Diagrama observado: " + servicio + " (" + periodoDe(diagrama) + ")";
            default:
                return "Diagrama de servicio: " + servicio;
        }
    }

    private String cuerpoPara(TipoEventoDiagrama evento, DiagramaDeServicio diagrama,
                              String nombreServicio, String comentarios) {
        StringBuilder sb = new StringBuilder();
        sb.append("Aromito :: Sistema de Gestión de Ausentismo Hospitalario\n");
        sb.append("Hospital Municipal de Urgencias - Córdoba\n");
        sb.append("=========================================================\n\n");

        switch (evento) {
            case DIAGRAMA_PENDIENTE_APROBACION:
                sb.append("Hay un diagrama de servicio esperando aprobación de la Oficina de Personal.\n\n");
                break;
            case DIAGRAMA_APROBADO:
                sb.append("El diagrama de servicio fue aprobado. A partir de ahora es inmutable:\n");
                sb.append("los cambios se gestionan como novedades (CH/CG) de cada agente.\n\n");
                break;
            case DIAGRAMA_OBSERVADO:
                sb.append("El diagrama de servicio fue observado y requiere correcciones\n");
                sb.append("antes de poder ser aprobado.\n");
                if (comentarios != null && !comentarios.isBlank()) {
                    sb.append("Observaciones: ").append(comentarios).append("\n");
                }
                sb.append("\n");
                break;
        }

        sb.append("Servicio: ").append(nombreServicio != null && !nombreServicio.isBlank()
                ? nombreServicio : "(sin nombre)").append("\n");
        sb.append("Período: ").append(periodoDe(diagrama)).append("\n");
        sb.append("\n");
        sb.append("Para gestionar este diagrama, ingresá a Aromito.\n");
        sb.append("\n");
        sb.append("---\n");
        sb.append("Este es un mensaje automático. Por favor no respondas a esta dirección.\n");
        return sb.toString();
    }
}
