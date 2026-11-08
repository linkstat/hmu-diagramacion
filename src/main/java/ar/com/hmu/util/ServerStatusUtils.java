package ar.com.hmu.util;

import java.util.Objects;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.util.Duration;

import ar.com.hmu.constants.DatabaseConnectorStatus;
import ar.com.hmu.repository.DatabaseConnector;
import ar.com.hmu.repository.ServerStatus;

public class ServerStatusUtils {

    private Timeline serverCheckTimeline;
    private int checkIntervalInSeconds = 4;  // Intervalo inicial de 4 segundos
    private DatabaseConnector databaseConnector;
    private Label statusLabel;
    private ImageView statusIcon;


    public ServerStatusUtils(DatabaseConnector databaseConnector, Label statusLabel, ImageView statusIcon) {
        this.databaseConnector = databaseConnector;
        this.statusLabel = statusLabel;
        this.statusIcon = statusIcon;
    }


    /**
     * Inicia un chequeo periódico del estado del servidor con un intervalo dinámico.
     *
     */
    public void startPeriodicServerCheck() {
        serverCheckTimeline = new Timeline(new KeyFrame(Duration.seconds(checkIntervalInSeconds), event -> {
            ServerStatus serverStatus = updateServerStatusUI();
            boolean serverIsFunctional = (serverStatus == ServerStatus.ONLINE);
            adjustCheckInterval(serverIsFunctional);
        }));
        serverCheckTimeline.setCycleCount(Timeline.INDEFINITE); // Se ejecuta indefinidamente
        serverCheckTimeline.play(); // Inicia el Timeline
    }

    /**
     * Verifica el estado del servidor y actualiza el Label e ícono correspondientes.
     *
     * @return serverStatus el estado del servidor
     */
    public ServerStatus updateServerStatusUI() {
        ServerStatus serverStatus = databaseConnector.checkServerStatus();

        // Actualizar los componentes de la interfaz en función del estado del servidor
        switch (serverStatus) {
            case ONLINE:
                statusLabel.setText(DatabaseConnectorStatus.FUNCTIONAL_MSG);
                statusLabel.setStyle("-fx-text-fill: " + DatabaseConnectorStatus.FUNCTIONAL_COLOR + ";");
                statusIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(DatabaseConnectorStatus.FUNCTIONAL_ICON))));
                break;
            case AUTHENTICATION_ERROR:
                statusLabel.setText(DatabaseConnectorStatus.AUTH_PROBLEM_MSG);
                statusLabel.setStyle("-fx-text-fill: " + DatabaseConnectorStatus.AUTH_PROBLEM_COLOR + ";");
                statusIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(DatabaseConnectorStatus.AUTH_PROBLEM_ICON))));
                break;
            case DATABASE_SERVICE_DOWN:
                statusLabel.setText(DatabaseConnectorStatus.DB_SERVICE_PROBLEM_MSG);
                statusLabel.setStyle("-fx-text-fill: " + DatabaseConnectorStatus.DB_SERVICE_PROBLEM_COLOR + ";");
                statusIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(DatabaseConnectorStatus.DB_SERVICE_PROBLEM_ICON))));
                break;
            case HOST_UNREACHABLE:
                statusLabel.setText(DatabaseConnectorStatus.HOST_UNREACHABLE_MSG);
                statusLabel.setStyle("-fx-text-fill: " + DatabaseConnectorStatus.HOST_UNREACHABLE_COLOR + ";");
                statusIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(DatabaseConnectorStatus.HOST_UNREACHABLE_ICON))));
                break;
            default:
                statusLabel.setText(DatabaseConnectorStatus.UNKNOWN_ERROR_MSG);
                statusLabel.setStyle("-fx-text-fill: " + DatabaseConnectorStatus.UNKNOWN_ERROR_COLOR + ";");
                statusIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(DatabaseConnectorStatus.UNKNOWN_ERROR_ICON))));
                break;
        }

        return serverStatus;
    }


    public void stop() {
        if (serverCheckTimeline != null) {
            serverCheckTimeline.stop();
        }
    }


    /**
     * Ajusta el intervalo de chequeo según el estado del servidor.
     *
     * @param serverIsFunctional true si el servidor está operativo; false si hay algún problema.
     */
    private void adjustCheckInterval(boolean serverIsFunctional) {
        if (serverIsFunctional) {
            checkIntervalInSeconds = 16; // Si el servidor está en línea y funcional, aumentar el intervalo de chequeo a 16 segundos
        } else {
            checkIntervalInSeconds = 4;  // Si el servidor tiene problemas, reducir el intervalo de chequeo a 4 segundos
        }

        if (serverCheckTimeline != null) {
            serverCheckTimeline.stop();
            serverCheckTimeline.getKeyFrames().setAll(new KeyFrame(Duration.seconds(checkIntervalInSeconds), event -> {
                ServerStatus updatedServerStatus = updateServerStatusUI();
                boolean isFunctional = (updatedServerStatus == ServerStatus.ONLINE);
                adjustCheckInterval(isFunctional);
            }));
            serverCheckTimeline.play();
        }
    }

}
