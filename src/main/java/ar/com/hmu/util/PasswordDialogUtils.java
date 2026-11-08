package ar.com.hmu.util;

import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.UsuarioService;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import java.util.Arrays;
import java.util.function.Consumer;

public class PasswordDialogUtils {

    /**
     * Muestra un diálogo para cambiar la contraseña del usuario.
     * <p>
     * Este método presenta una ventana emergente con los campos necesarios para cambiar la contraseña.
     * Si el cambio es exitoso, se llama al callback onSuccess; de lo contrario, se llama a onCancel.
     *
     * @param usuario    El usuario autenticado que necesita cambiar su contraseña.
     * @param onSuccess  Acción a ejecutar después de un cambio de contraseña exitoso.
     * @param onCancel   Acción a ejecutar si el usuario cancela el cambio de contraseña.
     */
    public static void showChangePasswordDialog(Usuario usuario, UsuarioService usuarioService, Consumer<String> onSuccess, Runnable onCancel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cambiar Contraseña");
        dialog.setHeaderText("Debe introducir su contraseña actual\nY luego, repetir dos veces su nueva contraseña.\n");

        // Campos de contraseña
        PasswordField currentPasswordField = new PasswordField();
        currentPasswordField.setPromptText("Contraseña actual");

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Nueva contraseña");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Repetir nueva contraseña");

        // Añadir campos a la ventana
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(10, currentPasswordField, newPasswordField, confirmPasswordField));

        // Procesar el resultado del diálogo
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Convertir las contraseñas a char[]
                char[] currentPassword = currentPasswordField.getText().toCharArray();
                char[] newPassword = newPasswordField.getText().toCharArray();
                char[] confirmPassword = confirmPasswordField.getText().toCharArray();

                try {
                    // Intentar cambiar la contraseña usando UsuarioService
                    usuarioService.changePassword(usuario, currentPassword, newPassword, confirmPassword);

                    // Llamar al callback onSuccess para proceder con la lógica después del cambio exitoso
                    if (onSuccess != null) {
                        onSuccess.accept("Contraseña cambiada exitosamente.");
                    }
                } catch (IllegalArgumentException e) {
                    AlertUtils.showErr(e.getMessage());
                    // Reabrir el diálogo si hay un error
                    showChangePasswordDialog(usuario, usuarioService, onSuccess, onCancel);
                } catch (RuntimeException e) {
                    AlertUtils.showErr("Ocurrió un error al actualizar la contraseña: " + e.getMessage());
                    // Podríamos reabrir el diálogo; pero mejor lo dejamos así nomás...
                } finally {
                    // Limpiar las contraseñas del arreglo para mayor seguridad
                    Arrays.fill(currentPassword, '\0');
                    Arrays.fill(newPassword, '\0');
                    Arrays.fill(confirmPassword, '\0');
                }
            } else {
                // Llamar al callback onCancel si el usuario cancela el diálogo
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        });
    }

}
