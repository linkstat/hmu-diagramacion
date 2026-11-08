package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.RoleData;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.RoleRepository.AsignacionRol;
import ar.com.hmu.service.AsignacionRolService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.Reloj;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Asignar roles con vigencia (P10; CU06).
 */
public class RolesVigenciaController {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private TextField buscarField;
    @FXML private ListView<Usuario> usuariosList;
    @FXML private Label usuarioLabel;
    @FXML private TableView<AsignacionRol> asignacionesTable;
    @FXML private DatePicker revocarPicker;
    @FXML private Button revocarButton;
    @FXML private ComboBox<RoleData> rolCombo;
    @FXML private ComboBox<Servicio> servicioCombo;
    @FXML private DatePicker desdePicker;
    @FXML private DatePicker hastaPicker;
    @FXML private TextField memorandoField;
    @FXML private Button asignarButton;

    private AsignacionRolService service;
    private Usuario usuarioActual;
    private Stage stage;

    public void setService(AsignacionRolService service) {
        this.service = service;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
    }

    public void postInitialize(List<Usuario> usuarios, List<Servicio> servicios) {
        ar.com.hmu.util.FechaUtils.formatear(revocarPicker, desdePicker, hastaPicker);
        FilteredList<Usuario> filtrados = new FilteredList<>(FXCollections.observableArrayList(usuarios.stream()
                .filter(Usuario::getEstado)
                .sorted(Comparator.comparing(Usuario::getApellidosNombres, String.CASE_INSENSITIVE_ORDER)).toList()));
        usuariosList.setItems(filtrados);
        usuariosList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getApellidosNombres());
            }
        });
        buscarField.textProperty().addListener((o, a, b) -> {
            String t = b == null ? "" : b.trim().toLowerCase();
            filtrados.setPredicate(u -> t.isEmpty() || u.getApellidosNombres().toLowerCase().contains(t)
                    || String.valueOf(u.getCuil()).contains(t));
        });
        usuariosList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> mostrar(b));

        asignacionesTable.getColumns().add(col("ROL", 160, a -> a.rol().replace("JefaturaDeServicio", "Jefatura de Servicio")
                .replace("OficinaDePersonal", "Oficina de Personal").replace("Direccion", "Dirección")
                .replace("Empleado", "Agente")));
        asignacionesTable.getColumns().add(col("SERVICIO", 150, a -> Objects.toString(a.servicio(), "")));
        asignacionesTable.getColumns().add(col("DESDE", 90, a -> a.desde().format(DDMMYYYY)));
        asignacionesTable.getColumns().add(col("HASTA", 90, a -> a.hasta() != null ? a.hasta().format(DDMMYYYY) : "—"));
        asignacionesTable.getColumns().add(col("MEMORANDO", 190, a -> Objects.toString(a.memorando(), "")));
        asignacionesTable.getColumns().add(col("OTORGADO POR", 140, a -> Objects.toString(a.otorgadoPor(), "")));
        asignacionesTable.getColumns().add(col("ESTADO", 90, a -> a.vigenteEl(Reloj.hoy()) ? "Vigente"
                : a.desde().isAfter(Reloj.hoy()) ? "Futura" : "Finalizada"));
        asignacionesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        asignacionesTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AsignacionRol a, boolean empty) {
                super.updateItem(a, empty);
                setStyle(!empty && a != null && !a.vigenteEl(Reloj.hoy()) ? "-fx-opacity: 0.6;" : "");
            }
        });
        asignacionesTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) ->
                revocarButton.setDisable(b == null || !b.vigenteEl(Reloj.hoy())));
        try {
            rolCombo.getItems().setAll(service.roles());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        rolCombo.setConverter(new StringConverter<>() {
            @Override public String toString(RoleData r) {
                if (r == null) return "";
                return switch (r.getTipoUsuario()) {
                    case EMPLEADO -> "Agente";
                    case JEFATURADESERVICIO -> "Jefatura de Servicio (JS)";
                    case OFICINADEPERSONAL -> "Oficina de Personal (OP)";
                    case DIRECCION -> "Dirección (DH)";
                };
            }
            @Override public RoleData fromString(String s) { return null; }
        });
        servicioCombo.getItems().setAll(servicios.stream().sorted(Comparator.comparing(Servicio::getNombre)).toList());
        servicioCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Servicio s) { return s == null ? "" : s.getNombre(); }
            @Override public Servicio fromString(String s) { return null; }
        });
        rolCombo.valueProperty().addListener((o, a, b) -> servicioCombo.setDisable(b == null
                || b.getTipoUsuario() != ar.com.hmu.constants.TipoUsuario.JEFATURADESERVICIO));
        servicioCombo.setDisable(true);
        desdePicker.setValue(Reloj.hoy());
        revocarPicker.setValue(Reloj.hoy());
        revocarButton.setDisable(true);
        mostrar(null);
    }

    /** Selecciona al usuario (enlace desde el ABM de agentes y modo de capturas). */
    public void seleccionar(long cuil) {
        usuariosList.getItems().stream().filter(u -> u.getCuil() == cuil).findFirst().ifPresent(u -> {
            usuariosList.getSelectionModel().select(u);
            usuariosList.scrollTo(u);
        });
    }

    /** Modo de capturas. */
    public void proponer(long cuil, ar.com.hmu.constants.TipoUsuario rol, String servicio, LocalDate desde, String memo) {
        seleccionar(cuil);
        rolCombo.getItems().stream().filter(r -> r.getTipoUsuario() == rol).findFirst().ifPresent(rolCombo::setValue);
        servicioCombo.getItems().stream().filter(s -> s.getNombre().equals(servicio)).findFirst()
                .ifPresent(servicioCombo::setValue);
        desdePicker.setValue(desde);
        memorandoField.setText(memo);
    }

    private void mostrar(Usuario u) {
        asignarButton.setDisable(u == null);
        if (u == null) {
            usuarioLabel.setText("Seleccione un usuario");
            asignacionesTable.getItems().clear();
            return;
        }
        usuarioLabel.setText(u.getApellidosNombres());
        try {
            asignacionesTable.getItems().setAll(service.asignaciones(u));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        if (u.getServicioId() != null) {
            servicioCombo.getItems().stream().filter(s -> s.getId().equals(u.getServicioId())).findFirst()
                    .ifPresent(servicioCombo::setValue);
        }
    }

    @FXML
    private void onAsignar() {
        Usuario u = usuariosList.getSelectionModel().getSelectedItem();
        Servicio s = servicioCombo.getValue();
        try {
            String msg = service.asignar(u, rolCombo.getValue(), s != null ? s.getId() : null, desdePicker.getValue(),
                    hastaPicker.getValue(), memorandoField.getText(), usuarioActual);
            AlertUtils.showInfo(msg);
            mostrar(u);
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onRevocar() {
        AsignacionRol a = asignacionesTable.getSelectionModel().getSelectedItem();
        if (a == null) return;
        try {
            service.revocar(a, revocarPicker.getValue(), usuarioActual);
            mostrar(usuariosList.getSelectionModel().getSelectedItem());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }

    private static TableColumn<AsignacionRol, String> col(String t, double w, Function<AsignacionRol, String> f) {
        TableColumn<AsignacionRol, String> c = new TableColumn<>(t);
        c.setPrefWidth(w);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }

    static UUID id(Usuario u) {
        return u != null ? u.getId() : null;
    }
}
