package ar.com.hmu.constants;

public class DatabaseConnectorStatus {

    public static final String FUNCTIONAL_MSG = "Servidor en línea y funcional.";
    public static final String FUNCTIONAL_COLOR = "green";
    public static final String FUNCTIONAL_ICON = "/images/serverStatus_icon_green_ok.png";

    public static final String AUTH_PROBLEM_MSG = "Problema de autenticación con la base de datos.";
    public static final String AUTH_PROBLEM_COLOR = "orange";
    public static final String AUTH_PROBLEM_ICON = "/images/serverStatus_icon_blue_question.png";

    public static final String DB_SERVICE_PROBLEM_MSG = "Servicio de base de datos no disponible.";
    public static final String DB_SERVICE_PROBLEM_COLOR = "red";
    public static final String DB_SERVICE_PROBLEM_ICON = "/images/serverStatus_icon_orange_warning.png";

    public static final String HOST_UNREACHABLE_MSG = "Servidor fuera de línea: No se puede alcanzar el host";
    public static final String HOST_UNREACHABLE_COLOR = "red";
    public static final String HOST_UNREACHABLE_ICON = "/images/serverStatus_icon_red_error.png";

    public static final String UNREACHABLE_SERVER_MSG = "Servidor completamente fuera de línea.";
    public static final String UNREACHABLE_SERVER_COLOR = "red";
    public static final String UNREACHABLE_SERVER_ICON = "/images/serverStatus_icon_red_error.png";

    public static final String ICON_LOAD_ERR = "Error al cargar el icono de estado del servidor: ";
    public static final String ICON_LOAD_ERR_ICON = "/images/icon_circle_blue_question_52x52.png";

    public static final String UNKNOWN_ERROR_MSG = "Error desconocido en el servidor.";
    public static final String UNKNOWN_ERROR_COLOR = "red";
    public static final String UNKNOWN_ERROR_ICON = "/images/icon_circle_blue_question_52x52.png";
    public static final String UNKNOWN_ERROR_STYLE = "-fx-text-fill: red;";
    public static final String UNKNOWN_ERROR_ERR = "Error desconocido: ";

    public static final String NULL_DB_CONNECTOR_MSG = "Error al inicializar la conexión al servidor.";
    public static final String NULL_DB_CONNECTOR_STYLE = "-fx-text-fill: red;";
    public static final String NULL_DB_CONNECTOR_ICON = "/images/icon_circle_red_error_52x52.png";

}

