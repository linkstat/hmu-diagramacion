package ar.com.hmu.exceptions;

public class DatabaseConnectionException extends RuntimeException {

    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;

    public DatabaseConnectionException(String message, Throwable cause, String dbHost, int dbPort, String dbName, String dbUser) {
        super(message, cause);
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
    }

    public DatabaseConnectionException(Throwable cause, String dbHost, int dbPort, String dbName, String dbUser) {
        super(buildMessage(cause, dbHost, dbPort, dbName, dbUser), cause);
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
    }

    private static String buildMessage(Throwable cause, String dbHost, int dbPort, String dbName, String dbUser) {
        String baseMessage = "Error al conectar a la base de datos '"+ dbName + "' en " + dbHost + ":" + dbPort + " para el usuario '" + dbUser + "'. Revisa el archivo de configuración 'config.yaml'";
        if (cause != null) {
            baseMessage += " Causa: " + cause.getMessage();
        }
        return baseMessage;
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

}
