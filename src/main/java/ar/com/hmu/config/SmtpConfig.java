package ar.com.hmu.config;

/**
 * Configuración SMTP para envío de notificaciones por email.
 *
 * <p>Mapea la sección {@code smtp:} de config.yaml. Cuando {@code host}
 * está vacío, el {@code EmailNotificationService} se comporta como no-op
 * silencioso (útil para entornos sin relay SMTP, p.ej. tests o dev local).
 * También es no-op si el relay pide autenticación y la clave está vacía:
 * los avisos quedan en la aplicación y no se intenta el envío.</p>
 */
public class SmtpConfig {

    private String host = "";
    private int port = 25;
    private String from = "";
    private boolean auth = false;
    private boolean tls = false;
    private String username = "";
    private String password = "";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host != null ? host : "";
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from != null ? from : "";
    }

    public boolean isAuth() {
        return auth;
    }

    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    public boolean isTls() {
        return tls;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username : "";
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password != null ? password : "";
    }

    /** {@code true} cuando el servicio de email debe operar como no-op. */
    public boolean isNoop() {
        return host == null || host.isBlank() || faltaClave();
    }

    /** {@code true} si hay relay configurado que pide autenticación pero sin clave. */
    public boolean faltaClave() {
        return host != null && !host.isBlank() && auth && (password == null || password.isBlank());
    }
}
