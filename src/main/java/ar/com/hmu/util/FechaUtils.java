package ar.com.hmu.util;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Formato de fecha único de la interfaz: dd/MM/yyyy. */
public final class FechaUtils {

    public static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    /** Al tipear se aceptan día y mes de un dígito (7/10/2026). */
    private static final DateTimeFormatter ENTRADA = DateTimeFormatter.ofPattern("d/M/uuuu");

    private FechaUtils() {
    }

    /** Muestra y lee las fechas de los selectores como dd/MM/yyyy. */
    public static void formatear(DatePicker... pickers) {
        for (DatePicker dp : pickers) {
            dp.setPromptText("dd/mm/aaaa");
            dp.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate d) {
                    return d == null ? "" : d.format(DDMMYYYY);
                }

                @Override
                public LocalDate fromString(String t) {
                    return t == null || t.isBlank() ? null : LocalDate.parse(t.trim(), ENTRADA);
                }
            });
        }
    }
}
