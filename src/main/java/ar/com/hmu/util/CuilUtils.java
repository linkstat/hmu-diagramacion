package ar.com.hmu.util;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

public class CuilUtils {

    /**
     * Configura un TextField para aceptar solo números y formatea el texto como un CUIL.
     *
     * @param cuilField El TextField a configurar.
     */
    public static void configureCuilField(TextField cuilField) {
        // Limitar el input a solo dígitos numéricos
        cuilField.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!event.getCharacter().matches("\\d")) {
                event.consume();  // Ignora el input si no es un número
            }
        });

        // Añadir un listener para formatear el texto mientras se escribe
        cuilField.textProperty().addListener(new ChangeListener<String>() {
            private boolean ignore;

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (ignore) {
                    return;
                }
                ignore = true;

                // Remover cualquier caracter que no sea un dígito
                String digitsOnly = newValue.replaceAll("[^\\d]", "");

                // Limitar la longitud máxima a 11 dígitos
                if (digitsOnly.length() > 11) {
                    digitsOnly = digitsOnly.substring(0, 11);
                }

                // Aplicar el formato NN-NNNNNNNN-N
                String formattedText = formatCuil(digitsOnly);

                // Actualizar el campo de texto con el texto formateado
                cuilField.setText(formattedText);

                // Mover el cursor al final del texto
                cuilField.positionCaret(formattedText.length());

                ignore = false;
            }
        });
    }


    /**
     * Aplica el formato NN-NNNNNNNN-N a una cadena de números.
     *
     * @param digits cadena de dígitos que representan el CUIL.
     * @return el CUIL formateado como NN-NNNNNNNN-N.
     */
    public static String formatCuil(String digits) {
        StringBuilder formatted = new StringBuilder();

        if (digits.length() >= 2) {
            formatted.append(digits, 0, 2).append("-");
        } else {
            formatted.append(digits);
            return formatted.toString();
        }

        if (digits.length() >= 10) {
            formatted.append(digits, 2, 10).append("-");
            formatted.append(digits.substring(10));
        } else if (digits.length() > 2) {
            formatted.append(digits.substring(2));
        }

        return formatted.toString();
    }


    /**
     * Remueve cualquier caracter que no sea un dígito.
     *
     * @param text El texto a procesar.
     * @return Una cadena que contiene solo dígitos.
     */
    public static String getDigitsOnly(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[^\\d]", "");
    }


}