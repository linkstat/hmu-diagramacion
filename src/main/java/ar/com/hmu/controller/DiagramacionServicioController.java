package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.ServicioService;
import ar.com.hmu.service.diagramacion.DiagramaService;
import ar.com.hmu.service.diagramacion.GeneradorJornadas;
import ar.com.hmu.service.diagramacion.PlantillasJornada;
import ar.com.hmu.service.diagramacion.Violacion;
import ar.com.hmu.util.AlertUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.DayOfWeek;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controller de la pantalla de diagramación de servicio (RFS02).
 *
 * <p>Vista tabla empleado × día: una fila por empleado del servicio, una
 * columna por día del rango del diagrama. Cada celda muestra la jornada en
 * forma compacta (07–14 / G 08–20 / F / FC / LIC) coloreada por tipo, con
 * el detalle en tooltip. Doble clic abre el editor de jornada (sólo en
 * BORRADOR/OBSERVADO y si el usuario puede gestionar el servicio).</p>
 *
 * <p>Vista calendario mensual (toggle "Tabla / Calendario"): semanas como
 * filas, una celda por día con las jornadas activas de todos los empleados
 * (los francos no se listan). Misma edición por doble clic sobre una
 * entrada; mismo modelo en memoria y mismo flujo dirty/guardar.</p>
 *
 * <p>Los cambios se acumulan en memoria ({@code dirty}) y se persisten con
 * "Guardar" (reemplazo total de jornadas con control optimista). "Validar"
 * guarda si hace falta y muestra violaciones (✖ bloquean el envío) y
 * advertencias de carga mensual (⚠ no bloquean) en el panel lateral,
 * resaltando las celdas afectadas. ESC cierra respetando {@code dirty}.</p>
 */
public class DiagramacionServicioController {

    // --- Top ---
    @FXML private ComboBox<Servicio> servicioCombo;
    @FXML private ComboBox<DiagramaDeServicio> diagramaCombo;
    @FXML private Button nuevoDiagramaButton;
    @FXML private ToggleButton vistaTablaToggle;
    @FXML private ToggleButton vistaCalendarioToggle;
    @FXML private Label estadoChip;
    @FXML private HBox observacionBanner;
    @FXML private Label observacionLabel;

    // --- Center ---
    @FXML private TableView<Usuario> grillaTable;
    @FXML private ScrollPane calendarioScroll;
    @FXML private GridPane calendarioGrid;
    @FXML private ListView<String> validacionList;
    @FXML private Label validacionResumenLabel;

    // --- Bottom ---
    @FXML private Label ayudaLabel;
    @FXML private Button eliminarButton;
    @FXML private Button validarButton;
    @FXML private Button guardarButton;
    @FXML private Button enviarButton;
    @FXML private Button observarButton;
    @FXML private Button aprobarButton;
    @FXML private Button vistaEmpleadosButton;
    @FXML private Button aplicarPlantillaButton;

    // --- Dependencias (inyectadas por el caller) ---
    private DiagramaService diagramaService;
    private ServicioService servicioService;
    private Usuario usuarioActual;

    // --- Estado en memoria ---
    private DiagramaDeServicio diagramaActual;
    /** Jornadas indexadas por empleado y fecha (el modelo de la grilla). */
    private final Map<UUID, Map<LocalDate, JornadaLaboral>> jornadasPorEmpleado = new HashMap<>();
    /** Celdas a resaltar tras la última validación: clave "empleadoId|fecha". */
    private final Set<String> celdasConViolacion = new HashSet<>();
    private boolean dirty;

    /** Modo "Mi Diagrama" (empleado): sólo diagramas APROBADOS, sin acciones,
     *  y la grilla se limita a la propia fila si el permiso lo indica. */
    @FXML private javafx.scene.control.SplitPane splitPane;
    @FXML private javafx.scene.layout.VBox validacionPanel;
    private boolean modoEmpleado = false;
    /** En modo empleado: ¿ve la grilla completa del servicio? */
    private boolean veContexto = true;

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_MIN = DateTimeFormatter.ofPattern("HH:mm");

    // ============================================================
    // Inyección + inicialización
    // ============================================================

    public void setDiagramaService(DiagramaService diagramaService) {
        this.diagramaService = diagramaService;
    }

    public void setServicioService(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    /**
     * Activa el modo "Mi Diagrama" (vista del empleado). Llamar ANTES de
     * {@link #postInitialize()}.
     */
    public void activarModoEmpleado() {
        this.modoEmpleado = true;
    }

    /** Llamar después de inyectar dependencias. */
    public void postInitialize() {
        configurarCombos();
        configurarListaValidacion();
        configurarToggleVistas();
        if (modoEmpleado) {
            ocultarAccionesDeGestion();
        }
        cargarServicios();
        configurarEscCierra();
        actualizarControles();
    }

    /** En modo empleado la pantalla es de sólo consulta: sin acciones. */
    private void ocultarAccionesDeGestion() {
        // Consulta: no se valida, el panel quedaría vacío.
        splitPane.getItems().remove(validacionPanel);
        for (Button b : new Button[]{nuevoDiagramaButton, eliminarButton, validarButton,
                guardarButton, enviarButton}) {
            b.setVisible(false);
            b.setManaged(false);
        }
        ayudaLabel.setVisible(false);
        ayudaLabel.setManaged(false);
    }

    /**
     * Navega a un diagrama concreto (usado por la bandeja de consulta de
     * OP/Dirección). Llamar después de {@link #postInitialize()}.
     */
    public void seleccionarDiagrama(UUID servicioId, UUID diagramaId) {
        servicioCombo.getItems().stream()
                .filter(s -> s.getId().equals(servicioId))
                .findFirst()
                .ifPresent(s -> servicioCombo.getSelectionModel().select(s));
        // El listener del servicio ya cargó sus diagramas; ubicamos el pedido.
        diagramaCombo.getItems().stream()
                .filter(d -> d.getId().equals(diagramaId))
                .findFirst()
                .ifPresent(d -> diagramaCombo.getSelectionModel().select(d));
    }

    /** Toggle Tabla / Calendario: misma información, dos representaciones.
     *  Siempre hay exactamente una vista seleccionada. */
    private void configurarToggleVistas() {
        ToggleGroup grupo = new ToggleGroup();
        vistaTablaToggle.setToggleGroup(grupo);
        vistaCalendarioToggle.setToggleGroup(grupo);
        grupo.selectedToggleProperty().addListener((obs, viejo, nuevo) -> {
            if (nuevo == null) {
                grupo.selectToggle(viejo); // no permitir des-seleccionar ambas
                return;
            }
            mostrarVistaActiva();
        });
    }

    private boolean vistaCalendarioActiva() {
        return vistaCalendarioToggle.isSelected();
    }

    private void mostrarVistaActiva() {
        boolean calendario = vistaCalendarioActiva();
        grillaTable.setVisible(!calendario);
        calendarioScroll.setVisible(calendario);
        if (calendario) {
            construirCalendario();
        }
    }

    /** Los mensajes de validación son largos: celdas con salto de línea al
     *  ancho de la lista, en vez de una sola línea recortada. */
    private void configurarListaValidacion() {
        validacionList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            {
                setWrapText(true);
                // 20 px: margen para la barra de desplazamiento vertical.
                prefWidthProperty().bind(lv.widthProperty().subtract(20));
                setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
    }

    private void configurarCombos() {
        servicioCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Servicio s) {
                return s != null ? s.getNombre() : "";
            }
            @Override public Servicio fromString(String s) {
                return null;
            }
        });
        servicioCombo.valueProperty().addListener((obs, viejo, nuevo) -> {
            if (nuevo != null && confirmarDescarteSiDirty()) {
                cargarDiagramasDe(nuevo.getId());
            }
        });

        diagramaCombo.setConverter(new StringConverter<>() {
            @Override public String toString(DiagramaDeServicio d) {
                if (d == null) {
                    return "";
                }
                return d.getFechaInicio().format(FECHA_CORTA) + " – "
                        + d.getFechaFin().format(FECHA_CORTA) + "  [" + d.getEstado() + "]";
            }
            @Override public DiagramaDeServicio fromString(String s) {
                return null;
            }
        });
        diagramaCombo.valueProperty().addListener((obs, viejo, nuevo) -> {
            if (nuevo != null && confirmarDescarteSiDirty()) {
                cargarDiagrama(nuevo);
            }
        });

        grillaTable.setPlaceholder(new Label("Seleccioná o creá un diagrama para empezar."));
        grillaTable.getSelectionModel().setCellSelectionEnabled(true);
    }

    private void cargarServicios() {
        try {
            List<Servicio> servicios = servicioService.readAll();
            // readAll no ordena (orden físico de inserción): ordenamos acá.
            servicios.sort(java.util.Comparator.comparing(Servicio::getNombre,
                    String.CASE_INSENSITIVE_ORDER));
            if (esOpODireccion()) {
                servicioCombo.getItems().setAll(servicios);
            } else {
                // Jefatura: sólo su propio servicio, selector bloqueado.
                servicios.stream()
                        .filter(s -> s.getId().equals(usuarioActual.getServicioId()))
                        .findFirst()
                        .ifPresent(s -> servicioCombo.getItems().setAll(s));
                servicioCombo.setDisable(true);
            }
            if (!servicioCombo.getItems().isEmpty()) {
                servicioCombo.getSelectionModel().selectFirst();
            }
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los servicios: " + e.getMessage());
        }
    }

    private void configurarEscCierra() {
        grillaTable.sceneProperty().addListener((obs, vieja, nueva) -> {
            if (nueva != null) {
                nueva.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCerrar);
            }
        });
    }

    // ============================================================
    // Carga de diagramas y grilla
    // ============================================================

    private void cargarDiagramasDe(UUID servicioId) {
        try {
            List<DiagramaDeServicio> diagramas = diagramaService.diagramasDeServicio(servicioId);
            if (modoEmpleado) {
                // El empleado sólo ve lo oficial: diagramas aprobados.
                diagramas = diagramas.stream()
                        .filter(d -> d.getEstado() == EstadoDiagrama.APROBADO)
                        .toList();
            }
            diagramaCombo.getItems().setAll(diagramas);
            if (!diagramas.isEmpty()) {
                diagramaCombo.getSelectionModel().selectFirst();
            } else {
                diagramaActual = null;
                limpiarGrilla();
                actualizarControles();
            }
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los diagramas del servicio: " + e.getMessage());
        }
    }

    private void cargarDiagrama(DiagramaDeServicio diagrama) {
        try {
            this.diagramaActual = diagrama;
            if (modoEmpleado) {
                veContexto = diagramaService.puedeVerContexto(usuarioActual.getId());
            }
            List<JornadaLaboral> jornadas = diagramaService.jornadasDe(diagrama.getId());

            jornadasPorEmpleado.clear();
            for (JornadaLaboral j : jornadas) {
                jornadasPorEmpleado
                        .computeIfAbsent(j.getEmpleadoId(), k -> new HashMap<>())
                        .put(j.getFecha(), j);
            }
            celdasConViolacion.clear();
            validacionList.getItems().clear();
            validacionResumenLabel.setText("");
            dirty = false;

            construirGrilla();
            if (vistaCalendarioActiva()) {
                construirCalendario();
            }
            actualizarControles();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar el diagrama: " + e.getMessage());
        }
    }

    private void limpiarGrilla() {
        grillaTable.getColumns().clear();
        grillaTable.getItems().clear();
        calendarioGrid.getChildren().clear();
        jornadasPorEmpleado.clear();
        celdasConViolacion.clear();
        validacionList.getItems().clear();
        validacionResumenLabel.setText("");
        dirty = false;
    }

    private void construirGrilla() {
        grillaTable.getColumns().clear();
        grillaTable.getItems().clear();
        if (diagramaActual == null) {
            return;
        }

        // Columna fija: empleado.
        TableColumn<Usuario, String> nombreCol = new TableColumn<>("Agente");
        nombreCol.setCellValueFactory(cd -> new SimpleStringProperty(nombreDe(cd.getValue())));
        nombreCol.setPrefWidth(190);
        nombreCol.setSortable(false);
        nombreCol.setReorderable(false);
        grillaTable.getColumns().add(nombreCol);

        // Una columna por día del rango.
        for (LocalDate fecha = diagramaActual.getFechaInicio();
             !fecha.isAfter(diagramaActual.getFechaFin());
             fecha = fecha.plusDays(1)) {
            grillaTable.getColumns().add(columnaDelDia(fecha));
        }

        // Filas: los empleados del servicio (o sólo el propio, según permiso).
        try {
            List<Usuario> empleados = servicioService.findUsuariosByServicio(diagramaActual.getServicioId());
            if (modoEmpleado && !veContexto) {
                empleados = empleados.stream()
                        .filter(u -> u.getId().equals(usuarioActual.getId()))
                        .toList();
            }
            grillaTable.getItems().setAll(empleados);
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los empleados del servicio: " + e.getMessage());
        }
    }

    private TableColumn<Usuario, String> columnaDelDia(LocalDate fecha) {
        String encabezado = String.format("%02d%n%s", fecha.getDayOfMonth(), inicialDia(fecha));
        TableColumn<Usuario, String> col = new TableColumn<>(encabezado);
        col.setPrefWidth(58);
        col.setSortable(false);
        col.setReorderable(false);

        final LocalDate fechaCol = fecha;
        col.setCellValueFactory(cd ->
                new SimpleStringProperty(textoCelda(jornadaDe(cd.getValue().getId(), fechaCol))));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("celda-turno", "celda-guardia", "celda-guardia-pasiva",
                        "celda-franco", "celda-franco-comp", "celda-licencia", "celda-violacion");
                setTooltip(null);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                Usuario empleado = getTableRow().getItem();
                JornadaLaboral j = jornadaDe(empleado.getId(), fechaCol);
                setText(item);
                if (j != null) {
                    getStyleClass().add(estiloDe(j.getTipo()));
                    setTooltip(new Tooltip(tooltipDe(j)));
                }
                if (celdasConViolacion.contains(claveCelda(empleado.getId(), fechaCol))) {
                    getStyleClass().add("celda-violacion");
                }
                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && esEditable()) {
                        editarCelda(empleado, fechaCol);
                    }
                });
            }
        });
        return col;
    }

    // ============================================================
    // Vista calendario mensual
    // ============================================================

    /**
     * Reconstruye la grilla calendario: semanas como filas (lunes a
     * domingo), una celda por día del rango del diagrama. Cada celda lista
     * las jornadas "activas" del día (turnos, guardias, FC, LIC) — los
     * francos no se muestran para no tapizar el mes. Doble clic en una
     * entrada abre el mismo editor de jornada que la vista tabla.
     */
    private void construirCalendario() {
        calendarioGrid.getChildren().clear();
        calendarioGrid.getColumnConstraints().clear();
        if (diagramaActual == null) {
            return;
        }

        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            calendarioGrid.getColumnConstraints().add(cc);
        }

        String[] encabezados = {"Lunes", "Martes", "Miércoles", "Jueves",
                "Viernes", "Sábado", "Domingo"};
        for (int i = 0; i < 7; i++) {
            Label h = new Label(encabezados[i]);
            h.getStyleClass().add("cal-header");
            h.setMaxWidth(Double.MAX_VALUE);
            calendarioGrid.add(h, i, 0);
        }

        LocalDate desde = diagramaActual.getFechaInicio();
        LocalDate hasta = diagramaActual.getFechaFin();
        LocalDate cursor = desde.with(java.time.temporal.TemporalAdjusters
                .previousOrSame(DayOfWeek.MONDAY));
        LocalDate fin = hasta.with(java.time.temporal.TemporalAdjusters
                .nextOrSame(DayOfWeek.SUNDAY));

        int fila = 1;
        while (!cursor.isAfter(fin)) {
            int columna = cursor.getDayOfWeek().getValue() - 1; // MONDAY=0
            calendarioGrid.add(celdaCalendario(cursor, desde, hasta), columna, fila);
            if (cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
                fila++;
            }
            cursor = cursor.plusDays(1);
        }
    }

    private VBox celdaCalendario(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        VBox celda = new VBox();
        celda.getStyleClass().add("cal-dia");

        boolean fueraDeRango = fecha.isBefore(desde) || fecha.isAfter(hasta);
        if (fueraDeRango) {
            celda.getStyleClass().add("cal-dia-fuera");
            return celda; // vacía: día fuera del diagrama
        }
        DayOfWeek dia = fecha.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            celda.getStyleClass().add("cal-dia-finde");
        }

        Label num = new Label(String.format("%02d", fecha.getDayOfMonth()));
        num.getStyleClass().add("cal-num");
        celda.getChildren().add(num);

        boolean diaConViolacion = false;
        for (Usuario empleado : grillaTable.getItems()) {
            JornadaLaboral j = jornadaDe(empleado.getId(), fecha);
            if (j == null || j.getTipo() == null || j.getTipo() == TipoJornada.FRANCO) {
                continue; // los francos no se listan en el calendario
            }
            Label entry = new Label(apellidoDe(empleado) + "  " + textoCelda(j));
            entry.getStyleClass().addAll("cal-entry", estiloDe(j.getTipo()));
            entry.setMaxWidth(Double.MAX_VALUE);
            entry.setTooltip(new Tooltip(nombreDe(empleado) + "\n" + tooltipDe(j)));
            final Usuario emp = empleado;
            final LocalDate f = fecha;
            entry.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && esEditable()) {
                    editarCelda(emp, f);
                }
            });
            if (celdasConViolacion.contains(claveCelda(empleado.getId(), fecha))) {
                diaConViolacion = true;
            }
            celda.getChildren().add(entry);
        }
        if (diaConViolacion) {
            celda.getStyleClass().add("celda-violacion");
        }
        return celda;
    }

    private String apellidoDe(Usuario u) {
        return u.getApellidos() != null ? u.getApellidos() : "";
    }

    /** Refresca la vista activa tras un cambio en el modelo en memoria. */
    private void refrescarVistaActiva() {
        grillaTable.refresh();
        if (vistaCalendarioActiva()) {
            construirCalendario();
        }
    }

    // ============================================================
    // Render de celdas
    // ============================================================

    private JornadaLaboral jornadaDe(UUID empleadoId, LocalDate fecha) {
        Map<LocalDate, JornadaLaboral> delEmpleado = jornadasPorEmpleado.get(empleadoId);
        return (delEmpleado != null) ? delEmpleado.get(fecha) : null;
    }

    private String textoCelda(JornadaLaboral j) {
        if (j == null || j.getTipo() == null) {
            return "";
        }
        return switch (j.getTipo()) {
            case TURNO_NORMAL -> rangoCorto(j);
            case GUARDIA_ACTIVA -> "G" + rangoCorto(j);
            case GUARDIA_PASIVA -> "GP" + rangoCorto(j);
            case FRANCO -> "F";
            case FRANCO_COMPENSATORIO -> "FC";
            case LICENCIA -> "LIC";
        };
    }

    private String rangoCorto(JornadaLaboral j) {
        if (!j.tieneHorario()) {
            return "¿?";
        }
        return horaCorta(j.getFechaIngreso()) + "–" + horaCorta(j.getFechaEgreso());
    }

    private String horaCorta(LocalDateTime t) {
        return (t.getMinute() == 0)
                ? String.format("%02d", t.getHour())
                : t.format(HORA_MIN);
    }

    private String estiloDe(TipoJornada tipo) {
        return switch (tipo) {
            case TURNO_NORMAL -> "celda-turno";
            case GUARDIA_ACTIVA -> "celda-guardia";
            case GUARDIA_PASIVA -> "celda-guardia-pasiva";
            case FRANCO -> "celda-franco";
            case FRANCO_COMPENSATORIO -> "celda-franco-comp";
            case LICENCIA -> "celda-licencia";
        };
    }

    private String tooltipDe(JornadaLaboral j) {
        StringBuilder sb = new StringBuilder(etiquetaDe(j.getTipo()));
        if (j.tieneHorario()) {
            sb.append("\n").append(j.getFechaIngreso().format(HORA_MIN))
                    .append(" → ").append(j.getFechaEgreso().format(HORA_MIN));
            if (!j.getFechaEgreso().toLocalDate().equals(j.getFecha())) {
                sb.append(" (día siguiente)");
            }
        }
        if (j.getObservaciones() != null && !j.getObservaciones().isBlank()) {
            sb.append("\n").append(j.getObservaciones());
        }
        return sb.toString();
    }

    private String etiquetaDe(TipoJornada tipo) {
        return switch (tipo) {
            case TURNO_NORMAL -> "Turno normal";
            case GUARDIA_ACTIVA -> "Guardia activa";
            case GUARDIA_PASIVA -> "Guardia pasiva";
            case FRANCO -> "Franco";
            case FRANCO_COMPENSATORIO -> "Franco compensatorio";
            case LICENCIA -> "Licencia";
        };
    }

    private String inicialDia(LocalDate fecha) {
        return switch (fecha.getDayOfWeek()) {
            case MONDAY -> "L";
            case TUESDAY -> "M";
            case WEDNESDAY -> "X";
            case THURSDAY -> "J";
            case FRIDAY -> "V";
            case SATURDAY -> "S";
            case SUNDAY -> "D";
        };
    }

    private String nombreDe(Usuario u) {
        String apellidos = u.getApellidos() != null ? u.getApellidos() : "";
        String nombres = u.getNombres() != null ? u.getNombres() : "";
        return (apellidos + ", " + nombres).trim();
    }

    private String claveCelda(UUID empleadoId, LocalDate fecha) {
        return empleadoId + "|" + fecha;
    }

    // ============================================================
    // Editor de jornada
    // ============================================================

    /** Abre el editor de jornada de una celda (modo de capturas). */
    public void editarCelda(UUID empleadoId, LocalDate fecha) {
        grillaTable.getItems().stream().filter(u -> u.getId().equals(empleadoId)).findFirst()
                .ifPresent(u -> editarCelda(u, fecha));
    }

    private void editarCelda(Usuario empleado, LocalDate fecha) {
        JornadaLaboral existente = jornadaDe(empleado.getId(), fecha);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar jornada");
        dialog.setHeaderText(nombreDe(empleado) + " — " + fecha.format(FECHA_CORTA)
                + " (" + inicialDia(fecha) + ")");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<TipoJornada> tipoCombo = new ComboBox<>();
        tipoCombo.getItems().setAll(TipoJornada.values());
        tipoCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TipoJornada t) {
                return t != null ? etiquetaDe(t) : "";
            }
            @Override public TipoJornada fromString(String s) {
                return null;
            }
        });

        TextField ingresoField = new TextField();
        ingresoField.setPromptText("HH:mm");
        TextField egresoField = new TextField();
        egresoField.setPromptText("HH:mm");
        Label notaCruce = new Label("Si el egreso es anterior al ingreso, se toma como día siguiente.");
        notaCruce.getStyleClass().add("ayuda");
        TextField obsField = new TextField();
        obsField.setPromptText("Observaciones (opcional)");

        // Estado inicial.
        if (existente != null && existente.getTipo() != null) {
            tipoCombo.setValue(existente.getTipo());
            if (existente.tieneHorario()) {
                ingresoField.setText(existente.getFechaIngreso().format(HORA_MIN));
                egresoField.setText(existente.getFechaEgreso().format(HORA_MIN));
            }
            obsField.setText(existente.getObservaciones() != null ? existente.getObservaciones() : "");
        } else {
            tipoCombo.setValue(TipoJornada.FRANCO);
        }

        Runnable sincronizarHabilitacion = () -> {
            boolean conHorario = tipoCombo.getValue() != null && tipoCombo.getValue().requiereHorario();
            ingresoField.setDisable(!conHorario);
            egresoField.setDisable(!conHorario);
        };
        tipoCombo.valueProperty().addListener((obs, v, n) -> sincronizarHabilitacion.run());
        sincronizarHabilitacion.run();

        // Precargas de los horarios más comunes del HMU. Sólo llenan los
        // campos: la jefatura puede retocarlos antes de aceptar. Las guardias
        // respetan la hora de ingreso ya escrita (o 08:00 si no hay).
        HBox precargas = new HBox(6,
                botonPrecarga("7 h · 07:00", () -> precargar(tipoCombo, ingresoField, egresoField,
                        TipoJornada.TURNO_NORMAL, LocalTime.of(7, 0), 7)),
                botonPrecarga("7 h · 08:00", () -> precargar(tipoCombo, ingresoField, egresoField,
                        TipoJornada.TURNO_NORMAL, LocalTime.of(8, 0), 7)),
                botonPrecarga("Guardia 12 h", () -> precargar(tipoCombo, ingresoField, egresoField,
                        TipoJornada.GUARDIA_ACTIVA, ingresoOOchoHoras(ingresoField), 12)),
                botonPrecarga("Guardia 24 h", () -> precargar(tipoCombo, ingresoField, egresoField,
                        TipoJornada.GUARDIA_ACTIVA, ingresoOOchoHoras(ingresoField), 24)));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Precargar:"), precargas);
        grid.addRow(1, new Label("Tipo:"), tipoCombo);
        grid.addRow(2, new Label("Ingreso:"), ingresoField);
        grid.addRow(3, new Label("Egreso:"), egresoField);
        grid.add(notaCruce, 1, 4);
        grid.addRow(5, new Label("Obs.:"), obsField);
        dialog.getDialogPane().setContent(grid);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        TipoJornada tipo = tipoCombo.getValue();
        LocalDateTime ingreso = null;
        LocalDateTime egreso = null;
        if (tipo.requiereHorario()) {
            try {
                LocalTime hIn = LocalTime.parse(ingresoField.getText().trim(), HORA_MIN);
                LocalTime hEg = LocalTime.parse(egresoField.getText().trim(), HORA_MIN);
                ingreso = fecha.atTime(hIn);
                // Egreso menor o igual al ingreso: la jornada cruza medianoche.
                egreso = !hEg.isAfter(hIn) ? fecha.plusDays(1).atTime(hEg) : fecha.atTime(hEg);
            } catch (DateTimeParseException e) {
                AlertUtils.showWarn("Horario inválido: usá el formato HH:mm (ej.: 07:00).");
                return;
            }
        }

        // Límites de duración (misma regla estructural del validador):
        // rechazar acá evita descubrirlo recién al validar/enviar. Se valida
        // sobre un candidato aparte para no tocar la jornada existente si se
        // rechaza el cambio.
        if (ingreso != null) {
            JornadaLaboral candidata = new JornadaLaboral();
            candidata.setEmpleadoId(empleado.getId());
            candidata.setFecha(fecha);
            candidata.setTipo(tipo);
            candidata.setFechaIngreso(ingreso);
            candidata.setFechaEgreso(egreso);
            ar.com.hmu.service.diagramacion.Violacion v =
                    ar.com.hmu.service.diagramacion.DiagramaValidator.validarDuracion(candidata,
                            diagramaService.reglasVigentes(diagramaActual.getFechaInicio()));
            if (v != null) {
                AlertUtils.showWarn(v.mensaje());
                return;
            }
        }

        JornadaLaboral j = (existente != null) ? existente : new JornadaLaboral();
        if (j.getId() == null) {
            j.setId(UUID.randomUUID());
        }
        j.setDiagramaId(diagramaActual.getId());
        j.setEmpleadoId(empleado.getId());
        j.setFecha(fecha);
        j.setTipo(tipo);
        j.setFechaIngreso(ingreso);
        j.setFechaEgreso(egreso);
        String obs = obsField.getText();
        j.setObservaciones(obs != null && !obs.isBlank() ? obs.trim() : null);

        jornadasPorEmpleado.computeIfAbsent(empleado.getId(), k -> new HashMap<>()).put(fecha, j);
        dirty = true;
        refrescarVistaActiva();
        actualizarControles();
    }

    private Button botonPrecarga(String texto, Runnable accion) {
        Button b = new Button(texto);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void precargar(ComboBox<TipoJornada> tipoCombo, TextField ingresoField, TextField egresoField,
                           TipoJornada tipo, LocalTime ingreso, int horas) {
        tipoCombo.setValue(tipo);
        ingresoField.setText(ingreso.format(HORA_MIN));
        egresoField.setText(ingreso.plusHours(horas).format(HORA_MIN));
    }

    private LocalTime ingresoOOchoHoras(TextField ingresoField) {
        try {
            return LocalTime.parse(ingresoField.getText().trim(), HORA_MIN);
        } catch (DateTimeParseException e) {
            return LocalTime.of(8, 0);
        }
    }

    // ============================================================
    // Plantillas de precarga (fila completa de un agente)
    // ============================================================

    private static final String PLANTILLA_ESTANDAR = "Semana estándar (7 h, lunes a viernes)";
    private static final String PLANTILLA_GUARDIA_MEDICA = "Guardia médica semanal (12 h + 24 h)";
    private static final String PLANTILLA_FERIANTE = "Feriante (10 guardias de 12 h en no laborables)";

    /**
     * Llena la fila de un agente en todo el período del diagrama con una
     * plantilla ({@link PlantillasJornada}). Los cambios quedan en memoria
     * (dirty) hasta "Guardar", igual que la edición celda por celda.
     */
    @FXML
    private void onAplicarPlantilla() {
        if (!esEditable() || grillaTable.getItems().isEmpty()) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Aplicar plantilla");
        dialog.setHeaderText("Reemplaza las jornadas del agente en todo el período del diagrama.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<Usuario> agenteCombo = new ComboBox<>();
        agenteCombo.getItems().setAll(grillaTable.getItems());
        agenteCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Usuario u) {
                return u != null ? nombreDe(u) : "";
            }
            @Override public Usuario fromString(String s) {
                return null;
            }
        });
        Usuario seleccionado = grillaTable.getSelectionModel().getSelectedItem();
        agenteCombo.setValue(seleccionado != null ? seleccionado : grillaTable.getItems().get(0));

        ComboBox<String> plantillaCombo = new ComboBox<>();
        plantillaCombo.getItems().setAll(PLANTILLA_ESTANDAR, PLANTILLA_GUARDIA_MEDICA, PLANTILLA_FERIANTE);
        plantillaCombo.setValue(PLANTILLA_ESTANDAR);

        // Parámetros de cada plantilla.
        ComboBox<String> ingresoEstandar = new ComboBox<>();
        ingresoEstandar.getItems().setAll("07:00", "08:00");
        ingresoEstandar.setEditable(true);
        ingresoEstandar.setValue("07:00");

        ComboBox<DayOfWeek> dia12 = comboDiaSemana(DayOfWeek.TUESDAY);
        TextField hora12 = campoHora("08:00");
        ComboBox<DayOfWeek> dia24 = comboDiaSemana(DayOfWeek.FRIDAY);
        TextField hora24 = campoHora("08:00");

        TextField horaFeriante = campoHora("08:00");
        Label notaFeriante = new Label("Si no alcanzan los sábados, domingos y feriados, las horas que faltan\n"
                + "para llegar a " + PlantillasJornada.HORAS_FERIANTE_MINIMAS + " h van a días hábiles.");
        notaFeriante.getStyleClass().add("ayuda");

        GridPane parametrosEstandar = new GridPane();
        parametrosEstandar.setHgap(8);
        parametrosEstandar.addRow(0, new Label("Ingreso:"), ingresoEstandar);

        GridPane parametrosGuardia = new GridPane();
        parametrosGuardia.setHgap(8);
        parametrosGuardia.setVgap(6);
        parametrosGuardia.addRow(0, new Label("Guardia de 12 h:"), dia12, new Label("ingreso"), hora12);
        parametrosGuardia.addRow(1, new Label("Guardia de 24 h:"), dia24, new Label("ingreso"), hora24);

        GridPane parametrosFeriante = new GridPane();
        parametrosFeriante.setHgap(8);
        parametrosFeriante.setVgap(6);
        parametrosFeriante.addRow(0, new Label("Ingreso:"), horaFeriante);
        parametrosFeriante.add(notaFeriante, 0, 1, 2, 1);

        javafx.scene.layout.StackPane parametros = new javafx.scene.layout.StackPane(
                parametrosEstandar, parametrosGuardia, parametrosFeriante);
        Runnable mostrarParametros = () -> {
            String p = plantillaCombo.getValue();
            parametrosEstandar.setVisible(PLANTILLA_ESTANDAR.equals(p));
            parametrosGuardia.setVisible(PLANTILLA_GUARDIA_MEDICA.equals(p));
            parametrosFeriante.setVisible(PLANTILLA_FERIANTE.equals(p));
        };
        plantillaCombo.valueProperty().addListener((obs, v, n) -> mostrarParametros.run());
        mostrarParametros.run();

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);
        grid.addRow(0, new Label("Agente:"), agenteCombo);
        grid.addRow(1, new Label("Plantilla:"), plantillaCombo);
        grid.add(parametros, 1, 2);
        dialog.getDialogPane().setContent(grid);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        Usuario agente = agenteCombo.getValue();
        String plantilla = plantillaCombo.getValue();
        LocalDate desde = diagramaActual.getFechaInicio();
        LocalDate hasta = diagramaActual.getFechaFin();
        List<JornadaLaboral> nuevas;
        try {
            nuevas = switch (plantilla) {
                case PLANTILLA_ESTANDAR -> PlantillasJornada.semanaEstandar(
                        parsearHora(ingresoEstandar.getEditor().getText()), desde, hasta,
                        diagramaService.contextoPara(desde, hasta));
                case PLANTILLA_GUARDIA_MEDICA -> PlantillasJornada.guardiaMedicaSemanal(
                        dia12.getValue(), parsearHora(hora12.getText()),
                        dia24.getValue(), parsearHora(hora24.getText()), desde, hasta);
                default -> PlantillasJornada.feriante(parsearHora(horaFeriante.getText()), desde, hasta,
                        diagramaService.contextoPara(desde, hasta));
            };
        } catch (DateTimeParseException e) {
            AlertUtils.showWarn("Horario inválido: usá el formato HH:mm (ej.: 07:00).");
            return;
        } catch (IllegalArgumentException | ServiceException e) {
            AlertUtils.showWarn(e.getMessage());
            return;
        }

        long conHorarioAntes = jornadasPorEmpleado.getOrDefault(agente.getId(), Map.of()).values().stream()
                .filter(JornadaLaboral::tieneHorario).count();
        if (conHorarioAntes > 0 && !AlertUtils.showConfirm("Se van a reemplazar las jornadas de "
                + nombreDe(agente) + " en todo el período (" + conHorarioAntes + " con horario). ¿Continuar?")) {
            return;
        }

        GeneradorJornadas.estampar(nuevas, diagramaActual.getId(), agente.getId());
        Map<LocalDate, JornadaLaboral> fila = new HashMap<>();
        for (JornadaLaboral j : nuevas) {
            fila.put(j.getFecha(), j);
        }
        jornadasPorEmpleado.put(agente.getId(), fila);

        long horas = nuevas.stream().filter(JornadaLaboral::tieneHorario)
                .mapToLong(j -> j.calcularDuracion().toHours()).sum();
        long conHorario = nuevas.stream().filter(JornadaLaboral::tieneHorario).count();
        // La validación anterior ya no corresponde a la fila modificada.
        celdasConViolacion.clear();
        validacionList.getItems().clear();
        validacionResumenLabel.setText("Plantilla aplicada a " + nombreDe(agente) + ": " + conHorario
                + " jornadas, " + horas + " h. Sin guardar: revisá y presioná Guardar.");

        dirty = true;
        refrescarVistaActiva();
        actualizarControles();
    }

    private ComboBox<DayOfWeek> comboDiaSemana(DayOfWeek inicial) {
        ComboBox<DayOfWeek> c = new ComboBox<>();
        c.getItems().setAll(DayOfWeek.values());
        c.setConverter(new StringConverter<>() {
            @Override public String toString(DayOfWeek d) {
                if (d == null) {
                    return "";
                }
                String nombre = d.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.of("es", "AR"));
                return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
            }
            @Override public DayOfWeek fromString(String s) {
                return null;
            }
        });
        c.setValue(inicial);
        return c;
    }

    private TextField campoHora(String inicial) {
        TextField f = new TextField(inicial);
        f.setPromptText("HH:mm");
        f.setPrefColumnCount(5);
        return f;
    }

    private LocalTime parsearHora(String texto) {
        return LocalTime.parse(texto == null ? "" : texto.trim(), HORA_MIN);
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onNuevoDiagrama() {
        if (servicioCombo.getValue() == null) {
            AlertUtils.showWarn("Seleccioná un servicio primero.");
            return;
        }
        if (!confirmarDescarteSiDirty()) {
            return;
        }

        // Por defecto: el mes calendario siguiente completo.
        LocalDate primeroProximoMes = ar.com.hmu.util.Reloj.hoy().plusMonths(1).withDayOfMonth(1);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Nuevo diagrama");
        dialog.setHeaderText("Nuevo diagrama para " + servicioCombo.getValue().getNombre());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.control.DatePicker desdePicker =
                new javafx.scene.control.DatePicker(primeroProximoMes);
        javafx.scene.control.DatePicker hastaPicker =
                new javafx.scene.control.DatePicker(primeroProximoMes.withDayOfMonth(
                        primeroProximoMes.lengthOfMonth()));
        ar.com.hmu.util.FechaUtils.formatear(desdePicker, hastaPicker);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Desde:"), desdePicker);
        grid.addRow(1, new Label("Hasta:"), hastaPicker);
        Label nota = new Label("La grilla inicial se genera desde el horario de cada empleado.");
        nota.getStyleClass().add("ayuda");
        grid.add(nota, 1, 2);
        dialog.getDialogPane().setContent(grid);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            DiagramaDeServicio nuevo = diagramaService.crearBorrador(
                    servicioCombo.getValue().getId(),
                    desdePicker.getValue(), hastaPicker.getValue(), usuarioActual);
            cargarDiagramasDe(servicioCombo.getValue().getId());
            diagramaCombo.getItems().stream()
                    .filter(d -> d.getId().equals(nuevo.getId()))
                    .findFirst()
                    .ifPresent(d -> diagramaCombo.getSelectionModel().select(d));
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo crear el diagrama:\n" + e.getMessage());
        }
    }

    @FXML
    private void onGuardar() {
        if (diagramaActual == null || !dirty) {
            return;
        }
        try {
            diagramaService.guardarJornadas(diagramaActual, jornadasComoLista(), usuarioActual);
            dirty = false;
            actualizarControles();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo guardar el diagrama:\n" + e.getMessage());
        }
    }

    @FXML
    private void onValidar() {
        if (diagramaActual == null) {
            return;
        }
        // El validador lee de la BD: persistimos los cambios pendientes primero.
        if (dirty) {
            onGuardar();
            if (dirty) {
                return; // el guardado falló; el error ya se mostró
            }
        }
        try {
            List<Violacion> violaciones = diagramaService.validarJornadas(diagramaActual.getId());
            List<Violacion> advertencias = diagramaService.advertenciasCarga(diagramaActual.getId());

            celdasConViolacion.clear();
            validacionList.getItems().clear();
            for (Violacion v : violaciones) {
                validacionList.getItems().add("✖ " + v);
                if (v.fecha() != null) {
                    celdasConViolacion.add(claveCelda(v.empleadoId(), v.fecha()));
                }
            }
            for (Violacion a : advertencias) {
                validacionList.getItems().add("⚠ " + a.mensaje());
            }
            if (violaciones.isEmpty() && advertencias.isEmpty()) {
                validacionResumenLabel.setText("Sin violaciones ni advertencias. Listo para enviar.");
            } else {
                int v = violaciones.size();
                int a = advertencias.size();
                String bloqueo = v == 0 ? "Ninguna violación bloquea el envío"
                        : v == 1 ? "1 violación que bloquea el envío"
                        : v + " violaciones que bloquean el envío";
                String avisos = a == 0 ? "sin advertencias" : a == 1 ? "1 advertencia" : a + " advertencias";
                validacionResumenLabel.setText(bloqueo + " · " + avisos);
            }
            refrescarVistaActiva();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al validar el diagrama:\n" + e.getMessage());
        }
    }

    @FXML
    private void onEnviar() {
        if (diagramaActual == null) {
            return;
        }
        if (dirty) {
            onGuardar();
            if (dirty) {
                return;
            }
        }
        if (!AlertUtils.showConfirm("¿Enviar el diagrama a aprobación de la Oficina de Personal?\n"
                + "Mientras esté pendiente no se podrá editar.")) {
            return;
        }
        try {
            diagramaService.enviarParaAprobacion(diagramaActual, usuarioActual);
            AlertUtils.showInfo("Diagrama enviado a aprobación.");
            refrescarComboDiagrama();
            actualizarControles();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo enviar el diagrama:\n" + e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        if (diagramaActual == null) {
            return;
        }
        if (!AlertUtils.showConfirm("¿Eliminar este borrador de diagrama?\n"
                + "Se pierden todas sus jornadas.")) {
            return;
        }
        try {
            diagramaService.eliminarBorrador(diagramaActual, usuarioActual);
            diagramaActual = null;
            dirty = false;
            cargarDiagramasDe(servicioCombo.getValue().getId());
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo eliminar el borrador:\n" + e.getMessage());
        }
    }

    /**
     * Aprobación final de la Oficina de Personal: el diagrama queda
     * inmutable. (El acto de aprobación del jefe a nivel servicio es el
     * "Enviar a aprobación"; esto es el visto bueno administrativo.)
     */
    @FXML
    private void onAprobar() {
        if (diagramaActual == null) {
            return;
        }
        if (!AlertUtils.showConfirm("¿Aprobar el diagrama de servicio?\n"
                + "A partir de la aprobación es inmutable: los cambios posteriores\n"
                + "se gestionan como novedades (CH/CG) de cada agente.")) {
            return;
        }
        try {
            diagramaService.aprobar(diagramaActual, usuarioActual);
            AlertUtils.showInfo("Diagrama aprobado.");
            refrescarComboDiagrama();
            actualizarControles();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo aprobar el diagrama:\n" + e.getMessage());
        }
    }

    /** Observación de OP: vuelve a la jefatura con comentarios obligatorios. */
    @FXML
    private void onObservar() {
        if (diagramaActual == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Observar diagrama");
        dialog.setHeaderText("El diagrama vuelve a la jefatura para corrección.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.control.TextArea comentariosArea = new javafx.scene.control.TextArea();
        comentariosArea.setPromptText("Comentarios para la jefatura (obligatorio)");
        comentariosArea.setPrefRowCount(4);
        comentariosArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Observaciones:"), comentariosArea);
        dialog.getDialogPane().setContent(grid);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        String comentarios = comentariosArea.getText();
        if (comentarios == null || comentarios.isBlank()) {
            AlertUtils.showWarn("La observación requiere comentarios para la jefatura.");
            return;
        }

        try {
            diagramaService.observar(diagramaActual, comentarios.trim(), usuarioActual);
            AlertUtils.showInfo("Diagrama observado. Se notificó a la jefatura.");
            refrescarComboDiagrama();
            actualizarControles();
        } catch (ServiceException e) {
            AlertUtils.showErr("No se pudo observar el diagrama:\n" + e.getMessage());
        }
    }

    /**
     * Diálogo de permisos de vista: un checkbox por empleado del servicio.
     * Tildado = en "Mi Diagrama" ve la grilla completa del servicio (la
     * cartelera); destildado = sólo sus propias jornadas.
     */
    @FXML
    private void onVistaEmpleados() {
        List<Usuario> empleados = new ArrayList<>(grillaTable.getItems());
        if (empleados.isEmpty()) {
            AlertUtils.showWarn("El servicio no tiene agentes activos.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Vista de agentes");
        dialog.setHeaderText("Tildado: el agente ve el diagrama completo del servicio.\n"
                + "Destildado: sólo ve sus propias jornadas.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Map<UUID, javafx.scene.control.CheckBox> checks = new HashMap<>();
        Map<UUID, Boolean> valoresOriginales = new HashMap<>();
        VBox lista = new VBox(6);
        try {
            for (Usuario emp : empleados) {
                boolean actual = diagramaService.puedeVerContexto(emp.getId());
                javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(nombreDe(emp));
                cb.setSelected(actual);
                checks.put(emp.getId(), cb);
                valoresOriginales.put(emp.getId(), actual);
                lista.getChildren().add(cb);
            }
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al leer los permisos de vista:\n" + e.getMessage());
            return;
        }
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(260, empleados.size() * 30 + 10));
        dialog.getDialogPane().setContent(scroll);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            for (Usuario emp : empleados) {
                boolean nuevo = checks.get(emp.getId()).isSelected();
                if (nuevo != valoresOriginales.get(emp.getId())) {
                    diagramaService.setPermisoVistaCompleta(emp.getId(), nuevo, usuarioActual);
                }
            }
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al guardar los permisos de vista:\n" + e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        if (!confirmarDescarteSiDirty()) {
            return;
        }
        ((Stage) grillaTable.getScene().getWindow()).close();
    }

    // ============================================================
    // Helpers de estado
    // ============================================================

    private boolean esOpODireccion() {
        return usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION);
    }

    private boolean puedeGestionar() {
        if (esOpODireccion()) {
            return true;
        }
        return usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO)
                && diagramaActual != null
                && diagramaActual.getServicioId().equals(usuarioActual.getServicioId());
    }

    private boolean esEditable() {
        return !modoEmpleado
                && diagramaActual != null
                && (diagramaActual.getEstado() == EstadoDiagrama.BORRADOR
                    || diagramaActual.getEstado() == EstadoDiagrama.OBSERVADO)
                && puedeGestionar();
    }

    private boolean confirmarDescarteSiDirty() {
        return !dirty || AlertUtils.showConfirm(
                "Hay cambios sin guardar en la grilla. ¿Descartarlos?");
    }

    private List<JornadaLaboral> jornadasComoLista() {
        List<JornadaLaboral> lista = new ArrayList<>();
        for (Map<LocalDate, JornadaLaboral> porFecha : jornadasPorEmpleado.values()) {
            lista.addAll(porFecha.values());
        }
        return lista;
    }

    /** Recarga el combo para que la etiqueta refleje el nuevo estado. */
    private void refrescarComboDiagrama() {
        DiagramaDeServicio seleccionado = diagramaActual;
        List<DiagramaDeServicio> items = new ArrayList<>(diagramaCombo.getItems());
        diagramaCombo.getItems().setAll(items);
        diagramaCombo.getSelectionModel().select(seleccionado);
    }

    private void actualizarControles() {
        boolean hayDiagrama = diagramaActual != null;
        boolean editable = esEditable();

        eliminarButton.setDisable(!hayDiagrama || !editable
                || diagramaActual.getEstado() != EstadoDiagrama.BORRADOR);
        validarButton.setDisable(!hayDiagrama);
        guardarButton.setDisable(!editable || !dirty);
        enviarButton.setDisable(!editable);
        nuevoDiagramaButton.setDisable(servicioCombo.getValue() == null || !puedeCrear());
        ayudaLabel.setVisible(editable);

        // Aprobación final: sólo OP y sólo con el diagrama pendiente.
        boolean puedeAprobar = !modoEmpleado
                && hayDiagrama
                && diagramaActual.getEstado() == EstadoDiagrama.PENDIENTE_APROBACION
                && usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL);
        aprobarButton.setVisible(puedeAprobar);
        aprobarButton.setManaged(puedeAprobar);
        observarButton.setVisible(puedeAprobar);
        observarButton.setManaged(puedeAprobar);

        // Permisos de vista por empleado: administra quien gestiona el servicio.
        boolean puedeAdministrarVista = !modoEmpleado && hayDiagrama && puedeGestionar();
        vistaEmpleadosButton.setVisible(puedeAdministrarVista);
        vistaEmpleadosButton.setManaged(puedeAdministrarVista);

        // Plantillas de precarga: sólo con el diagrama editable.
        aplicarPlantillaButton.setVisible(editable);
        aplicarPlantillaButton.setManaged(editable);

        // Chip de estado.
        estadoChip.getStyleClass().removeAll("estado-borrador", "estado-pendiente",
                "estado-aprobado", "estado-observado");
        if (hayDiagrama) {
            estadoChip.setText(etiquetaEstado(diagramaActual.getEstado())
                    + (dirty ? " •" : ""));
            estadoChip.getStyleClass().add(claseEstado(diagramaActual.getEstado()));
        } else {
            estadoChip.setText("");
        }

        // Banner de observación.
        boolean observado = hayDiagrama
                && diagramaActual.getEstado() == EstadoDiagrama.OBSERVADO
                && diagramaActual.getComentariosObservacion() != null;
        observacionBanner.setVisible(observado);
        observacionBanner.setManaged(observado);
        if (observado) {
            observacionLabel.setText(diagramaActual.getComentariosObservacion());
        }
    }

    private boolean puedeCrear() {
        return esOpODireccion() || usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO);
    }

    private String etiquetaEstado(EstadoDiagrama estado) {
        return switch (estado) {
            case BORRADOR -> "Borrador";
            case PENDIENTE_APROBACION -> "Pendiente de aprobación";
            case APROBADO -> "Aprobado";
            case OBSERVADO -> "Observado";
        };
    }

    private String claseEstado(EstadoDiagrama estado) {
        return switch (estado) {
            case BORRADOR -> "estado-borrador";
            case PENDIENTE_APROBACION -> "estado-pendiente";
            case APROBADO -> "estado-aprobado";
            case OBSERVADO -> "estado-observado";
        };
    }
}
