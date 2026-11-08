package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoTramite;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.EstadoTramiteRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.MemorandumService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.AppInfo;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador de la pantalla principal de memorándums (bandejas y acciones).
 *
 * <p>Tres pestañas: Recibidos / Enviados / Pendientes de autorizar.
 * Doble clic en una fila abre el detalle. La pestaña "Pendientes" se
 * oculta para usuarios sin rol de jefatura/OP/dirección.</p>
 */
public class BandejaMemorandumsController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private TabPane tabPane;
    @FXML private Tab tabRecibidos;
    @FXML private Tab tabEnviados;
    @FXML private Tab tabPendientes;

    @FXML private Label resumenLabel;
    @FXML private Button nuevoMemoButton;

    @FXML private TableView<Memorandum> recibidosTable;
    @FXML private TableColumn<Memorandum, String> recibidosFechaCol;
    @FXML private TableColumn<Memorandum, String> recibidosAsuntoCol;
    @FXML private TableColumn<Memorandum, String> recibidosRemitenteCol;
    @FXML private TableColumn<Memorandum, String> recibidosEstadoCol;

    @FXML private TableView<Memorandum> enviadosTable;
    @FXML private TableColumn<Memorandum, String> enviadosFechaCol;
    @FXML private TableColumn<Memorandum, String> enviadosAsuntoCol;
    @FXML private TableColumn<Memorandum, String> enviadosDestinatariosCol;
    @FXML private TableColumn<Memorandum, String> enviadosEstadoCol;

    @FXML private TableView<Memorandum> pendientesTable;
    @FXML private TableColumn<Memorandum, String> pendientesFechaCol;
    @FXML private TableColumn<Memorandum, String> pendientesAsuntoCol;
    @FXML private TableColumn<Memorandum, String> pendientesRemitenteCol;

    private MemorandumService memorandumService;
    private UsuarioRepository usuarioRepository;
    private EstadoTramiteRepository estadoTramiteRepository;
    private Usuario usuarioActual;

    /** Cache de nombres de usuario por UUID para no martillar la BD. */
    private final Map<UUID, String> nombresUsuarioCache = new HashMap<>();
    /** Cache de resúmenes de destinatarios por memo (col. Enviados). */
    private final Map<UUID, String> resumenDestinatariosCache = new HashMap<>();

    public void setMemorandumService(MemorandumService memorandumService) {
        this.memorandumService = memorandumService;
    }

    public void setUsuarioRepository(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public void setEstadoTramiteRepository(EstadoTramiteRepository estadoTramiteRepository) {
        this.estadoTramiteRepository = estadoTramiteRepository;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarDoubleClickAbreDetalle(recibidosTable);
        configurarDoubleClickAbreDetalle(enviadosTable);
        configurarDoubleClickAbreDetalle(pendientesTable);
    }

    /** Llamar tras setear todas las dependencias. Carga las bandejas y
     *  ajusta visibilidad según rol. */
    public void postInitialize() {
        boolean puedeAutorizar = usuarioActual != null && usuarioActual.hasRole(
                TipoUsuario.JEFATURADESERVICIO,
                TipoUsuario.OFICINADEPERSONAL,
                TipoUsuario.DIRECCION);
        if (!puedeAutorizar) {
            tabPane.getTabs().remove(tabPendientes);
        }
        cargarBandejas();
        // ESC cierra la ventana. Se registra cuando la Scene queda asociada.
        tabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ESCAPE),
                        this::onCerrar);
            }
        });
    }

    @FXML
    private void onRefrescar() {
        nombresUsuarioCache.clear();
        resumenDestinatariosCache.clear();
        cargarBandejas();
    }

    @FXML
    private void onNuevoMemo() {
        abrirRedaccion(null);
    }

    @FXML
    private void onCerrar() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        stage.close();
    }

    /** Abre la pantalla de redacción. Si {@code memoEdicion} no es null, se
     *  abre en modo edición (precarga el borrador). */
    void abrirRedaccion(Memorandum memoEdicion) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/redactarMemorandum.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == RedactarMemorandumController.class) {
                    RedactarMemorandumController c = new RedactarMemorandumController();
                    c.setMemorandumService(memorandumService);
                    c.setUsuarioActual(usuarioActual);
                    if (memoEdicion != null) {
                        c.setMemoEdicion(memoEdicion);
                    }
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            RedactarMemorandumController controller = loader.getController();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle((memoEdicion != null ? "Editar memorándum" : "Nuevo memorándum")
                    + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();

            cargarBandejas();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir el editor de memorándums: " + e.getMessage());
        }
    }

    // ============================================================
    // Carga de bandejas
    // ============================================================

    private void cargarBandejas() {
        // Invalidar cache: el resumen de destinatarios puede haber cambiado
        // si se editó un borrador y luego se envió.
        resumenDestinatariosCache.clear();
        try {
            List<Memorandum> recibidos = memorandumService.bandejaEntrada(usuarioActual);
            List<Memorandum> enviados = memorandumService.bandejaSalida(usuarioActual);
            recibidosTable.getItems().setAll(recibidos);
            enviadosTable.getItems().setAll(enviados);

            if (tabPane.getTabs().contains(tabPendientes)) {
                List<Memorandum> pendientes = memorandumService.pendientesDeAutorizar(usuarioActual);
                pendientesTable.getItems().setAll(pendientes);
            }

            int noLeidos = memorandumService.contarNoLeidos(usuarioActual);
            resumenLabel.setText(noLeidos > 0 ? "(" + noLeidos + " sin leer)" : "");
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar las bandejas: " + e.getMessage());
        }
    }

    private void configurarColumnas() {
        recibidosFechaCol.setCellValueFactory(c ->
                new SimpleStringProperty(formatearFecha(c.getValue().getFechaEnvio())));
        recibidosAsuntoCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAsunto()));
        recibidosRemitenteCol.setCellValueFactory(c ->
                new SimpleStringProperty(nombrePorId(c.getValue().getRemitenteId())));
        recibidosEstadoCol.setCellValueFactory(c ->
                new SimpleStringProperty(estadoLegible(c.getValue())));

        enviadosFechaCol.setCellValueFactory(c ->
                new SimpleStringProperty(formatearFecha(c.getValue().getFechaEnvio())));
        enviadosAsuntoCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAsunto()));
        enviadosDestinatariosCol.setCellValueFactory(c ->
                new SimpleStringProperty(resumenDestinatarios(c.getValue())));
        enviadosEstadoCol.setCellValueFactory(c ->
                new SimpleStringProperty(estadoLegible(c.getValue())));

        pendientesFechaCol.setCellValueFactory(c ->
                new SimpleStringProperty(formatearFecha(c.getValue().getFechaEnvio())));
        pendientesAsuntoCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAsunto()));
        pendientesRemitenteCol.setCellValueFactory(c ->
                new SimpleStringProperty(nombrePorId(c.getValue().getRemitenteId())));
    }

    private static final javafx.css.PseudoClass BORRADOR_PC =
            javafx.css.PseudoClass.getPseudoClass("borrador");

    private void configurarDoubleClickAbreDetalle(TableView<Memorandum> tabla) {
        boolean esTablaEnviados = tabla == enviadosTable;
        tabla.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Memorandum> row = new javafx.scene.control.TableRow<>() {
                @Override
                protected void updateItem(Memorandum memo, boolean empty) {
                    super.updateItem(memo, empty);
                    // Activar pseudo-class :borrador en filas de la pestaña
                    // Enviados/borradores. La regla CSS está en
                    // /css/bandejaMemorandums.css (color marrón + itálica).
                    boolean esBorrador = esTablaEnviados && !empty && memo != null
                            && esBorrador(memo);
                    pseudoClassStateChanged(BORRADOR_PC, esBorrador);
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    abrirDetalle(row.getItem());
                }
            });
            return row;
        });
    }

    private boolean esBorrador(Memorandum memo) {
        try {
            EstadoTramite e = estadoTramiteRepository.getEstadoTramite(memo.getEstadoTramiteId());
            return e == EstadoTramite.BORRADOR;
        } catch (SQLException ex) {
            return false;
        }
    }

    private void abrirDetalle(Memorandum memo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/detalleMemorandum.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == DetalleMemorandumController.class) {
                    DetalleMemorandumController c = new DetalleMemorandumController();
                    c.setMemorandumService(memorandumService);
                    c.setUsuarioRepository(usuarioRepository);
                    c.setEstadoTramiteRepository(estadoTramiteRepository);
                    c.setUsuarioActual(usuarioActual);
                    c.setMemorandum(memo);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            DetalleMemorandumController controller = loader.getController();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle("Memorándum :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();

            // Al cerrar el detalle, refrescamos por si hubo cambios
            // (marcado leído, autorización resuelta, etc.).
            cargarBandejas();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir el detalle del memorándum: " + e.getMessage());
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String formatearFecha(java.time.LocalDateTime f) {
        return f != null ? f.format(FORMATO_FECHA) : "(borrador)";
    }

    private String nombrePorId(UUID userId) {
        if (userId == null) return "";
        String cached = nombresUsuarioCache.get(userId);
        if (cached != null) return cached;
        try {
            Usuario u = usuarioRepository.readByUUID(userId);
            String nombre = u != null ? (u.getApellidos() + ", " + u.getNombres()) : "(desconocido)";
            nombresUsuarioCache.put(userId, nombre);
            return nombre;
        } catch (SQLException e) {
            return "(error)";
        }
    }

    private String estadoLegible(Memorandum memo) {
        try {
            EstadoTramite e = estadoTramiteRepository.getEstadoTramite(memo.getEstadoTramiteId());
            return e != null ? e.toDbName() : "";
        } catch (SQLException ex) {
            return "";
        }
    }

    /**
     * Devuelve un resumen tipo "Apellido1 X., Apellido2 Y. (+2)" de los
     * destinatarios de un memo. Cacheado por memoId para evitar N+1 al
     * navegar la tabla.
     */
    private String resumenDestinatarios(Memorandum memo) {
        if (memo == null || memo.getId() == null) return "";
        String cached = resumenDestinatariosCache.get(memo.getId());
        if (cached != null) return cached;
        try {
            ar.com.hmu.model.Memorandum completo = memorandumService.findDetalleCompleto(memo.getId());
            if (completo == null || completo.getDestinatarios() == null
                    || completo.getDestinatarios().isEmpty()) {
                resumenDestinatariosCache.put(memo.getId(), "(sin destinatarios)");
                return "(sin destinatarios)";
            }
            int total = completo.getDestinatarios().size();
            int max = Math.min(total, 2);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < max; i++) {
                if (i > 0) sb.append(", ");
                sb.append(nombrePorId(completo.getDestinatarios().get(i).getUsuarioId()));
            }
            if (total > max) sb.append(" (+").append(total - max).append(")");
            String resumen = sb.toString();
            resumenDestinatariosCache.put(memo.getId(), resumen);
            return resumen;
        } catch (ServiceException e) {
            return "(error)";
        }
    }
}
