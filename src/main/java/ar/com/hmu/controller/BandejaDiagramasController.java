package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.ServicioService;
import ar.com.hmu.service.diagramacion.DiagramaService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.AppInfo;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bandeja "Consulta de Diagramas de Servicios" (OP / Dirección): lista los
 * diagramas de todos los servicios con filtros por estado (por defecto,
 * pendientes de aprobación) y por servicio. Abrir un diagrama navega a la
 * pantalla de diagramación, donde OP dispone de Aprobar / Observar.
 *
 * <p>Sirve a la vez como bandeja de aprobación (filtro por defecto) y como
 * consulta histórica (filtro "Todos" / "Aprobados" / etc.).</p>
 */
public class BandejaDiagramasController {

    /** Opciones del filtro de estado; {@code null} = todos. */
    private enum FiltroEstado {
        PENDIENTES("Pendientes de aprobación", EstadoDiagrama.PENDIENTE_APROBACION),
        TODOS("Todos", null),
        BORRADORES("Borradores", EstadoDiagrama.BORRADOR),
        APROBADOS("Aprobados", EstadoDiagrama.APROBADO),
        OBSERVADOS("Observados", EstadoDiagrama.OBSERVADO);

        final String etiqueta;
        final EstadoDiagrama estado;

        FiltroEstado(String etiqueta, EstadoDiagrama estado) {
            this.etiqueta = etiqueta;
            this.estado = estado;
        }
    }

    /** Fila de la tabla: diagrama + nombres ya resueltos. */
    private record Fila(DiagramaDeServicio diagrama, String servicioNombre, String creadoPor) {
    }

    @FXML private ComboBox<FiltroEstado> estadoFiltroCombo;
    @FXML private ComboBox<Servicio> servicioFiltroCombo;
    @FXML private TableView<Fila> diagramasTable;
    @FXML private TableColumn<Fila, String> servicioCol;
    @FXML private TableColumn<Fila, String> periodoCol;
    @FXML private TableColumn<Fila, String> estadoCol;
    @FXML private TableColumn<Fila, String> actualizadoCol;
    @FXML private TableColumn<Fila, String> creadoPorCol;
    @FXML private Button abrirButton;

    private DiagramaService diagramaService;
    private ServicioService servicioService;
    private UsuarioRepository usuarioRepository;
    private Usuario usuarioActual;

    /** Nombres de servicios por id (cargado una vez por sesión de bandeja). */
    private final Map<UUID, String> nombresServicios = new HashMap<>();
    /** Cache de nombres de creadores (se van resolviendo a demanda). */
    private final Map<UUID, String> nombresCreadores = new HashMap<>();

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ============================================================
    // Inyección + inicialización
    // ============================================================

    public void setDiagramaService(DiagramaService diagramaService) {
        this.diagramaService = diagramaService;
    }

    public void setServicioService(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    public void setUsuarioRepository(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    /** Llamar después de inyectar dependencias. */
    public void postInitialize() {
        configurarFiltros();
        configurarTabla();
        onRefrescar();
    }

    private void configurarFiltros() {
        estadoFiltroCombo.getItems().setAll(FiltroEstado.values());
        estadoFiltroCombo.setConverter(new StringConverter<>() {
            @Override public String toString(FiltroEstado f) {
                return f != null ? f.etiqueta : "";
            }
            @Override public FiltroEstado fromString(String s) {
                return null;
            }
        });
        estadoFiltroCombo.getSelectionModel().select(FiltroEstado.PENDIENTES);
        estadoFiltroCombo.valueProperty().addListener((obs, v, n) -> onRefrescar());

        // Sentinela "(Todos)": Servicio con id null.
        Servicio todos = new Servicio();
        todos.setNombre("(Todos)");
        try {
            List<Servicio> servicios = servicioService.readAll();
            servicios.sort(Comparator.comparing(Servicio::getNombre, String.CASE_INSENSITIVE_ORDER));
            for (Servicio s : servicios) {
                nombresServicios.put(s.getId(), s.getNombre());
            }
            servicioFiltroCombo.getItems().add(todos);
            servicioFiltroCombo.getItems().addAll(servicios);
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los servicios: " + e.getMessage());
        }
        servicioFiltroCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Servicio s) {
                return s != null ? s.getNombre() : "";
            }
            @Override public Servicio fromString(String s) {
                return null;
            }
        });
        servicioFiltroCombo.getSelectionModel().select(todos);
        servicioFiltroCombo.valueProperty().addListener((obs, v, n) -> onRefrescar());
    }

    private void configurarTabla() {
        servicioCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().servicioNombre()));
        periodoCol.setCellValueFactory(cd -> {
            DiagramaDeServicio d = cd.getValue().diagrama();
            return new SimpleStringProperty(d.getFechaInicio().format(FECHA_CORTA)
                    + " – " + d.getFechaFin().format(FECHA_CORTA));
        });
        estadoCol.setCellValueFactory(cd -> new SimpleStringProperty(
                etiquetaEstado(cd.getValue().diagrama().getEstado())));
        estadoCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String etiqueta, boolean empty) {
                super.updateItem(etiqueta, empty);
                setText(null);
                if (empty || etiqueta == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                javafx.scene.control.Label chip = new javafx.scene.control.Label(etiqueta);
                chip.getStyleClass().addAll("estado-chip", "chip-tabla",
                        claseEstado(getTableRow().getItem().diagrama().getEstado()));
                setGraphic(chip);
            }
        });
        actualizadoCol.setCellValueFactory(cd -> {
            DiagramaDeServicio d = cd.getValue().diagrama();
            return new SimpleStringProperty(d.getUpdatedAt() != null
                    ? d.getUpdatedAt().format(FECHA_HORA) : "");
        });
        creadoPorCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().creadoPor()));

        diagramasTable.setPlaceholder(new javafx.scene.control.Label(
                "No hay diagramas que coincidan con el filtro."));
        diagramasTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Fila> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    abrirDiagrama(row.getItem());
                }
            });
            return row;
        });
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onRefrescar() {
        try {
            EstadoDiagrama filtroEstado = (estadoFiltroCombo.getValue() != null)
                    ? estadoFiltroCombo.getValue().estado : null;
            Servicio filtroServicio = servicioFiltroCombo.getValue();
            UUID filtroServicioId = (filtroServicio != null) ? filtroServicio.getId() : null;

            List<Fila> filas = new ArrayList<>();
            for (DiagramaDeServicio d : diagramaService.todos()) {
                if (filtroEstado != null && d.getEstado() != filtroEstado) {
                    continue;
                }
                if (filtroServicioId != null && !filtroServicioId.equals(d.getServicioId())) {
                    continue;
                }
                filas.add(new Fila(d,
                        nombresServicios.getOrDefault(d.getServicioId(), "(desconocido)"),
                        nombreCreador(d.getCreadoPorId())));
            }
            // Más recientes primero; a igual fecha, por servicio (orden estable entre ejecuciones).
            filas.sort(Comparator.comparing(
                    (Fila f) -> f.diagrama().getUpdatedAt(),
                    Comparator.nullsLast(Comparator.<java.time.LocalDateTime>reverseOrder()))
                    .thenComparing(Fila::servicioNombre, String.CASE_INSENSITIVE_ORDER));
            diagramasTable.getItems().setAll(filas);
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los diagramas: " + e.getMessage());
        }
    }

    @FXML
    private void onAbrir() {
        Fila seleccionada = diagramasTable.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            AlertUtils.showWarn("Seleccioná un diagrama de la lista.");
            return;
        }
        abrirDiagrama(seleccionada);
    }

    @FXML
    private void onCerrar() {
        ((Stage) diagramasTable.getScene().getWindow()).close();
    }

    /**
     * Abre la pantalla de diagramación navegada al diagrama elegido. OP ve
     * ahí los botones Aprobar / Observar cuando corresponde. Al cerrar, la
     * bandeja se refresca (el estado pudo cambiar).
     */
    private void abrirDiagrama(Fila fila) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/diagramacionServicio.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == DiagramacionServicioController.class) {
                    DiagramacionServicioController c = new DiagramacionServicioController();
                    c.setDiagramaService(diagramaService);
                    c.setServicioService(servicioService);
                    c.setUsuarioActual(usuarioActual);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            DiagramacionServicioController controller = loader.getController();
            controller.postInitialize();
            controller.seleccionarDiagrama(fila.diagrama().getServicioId(), fila.diagrama().getId());

            Stage stage = new Stage();
            stage.setTitle("Diagrama de " + fila.servicioNombre() + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(diagramasTable.getScene().getWindow());
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();

            onRefrescar();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir el diagrama: " + e.getMessage());
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String nombreCreador(UUID creadoPorId) {
        if (creadoPorId == null) {
            return "";
        }
        return nombresCreadores.computeIfAbsent(creadoPorId, id -> {
            try {
                Usuario u = usuarioRepository.readByUUID(id);
                if (u != null) {
                    return (u.getApellidos() + ", " + u.getNombres()).trim();
                }
            } catch (SQLException e) {
                // nombre cosmético: no propaga
            }
            return "(desconocido)";
        });
    }

    private static String claseEstado(EstadoDiagrama estado) {
        return switch (estado) {
            case BORRADOR -> "estado-borrador";
            case PENDIENTE_APROBACION -> "estado-pendiente";
            case APROBADO -> "estado-aprobado";
            case OBSERVADO -> "estado-observado";
        };
    }

    private String etiquetaEstado(EstadoDiagrama estado) {
        return switch (estado) {
            case BORRADOR -> "Borrador";
            case PENDIENTE_APROBACION -> "Pendiente OP";
            case APROBADO -> "Aprobado";
            case OBSERVADO -> "Observado";
        };
    }
}
