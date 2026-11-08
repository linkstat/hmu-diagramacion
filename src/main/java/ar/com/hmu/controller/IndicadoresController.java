package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.asistencia.IndicadoresService;
import ar.com.hmu.service.asistencia.IndicadoresService.Ausentismo;
import ar.com.hmu.service.asistencia.IndicadoresService.Cobertura;
import ar.com.hmu.util.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultar indicadores (P16; CU36): ausentismo por tipo de novedad y
 * cobertura por franja, por servicio y período, sólo para OP y DH.
 */
public class IndicadoresController {

    private static final DateTimeFormatter DDMM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private DatePicker desdePicker;
    @FXML private DatePicker hastaPicker;
    @FXML private ComboBox<Servicio> servicioCombo;
    @FXML private Label periodoLabel;
    @FXML private TableView<Ausentismo> ausentismoTable;
    @FXML private BarChart<Number, String> ausentismoChart;
    @FXML private NumberAxis porcentajeAxis;
    @FXML private CheckBox soloBajoMinimoCheck;
    @FXML private TableView<Cobertura> coberturaTable;

    private IndicadoresService service;
    private Usuario usuarioActual;
    private Stage stage;
    private FilteredList<Cobertura> cobertura;

    public void setService(IndicadoresService service) {
        this.service = service;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
    }

    public void postInitialize(List<Servicio> servicios, LocalDate desde, LocalDate hasta) {
        servicioCombo.getItems().add(null);
        servicioCombo.getItems().addAll(servicios.stream()
                .sorted(Comparator.comparing(Servicio::getNombre)).toList());
        servicioCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Servicio s) { return s == null ? "(Todos los servicios)" : s.getNombre(); }
            @Override public Servicio fromString(String t) { return null; }
        });
        servicioCombo.setValue(null);
        for (DatePicker dp : List.of(desdePicker, hastaPicker)) {
            dp.setConverter(new StringConverter<>() {
                @Override public String toString(LocalDate d) { return d == null ? "" : d.format(DDMMYYYY); }
                @Override public LocalDate fromString(String t) {
                    return t == null || t.isBlank() ? null : LocalDate.parse(t.trim(), DDMMYYYY);
                }
            });
        }
        desdePicker.setValue(desde);
        hastaPicker.setValue(hasta);
        configurarTablas();
        soloBajoMinimoCheck.selectedProperty().addListener((o, a, b) -> filtrarCobertura());
        onCalcular();
    }

    private void configurarTablas() {
        ausentismoTable.getColumns().add(columna("SERVICIO", 175, Ausentismo::servicio));
        ausentismoTable.getColumns().add(columna("PROGR.", 70, a -> horas(a.horasProgramadas())));
        for (String tipo : IndicadoresService.TIPOS) {
            ausentismoTable.getColumns().add(conAyuda(columna(codigo(tipo), 70,
                    a -> horas(a.horasPorTipo().getOrDefault(tipo, 0.0))), tipo + codigosDe(tipo)));
        }
        ausentismoTable.getColumns().add(conAyuda(columna("AUS", 70, a -> horas(a.horasAusencia())),
                "Ausencia total"));
        ausentismoTable.getColumns().add(columna("%", 60,
                a -> String.format(Locale.of("es", "AR"), "%.1f %%", a.porcentaje())));
        ausentismoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ausentismoTable.setPlaceholder(new Label("No hay partes cerrados en el período."));

        coberturaTable.getColumns().add(columnaC("FECHA", 90, c -> c.fecha().format(DDMMYYYY)));
        coberturaTable.getColumns().add(columnaC("SERVICIO", 200, Cobertura::servicio));
        coberturaTable.getColumns().add(columnaC("FRANJA", 150, c -> c.franja() + " (" + c.desde() + " a " + c.hasta() + " h)"));
        coberturaTable.getColumns().add(columnaC("DIAGRAMADOS", 100, c -> String.valueOf(c.diagramados())));
        coberturaTable.getColumns().add(columnaC("PRESENTES", 100, c -> String.valueOf(c.presentes())));
        coberturaTable.getColumns().add(columnaC("MÍNIMO", 80, c -> String.valueOf(c.minimo())));
        coberturaTable.getColumns().add(columnaC("SITUACIÓN", 260, c -> c.bajoMinimo()
                ? "⚠ Bajo la dotación mínima (" + (c.minimo() - c.presentes()) + " de menos)" : "Cubierta"));
        coberturaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        coberturaTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Cobertura c, boolean empty) {
                super.updateItem(c, empty);
                getStyleClass().remove("bajo-minimo");
                if (!empty && c != null && c.bajoMinimo()) getStyleClass().add("bajo-minimo");
            }
        });
    }

    @FXML
    private void onCalcular() {
        Servicio s = servicioCombo.getValue();
        try {
            IndicadoresService.Resultado r = service.calcular(desdePicker.getValue(), hastaPicker.getValue(),
                    s != null ? s.getId() : null, usuarioActual);
            periodoLabel.setText(textoPeriodo(r));
            ausentismoTable.getItems().setAll(r.ausentismo());
            graficar(r.ausentismo());
            cobertura = new FilteredList<>(javafx.collections.FXCollections.observableArrayList(r.cobertura()));
            coberturaTable.setItems(cobertura);
            filtrarCobertura();
            coberturaTable.setPlaceholder(new Label(r.diasCerrados().isEmpty()
                    ? "No hay partes cerrados en el período: no hay datos para calcular los indicadores."
                    : "Todas las franjas alcanzaron la dotación mínima."));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    private String textoPeriodo(IndicadoresService.Resultado r) {
        if (r.diasCerrados().isEmpty()) {
            return "No hay partes cerrados en el período: no hay datos para calcular los indicadores (CU36 2a).";
        }
        String cerrados = r.diasCerrados().size() + (r.diasCerrados().size() == 1 ? " día" : " días")
                + " con el parte cerrado (" + r.diasCerrados().stream().map(d -> d.format(DDMM))
                .collect(Collectors.joining(", ")) + ")";
        String abiertos = r.diasAbiertos().isEmpty() ? "" : ". Días con el parte abierto, no incluidos: "
                + r.diasAbiertos().stream().map(d -> d.format(DDMM)).collect(Collectors.joining(", "));
        return "Calculado sobre " + cerrados + abiertos + ".";
    }

    /** Una serie (magnitud): barras horizontales de un solo tono; valor y detalle en el tooltip. */
    private void graficar(List<Ausentismo> datos) {
        ausentismoChart.getData().clear();
        XYChart.Series<Number, String> serie = new XYChart.Series<>();
        datos.stream().sorted(Comparator.comparingDouble(Ausentismo::porcentaje))
                .forEach(a -> serie.getData().add(new XYChart.Data<>(a.porcentaje(), a.servicio())));
        ausentismoChart.getData().add(serie);
        double max = datos.stream().mapToDouble(Ausentismo::porcentaje).max().orElse(0);
        porcentajeAxis.setAutoRanging(false);
        porcentajeAxis.setLowerBound(0);
        porcentajeAxis.setUpperBound(Math.max(5, Math.ceil(max / 5) * 5));
        porcentajeAxis.setTickUnit(Math.max(1, Math.ceil(max / 5) * 5) / 5);
        for (XYChart.Data<Number, String> d : serie.getData()) {
            Ausentismo a = datos.stream().filter(x -> x.servicio().equals(d.getYValue())).findFirst().orElseThrow();
            String detalle = a.horasPorTipo().entrySet().stream().filter(e -> e.getValue() > 0)
                    .map(e -> e.getKey() + ": " + horas(e.getValue()) + " h").collect(Collectors.joining("\n"));
            Tooltip.install(d.getNode(), new Tooltip(a.servicio() + "\n"
                    + String.format(Locale.of("es", "AR"), "%.1f %% de %s h programadas", a.porcentaje(),
                    horas(a.horasProgramadas())) + (detalle.isEmpty() ? "" : "\n" + detalle)));
        }
    }

    private void filtrarCobertura() {
        if (cobertura != null) {
            cobertura.setPredicate(c -> !soloBajoMinimoCheck.isSelected() || c.bajoMinimo());
        }
    }

    @FXML
    private void onCerrar() {
        stage.close();
    }

    private static String horas(double h) {
        return String.format(Locale.of("es", "AR"), "%.1f", h);
    }

    /** Sigla interna del HMU (la misma columna SIGLA del parte diario, P04). */
    private static String codigo(String tipo) {
        return switch (tipo) {
            case "Licencias" -> "LIC";
            case "Faltas justificadas" -> "FJ";
            case "Faltas injustificadas" -> "FI";
            case "Francos compensatorios" -> "FC";
            case "Pases de salida" -> "PS";
            case "Llegadas tarde" -> "T";
            default -> tipo.toUpperCase();
        };
    }

    /** Códigos de Capital Humano que suma cada columna, para el tooltip. */
    private static String codigosDe(String tipo) {
        return switch (tipo) {
            case "Licencias" -> " (códigos de licencia de Capital Humano, salvo capacitación)";
            case "Faltas justificadas" -> " (códigos 2 y 41)";
            case "Faltas injustificadas" -> " (código 1 e inasistencias sin justificar, 666)";
            case "Francos compensatorios" -> " (código 6)";
            case "Pases de salida" -> " sin retorno (PSSR, código 333)";
            case "Llegadas tarde" -> " (códigos 3, 4, 5, 42 y 55, y tardanzas sin justificar, 666)";
            default -> "";
        };
    }

    /** Encabezado con el código y el nombre completo en un tooltip. */
    private static <T> TableColumn<T, String> conAyuda(TableColumn<T, String> c, String nombre) {
        Label encabezado = new Label(c.getText());
        encabezado.setTooltip(new javafx.scene.control.Tooltip(nombre + " (horas)"));
        c.setText(null);
        c.setGraphic(encabezado);
        return c;
    }

    private static TableColumn<Ausentismo, String> columna(String titulo, double ancho, Function<Ausentismo, String> f) {
        TableColumn<Ausentismo, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }

    private static TableColumn<Cobertura, String> columnaC(String titulo, double ancho, Function<Cobertura, String> f) {
        TableColumn<Cobertura, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(ancho);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }
}
