package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.novedades.NovedadService;
import ar.com.hmu.service.novedades.PerfilNovedades;
import ar.com.hmu.service.novedades.ResultadoValidacion;
import ar.com.hmu.service.novedades.ValidacionNovedadException;
import ar.com.hmu.service.novedades.ViolacionNovedad;
import ar.com.hmu.util.AlertUtils;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Registrar licencia de origen externo (P15; CU29): la OP asienta como
 * novedad informativa una licencia tramitada en VeDi o MiDocta; si se
 * superpone con otra novedad, se informa y se pide confirmación (3a).
 */
public class LicenciaExternaController {

    @FXML private ComboBox<Usuario> agenteCombo;
    @FXML private ComboBox<ar.com.hmu.model.CodigoNovedad> tipoCombo;
    @FXML private DatePicker desdePicker;
    @FXML private DatePicker hastaPicker;
    @FXML private Label diasLabel;
    @FXML private TextField referenciaField;
    @FXML private TextArea observacionesArea;
    @FXML private ListView<ViolacionNovedad> validacionList;
    @FXML private Button registrarButton;

    private NovedadService service;
    private Usuario usuarioActual;
    private Stage stage;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    public void setService(NovedadService service) {
        this.service = service;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCancelar);
    }

    public void postInitialize() {
        ar.com.hmu.util.FechaUtils.formatear(desdePicker, hastaPicker);
        try {
            agenteCombo.getItems().setAll(service.agentesSeleccionables(usuarioActual, PerfilNovedades.OFICINA_PERSONAL));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        agenteCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Usuario u) { return u == null ? "" : u.getApellidosNombres(); }
            @Override public Usuario fromString(String s) { return null; }
        });
        // Tipos de licencia: códigos del catálogo de novedades marcados como licencia.
        try {
            tipoCombo.getItems().setAll(service.tiposDeLicencia());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        tipoCombo.valueProperty().addListener((o, a, b) -> debounce.playFromStart());
        validacionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ViolacionNovedad v, boolean empty) {
                super.updateItem(v, empty);
                getStyleClass().removeAll("item-bloqueante", "item-advertencia", "item-ok");
                if (empty || v == null) {
                    setText(null);
                } else if (v.regla() == null) {
                    setText("✔ " + v.mensaje());
                    getStyleClass().add("item-ok");
                } else {
                    setText((v.esBloqueante() ? "✖ " : "⚠ ") + v.mensaje());
                    getStyleClass().add(v.esBloqueante() ? "item-bloqueante" : "item-advertencia");
                }
            }
        });
        debounce.setOnFinished(e -> validar());
        agenteCombo.valueProperty().addListener((o, a, b) -> debounce.playFromStart());
        desdePicker.valueProperty().addListener((o, a, b) -> {
            if (b != null && (hastaPicker.getValue() == null || hastaPicker.getValue().isBefore(b))) {
                hastaPicker.setValue(b);
            }
            debounce.playFromStart();
        });
        hastaPicker.valueProperty().addListener((o, a, b) -> debounce.playFromStart());
        validar();
    }

    /** Modo de capturas. */
    public void completar(UUID agenteId, int codigoLicencia, LocalDate desde, LocalDate hasta, String referencia,
                          String obs) {
        agenteCombo.getItems().stream().filter(u -> u.getId().equals(agenteId)).findFirst().ifPresent(agenteCombo::setValue);
        tipoCombo.getItems().stream().filter(c -> c.codigo() == codigoLicencia).findFirst()
                .ifPresent(tipoCombo::setValue);
        desdePicker.setValue(desde);
        hastaPicker.setValue(hasta);
        referenciaField.setText(referencia);
        observacionesArea.setText(obs);
        validar();
    }

    private Novedad armar() {
        Novedad n = new Novedad();
        Usuario a = agenteCombo.getValue();
        n.setAgenteId(a != null ? a.getId() : null);
        n.setFechaDesde(desdePicker.getValue());
        n.setFechaHasta(hastaPicker.getValue());
        n.setReferenciaExterna(referenciaField.getText() != null ? referenciaField.getText().trim() : null);
        ar.com.hmu.model.CodigoNovedad tipo = tipoCombo.getValue();
        n.setCodigoNovedad(tipo != null ? tipo.codigo() : null);
        String obs = observacionesArea.getText() != null ? observacionesArea.getText().trim() : "";
        n.setMotivo("Licencia " + (tipo != null ? "por " + tipo.denominacionHmu().toLowerCase() : "")
                + (obs.isEmpty() ? "" : ". " + obs));
        return n;
    }

    private void validar() {
        LocalDate d = desdePicker.getValue();
        LocalDate h = hastaPicker.getValue();
        diasLabel.setText(d != null && h != null && !h.isBefore(d) ? (ChronoUnit.DAYS.between(d, h) + 1) + " días" : "");
        Novedad n = armar();
        if (n.getAgenteId() == null || d == null || h == null) {
            validacionList.getItems().setAll(new ViolacionNovedad("datos", ar.com.hmu.model.Severidad.BLOQUEANTE,
                    "Complete el agente y el período de la licencia."));
            registrarButton.setDisable(true);
            return;
        }
        try {
            ResultadoValidacion r = service.validarLicenciaExterna(n);
            validacionList.getItems().setAll(r.violaciones().isEmpty()
                    ? List.of(new ViolacionNovedad(null, null, "Sin superposiciones: la licencia puede registrarse."))
                    : r.violaciones());
            registrarButton.setDisable(r.hayBloqueantes());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onRegistrar() {
        boolean conAdvertencias = validacionList.getItems().stream().anyMatch(v -> v.regla() != null && !v.esBloqueante());
        if (conAdvertencias && !AlertUtils.showConfirm("La licencia se superpone con otras novedades (ver validación). "
                + "¿Registrarla igualmente?")) {
            return;
        }
        try {
            Novedad n = service.registrarLicenciaExterna(armar(), usuarioActual);
            AlertUtils.showInfo("Se registró la licencia de " + n.getAgenteNombre() + " (" + n.getReferenciaExterna()
                    + ") y se marcaron sus jornadas.");
            stage.close();
        } catch (ValidacionNovedadException e) {
            validacionList.getItems().setAll(e.getResultado().violaciones());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCancelar() {
        stage.close();
    }
}
