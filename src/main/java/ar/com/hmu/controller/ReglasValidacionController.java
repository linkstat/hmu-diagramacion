package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.ReglaValidacion;
import ar.com.hmu.model.Severidad;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.ReglaValidacionService;
import ar.com.hmu.util.AlertUtils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parametrizar reglas de validación (P12; CU08): la OP selecciona una regla,
 * ve los parámetros vigentes, su severidad y su vigencia, y registra una
 * nueva versión con la fecha desde la que rige. La DH la consulta.
 */
public class ReglasValidacionController {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private DatePicker fechaPicker;
    @FXML private TableView<ReglaValidacion> reglasTable;
    @FXML private TableColumn<ReglaValidacion, String> codigoCol;
    @FXML private TableColumn<ReglaValidacion, String> nombreCol;
    @FXML private TableColumn<ReglaValidacion, String> ambitoCol;
    @FXML private TableColumn<ReglaValidacion, ReglaValidacion> severidadCol;
    @FXML private TableColumn<ReglaValidacion, String> parametrosCol;
    @FXML private Label tituloLabel;
    @FXML private Label severidadChip;
    @FXML private Label descripcionLabel;
    @FXML private Label modalidadLabel;
    @FXML private GridPane parametrosGrid;
    @FXML private ComboBox<Severidad> severidadCombo;
    @FXML private CheckBox activaCheck;
    @FXML private DatePicker desdePicker;
    @FXML private Label avisoLabel;
    @FXML private Button registrarButton;
    @FXML private TableView<ReglaValidacion> versionesTable;
    @FXML private TableColumn<ReglaValidacion, String> vDesdeCol;
    @FXML private TableColumn<ReglaValidacion, String> vHastaCol;
    @FXML private TableColumn<ReglaValidacion, String> vSeveridadCol;
    @FXML private TableColumn<ReglaValidacion, String> vParametrosCol;
    @FXML private TableColumn<ReglaValidacion, String> vActivaCol;

    private ReglaValidacionService service;
    private Usuario usuarioActual;
    private Stage stage;
    private final Map<String, TextField> camposParametros = new LinkedHashMap<>();

    public void setService(ReglaValidacionService service) {
        this.service = service;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
    }

    public void postInitialize(LocalDate fecha) {
        ar.com.hmu.util.FechaUtils.formatear(fechaPicker, desdePicker);
        fechaPicker.setValue(fecha);
        fechaPicker.valueProperty().addListener((o, a, b) -> onRefrescar());
        severidadCombo.getItems().setAll(Severidad.values());
        severidadCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Severidad s) { return s == null ? "" : etiqueta(s); }
            @Override public Severidad fromString(String s) { return null; }
        });
        desdePicker.valueProperty().addListener((o, a, b) -> mostrarAviso());

        codigoCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCodigo()));
        nombreCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNombre()));
        ambitoCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAmbito().toString()));
        parametrosCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().parametrosTexto()));
        severidadCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        severidadCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ReglaValidacion r, boolean empty) {
                super.updateItem(r, empty);
                setText(null);
                if (empty || r == null) {
                    setGraphic(null);
                } else {
                    setGraphic(chip(r));
                }
            }
        });
        vDesdeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getVigenteDesde().format(DDMMYYYY)));
        vHastaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getVigenteHasta() != null
                ? cd.getValue().getVigenteHasta().format(DDMMYYYY) : "vigente"));
        vSeveridadCol.setCellValueFactory(cd -> new SimpleStringProperty(etiqueta(cd.getValue().getSeveridad())));
        vParametrosCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().parametrosTexto()));
        vActivaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().isActiva() ? "Sí" : "No"));
        reglasTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> mostrar(b));
        reglasTable.setPlaceholder(new Label("No hay reglas vigentes a esa fecha."));
        versionesTable.setPlaceholder(new Label(""));

        boolean puedeEditar = usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL);
        registrarButton.setDisable(!puedeEditar);
        severidadCombo.setDisable(!puedeEditar);
        activaCheck.setDisable(!puedeEditar);
        desdePicker.setDisable(!puedeEditar);
        onRefrescar();
    }

    /** Para el modo de capturas. */
    public void seleccionar(String codigo) {
        reglasTable.getItems().stream().filter(r -> r.getCodigo().equals(codigo)).findFirst()
                .ifPresent(r -> {
                    reglasTable.getSelectionModel().select(r);
                    reglasTable.scrollTo(r);
                });
    }

    /** Para el modo de capturas: carga un valor nuevo en el formulario sin guardarlo. */
    public void proponer(String clave, String valor, LocalDate desde) {
        TextField f = camposParametros.get(clave);
        if (f != null) f.setText(valor);
        desdePicker.setValue(desde);
    }

    @FXML
    private void onRefrescar() {
        String previa = Optional.ofNullable(reglasTable.getSelectionModel().getSelectedItem())
                .map(ReglaValidacion::getCodigo).orElse(null);
        try {
            reglasTable.getItems().setAll(service.vigentes(fechaPicker.getValue() != null
                    ? fechaPicker.getValue() : ar.com.hmu.util.Reloj.hoy()));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        if (previa != null) {
            seleccionar(previa);
        } else {
            reglasTable.getSelectionModel().selectFirst();
        }
    }

    private void mostrar(ReglaValidacion r) {
        parametrosGrid.getChildren().clear();
        camposParametros.clear();
        versionesTable.getItems().clear();
        if (r == null) {
            tituloLabel.setText("Seleccione una regla");
            severidadChip.setVisible(false);
            descripcionLabel.setText("");
            modalidadLabel.setText("");
            return;
        }
        tituloLabel.setText(r.getNombre() + " (" + r.getCodigo() + ")");
        severidadChip.setText(chip(r).getText());
        severidadChip.getStyleClass().setAll(chip(r).getStyleClass());
        severidadChip.setVisible(true);
        descripcionLabel.setText(r.getDescripcion());
        modalidadLabel.setText("Ámbito: " + r.getAmbito() + " · Modalidad: "
                + (r.getModalidad() != null ? r.getModalidad().replace("Horario", "") : "todas")
                + " · Vigente desde el " + r.getVigenteDesde().format(DDMMYYYY)
                + " · Fundamento: " + (r.getFundamentoNormativo() != null && !r.getFundamentoNormativo().isBlank()
                        ? r.getFundamentoNormativo() : "—"));
        int fila = 0;
        for (Map.Entry<String, Double> p : r.getParametros().entrySet()) {
            TextField f = new TextField(ReglaValidacion.formatear(p.getValue()));
            f.setPrefColumnCount(6);
            f.setMaxWidth(90);
            f.setDisable(registrarButton.isDisabled());
            camposParametros.put(p.getKey(), f);
            parametrosGrid.addRow(fila++, new Label(nombreParametro(p.getKey()) + ":"), f);
        }
        if (fila == 0) {
            parametrosGrid.addRow(0, new Label("Parámetros:"), new Label("Sin parámetros: se ajusta su severidad y su estado."));
        }
        severidadCombo.setValue(r.getSeveridad());
        activaCheck.setSelected(r.isActiva());
        desdePicker.setValue(null);
        try {
            versionesTable.getItems().setAll(service.versiones(r.getCodigo()));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        mostrarAviso();
    }

    private void mostrarAviso() {
        Optional<String> aviso = desdePicker.getValue() != null
                ? service.advertenciaVigencia(desdePicker.getValue()) : Optional.empty();
        avisoLabel.setText(aviso.map(a -> "⚠ " + a).orElse(""));
        avisoLabel.setVisible(aviso.isPresent());
        avisoLabel.setManaged(aviso.isPresent());
    }

    @FXML
    private void onRegistrar() {
        ReglaValidacion actual = reglasTable.getSelectionModel().getSelectedItem();
        if (actual == null) return;
        Map<String, String> valores = new LinkedHashMap<>();
        camposParametros.forEach((k, f) -> valores.put(k, f.getText()));
        try {
            ReglaValidacion nueva = service.registrarNuevaVersion(actual, valores, severidadCombo.getValue(),
                    activaCheck.isSelected(), desdePicker.getValue(), usuarioActual);
            AlertUtils.showInfo("Se registró la nueva versión de " + actual.getCodigo() + ", vigente desde el "
                    + nueva.getVigenteDesde().format(DDMMYYYY) + ".");
            onRefrescar();
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }

    private static Label chip(ReglaValidacion r) {
        Label l = new Label(r.isActiva() ? etiqueta(r.getSeveridad()) : "Inactiva");
        l.getStyleClass().setAll("label", "estado-chip", !r.isActiva() ? "sev-inactiva"
                : r.getSeveridad() == Severidad.BLOQUEANTE ? "sev-bloqueante" : "sev-advertencia");
        return l;
    }

    private static String etiqueta(Severidad s) {
        return s == Severidad.BLOQUEANTE ? "Bloqueante" : "Advertencia";
    }

    /** "horasMinimas" → "Horas mínimas". */
    private static String nombreParametro(String clave) {
        return switch (clave) {
            case "horasMinimas" -> "Horas mínimas";
            case "horasMaximas" -> "Horas máximas";
            case "toleranciaHoras" -> "Tolerancia (horas)";
            case "topeMensual" -> "Tope mensual";
            default -> clave;
        };
    }
}
