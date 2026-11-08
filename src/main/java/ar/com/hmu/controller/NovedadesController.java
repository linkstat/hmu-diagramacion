package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.EstadoNovedadAutorizacion;
import ar.com.hmu.model.InstanciaNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.NovedadAdjunto;
import ar.com.hmu.model.NovedadAutorizacion;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.novedades.NovedadService;
import ar.com.hmu.service.novedades.PerfilNovedades;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.AppInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pantalla de novedades (P03, Figura 23 del E4; CU20–CU25 y CU28): bandeja
 * filtrable por estado, detalle de la novedad seleccionada, historial de
 * instancias con usuario, fecha y hora, resultado y comentario, y las
 * acciones de la instancia que corresponde al perfil (autorizar / verificar
 * / aprobar, devolver para corrección y rechazar).
 */
public class NovedadesController {

    /** Fila del historial de instancias (CU28). */
    public record FilaHistorial(String instancia, String usuario, String fechaHora, String resultado,
                                String claseResultado, String comentario) {
    }

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DDMM_HHMM = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter DDMMYYYY_HHMM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TODOS = "Todos";
    private static final ButtonType CANCELAR = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

    @FXML private Label perfilLabel;
    @FXML private ComboBox<PerfilNovedades> perfilCombo;
    @FXML private ComboBox<String> estadoFiltroCombo;
    @FXML private TextField agenteFiltroField;
    @FXML private TableView<Novedad> novedadesTable;
    @FXML private TableColumn<Novedad, String> agenteCol;
    @FXML private TableColumn<Novedad, String> tipoCol;
    @FXML private TableColumn<Novedad, String> desdeCol;
    @FXML private TableColumn<Novedad, String> hastaCol;
    @FXML private TableColumn<Novedad, EstadoNovedad> estadoCol;
    @FXML private Label contadorLabel;
    @FXML private Label detalleTituloLabel;
    @FXML private Label detalleEstadoChip;
    @FXML private GridPane detalleGrid;
    @FXML private TableView<FilaHistorial> historialTable;
    @FXML private TableColumn<FilaHistorial, String> instanciaCol;
    @FXML private TableColumn<FilaHistorial, String> usuarioCol;
    @FXML private TableColumn<FilaHistorial, String> fechaHoraCol;
    @FXML private TableColumn<FilaHistorial, FilaHistorial> resultadoCol;
    @FXML private TableColumn<FilaHistorial, String> comentarioCol;
    @FXML private Label ayudaLabel;
    @FXML private Button nuevaButton;
    @FXML private Button licenciaButton;
    @FXML private Button corregirButton;
    @FXML private Button anularButton;
    @FXML private Button secretariaButton;
    @FXML private Button autorizarButton;
    @FXML private Button devolverButton;
    @FXML private Button rechazarButton;

    private NovedadService novedadService;
    private Usuario usuarioActual;
    private PerfilNovedades perfil;
    private Stage stage;

    private final ObservableList<Novedad> novedades = FXCollections.observableArrayList();
    private FilteredList<Novedad> filtradas;
    /** Detalle (con instancias y adjuntos) de la novedad seleccionada. */
    private Novedad seleccionada;

    // ============================================================
    // Inyección + inicialización
    // ============================================================

    public void setNovedadService(NovedadService novedadService) {
        this.novedadService = novedadService;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    /** Llamar después de inyectar dependencias. */
    public void postInitialize() {
        List<PerfilNovedades> perfiles = PerfilNovedades.de(usuarioActual);
        perfil = perfiles.get(0);
        if (perfiles.size() > 1) {
            perfilCombo.getItems().setAll(perfiles);
            perfilCombo.setValue(perfil);
            perfilCombo.setVisible(true);
            perfilCombo.setManaged(true);
            perfilLabel.setVisible(false);
            perfilLabel.setManaged(false);
            perfilCombo.valueProperty().addListener((obs, a, b) -> {
                if (b != null) {
                    perfil = b;
                    aplicarPerfil();
                    onRefrescar();
                }
            });
        }
        configurarFiltros();
        configurarTablas();
        aplicarPerfil();
        onRefrescar();
    }

    /** La ventana de la pantalla (los nodos dentro del SplitPane reciben la escena recién al mostrarse). */
    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
    }

    public Stage getStage() {
        return stage;
    }

    /** Para el modo de capturas: fija el perfil sin pasar por el combo. */
    public void usarPerfil(PerfilNovedades p) {
        if (perfilCombo.isVisible()) {
            perfilCombo.setValue(p);
        } else {
            perfil = p;
            aplicarPerfil();
            onRefrescar();
        }
    }

    /** Para el modo de capturas: selecciona una novedad por id. */
    public void seleccionar(UUID novedadId) {
        for (Novedad n : novedadesTable.getItems()) {
            if (n.getId().equals(novedadId)) {
                novedadesTable.getSelectionModel().clearAndSelect(novedadesTable.getItems().indexOf(n));
                novedadesTable.scrollTo(n);
                return;
            }
        }
    }

    public void filtrarEstado(String etiqueta) {
        estadoFiltroCombo.setValue(etiqueta);
    }

    private void configurarFiltros() {
        estadoFiltroCombo.getItems().add(TODOS);
        for (EstadoNovedad e : EstadoNovedad.values()) {
            estadoFiltroCombo.getItems().add(e.getEtiqueta());
        }
        estadoFiltroCombo.setValue(TODOS);
        filtradas = new FilteredList<>(novedades, n -> true);
        novedadesTable.setItems(filtradas);
        estadoFiltroCombo.valueProperty().addListener((o, a, b) -> aplicarFiltro());
        agenteFiltroField.textProperty().addListener((o, a, b) -> aplicarFiltro());
    }

    private void aplicarFiltro() {
        String estado = estadoFiltroCombo.getValue();
        String texto = agenteFiltroField.getText() == null ? "" : agenteFiltroField.getText().trim().toLowerCase();
        filtradas.setPredicate(n -> (estado == null || TODOS.equals(estado) || n.getEstado().getEtiqueta().equals(estado))
                && (texto.isEmpty() || n.getAgenteNombre().toLowerCase().contains(texto)));
        actualizarContador();
    }

    private void configurarTablas() {
        novedadesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        agenteCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAgenteNombre()));
        tipoCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTipo().nombre()));
        desdeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFechaDesde().format(DDMMYYYY)));
        hastaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFechaHasta().format(DDMMYYYY)));
        estadoCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getEstado()));
        estadoCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(EstadoNovedad estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setGraphic(null);
                } else {
                    Label chip = new Label(estado.getEtiqueta());
                    chip.getStyleClass().addAll("estado-chip", claseEstado(estado));
                    setGraphic(chip);
                }
                setText(null);
            }
        });
        novedadesTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> mostrarDetalle(b));
        novedadesTable.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<Novedad>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty() && perfil == PerfilNovedades.AGENTE
                        && !corregirButton.isDisabled()) {
                    onCorregir();
                }
            });
            return row;
        });

        instanciaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().instancia()));
        usuarioCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().usuario()));
        fechaHoraCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().fechaHora()));
        comentarioCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().comentario()));
        resultadoCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        resultadoCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FilaHistorial f, boolean empty) {
                super.updateItem(f, empty);
                getStyleClass().removeAll("res-positivo", "res-pendiente", "res-observada", "res-rechazada",
                        "res-sin-resolver");
                if (empty || f == null) {
                    setText(null);
                } else {
                    setText(f.resultado());
                    getStyleClass().add(f.claseResultado());
                }
            }
        });
        historialTable.setPlaceholder(new Label(""));
        novedadesTable.setPlaceholder(new Label("No hay novedades para mostrar."));
    }

    // ============================================================
    // Perfil
    // ============================================================

    private void aplicarPerfil() {
        perfilLabel.setText(perfil.toString());
        boolean agente = perfil == PerfilNovedades.AGENTE;
        mostrar(nuevaButton, true);
        mostrar(licenciaButton, perfil == PerfilNovedades.OFICINA_PERSONAL);
        mostrar(corregirButton, agente);
        mostrar(anularButton, agente);
        mostrar(secretariaButton, perfil == PerfilNovedades.OFICINA_PERSONAL);
        mostrar(autorizarButton, !agente);
        mostrar(devolverButton, !agente);
        mostrar(rechazarButton, !agente);
        switch (perfil) {
            case JEFATURA -> {
                autorizarButton.setText("Autorizar");
                ayudaLabel.setText("Autorizar eleva la novedad a la Oficina de Personal. "
                        + "Devolver la regresa al agente con comentarios.");
            }
            case OFICINA_PERSONAL -> {
                autorizarButton.setText("Verificar");
                ayudaLabel.setText("Verificar eleva la novedad a la Dirección. "
                        + "Devolver la regresa al agente con comentarios.");
            }
            case DIRECCION -> {
                autorizarButton.setText("Aprobar");
                ayudaLabel.setText("Aprobar aplica la novedad al diagrama vigente. "
                        + "Con Ctrl+clic se seleccionan varias para aprobarlas juntas.");
            }
            case AGENTE -> ayudaLabel.setText("Una solicitud observada vuelve a usted con los comentarios: "
                    + "corríjala y reenvíela.");
        }
        actualizarBotones();
    }

    private static void mostrar(Button b, boolean visible) {
        b.setVisible(visible);
        b.setManaged(visible);
    }

    // ============================================================
    // Carga y detalle
    // ============================================================

    @FXML
    public void onRefrescar() {
        UUID previa = seleccionada != null ? seleccionada.getId() : null;
        try {
            novedades.setAll(novedadService.bandeja(usuarioActual, perfil));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
            novedades.clear();
        }
        aplicarFiltro();
        if (previa != null) {
            seleccionar(previa);
        }
        if (novedadesTable.getSelectionModel().isEmpty()) {
            mostrarDetalle(null);
        }
    }

    private void actualizarContador() {
        int total = filtradas.size();
        long pendientes = novedades.stream().filter(n -> novedadService.puedeResolver(n, usuarioActual, perfil)).count();
        String sufijo = switch (perfil) {
            case JEFATURA -> pendientes + " por autorizar por la Jefatura";
            case OFICINA_PERSONAL -> pendientes + " por resolver por la Oficina de Personal";
            case DIRECCION -> pendientes + " por aprobar por la Dirección";
            case AGENTE -> novedades.stream().filter(n -> n.getEstado().esPendiente()).count() + " en trámite";
        };
        contadorLabel.setText(total + (total == 1 ? " novedad" : " novedades") + " · " + sufijo);
    }

    private void mostrarDetalle(Novedad fila) {
        detalleGrid.getChildren().clear();
        historialTable.getItems().clear();
        if (fila == null) {
            seleccionada = null;
            detalleTituloLabel.setText("Seleccione una novedad");
            detalleEstadoChip.setVisible(false);
            actualizarBotones();
            return;
        }
        try {
            seleccionada = novedadService.detalle(fila.getId());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
            return;
        }
        Novedad n = seleccionada;
        detalleTituloLabel.setText(n.getTipo().nombreConCodigo());
        detalleEstadoChip.setText(n.getEstado().getEtiqueta());
        detalleEstadoChip.getStyleClass().setAll("label", "estado-chip", claseEstado(n.getEstado()));
        detalleEstadoChip.setVisible(true);

        int fil = 0;
        fil = campo(fil, "Agente:", n.getAgenteNombre() + (n.getServicioNombre() != null ? " — " + n.getServicioNombre() : ""));
        if (n.getContraparteAgenteId() != null) {
            fil = campo(fil, "Intercambia con:", n.getContraparteNombre()
                    + (n.getServicioNombre() != null ? " — " + n.getServicioNombre() : ""));
        }
        fil = camposDelTipo(fil, n);
        if (n.getFechaSolicitud() != null) {
            fil = campo(fil, "Solicitada:", n.getFechaSolicitud().format(DDMMYYYY_HHMM)
                    + (n.esSolicitadaPorElAgente() ? "" : " por " + n.getSolicitadoPorNombre()));
        }
        if (n.getReferenciaExterna() != null) {
            fil = campo(fil, "Referencia:", n.getReferenciaExterna());
        }
        if (n.getMotivo() != null && !n.getMotivo().isBlank()) {
            fil = campo(fil, "Motivo:", n.getMotivo());
        }
        if (n.getJustificacion() != null) {
            fil = campo(fil, "Justificación:", n.getJustificacion());
        }
        if (!n.getAdjuntos().isEmpty()) {
            FlowPane links = new FlowPane(8, 2);
            for (NovedadAdjunto a : n.getAdjuntos()) {
                Hyperlink h = new Hyperlink(a.descripcion());
                h.setPadding(javafx.geometry.Insets.EMPTY);
                h.setOnAction(e -> guardarAdjunto(a));
                links.getChildren().add(h);
            }
            Label et = new Label("Adjunto:");
            et.getStyleClass().add("detalle-etiqueta");
            detalleGrid.addRow(fil++, et, links);
        }
        historialTable.getItems().setAll(historial(n));
        actualizarBotones();
    }

    private int camposDelTipo(int fil, Novedad n) {
        TipoNovedad t = n.getTipo();
        switch (t.codigo()) {
            case TipoNovedad.CAMBIO_GUARDIA -> {
                String apA = apellido(n.getAgenteNombre());
                String apB = apellido(n.getContraparteNombre());
                LocalDate cedida = fechaCedida(n);
                LocalDate recibida = cedida.equals(n.getFechaDesde()) ? n.getFechaHasta() : n.getFechaDesde();
                fil = campo(fil, "Guardia cedida:", guardia(n.getAgenteId(), n.getContraparteAgenteId(),
                        cedida) + " (pasa a " + apB + ")");
                fil = campo(fil, "Guardia recibida:", guardia(n.getContraparteAgenteId(), n.getAgenteId(),
                        recibida) + " (pasa a " + apA + ")");
            }
            // Las horas extras no llevan horario: se computan de las marcaciones al cotejar.
            case TipoNovedad.HORAS_EXTRA -> {
                fil = campo(fil, "Fecha:", n.getFechaDesde().format(DDMMYYYY));
                fil = campo(fil, "Horas:", n.getCantHoras() != null
                        ? n.getCantHoras().stripTrailingZeros().toPlainString() + " h computadas de las marcaciones"
                        : "Se computan de las marcaciones al cotejar la jornada.");
            }
            case TipoNovedad.CAMBIO_HORARIO, TipoNovedad.PASE_SALIDA_OFICIAL, TipoNovedad.PASE_SALIDA_PARTICULAR -> {
                String etiqueta = t.es(TipoNovedad.CAMBIO_HORARIO) ? "Nuevo horario:" : "Horario:";
                String horas = n.getCantHoras() != null
                        ? " (" + n.getCantHoras().stripTrailingZeros().toPlainString() + " h)" : "";
                fil = campo(fil, etiqueta, n.getFechaDesde().format(DDMMYYYY) + " "
                        + (n.getHoraDesde() != null ? n.getHoraDesde().format(HHMM) : "?") + " → "
                        + (n.getHoraHasta() != null ? n.getHoraHasta().format(HHMM) : "?") + horas);
            }
            case TipoNovedad.FRANCO_COMPENSATORIO -> {
                fil = campo(fil, "Período:", periodo(n));
                if (n.getCantHoras() != null) {
                    fil = campo(fil, "Horas:", n.getCantHoras().stripTrailingZeros().toPlainString()
                            + " h del saldo de horas");
                }
            }
            default -> fil = campo(fil, "Período:", periodo(n));
        }
        return fil;
    }

    /** Fecha de la guardia que cedió el agente (ya aplicada, las jornadas están intercambiadas). */
    private LocalDate fechaCedida(Novedad n) {
        boolean aplicada = n.getEstado() == EstadoNovedad.APLICADA;
        UUID original = aplicada ? n.getContraparteAgenteId() : n.getAgenteId();
        UUID otro = aplicada ? n.getAgenteId() : n.getContraparteAgenteId();
        List<JornadaLaboral> deOriginal = new ArrayList<>();
        List<JornadaLaboral> deOtro = new ArrayList<>();
        for (LocalDate d : List.of(n.getFechaDesde(), n.getFechaHasta())) {
            novedadService.jornadaVigente(original, d).ifPresent(deOriginal::add);
            novedadService.jornadaVigente(otro, d).ifPresent(deOtro::add);
        }
        return ar.com.hmu.service.novedades.ValidadorNovedad.fechaCedida(n, deOriginal, deOtro);
    }

    /** "18/10/2026 20:00 → 19/10/2026 08:00": la guardia de ese día, esté en quien la tenía o en quien la recibió. */
    private String guardia(UUID original, UUID receptor, LocalDate fecha) {
        Optional<JornadaLaboral> j = novedadService.jornadaVigente(original, fecha).filter(JornadaLaboral::tieneHorario);
        if (j.isEmpty()) {
            j = novedadService.jornadaVigente(receptor, fecha).filter(JornadaLaboral::tieneHorario);
        }
        return j.map(x -> x.getFechaIngreso().format(DDMMYYYY_HHMM) + " → " + x.getFechaEgreso().format(DDMMYYYY_HHMM))
                .orElse(fecha.format(DDMMYYYY));
    }

    private static String periodo(Novedad n) {
        return n.getFechaDesde().equals(n.getFechaHasta())
                ? n.getFechaDesde().format(DDMMYYYY)
                : n.getFechaDesde().format(DDMMYYYY) + " al " + n.getFechaHasta().format(DDMMYYYY);
    }

    private static String apellido(String apellidoNombre) {
        if (apellidoNombre == null) return "?";
        int i = apellidoNombre.indexOf(',');
        return i > 0 ? apellidoNombre.substring(0, i) : apellidoNombre;
    }

    private int campo(int fila, String etiqueta, String valor) {
        Label et = new Label(etiqueta);
        et.getStyleClass().add("detalle-etiqueta");
        Label v = new Label(valor);
        v.setWrapText(true);
        detalleGrid.addRow(fila, et, v);
        GridPane.setValignment(et, javafx.geometry.VPos.TOP);
        return fila + 1;
    }

    /**
     * Historial (CU28): la presentación, cada instancia resuelta con su
     * autor, fecha, resultado y fundamento, los reenvíos tras una
     * observación, y las instancias que faltan.
     */
    private List<FilaHistorial> historial(Novedad n) {
        List<FilaHistorial> filas = new ArrayList<>();
        String quien = n.esSolicitadaPorElAgente() ? "Agente" : "Solicitante";
        if (n.getFechaSolicitud() != null) {
            String fundamento = (n.getJustificacion() != null ? "Justificación: " + n.getJustificacion() + ". " : "")
                    + (n.getAdjuntos().isEmpty() ? "" : "Con constancia.");
            filas.add(new FilaHistorial(quien, n.getSolicitadoPorNombre(), n.getFechaSolicitud().format(DDMM_HHMM),
                    "Presentada", "res-positivo", fundamento.strip()));
        }
        List<NovedadAutorizacion> auts = new ArrayList<>(n.getAutorizaciones());
        auts.sort(Comparator.comparingInt(NovedadAutorizacion::getOrden));
        Optional<NovedadAutorizacion> actual = n.instanciaPendiente();
        InstanciaNovedad anterior = null;
        for (int i = 0; i < auts.size(); i++) {
            NovedadAutorizacion a = auts.get(i);
            if (i > 0 && auts.get(i - 1).getEstado() == EstadoNovedadAutorizacion.OBSERVADO) {
                filas.add(new FilaHistorial(quien, n.getSolicitadoPorNombre(), "—", "Reenviada", "res-positivo",
                        "Corregida y reenviada."));
            }
            String etiqueta = a.getInstancia().getEtiqueta();
            if (a.estaPendiente()) {
                boolean esActual = actual.isPresent() && actual.get().getId().equals(a.getId());
                if (esActual) {
                    // Nadie resolvió todavía: no se muestra usuario.
                    filas.add(new FilaHistorial(etiqueta, "—", "—",
                            "Pendiente", "res-pendiente", comentarioPendiente(a.getInstancia())));
                } else {
                    filas.add(new FilaHistorial(etiqueta, "—", "—", "Sin resolver", "res-sin-resolver",
                            "Espera a " + sigla(anterior) + "."));
                }
            } else {
                filas.add(new FilaHistorial(etiqueta, a.getResueltoPorNombre(),
                        a.getFechaResolucion() != null ? a.getFechaResolucion().format(DDMM_HHMM) : "—",
                        resultado(a), claseResultado(a.getEstado()), a.getComentarios() != null ? a.getComentarios() : ""));
            }
            anterior = a.getInstancia();
        }
        if (n.getEstado() == EstadoNovedad.APLICADA) {
            filas.add(new FilaHistorial("Sistema", "—", n.getUpdatedAt().format(DDMM_HHMM), "Aplicada",
                    "res-positivo", "Aplicada al diagrama vigente."));
        } else if (n.getEstado() == EstadoNovedad.ANULADA) {
            filas.add(new FilaHistorial(quien, n.getSolicitadoPorNombre(), n.getUpdatedAt().format(DDMM_HHMM),
                    "Anulada", "res-sin-resolver", ""));
        }
        return filas;
    }

    private static String comentarioPendiente(InstanciaNovedad i) {
        return switch (i) {
            case JEFATURA -> "En evaluación de la cobertura.";
            case OFICINA_PERSONAL -> "En verificación.";
            case DIRECCION -> "En evaluación.";
            case SECRETARIA_SALUD -> "Espera el expediente.";
        };
    }

    private static String sigla(InstanciaNovedad i) {
        if (i == null) return "la instancia anterior";
        return switch (i) {
            case JEFATURA -> "JS";
            case OFICINA_PERSONAL -> "OP";
            case DIRECCION -> "DH";
            case SECRETARIA_SALUD -> "Salud";
        };
    }

    private static String resultado(NovedadAutorizacion a) {
        return switch (a.getEstado()) {
            case AUTORIZADO -> switch (a.getInstancia()) {
                case JEFATURA, SECRETARIA_SALUD -> "Autorizada";
                case OFICINA_PERSONAL -> "Verificada";
                case DIRECCION -> "Aprobada";
            };
            case OBSERVADO -> "Observada";
            case RECHAZADO -> "Rechazada";
            case PENDIENTE -> "Pendiente";
        };
    }

    private static String claseResultado(EstadoNovedadAutorizacion e) {
        return switch (e) {
            case AUTORIZADO -> "res-positivo";
            case OBSERVADO -> "res-observada";
            case RECHAZADO -> "res-rechazada";
            case PENDIENTE -> "res-pendiente";
        };
    }

    static String claseEstado(EstadoNovedad e) {
        return switch (e) {
            case BORRADOR -> "nov-borrador";
            case PENDIENTE_JEFATURA, PENDIENTE_OFICINA_PERSONAL, PENDIENTE_DIRECCION -> "nov-pendiente";
            case OBSERVADA -> "nov-observada";
            case APROBADA -> "nov-aprobada";
            case APLICADA -> "nov-aplicada";
            case RECHAZADA -> "nov-rechazada";
            case ANULADA -> "nov-anulada";
        };
    }

    private void actualizarBotones() {
        Novedad n = seleccionada;
        boolean puede = n != null && novedadService.puedeResolver(n, usuarioActual, perfil);
        boolean pendienteSecretaria = puede && n.instanciaPendiente()
                .map(a -> a.getInstancia() == InstanciaNovedad.SECRETARIA_SALUD).orElse(false);
        int seleccion = novedadesTable.getSelectionModel().getSelectedItems().size();
        autorizarButton.setDisable(!(puede && !pendienteSecretaria) && !(perfil == PerfilNovedades.DIRECCION && seleccion > 1));
        devolverButton.setDisable(!puede || pendienteSecretaria || seleccion > 1);
        rechazarButton.setDisable(!puede || pendienteSecretaria || seleccion > 1);
        secretariaButton.setDisable(!pendienteSecretaria);
        boolean editable = n != null && (n.getEstado() == EstadoNovedad.BORRADOR || n.getEstado() == EstadoNovedad.OBSERVADA)
                && (usuarioActual.getId().equals(n.getSolicitadoPorId()) || usuarioActual.getId().equals(n.getAgenteId()));
        corregirButton.setDisable(!editable);
        anularButton.setDisable(!editable);
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onAutorizar() {
        if (perfil == PerfilNovedades.DIRECCION && novedadesTable.getSelectionModel().getSelectedItems().size() > 1) {
            aprobarLote();
            return;
        }
        if (seleccionada == null) return;
        Optional<String> comentario = pedirTexto(autorizarButton.getText() + " novedad",
                resumen(seleccionada) + "\nComentario (opcional):", false, autorizarButton.getText());
        if (comentario.isEmpty()) return;
        ejecutar(() -> novedadService.autorizar(seleccionada, usuarioActual, perfil, comentario.get()));
    }

    private void aprobarLote() {
        List<Novedad> lote = new ArrayList<>(novedadesTable.getSelectionModel().getSelectedItems());
        lote.removeIf(n -> !novedadService.puedeResolver(n, usuarioActual, perfil));
        if (lote.isEmpty()) {
            AlertUtils.showWarn("Ninguna de las novedades seleccionadas está pendiente de la Dirección.");
            return;
        }
        if (!AlertUtils.showConfirm("¿Aprobar " + lote.size() + " novedades? Se aplicarán al diagrama vigente.")) {
            return;
        }
        List<String> errores = novedadService.aprobarLote(lote, usuarioActual);
        if (!errores.isEmpty()) {
            AlertUtils.showWarn("No se pudieron aprobar:\n" + String.join("\n", errores));
        }
        onRefrescar();
    }

    @FXML
    private void onDevolver() {
        if (seleccionada == null) return;
        Optional<String> motivo = pedirTexto("Devolver para corrección",
                resumen(seleccionada) + "\nIndique qué debe corregir el agente:", true, "Devolver");
        motivo.ifPresent(m -> ejecutar(() -> novedadService.observar(seleccionada, usuarioActual, perfil, m)));
    }

    @FXML
    private void onRechazar() {
        if (seleccionada == null) return;
        Optional<String> motivo = pedirTexto("Rechazar novedad",
                resumen(seleccionada) + "\nFundamento del rechazo:", true, "Rechazar");
        motivo.ifPresent(m -> ejecutar(() -> novedadService.rechazar(seleccionada, usuarioActual, perfil, m)));
    }

    @FXML
    private void onSecretaria() {
        if (seleccionada == null) return;
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Resultado de la Secretaría de Salud");
        d.setHeaderText(resumen(seleccionada) + "\nSupera el tope mensual de faltas justificadas.");
        ToggleGroup g = new ToggleGroup();
        RadioButton si = new RadioButton("Autorizada");
        RadioButton no = new RadioButton("No autorizada");
        si.setToggleGroup(g);
        no.setToggleGroup(g);
        si.setSelected(true);
        TextField expediente = new TextField();
        expediente.setPromptText("Referencia del expediente");
        VBox box = new VBox(8, new HBox(16, si, no), new Label("Expediente:"), expediente);
        d.getDialogPane().setContent(box);
        ButtonType registrar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(registrar, CANCELAR);
        d.getDialogPane().lookupButton(registrar).disableProperty().bind(expediente.textProperty().isEmpty());
        if (d.showAndWait().filter(b -> b == registrar).isPresent()) {
            ejecutar(() -> novedadService.registrarResultadoSecretaria(seleccionada, usuarioActual,
                    si.isSelected(), expediente.getText()));
        }
    }

    @FXML
    private void onNueva() {
        abrirSolicitud(null);
    }

    @FXML
    private void onLicencia() {
        abrirLicenciaExterna();
    }

    /** CU29 (P15). */
    public LicenciaExternaController abrirLicenciaExterna() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/licenciaExterna.fxml"));
            Parent root = loader.load();
            LicenciaExternaController c = loader.getController();
            c.setService(novedadService);
            c.setUsuarioActual(usuarioActual);
            Stage s = new Stage();
            s.setTitle("Licencia de origen externo :: " + AppInfo.PRG_LONG_TITLE);
            s.setScene(new Scene(root));
            s.initModality(Modality.APPLICATION_MODAL);
            s.initOwner(this.stage);
            s.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            c.setStage(s);
            c.postInitialize();
            s.setOnHidden(e -> Platform.runLater(this::onRefrescar));
            s.show();
            return c;
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir la licencia externa: " + e.getMessage());
            return null;
        }
    }

    @FXML
    private void onCorregir() {
        if (seleccionada != null) {
            abrirSolicitud(seleccionada);
        }
    }

    @FXML
    private void onAnular() {
        if (seleccionada == null) return;
        if (AlertUtils.showConfirm("¿Anular la solicitud? " + resumen(seleccionada))) {
            ejecutar(() -> novedadService.anular(seleccionada, usuarioActual));
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }

    /** Abre la pantalla de solicitud (CU20); null = nueva. */
    public SolicitarNovedadController abrirSolicitud(Novedad aCorregir) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/solicitarNovedad.fxml"));
            Parent root = loader.load();
            SolicitarNovedadController c = loader.getController();
            c.setNovedadService(novedadService);
            c.setUsuarioActual(usuarioActual);
            c.setPerfil(perfil);
            c.postInitialize(aCorregir);

            Stage stage = new Stage();
            c.setStage(stage);
            stage.setTitle((aCorregir == null ? "Solicitar novedad" : "Corregir solicitud") + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(this.stage);
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.setOnHidden(e -> Platform.runLater(this::onRefrescar));
            stage.show();
            return c;
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir la solicitud: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws ServiceException;
    }

    private void ejecutar(Accion accion) {
        try {
            accion.ejecutar();
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        onRefrescar();
    }

    /** Diálogo de comentario/fundamento (CU25: obligatorio para devolver o rechazar). */
    public static Optional<String> pedirTexto(String titulo, String encabezado, boolean obligatorio, String accion) {
        return dialogoTexto(titulo, encabezado, obligatorio, accion, null).showAndWait();
    }

    /** Modo de capturas: el mismo diálogo, abierto sin bloquear y con un texto ya escrito. */
    public static void pedirTextoNoBloqueante(String titulo, String encabezado, String accion, String texto,
                                              Window owner, java.util.function.Consumer<Stage> alMostrar) {
        Dialog<String> d = dialogoTexto(titulo, encabezado, true, accion, texto);
        d.initOwner(owner);
        d.setOnShown(e -> alMostrar.accept((Stage) d.getDialogPane().getScene().getWindow()));
        d.show();
    }

    private static Dialog<String> dialogoTexto(String titulo, String encabezado, boolean obligatorio, String accion,
                                               String texto) {
        Dialog<String> d = new Dialog<>();
        d.setTitle(titulo);
        d.setHeaderText(encabezado);
        TextArea area = new TextArea(texto != null ? texto : "");
        area.setWrapText(true);
        area.setPrefRowCount(4);
        area.setPrefColumnCount(40);
        d.getDialogPane().setContent(area);
        ButtonType ok = new ButtonType(accion, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, CANCELAR);
        if (obligatorio) {
            d.getDialogPane().lookupButton(ok).disableProperty()
                    .bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> area.getText().isBlank(),
                            area.textProperty()));
        }
        d.setResultConverter(b -> b == ok ? area.getText().trim() : null);
        Platform.runLater(area::requestFocus);
        return d;
    }

    private static String resumen(Novedad n) {
        return n.getTipo().nombre() + " de " + n.getAgenteNombre() + " (" + periodo(n) + ")";
    }

    private void guardarAdjunto(NovedadAdjunto a) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar adjunto");
        fc.setInitialFileName(a.getNombreArchivo());
        File destino = fc.showSaveDialog(stage);
        if (destino == null) return;
        try {
            Files.write(destino.toPath(), novedadService.contenidoAdjunto(a.getId()));
        } catch (IOException | ServiceException e) {
            AlertUtils.showErr("No se pudo guardar el adjunto: " + e.getMessage());
        }
    }
}
