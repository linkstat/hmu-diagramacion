package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.diagramacion.ModalidadHorariaService;
import ar.com.hmu.service.diagramacion.ModalidadHorariaService.Modalidad;
import ar.com.hmu.service.diagramacion.ModalidadHorariaService.Parametro;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.Reloj;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Asignar modalidad horaria (P08; CU04): la OP elige al agente, ve la
 * modalidad vigente y registra la nueva con la fecha desde la que rige y,
 * si corresponde, una franquicia con su período.
 */
public class ModalidadHorariaController {

    @FXML private TextField buscarField;
    @FXML private ListView<Usuario> agentesList;
    @FXML private Label agenteLabel;
    @FXML private Label vigenteLabel;
    @FXML private ComboBox<Modalidad> modalidadCombo;
    @FXML private Label descripcionLabel;
    @FXML private GridPane parametrosGrid;
    @FXML private DatePicker desdePicker;
    @FXML private CheckBox franquiciaCheck;
    @FXML private TextField franquiciaHorasField;
    @FXML private DatePicker franquiciaHastaPicker;
    @FXML private Label avisoLabel;
    @FXML private Button asignarButton;

    private ModalidadHorariaService service;
    private UsuarioRepository usuarioRepository;
    private Map<UUID, String> servicios;
    private Usuario usuarioActual;
    private Stage stage;
    private final Map<String, TextField> campos = new LinkedHashMap<>();

    public void setService(ModalidadHorariaService service) {
        this.service = service;
    }

    public void setUsuarioRepository(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Nombre de cada servicio, para la lista. */
    public void setServicios(Map<UUID, String> servicios) {
        this.servicios = servicios;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
    }

    public void postInitialize() {
        ar.com.hmu.util.FechaUtils.formatear(desdePicker, franquiciaHastaPicker);
        FilteredList<Usuario> filtrados;
        try {
            filtrados = new FilteredList<>(FXCollections.observableArrayList(usuarioRepository.readAll().stream()
                    .filter(Usuario::getEstado)
                    .sorted(Comparator.comparing(Usuario::getApellidosNombres, String.CASE_INSENSITIVE_ORDER))
                    .toList()));
        } catch (SQLException e) {
            AlertUtils.showErr("Error al leer el padrón: " + e.getMessage());
            return;
        }
        agentesList.setItems(filtrados);
        agentesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getApellidosNombres() + " — " + servicioDe(u));
            }
        });
        buscarField.textProperty().addListener((o, a, b) -> {
            String t = b == null ? "" : b.trim().toLowerCase();
            filtrados.setPredicate(u -> t.isEmpty() || u.getApellidosNombres().toLowerCase().contains(t)
                    || String.valueOf(u.getCuil()).contains(t) || servicioDe(u).toLowerCase().contains(t));
        });
        agentesList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> mostrar(b));

        modalidadCombo.getItems().setAll(Modalidad.values());
        modalidadCombo.valueProperty().addListener((o, a, b) -> mostrarParametros(b));
        desdePicker.valueProperty().addListener((o, a, b) -> mostrarAviso());
        franquiciaHorasField.disableProperty().bind(franquiciaCheck.selectedProperty().not());
        franquiciaHastaPicker.disableProperty().bind(franquiciaCheck.selectedProperty().not());
        mostrar(null);
    }

    /** Modo de capturas: selecciona al agente y propone una modalidad. */
    public void proponer(UUID agenteId, Modalidad m, LocalDate desde, Integer franquicia, LocalDate franquiciaHasta) {
        agentesList.getItems().stream().filter(u -> u.getId().equals(agenteId)).findFirst().ifPresent(u -> {
            agentesList.getSelectionModel().select(u);
            agentesList.scrollTo(u);
        });
        modalidadCombo.setValue(m);
        desdePicker.setValue(desde);
        franquiciaCheck.setSelected(franquicia != null);
        if (franquicia != null) {
            franquiciaHorasField.setText(String.valueOf(franquicia));
            franquiciaHastaPicker.setValue(franquiciaHasta);
        }
    }

    private String servicioDe(Usuario u) {
        String s = servicios != null && u.getServicioId() != null ? servicios.get(u.getServicioId()) : null;
        return s != null ? s : "sin servicio";
    }

    private void mostrar(Usuario u) {
        boolean hay = u != null;
        asignarButton.setDisable(!hay);
        modalidadCombo.setDisable(!hay);
        if (!hay) {
            agenteLabel.setText("Seleccione un agente");
            vigenteLabel.setText("");
            return;
        }
        agenteLabel.setText(u.getApellidosNombres() + " — " + servicioDe(u));
        try {
            HorarioBase h = service.vigente(u);
            vigenteLabel.setText(ModalidadHorariaService.describir(h));
            Modalidad m = h != null ? Modalidad.de(h) : null;
            modalidadCombo.setValue(m != null ? m : Modalidad.ESTANDAR);
        } catch (ServiceException e) {
            vigenteLabel.setText(e.getMessage());
        }
        if (desdePicker.getValue() == null) {
            desdePicker.setValue(Reloj.hoy().plusMonths(1).withDayOfMonth(1));
        }
        mostrarAviso();
    }

    private void mostrarParametros(Modalidad m) {
        parametrosGrid.getChildren().clear();
        campos.clear();
        descripcionLabel.setText(m != null ? m.descripcion() : "");
        if (m == null) return;
        int fila = 0;
        for (Parametro p : m.parametros()) {
            TextField f = new TextField(String.valueOf(p.valor()));
            f.setMaxWidth(70);
            campos.put(p.clave(), f);
            parametrosGrid.addRow(fila++, new Label(p.etiqueta() + ":"), f);
        }
    }

    private void mostrarAviso() {
        Usuario u = agentesList.getSelectionModel().getSelectedItem();
        Optional<String> aviso = service.advertencia(u, desdePicker.getValue());
        avisoLabel.setText(aviso.map(a -> "⚠ " + a).orElse(""));
        avisoLabel.setVisible(aviso.isPresent());
        avisoLabel.setManaged(aviso.isPresent());
    }

    @FXML
    private void onAsignar() {
        Usuario u = agentesList.getSelectionModel().getSelectedItem();
        if (u == null) return;
        Map<String, Integer> valores = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, TextField> e : campos.entrySet()) {
                valores.put(e.getKey(), Integer.parseInt(e.getValue().getText().trim()));
            }
            Integer franquicia = franquiciaCheck.isSelected()
                    ? Integer.parseInt(franquiciaHorasField.getText().trim()) : null;
            HorarioBase h = service.asignar(u, modalidadCombo.getValue(), valores, desdePicker.getValue(),
                    franquicia, franquiciaHastaPicker.getValue(), usuarioActual);
            AlertUtils.showInfo("Se asignó la modalidad a " + u.getApellidosNombres() + ":\n"
                    + ModalidadHorariaService.describir(h));
            mostrar(u);
        } catch (NumberFormatException e) {
            AlertUtils.showErr("Los parámetros deben ser números enteros.");
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }
}
