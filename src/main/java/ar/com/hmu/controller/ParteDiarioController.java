package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.ParteDiario;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.asistencia.CotejoAsistencia;
import ar.com.hmu.service.asistencia.DetalleParte;
import ar.com.hmu.service.asistencia.ImportadorMarcaciones;
import ar.com.hmu.service.asistencia.LoteMarcaciones;
import ar.com.hmu.service.asistencia.ParteDiarioService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.Reloj;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Parte diario (P04, Figura 24 del E4; CU30–CU35): resumen del último lote
 * de marcaciones importado, cotejo por agente entre la jornada diagramada y
 * la registrada, diferencias sin justificar resaltadas, carga manual con
 * observación obligatoria, solicitud de justificación y cierre del parte.
 */
public class ParteDiarioController {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DDMMYYYY_HHMM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter LARGA = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.of("es", "AR"));
    private static final String TODOS = "(Todos)";

    /** Códigos para la carga manual. */
    /** Catálogo de códigos numéricos (carga manual y tooltip de la columna CÓD.). */
    private final Map<Integer, ar.com.hmu.model.CodigoNovedad> codigos = new LinkedHashMap<>();

    private enum FiltroVinculacion {
        TODAS("Todas las vinculaciones"), DEPENDENCIA("Relación de dependencia"), OTRAS("Monotributistas y becarios");

        private final String etiqueta;

        FiltroVinculacion(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    @FXML private DatePicker fechaPicker;
    @FXML private Label fechaLargaLabel;
    @FXML private Label estadoChip;
    @FXML private Button importarButton;
    @FXML private Button generarButton;
    @FXML private HBox loteBox;
    @FXML private Label loteLabel;
    @FXML private ComboBox<FiltroVinculacion> vinculacionCombo;
    @FXML private ComboBox<String> servicioCombo;
    @FXML private CheckBox soloDiferenciasCheck;
    @FXML private TableView<DetalleParte> detallesTable;
    @FXML private TableColumn<DetalleParte, String> agenteCol;
    @FXML private TableColumn<DetalleParte, String> servicioCol;
    @FXML private TableColumn<DetalleParte, String> diagramadaCol;
    @FXML private TableColumn<DetalleParte, String> registradaCol;
    @FXML private TableColumn<DetalleParte, String> diferenciaCol;
    @FXML private TableColumn<DetalleParte, String> siglaCol;
    @FXML private TableColumn<DetalleParte, String> codigoCol;
    @FXML private TableColumn<DetalleParte, String> origenCol;
    @FXML private TableColumn<DetalleParte, String> observacionCol;
    @FXML private Label contadorLabel;
    @FXML private VBox cargaBox;
    @FXML private ComboBox<DetalleParte> cargaAgenteCombo;
    @FXML private ComboBox<ar.com.hmu.model.CodigoNovedad> cargaCodigoCombo;
    @FXML private TextField cargaObservacionField;
    @FXML private Button registrarCargaButton;
    @FXML private Button justificacionButton;
    @FXML private Button cerrarParteButton;

    private ParteDiarioService service;
    private Usuario usuarioActual;
    private Stage stage;
    private ParteDiarioService.Vista vista;
    private final ObservableList<DetalleParte> detalles = FXCollections.observableArrayList();
    private FilteredList<DetalleParte> filtrados;

    public void setService(ParteDiarioService service) {
        this.service = service;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onSalir);
    }

    public void postInitialize(LocalDate fecha) {
        configurarTabla();
        vinculacionCombo.getItems().setAll(FiltroVinculacion.values());
        vinculacionCombo.setValue(FiltroVinculacion.TODAS);
        vinculacionCombo.valueProperty().addListener((o, a, b) -> aplicarFiltro());
        servicioCombo.valueProperty().addListener((o, a, b) -> aplicarFiltro());
        soloDiferenciasCheck.selectedProperty().addListener((o, a, b) -> aplicarFiltro());
        try {
            service.codigos().forEach(c -> codigos.put(c.codigo(), c));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        cargaCodigoCombo.getItems().setAll(codigos.values());
        cargaAgenteCombo.setConverter(new StringConverter<>() {
            @Override public String toString(DetalleParte d) { return d == null ? "" : d.getAgenteNombre(); }
            @Override public DetalleParte fromString(String s) { return null; }
        });
        detallesTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) cargaAgenteCombo.setValue(b);
            actualizarBotones();
        });
        fechaPicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : d.format(DDMMYYYY); }
            @Override public LocalDate fromString(String t) {
                return t == null || t.isBlank() ? null : LocalDate.parse(t.trim(), DDMMYYYY);
            }
        });
        fechaPicker.setValue(fecha);
        fechaPicker.valueProperty().addListener((o, a, b) -> cargar());
        cargar();
    }

    private void configurarTabla() {
        agenteCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAgenteNombre()));
        servicioCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getServicioNombre()));
        diagramadaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDiagramada()));
        registradaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getRegistrada()));
        diferenciaCol.setCellValueFactory(cd -> new SimpleStringProperty(
                CotejoAsistencia.formatearDiferencia(cd.getValue().getDiferenciaMinutos())));
        siglaCol.setCellValueFactory(cd -> new SimpleStringProperty(sigla(cd.getValue())));
        codigoCol.setCellValueFactory(cd -> new SimpleStringProperty(
                Objects.toString(cd.getValue().getCodigo(), "")));
        // Tooltip: denominación del HMU y la de Capital Humano.
        codigoCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean empty) {
                super.updateItem(texto, empty);
                setText(empty ? null : texto);
                ar.com.hmu.model.CodigoNovedad c = empty || texto == null || texto.isEmpty() ? null
                        : codigos.get(Integer.valueOf(texto));
                setTooltip(c == null ? null : new javafx.scene.control.Tooltip(
                        c.denominacionHmu() + "\nCapital Humano: " + c.denominacionCapitalHumano()));
            }
        });
        origenCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getOrigen().toString()));
        observacionCol.setCellValueFactory(cd -> new SimpleStringProperty(
                Objects.toString(cd.getValue().getObservaciones(), "")));
        detallesTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(DetalleParte d, boolean empty) {
                super.updateItem(d, empty);
                getStyleClass().removeAll("sin-justificar", "carga-manual");
                if (!empty && d != null) {
                    if (d.sinJustificar()) getStyleClass().add("sin-justificar");
                    if (d.getOrigen() == DetalleParte.Origen.CARGA_MANUAL) getStyleClass().add("carga-manual");
                }
            }
        });
        filtrados = new FilteredList<>(detalles, d -> true);
        detallesTable.setItems(filtrados);
    }

    // ============================================================
    // Carga
    // ============================================================

    private void cargar() {
        LocalDate fecha = fechaPicker.getValue();
        if (fecha == null) return;
        fechaLargaLabel.setText(capitalizar(fecha.format(LARGA)));
        try {
            vista = service.consultar(fecha);
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
            return;
        }
        mostrarLote(vista.ultimoLote());
        ParteDiario p = vista.parte();
        estadoChip.setVisible(p != null);
        if (p != null) {
            estadoChip.setText(p.estaCerrado() ? "CERRADO" : "ABIERTO");
            estadoChip.getStyleClass().setAll("label", "estado-chip", p.estaCerrado() ? "parte-cerrado" : "parte-abierto");
        }
        generarButton.setText(p == null ? "Generar parte" : "Actualizar cotejo");
        detalles.setAll(vista.detalles());
        cargaAgenteCombo.getItems().setAll(vista.detalles().stream()
                .filter(d -> d.getOrigen() != DetalleParte.Origen.CARGA_MANUAL).toList());
        servicioCombo.getItems().setAll(TODOS);
        vista.detalles().stream().map(DetalleParte::getServicioNombre).filter(Objects::nonNull).distinct().sorted()
                .forEach(servicioCombo.getItems()::add);
        servicioCombo.setValue(TODOS);
        detallesTable.setPlaceholder(new Label(p == null
                ? "No se generó el parte de esta fecha. Importe las marcaciones y use «Generar parte»."
                : "No hay agentes para el filtro elegido."));
        aplicarFiltro();
    }

    private void mostrarLote(LoteMarcaciones l) {
        if (l == null) {
            loteLabel.setText("No hay marcaciones importadas para la fecha: el cotejo será parcial.");
            loteLabel.setStyle("-fx-text-fill: #b9770e;");
            return;
        }
        loteLabel.setStyle("");
        loteLabel.setText("Archivo " + l.nombreArchivo() + "     Registros " + String.format(Locale.of("es", "AR"), "%,d",
                l.cantidadRegistros()) + "     Rango " + (l.rangoDesde() != null ? l.rangoDesde().format(DDMMYYYY_HHMM) : "?")
                + " – " + (l.rangoHasta() != null ? l.rangoHasta().format(DDMMYYYY_HHMM) : "?")
                + "     Importado " + l.fechaImportacion().format(DDMMYYYY_HHMM) + " · " + l.importadoPorNombre());
    }

    private void aplicarFiltro() {
        String servicio = servicioCombo.getValue();
        FiltroVinculacion v = vinculacionCombo.getValue();
        boolean solo = soloDiferenciasCheck.isSelected();
        filtrados.setPredicate(d -> (servicio == null || TODOS.equals(servicio) || servicio.equals(d.getServicioNombre()))
                && (v == null || v == FiltroVinculacion.TODAS
                || (v == FiltroVinculacion.DEPENDENCIA) == d.isRelacionDeDependencia())
                && (!solo || d.sinJustificar()));
        actualizarContador();
        actualizarBotones();
    }

    private void actualizarContador() {
        if (vista == null || vista.parte() == null) {
            contadorLabel.setText("");
            return;
        }
        long agentes = filtrados.stream().map(DetalleParte::getAgenteId).distinct().count();
        long sinJust = filtrados.stream().filter(DetalleParte::sinJustificar).count();
        long manual = filtrados.stream().filter(d -> d.getOrigen() == DetalleParte.Origen.CARGA_MANUAL).count();
        long novedades = filtrados.stream().filter(d -> d.getOrigen() == DetalleParte.Origen.NOVEDAD_APROBADA).count();
        long licencias = filtrados.stream().filter(d -> d.getOrigen() == DetalleParte.Origen.LICENCIA_EXTERNA).count();
        contadorLabel.setText(agentes + " agentes · " + sinJust + (sinJust == 1 ? " diferencia" : " diferencias")
                + " sin justificar · " + manual + (manual == 1 ? " carga manual" : " cargas manuales") + " · "
                + novedades + (novedades == 1 ? " novedad aprobada" : " novedades aprobadas") + " · "
                + licencias + (licencias == 1 ? " licencia externa" : " licencias externas")
                + (vista.parte().estaCerrado() && vista.parte().getFechaCierre() != null
                ? " · Cerrado el " + vista.parte().getFechaCierre().format(DDMMYYYY_HHMM) + " por "
                + vista.parte().getCerradoPorNombre() : ""));
    }

    private void actualizarBotones() {
        boolean abierto = vista != null && vista.parte() != null && !vista.parte().estaCerrado();
        boolean cerrado = vista != null && vista.parte() != null && vista.parte().estaCerrado();
        generarButton.setDisable(cerrado);
        cerrarParteButton.setDisable(!abierto);
        cargaBox.setDisable(!abierto);
        DetalleParte sel = detallesTable.getSelectionModel().getSelectedItem();
        justificacionButton.setDisable(!abierto || sel == null || !sel.sinJustificar());
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onImportar() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Importar marcaciones del sistema de relojes");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Marcaciones (CSV)", "*.csv", "*.txt"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            ImportadorMarcaciones.Resumen r = service.importar(f.getName(), Files.readAllBytes(f.toPath()), usuarioActual);
            cargar();
            // Aunque el archivo no cubra la fecha consultada, la cabecera pasa al lote recién importado.
            mostrarLote(r.lote());
            AlertUtils.showInfo(resumen(r));
        } catch (IOException e) {
            AlertUtils.showErr("No se pudo leer el archivo: " + e.getMessage());
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    /** CU30 paso 5. */
    public static String resumen(ImportadorMarcaciones.Resumen r) {
        StringBuilder sb = new StringBuilder("Lote importado: ").append(r.lote().nombreArchivo()).append("\n")
                .append(r.marcaciones()).append(" marcaciones de ").append(r.agentes()).append(" agentes, del ")
                .append(r.lote().rangoDesde().format(DDMMYYYY_HHMM)).append(" al ")
                .append(r.lote().rangoHasta().format(DDMMYYYY_HHMM)).append(".\n")
                .append(r.jornadas()).append(" jornadas registradas conformadas, ").append(r.omisiones())
                .append(r.omisiones() == 1 ? " con omisión de marcación." : " con omisiones de marcación.");
        if (!r.sinAgente().isEmpty()) {
            sb.append("\n\nMarcaciones sin agente en el padrón (no se importaron):");
            r.sinAgente().forEach((cuil, n) -> sb.append("\n  CUIL ").append(cuil).append(": ").append(n));
        }
        if (!r.filasRechazadas().isEmpty()) {
            sb.append("\n\nFilas con formato inválido: ").append(r.filasRechazadas().size());
        }
        return sb.toString();
    }

    @FXML
    private void onGenerar() {
        try {
            vista = service.generar(fechaPicker.getValue(), usuarioActual);
            cargar();
            if (vista.cotejoParcial()) {
                AlertUtils.showWarn("Faltan marcaciones de la fecha: el cotejo es parcial (CU31 2a).");
            }
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
            cargar();
        }
    }

    @FXML
    private void onRegistrarCarga() {
        DetalleParte agente = cargaAgenteCombo.getValue();
        try {
            service.cargaManual(vista.parte(), agente != null ? agente.getAgenteId() : null,
                    cargaCodigoCombo.getValue() != null ? cargaCodigoCombo.getValue().codigo() : null,
                    cargaObservacionField.getText(), usuarioActual);
            cargaObservacionField.clear();
            cargaCodigoCombo.setValue(null);
            cargar();
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
            cargar();
        }
    }

    @FXML
    private void onSolicitarJustificacion() {
        DetalleParte sel = detallesTable.getSelectionModel().getSelectedItem();
        try {
            service.solicitarJustificacion(vista.parte(), sel, usuarioActual);
            AlertUtils.showInfo("Se pidió a " + sel.getAgenteNombre() + " y a la jefatura de " + sel.getServicioNombre()
                    + " que presenten la novedad que justifique la diferencia.");
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCerrarParte() {
        long sinJust = vista.sinJustificar();
        String aviso = sinJust > 0 ? "\nQuedan " + sinJust + " diferencias sin justificar: se informarán con el código "
                + "de inasistencia o incumplimiento y se avisará a las jefaturas." : "";
        if (!AlertUtils.showConfirm("¿Cerrar el parte del " + vista.parte().getFecha().format(DDMMYYYY) + "?" + aviso)) {
            return;
        }
        try {
            service.cerrar(vista.parte(), usuarioActual);
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        cargar();
    }

    @FXML
    private void onSalir() {
        stage.close();
    }

    /**
     * Sigla interna del HMU: la del código del catálogo; una diferencia sin
     * justificar (666) muestra la de la diferencia detectada (T, RA, OM, FI).
     */
    private String sigla(DetalleParte d) {
        if (d.sinJustificar() && d.getTipoDiferencia() != null) {
            return switch (d.getTipoDiferencia()) {
                case LLEGADA_TARDE -> "T";
                case RETIRO_ANTICIPADO -> "RA";
                case OMISION_INGRESO, OMISION_EGRESO -> "OM";
                case INASISTENCIA, GUARDIA_NO_CUBIERTA -> "FI";
                default -> "—";
            };
        }
        ar.com.hmu.model.CodigoNovedad c = d.getCodigo() != null ? codigos.get(d.getCodigo()) : null;
        return c != null && c.sigla() != null ? c.sigla() : "";
    }

    /** Modo de capturas: filtra y selecciona como lo haría la OP. */
    public void seleccionarAgente(UUID agenteId) {
        detallesTable.getItems().stream().filter(d -> d.getAgenteId().equals(agenteId)).findFirst()
                .ifPresent(d -> detallesTable.getSelectionModel().select(d));
    }

    public void prepararCarga(UUID agenteId, int codigo, String observacion) {
        cargaAgenteCombo.getItems().stream().filter(d -> d.getAgenteId().equals(agenteId)).findFirst()
                .ifPresent(cargaAgenteCombo::setValue);
        cargaCodigoCombo.setValue(codigos.get(codigo));
        cargaObservacionField.setText(observacion);
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static LocalDate hoy() {
        return Reloj.hoy();
    }
}
