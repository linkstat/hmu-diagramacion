package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.MovimientoHoras;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.novedades.NovedadService;
import ar.com.hmu.service.novedades.PerfilNovedades;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.Reloj;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Banco de horas del agente: créditos de horas extras para FC con su
 * vencimiento (regla RV-BANCO-VENC), débitos de francos compensatorios por
 * orden de vencimiento (FIFO), vencimientos y saldo vigente. El agente ve el
 * suyo; la JS, los de su servicio; la OP y la DH, todos.
 */
public class BancoHorasController {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label agenteLabel;
    @FXML private Label agenteComboLabel;
    @FXML private ComboBox<Usuario> agenteCombo;
    @FXML private Label saldoLabel;
    @FXML private Label proximoVencimientoLabel;
    @FXML private TableView<MovimientoHoras> movimientosTable;
    @FXML private Label ayudaLabel;

    private NovedadService service;
    private Usuario usuarioActual;
    private Stage stage;

    public void setService(NovedadService service) {
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
        configurarTabla();
        PerfilNovedades perfil = usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL) ? PerfilNovedades.OFICINA_PERSONAL
                : usuarioActual.hasRole(TipoUsuario.DIRECCION) ? PerfilNovedades.DIRECCION
                : usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO) ? PerfilNovedades.JEFATURA
                : PerfilNovedades.AGENTE;
        try {
            agenteCombo.getItems().setAll(service.agentesSeleccionables(usuarioActual, perfil));
        } catch (ServiceException e) {
            agenteCombo.getItems().setAll(usuarioActual);
        }
        agenteCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Usuario u) { return u == null ? "" : u.getApellidosNombres(); }
            @Override public Usuario fromString(String s) { return null; }
        });
        boolean elige = agenteCombo.getItems().size() > 1;
        agenteCombo.setVisible(elige);
        agenteCombo.setManaged(elige);
        agenteComboLabel.setVisible(elige);
        agenteComboLabel.setManaged(elige);
        agenteCombo.valueProperty().addListener((o, a, b) -> cargar());
        ayudaLabel.setText("Las horas extras para FC se acreditan al cotejar las marcaciones de la jornada y vencen a "
                + "los 180 días (regla RV-BANCO-VENC). Los francos compensatorios consumen primero las horas que "
                + "vencen antes. El saldo vigente no incluye lo vencido.");
        seleccionar(usuarioActual.getId());
    }

    /** Muestra el banco de un agente (también lo usa el modo de capturas). */
    public void seleccionar(UUID agenteId) {
        agenteCombo.getItems().stream().filter(u -> u.getId().equals(agenteId)).findFirst()
                .ifPresentOrElse(agenteCombo::setValue, () -> agenteCombo.getSelectionModel().selectFirst());
    }

    private void configurarTabla() {
        movimientosTable.getColumns().add(columna("FECHA", 95, m -> m.fecha().format(DDMMYYYY)));
        movimientosTable.getColumns().add(columna("MOVIMIENTO", 105, m -> m.tipo().toString()));
        movimientosTable.getColumns().add(columna("CONCEPTO", 640, MovimientoHoras::concepto));
        movimientosTable.getColumns().add(columna("HORAS", 75, m -> conSigno(m.horas())));
        movimientosTable.getColumns().add(columna("VENCE", 95, m -> m.fechaVencimiento() != null
                ? m.fechaVencimiento().format(DDMMYYYY) : ""));
        movimientosTable.getColumns().add(columna("RESTANTES", 90, m -> m.tipo() == MovimientoHoras.Tipo.CREDITO
                ? horas(m.horasRestantes()) : ""));
        movimientosTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movimientosTable.setPlaceholder(new Label("El agente no tiene movimientos en el banco de horas."));
        LocalDate hoy = Reloj.hoy();
        movimientosTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MovimientoHoras m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("credito-vigente", "movimiento-vencido");
                if (!empty && m != null) {
                    if (m.vigenteEl(hoy)) getStyleClass().add("credito-vigente");
                    if (m.tipo() == MovimientoHoras.Tipo.VENCIMIENTO) getStyleClass().add("movimiento-vencido");
                }
            }
        });
    }

    private void cargar() {
        Usuario agente = agenteCombo.getValue();
        if (agente == null) {
            return;
        }
        agenteLabel.setText(agente.getApellidosNombres());
        try {
            List<MovimientoHoras> movimientos = service.movimientosHoras(agente.getId(), usuarioActual);
            movimientosTable.getItems().setAll(movimientos);
            LocalDate hoy = Reloj.hoy();
            BigDecimal saldo = service.saldoHoras(agente.getId());
            saldoLabel.setText("Saldo vigente: " + horas(saldo) + " h");
            movimientos.stream().filter(m -> m.vigenteEl(hoy))
                    .min(Comparator.comparing(MovimientoHoras::fechaVencimiento,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .ifPresentOrElse(m -> proximoVencimientoLabel.setText("Próximo vencimiento: "
                                    + horas(m.horasRestantes()) + " h el "
                                    + (m.fechaVencimiento() != null ? m.fechaVencimiento().format(DDMMYYYY) : "—")),
                            () -> proximoVencimientoLabel.setText("Sin horas vigentes."));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }

    private static String horas(BigDecimal h) {
        return h == null ? "" : h.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String conSigno(BigDecimal h) {
        return h == null ? "" : (h.signum() > 0 ? "+" : "") + horas(h);
    }

    private static TableColumn<MovimientoHoras, String> columna(String titulo, double ancho,
                                                                Function<MovimientoHoras, String> f) {
        TableColumn<MovimientoHoras, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setSortable(false);
        c.setCellValueFactory(cd -> new SimpleStringProperty(Objects.toString(f.apply(cd.getValue()), "")));
        return c;
    }
}
