package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoTramite;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.MemorandumAutorizacion;
import ar.com.hmu.model.MemorandumDestinatario;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.EstadoTramiteRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.MemorandumService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.MarkdownRenderer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controlador del detalle de un memorándum: render Markdown del contenido,
 * cabecera con metadata, y botones de acción contextuales según rol del
 * usuario y estado del memo.
 */
public class DetalleMemorandumController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label asuntoLabel;
    @FXML private Label remitenteLabel;
    @FXML private Label destinatariosLabel;
    @FXML private Label fechaLabel;
    @FXML private Label estadoLabel;
    @FXML private Label historialLabel;
    @FXML private WebView contenidoWebView;

    @FXML private Button editarButton;
    @FXML private Button autorizarButton;
    @FXML private Button rechazarButton;
    @FXML private Button observarButton;
    @FXML private Button reenviarButton;
    @FXML private Button marcarLeidoButton;
    @FXML private Button eliminarButton;

    private MemorandumService memorandumService;
    private UsuarioRepository usuarioRepository;
    private EstadoTramiteRepository estadoTramiteRepository;
    private Usuario usuarioActual;
    private Memorandum memo;

    private final Map<UUID, String> cacheNombres = new HashMap<>();

    public void setMemorandumService(MemorandumService s) { this.memorandumService = s; }
    public void setUsuarioRepository(UsuarioRepository r) { this.usuarioRepository = r; }
    public void setEstadoTramiteRepository(EstadoTramiteRepository r) { this.estadoTramiteRepository = r; }
    public void setUsuarioActual(Usuario u) { this.usuarioActual = u; }
    public void setMemorandum(Memorandum m) { this.memo = m; }

    @FXML
    public void initialize() {
        // Configuración mínima en initialize; el resto se hace en
        // postInitialize una vez inyectadas las dependencias.
    }

    /** Llamar después de set* para poblar la pantalla. Carga los hijos
     *  del memo (destinatarios + autorizaciones) para mostrar la metadata
     *  completa. */
    public void postInitialize() {
        try {
            Memorandum completo = memorandumService.findDetalleCompleto(memo.getId());
            if (completo != null) {
                this.memo = completo;
            }
        } catch (ServiceException e) {
            AlertUtils.showWarn("No se pudo cargar el detalle completo del memorándum: " + e.getMessage());
        }
        renderCabecera();
        renderContenido();
        renderHistorial();
        ajustarBotones();
        // ESC cierra la ventana de detalle.
        asuntoLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ESCAPE),
                        this::cerrar);
            }
        });
    }

    // ============================================================
    // Render
    // ============================================================

    private void renderCabecera() {
        asuntoLabel.setText(memo.getAsunto() != null ? memo.getAsunto() : "(sin asunto)");
        remitenteLabel.setText(nombrePorId(memo.getRemitenteId()));
        fechaLabel.setText(memo.getFechaEnvio() != null ? memo.getFechaEnvio().format(FORMATO_FECHA) : "(borrador)");
        estadoLabel.setText(estadoLegible());

        try {
            List<MemorandumDestinatario> dests = obtenerDestinatarios();
            String texto = dests.stream()
                    .map(d -> nombrePorId(d.getUsuarioId()) + (d.estaLeido() ? " (leído)" : ""))
                    .collect(Collectors.joining(" • "));
            destinatariosLabel.setText(texto.isEmpty() ? "(sin destinatarios)" : texto);
        } catch (Exception e) {
            destinatariosLabel.setText("(error al cargar destinatarios)");
        }
    }

    private void renderContenido() {
        String html = MarkdownRenderer.renderToHtmlDocument(memo.getContenido());
        contenidoWebView.getEngine().loadContent(html, "text/html");
    }

    private void renderHistorial() {
        try {
            List<MemorandumAutorizacion> auths = obtenerAutorizaciones();
            if (auths.isEmpty()) {
                historialLabel.setText("");
                return;
            }
            StringBuilder sb = new StringBuilder("Historial de autorización: ");
            for (int i = 0; i < auths.size(); i++) {
                MemorandumAutorizacion a = auths.get(i);
                if (i > 0) sb.append(" → ");
                sb.append(a.getEstado());
                if (a.getAutorizadoPorId() != null) {
                    sb.append(" por ").append(nombrePorId(a.getAutorizadoPorId()));
                }
                if (a.getFechaAutorizacion() != null) {
                    sb.append(" el ").append(a.getFechaAutorizacion().format(FORMATO_FECHA));
                }
                if (a.getComentarios() != null && !a.getComentarios().isBlank()) {
                    sb.append(" — \"").append(a.getComentarios()).append("\"");
                }
            }
            historialLabel.setText(sb.toString());
        } catch (Exception e) {
            historialLabel.setText("");
        }
    }

    private void ajustarBotones() {
        EstadoTramite estado = estadoActual();
        boolean esRemitente = usuarioActual.getId().equals(memo.getRemitenteId());
        boolean esDestinatario = esDestinatario();
        boolean esEncargadoActual = esEncargadoDelServicioDelRemitente();

        // Por defecto ocultos.
        editarButton.setVisible(false);
        autorizarButton.setVisible(false);
        rechazarButton.setVisible(false);
        observarButton.setVisible(false);
        reenviarButton.setVisible(false);
        marcarLeidoButton.setVisible(false);
        eliminarButton.setVisible(false);

        if (estado == EstadoTramite.PENDIENTE_DE_AUTORIZACION && esEncargadoActual) {
            autorizarButton.setVisible(true);
            rechazarButton.setVisible(true);
            observarButton.setVisible(true);
        }
        if (estado == EstadoTramite.OBSERVADO && esRemitente) {
            reenviarButton.setVisible(true);
        }
        if ((estado == EstadoTramite.ENVIADO || estado == EstadoTramite.LEIDO) && esDestinatario) {
            // Sólo ofrecer marcar leído si todavía no leyó.
            try {
                List<MemorandumDestinatario> dests = obtenerDestinatarios();
                boolean yaLeyo = dests.stream()
                        .anyMatch(d -> usuarioActual.getId().equals(d.getUsuarioId()) && d.estaLeido());
                marcarLeidoButton.setVisible(!yaLeyo);
            } catch (Exception ignored) {
            }
        }
        if (estado == EstadoTramite.BORRADOR && esRemitente) {
            editarButton.setVisible(true);
            eliminarButton.setVisible(true);
        }

        for (Button b : new Button[]{editarButton, autorizarButton, rechazarButton, observarButton,
                reenviarButton, marcarLeidoButton, eliminarButton}) {
            b.setManaged(b.isVisible());
        }
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onAutorizar() {
        try {
            memorandumService.autorizar(memo.getId(), usuarioActual);
            AlertUtils.showInfo("Memorándum autorizado.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al autorizar: " + e.getMessage());
        }
    }

    @FXML
    private void onRechazar() {
        Optional<String> motivo = pedirComentarios("Rechazar memorándum",
                "Motivo del rechazo (opcional pero recomendado):", false);
        if (motivo.isEmpty()) return;
        try {
            memorandumService.rechazar(memo.getId(), usuarioActual, motivo.get());
            AlertUtils.showInfo("Memorándum rechazado.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al rechazar: " + e.getMessage());
        }
    }

    @FXML
    private void onObservar() {
        Optional<String> coment = pedirComentarios("Observar memorándum",
                "Indicá las correcciones a realizar (obligatorio):", true);
        if (coment.isEmpty()) return;
        try {
            memorandumService.observar(memo.getId(), usuarioActual, coment.get());
            AlertUtils.showInfo("Memorándum devuelto al remitente con observaciones.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al observar: " + e.getMessage());
        }
    }

    @FXML
    private void onReenviar() {
        try {
            memorandumService.reenviarPostObservacion(memo.getId(), usuarioActual);
            AlertUtils.showInfo("Memorándum reenviado para autorización.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al reenviar: " + e.getMessage());
        }
    }

    @FXML
    private void onMarcarLeido() {
        try {
            memorandumService.marcarLeido(memo.getId(), usuarioActual);
            AlertUtils.showInfo("Memorándum marcado como leído.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al marcar como leído: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminarBorrador() {
        boolean confirmar = AlertUtils.showConfirm("¿Eliminar este borrador? Esta acción no se puede deshacer.");
        if (!confirmar) return;
        try {
            memorandumService.eliminarBorrador(memo.getId(), usuarioActual);
            AlertUtils.showInfo("Borrador eliminado.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al eliminar el borrador: " + e.getMessage());
        }
    }

    @FXML
    private void onEditarBorrador() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/redactarMemorandum.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == RedactarMemorandumController.class) {
                    RedactarMemorandumController c = new RedactarMemorandumController();
                    c.setMemorandumService(memorandumService);
                    c.setUsuarioActual(usuarioActual);
                    c.setMemoEdicion(memo);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            javafx.scene.Parent root = loader.load();
            RedactarMemorandumController controller = loader.getController();
            controller.postInitialize();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Editar memorándum :: " + ar.com.hmu.util.AppInfo.PRG_LONG_TITLE);
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            try {
                stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream(ar.com.hmu.util.AppInfo.ICON_IMAGE)));
            } catch (Exception ignored) {}
            stage.showAndWait();
            cerrar();
        } catch (java.io.IOException e) {
            AlertUtils.showErr("Error al abrir el editor: " + e.getMessage());
        }
    }

    @FXML
    private void onCerrar() {
        cerrar();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void cerrar() {
        Stage stage = (Stage) asuntoLabel.getScene().getWindow();
        stage.close();
    }

    private Optional<String> pedirComentarios(String titulo, String prompt, boolean obligatorio) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.setHeaderText(prompt);

        TextArea area = new TextArea();
        area.setPrefRowCount(5);
        area.setWrapText(true);
        VBox box = new VBox(area);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == ButtonType.OK ? area.getText() : null);

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return Optional.empty();
        String texto = result.get() != null ? result.get().trim() : "";
        if (obligatorio && texto.isEmpty()) {
            AlertUtils.showWarn("Las observaciones son obligatorias.");
            return Optional.empty();
        }
        return Optional.of(texto);
    }

    private List<MemorandumDestinatario> obtenerDestinatarios() {
        // Tras postInitialize, el memo tiene los hijos cargados vía
        // MemorandumService.findDetalleCompleto. Si por algún motivo no
        // están, devolvemos lista vacía.
        return memo.getDestinatarios() != null ? memo.getDestinatarios() : List.of();
    }

    private List<MemorandumAutorizacion> obtenerAutorizaciones() {
        return memo.getAutorizaciones() != null ? memo.getAutorizaciones() : List.of();
    }

    private boolean esDestinatario() {
        try {
            List<MemorandumDestinatario> dests = obtenerDestinatarios();
            return dests.stream().anyMatch(d -> usuarioActual.getId().equals(d.getUsuarioId()));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean esEncargadoDelServicioDelRemitente() {
        try {
            return memorandumService.puedeResolverAutorizacion(memo, usuarioActual);
        } catch (ServiceException e) {
            return false;
        }
    }

    private EstadoTramite estadoActual() {
        try {
            return estadoTramiteRepository.getEstadoTramite(memo.getEstadoTramiteId());
        } catch (SQLException e) {
            return null;
        }
    }

    private String estadoLegible() {
        EstadoTramite e = estadoActual();
        return e != null ? e.toDbName() : "";
    }

    private String nombrePorId(UUID userId) {
        if (userId == null) return "";
        String cached = cacheNombres.get(userId);
        if (cached != null) return cached;
        try {
            Usuario u = usuarioRepository.readByUUID(userId);
            String nombre = u != null ? (u.getApellidos() + ", " + u.getNombres()) : "(desconocido)";
            cacheNombres.put(userId, nombre);
            return nombre;
        } catch (SQLException e) {
            return "(error)";
        }
    }
}
