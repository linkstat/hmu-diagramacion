package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.ImportacionPadronService;
import ar.com.hmu.service.ImportacionPadronService.Analisis;
import ar.com.hmu.service.ImportacionPadronService.Fila;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.CuilUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Function;

/**
 * Importar listado de agentes (P09; CU05): vista previa con las filas
 * válidas y las rechazadas (con su motivo) antes de confirmar.
 */
public class ImportarPadronController {

    @FXML private Label archivoLabel;
    @FXML private Label resumenLabel;
    @FXML private TableView<Fila> filasTable;
    @FXML private Button confirmarButton;

    private ImportacionPadronService service;
    private Usuario usuarioActual;
    private Stage stage;
    private Analisis analisis;

    public void setService(ImportacionPadronService service) {
        this.service = service;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
    }

    public void postInitialize() {
        filasTable.getColumns().add(col("FILA", 50, f -> String.valueOf(f.numero())));
        filasTable.getColumns().add(col("CUIL", 120, f -> f.cuil() > 0 ? CuilUtils.formatCuil(String.valueOf(f.cuil())) : "—"));
        filasTable.getColumns().add(col("APELLIDO Y NOMBRE", 190, f -> f.apellidos() + ", " + f.nombres()));
        filasTable.getColumns().add(col("SERVICIO", 180, f -> f.servicio() != null ? f.servicio().getNombre() : "—"));
        filasTable.getColumns().add(col("MODALIDAD", 160, f -> f.modalidad() != null ? f.modalidad().toString() : "—"));
        filasTable.getColumns().add(col("VINCULACIÓN", 140, f -> f.vinculacion().toString()));
        filasTable.getColumns().add(col("RESULTADO", 330, f -> f.valida() ? "✔ Válida" : "✖ Rechazada: " + f.motivoRechazo()));
        filasTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        filasTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Fila f, boolean empty) {
                super.updateItem(f, empty);
                getStyleClass().remove("sin-justificar");
                if (!empty && f != null && !f.valida()) getStyleClass().add("sin-justificar");
            }
        });
        filasTable.setPlaceholder(new Label("Seleccione el archivo del padrón."));
    }

    @FXML
    private void onSeleccionar() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Archivo del padrón");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Planilla (CSV)", "*.csv", "*.txt"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            analizar(f.getName(), Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            AlertUtils.showErr("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    /** También lo usa el modo de capturas. */
    public void analizar(String nombre, byte[] contenido) {
        try {
            analisis = service.analizar(nombre, contenido);
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
            return;
        }
        archivoLabel.setText(nombre);
        int validas = analisis.validas().size();
        resumenLabel.setText("     " + analisis.filas().size() + " filas: " + validas + " válidas, "
                + analisis.rechazadas() + " rechazadas.");
        filasTable.getItems().setAll(analisis.filas());
        confirmarButton.setText("Confirmar importación (" + validas + (validas == 1 ? " agente)" : " agentes)"));
        confirmarButton.setDisable(validas == 0);
    }

    @FXML
    private void onConfirmar() {
        if (analisis == null) return;
        if (!AlertUtils.showConfirm("¿Importar " + analisis.validas().size() + " agentes? Las filas rechazadas no se registran.")) {
            return;
        }
        try {
            int n = service.confirmar(analisis, usuarioActual);
            AlertUtils.showInfo("Se incorporaron " + n + " agentes al padrón, con su modalidad horaria. "
                    + "Deberán cambiar la contraseña inicial en el primer ingreso.");
            stage.close();
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }

    private static TableColumn<Fila, String> col(String t, double w, Function<Fila, String> f) {
        TableColumn<Fila, String> c = new TableColumn<>(t);
        c.setPrefWidth(w);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }
}
