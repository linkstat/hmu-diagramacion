package ar.com.hmu.util;

import ar.com.hmu.auth.LoginService;
import ar.com.hmu.config.AppConfigReader;
import ar.com.hmu.factory.UsuarioFactory;
import ar.com.hmu.repository.*;
import ar.com.hmu.controller.LoginController;
import ar.com.hmu.service.CargoService;
import ar.com.hmu.service.DomicilioService;
import ar.com.hmu.service.FeriadoService;
import ar.com.hmu.service.MemorandumService;
import ar.com.hmu.service.RoleService;
import ar.com.hmu.service.ServicioService;
import ar.com.hmu.service.UsuarioService;
import ar.com.hmu.service.diagramacion.DiagramaService;
import ar.com.hmu.service.notification.EmailNotificationService;
import ar.com.hmu.service.notification.NotificationService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Objects;

public class SessionUtils {

    /**
     * Muestra la ventana de inicio de sesión y cierra el menú principal.
     */
    public static void handleLogout(Stage currentStage) {
        ContextoAuditoria.limpiar();
        ControlInactividad.detener();
        try {
            // Cargar el archivo FXML de la pantalla de login
            FXMLLoader loader = new FXMLLoader(LoginController.class.getResource("/fxml/loginScreen.fxml"));
            Parent root = loader.load();

            // Obtener el controlador
            LoginController controller = loader.getController();
            if (controller == null) {
                throw new IllegalStateException("El controlador de Login no fue inicializado correctamente.");
            }

            // Servicios nuevos para la próxima sesión (mismo armado que el arranque).
            ar.com.hmu.ui.ContextoAplicacion.crear().inyectar(controller);
            controller.postInitialize();

            // Configurar la nueva ventana
            Stage stage = new Stage();
            stage.setTitle("Inicio de sesión" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setResizable(false);

            // Establecer el ícono de la aplicación
            setStageIcon(stage, AppInfo.ICON_IMAGE);

            Scene scene = new Scene(root);
            stage.setScene(scene);

            // Cerrar la ventana actual
            currentStage.close();

            // Mostrar la ventana de inicio de sesión
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            AlertUtils.showErr("Error al cargar la pantalla de inicio de sesión:\n" + e.getMessage());
        }
    }

    private static void setStageIcon(Stage stage, String iconPath) {
        try {
            Image icon = new Image(Objects.requireNonNull(
                    SessionUtils.class.getResourceAsStream(iconPath)
            ));
            if (icon.isError()) {
                throw new IllegalArgumentException("Error al cargar el icono: " + iconPath);
            }
            stage.getIcons().add(icon);
        } catch (Exception e) {
            e.printStackTrace(); // Imprimir mensaje completo del error por consola
            System.err.println("No se pudo establecer el icono: " + e.getMessage());
        }
    }

}
