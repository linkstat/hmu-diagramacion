package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoFeriado;
import ar.com.hmu.model.Feriado;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.FeriadoService;
import ar.com.hmu.util.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pantalla de feriados institucionales.
 *
 * <p>Oficina de Personal: carga anual en bloque (directa, sólo si el año
 * está vacío) y propuestas individuales de alta/baja. Dirección: autoriza
 * o rechaza las propuestas pendientes. Los botones se muestran según el
 * rol del usuario; la validación final la impone {@link FeriadoService}.</p>
 */
public class FeriadosController {

    @FXML private ComboBox<Integer> anioCombo;
    @FXML private TableView<Feriado> feriadosTable;
    @FXML private TableColumn<Feriado, String> fechaCol;
    @FXML private TableColumn<Feriado, String> descripcionCol;
    @FXML private TableColumn<Feriado, String> estadoCol;
    @FXML private Label ayudaLabel;
    @FXML private Button cargaAnualButton;
    @FXML private Button proponerAltaButton;
    @FXML private Button proponerBajaButton;
    @FXML private Button autorizarButton;
    @FXML private Button rechazarButton;

    private FeriadoService feriadoService;
    private Usuario usuarioActual;

    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("dd/MM/yyyy (EEE)",
            java.util.Locale.of("es", "AR"));

    public void setFeriadoService(FeriadoService feriadoService) {
        this.feriadoService = feriadoService;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    /** Llamar después de inyectar dependencias. */
    public void postInitialize() {
        int anioActual = Year.now().getValue();
        for (int a = anioActual - 1; a <= anioActual + 2; a++) {
            anioCombo.getItems().add(a);
        }
        anioCombo.getSelectionModel().select(Integer.valueOf(anioActual));
        anioCombo.valueProperty().addListener((obs, v, n) -> onRefrescar());

        fechaCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getFecha().format(FECHA_LARGA)));
        descripcionCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getDescripcion()));
        estadoCol.setCellValueFactory(cd -> new SimpleStringProperty(
                etiquetaEstado(cd.getValue().getEstado())));
        feriadosTable.setPlaceholder(new Label("El año no tiene feriados cargados."));
        feriadosTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, v, n) -> actualizarBotones());

        boolean esOP = usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL);
        boolean esDireccion = usuarioActual.hasRole(TipoUsuario.DIRECCION);
        for (Button b : new Button[]{cargaAnualButton, proponerAltaButton, proponerBajaButton}) {
            b.setVisible(esOP);
            b.setManaged(esOP);
        }
        for (Button b : new Button[]{autorizarButton, rechazarButton}) {
            b.setVisible(esDireccion);
            b.setManaged(esDireccion);
        }
        if (esOP && !esDireccion) {
            ayudaLabel.setText("Los cambios posteriores a la carga anual quedan pendientes "
                    + "hasta que la Dirección los autorice.");
        } else if (esDireccion && !esOP) {
            ayudaLabel.setText("Seleccioná una propuesta pendiente para autorizarla o rechazarla.");
        }

        onRefrescar();
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onRefrescar() {
        Integer anio = anioCombo.getValue();
        if (anio == null) {
            return;
        }
        try {
            feriadosTable.getItems().setAll(feriadoService.feriadosDelAnio(anio));
            actualizarBotones();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los feriados: " + e.getMessage());
        }
    }

    /** Carga anual en bloque: mini-formulario para ir sumando fechas a una
     *  lista y confirmar todo junto. */
    @FXML
    private void onCargaAnual() {
        Integer anio = anioCombo.getValue();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Carga anual de feriados");
        dialog.setHeaderText("Feriados del año " + anio + " — quedan vigentes al confirmar.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        DatePicker fechaPicker = new DatePicker(LocalDate.of(anio, 1, 1));
        ar.com.hmu.util.FechaUtils.formatear(fechaPicker);
        TextField descripcionField = new TextField();
        descripcionField.setPromptText("Descripción (ej.: Día de la Independencia)");
        descripcionField.setPrefWidth(280);
        Button agregarButton = new Button("Agregar a la lista");
        ListView<String> lista = new ListView<>();
        lista.setPrefHeight(180);

        Map<LocalDate, String> feriados = new LinkedHashMap<>();
        agregarButton.setOnAction(e -> {
            LocalDate fecha = fechaPicker.getValue();
            String desc = descripcionField.getText();
            if (fecha == null || desc == null || desc.isBlank()) {
                AlertUtils.showWarn("Cada feriado requiere fecha y descripción.");
                return;
            }
            if (fecha.getYear() != anio) {
                AlertUtils.showWarn("La fecha debe pertenecer al año " + anio + ".");
                return;
            }
            if (feriados.containsKey(fecha)) {
                AlertUtils.showWarn("Esa fecha ya está en la lista.");
                return;
            }
            feriados.put(fecha, desc.trim());
            lista.getItems().add(fecha.format(FECHA_LARGA) + " — " + desc.trim());
            descripcionField.clear();
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Fecha:"), fechaPicker, agregarButton);
        grid.add(descripcionField, 1, 1, 2, 1);
        grid.add(new Label("A cargar:"), 0, 2);
        grid.add(lista, 1, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        if (feriados.isEmpty()) {
            AlertUtils.showWarn("No agregaste ningún feriado a la lista.");
            return;
        }
        try {
            feriadoService.cargarAnio(anio, feriados, usuarioActual);
            AlertUtils.showInfo("Se cargaron " + feriados.size() + " feriados del año " + anio + ".");
            onRefrescar();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo realizar la carga anual:\n" + e.getMessage());
        }
    }

    @FXML
    private void onProponerAlta() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Proponer feriado");
        dialog.setHeaderText("La propuesta queda pendiente hasta que la Dirección la autorice.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        DatePicker fechaPicker = new DatePicker(LocalDate.now());
        ar.com.hmu.util.FechaUtils.formatear(fechaPicker);
        TextField descripcionField = new TextField();
        descripcionField.setPromptText("Descripción (ej.: Feriado administrativo)");
        descripcionField.setPrefWidth(280);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Fecha:"), fechaPicker);
        grid.addRow(1, new Label("Descripción:"), descripcionField);
        dialog.getDialogPane().setContent(grid);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            feriadoService.proponerAlta(fechaPicker.getValue(), descripcionField.getText(), usuarioActual);
            AlertUtils.showInfo("Propuesta registrada: queda pendiente de autorización de la Dirección.");
            sincronizarAnioCon(fechaPicker.getValue());
            onRefrescar();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo registrar la propuesta:\n" + e.getMessage());
        }
    }

    @FXML
    private void onProponerBaja() {
        Feriado seleccionado = feriadosTable.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        if (!AlertUtils.showConfirm("¿Proponer la baja del feriado \"" + seleccionado.getDescripcion()
                + "\" (" + seleccionado.getFecha().format(FECHA_LARGA) + ")?\n"
                + "Sigue vigente hasta que la Dirección autorice la baja.")) {
            return;
        }
        try {
            feriadoService.proponerBaja(seleccionado.getId(), usuarioActual);
            onRefrescar();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo proponer la baja:\n" + e.getMessage());
        }
    }

    @FXML
    private void onAutorizar() {
        resolverSeleccionado(true);
    }

    @FXML
    private void onRechazar() {
        resolverSeleccionado(false);
    }

    @FXML
    private void onCerrar() {
        ((Stage) feriadosTable.getScene().getWindow()).close();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void resolverSeleccionado(boolean autoriza) {
        Feriado seleccionado = feriadosTable.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        String accion = autoriza ? "autorizar" : "rechazar";
        if (!AlertUtils.showConfirm("¿" + (autoriza ? "Autorizar" : "Rechazar") + " la propuesta sobre \""
                + seleccionado.getDescripcion() + "\" ("
                + seleccionado.getFecha().format(FECHA_LARGA) + ")?")) {
            return;
        }
        try {
            if (autoriza) {
                feriadoService.autorizar(seleccionado.getId(), usuarioActual);
            } else {
                feriadoService.rechazar(seleccionado.getId(), usuarioActual);
            }
            onRefrescar();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo " + accion + " la propuesta:\n" + e.getMessage());
        }
    }

    private void actualizarBotones() {
        Feriado seleccionado = feriadosTable.getSelectionModel().getSelectedItem();
        boolean pendiente = seleccionado != null
                && (seleccionado.getEstado() == EstadoFeriado.ALTA_PENDIENTE
                    || seleccionado.getEstado() == EstadoFeriado.BAJA_PENDIENTE);
        autorizarButton.setDisable(!pendiente);
        rechazarButton.setDisable(!pendiente);
        proponerBajaButton.setDisable(seleccionado == null
                || seleccionado.getEstado() != EstadoFeriado.VIGENTE);
    }

    /** Si la propuesta fue para otro año, mueve el combo para que se vea. */
    private void sincronizarAnioCon(LocalDate fecha) {
        if (fecha != null && anioCombo.getValue() != null
                && fecha.getYear() != anioCombo.getValue()
                && anioCombo.getItems().contains(fecha.getYear())) {
            anioCombo.getSelectionModel().select(Integer.valueOf(fecha.getYear()));
        }
    }

    private String etiquetaEstado(EstadoFeriado estado) {
        return switch (estado) {
            case VIGENTE -> "Vigente";
            case ALTA_PENDIENTE -> "Alta pendiente de Dirección";
            case BAJA_PENDIENTE -> "Baja pendiente de Dirección";
            case RECHAZADO -> "Rechazado";
            case ANULADO -> "Anulado";
        };
    }
}
