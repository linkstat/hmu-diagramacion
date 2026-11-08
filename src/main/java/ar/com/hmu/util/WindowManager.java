package ar.com.hmu.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class WindowManager {

    /**
     * Abre una nueva ventana modal con inyección de servicios.
     *
     * @param fxmlPath        Ruta del archivo FXML.
     * @param title           Título de la ventana.
     * @param owner           Ventana propietaria (puede ser null).
     * @param controllerClass Clase del controlador asociado.
     * @param services        Pares clave-valor para inyectar servicios.
     */
    public static void openWindow(String fxmlPath, String title, Window owner, Class<?> controllerClass, Object... services) {
        try {
            FXMLLoader loader = new FXMLLoader(WindowManager.class.getResource(fxmlPath));

            // Configurar la fábrica de controladores para inyectar servicios
            loader.setControllerFactory(cls -> {
                if (cls == controllerClass) {
                    try {
                        Object controller = cls.getDeclaredConstructor().newInstance();
                        // Inyectar servicios mediante métodos setters
                        for (int i = 0; i < services.length; i += 2) {
                            String propertyName = (String) services[i];
                            Object serviceInstance = services[i + 1];
                            String setterMethod = "set" + capitalize(propertyName);
                            try {
                                // Encontrar el método setter correspondiente
                                cls.getMethod(setterMethod, serviceInstance.getClass()).invoke(controller, serviceInstance);
                            } catch (NoSuchMethodException e) {
                                System.err.println("Setter no encontrado: " + setterMethod);
                            }
                        }
                        return controller;
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        return cls.getDeclaredConstructor().newInstance();
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) {
                stage.initOwner(owner);
            }

            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            // Aquí podrías manejar el error mostrando una alerta al usuario
        }
    }

    /**
     * Capitaliza la primera letra de una cadena.
     *
     * @param str La cadena a capitalizar.
     * @return La cadena capitalizada.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
