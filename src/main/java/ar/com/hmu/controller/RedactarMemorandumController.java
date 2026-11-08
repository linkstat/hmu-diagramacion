package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.MemorandumDestinatario;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.MemorandumService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.MarkdownRenderer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controlador de la pantalla de redacción de memorándums.
 *
 * <p>Soporta dos modos:</p>
 * <ul>
 *   <li><b>Nuevo</b>: el controller se abre sin memo precargado y el remitente
 *       redacta desde cero.</li>
 *   <li><b>Editar borrador</b>: se le pasa un {@link Memorandum} en estado
 *       BORRADOR del propio remitente, y se precargan asunto, contenido y
 *       destinatarios para modificar antes de guardar/enviar.</li>
 * </ul>
 *
 * <p>El editor es un {@link TextArea} plano con toolbar que inserta sintaxis
 * Markdown sobre el cursor o la selección. El botón "Vista previa" abre un
 * popup modal con el render Markdown→HTML del contenido actual.</p>
 */
public class RedactarMemorandumController {

    private static final int CONTENIDO_MAX = 8000;
    private static final int ASUNTO_MAX = 255;

    @FXML private TextField asuntoTextField;
    @FXML private ComboBox<Usuario> destinatariosComboBox;
    @FXML private ListView<Usuario> destinatariosSeleccionadosList;
    @FXML private TextArea contenidoTextArea;
    @FXML private WebView vistaPreviaWebView;
    @FXML private Button enviarButton;
    @FXML private Button cancelarButton;

    /** Lista completa de destinatarios válidos (sin filtrar). El ComboBox
     *  muestra una vista filtrada de esta lista según lo que el usuario
     *  tipea en el editor. */
    private final javafx.collections.ObservableList<Usuario> destinatariosTodos =
            javafx.collections.FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<Usuario> destinatariosFiltrados;

    /** True cuando el formulario tiene cambios sin guardar. Resetea al
     *  guardar borrador o enviar. */
    private boolean dirty = false;

    /** Debounce para el render Markdown live: 200 ms desde el último keystroke
     *  antes de re-renderizar el HTML. Mantiene la UI fluida en textos largos. */
    private final PauseTransition renderDebounce = new PauseTransition(Duration.millis(200));

    private MemorandumService memorandumService;
    private Usuario usuarioActual;
    /** Memo a editar (modo "editar borrador"); null en modo "nuevo". */
    private Memorandum memoEdicion;

    private final Set<UUID> destinatariosIds = new HashSet<>();

    public void setMemorandumService(MemorandumService s) { this.memorandumService = s; }
    public void setUsuarioActual(Usuario u) { this.usuarioActual = u; }
    public void setMemoEdicion(Memorandum m) { this.memoEdicion = m; }

    @FXML
    public void initialize() {
        StringConverter<Usuario> converter = new StringConverter<>() {
            @Override
            public String toString(Usuario u) {
                if (u == null) return "";
                return u.getApellidos() + ", " + u.getNombres();
            }
            @Override
            public Usuario fromString(String s) {
                // Match exacto contra la lista completa: si el editor tiene
                // "Apellido, Nombre" devolvemos el Usuario correspondiente.
                // Esto evita que el ComboBox setee value=null cuando se
                // actualiza el FilteredList tras una selección.
                if (s == null || s.isBlank()) return null;
                final String t = s.trim();
                return destinatariosTodos.stream()
                        .filter(u -> (u.getApellidos() + ", " + u.getNombres()).equalsIgnoreCase(t))
                        .findFirst()
                        .orElse(null);
            }
        };
        destinatariosComboBox.setConverter(converter);
        destinatariosSeleccionadosList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? "" : u.getApellidos() + ", " + u.getNombres());
            }
        });

        // ComboBox filtrable: el usuario tipea apellido o nombre y la lista
        // se filtra en vivo. Mantenemos una FilteredList sobre la lista
        // completa; el predicate se actualiza en cada keystroke.
        destinatariosFiltrados = new javafx.collections.transformation.FilteredList<>(
                destinatariosTodos, u -> true);
        destinatariosComboBox.setItems(destinatariosFiltrados);
        destinatariosComboBox.setEditable(true);
        destinatariosComboBox.setVisibleRowCount(12);
        destinatariosComboBox.getEditor().textProperty().addListener((obs, oldV, newV) -> {
            // Si el texto del editor coincide con la representación del item
            // seleccionado, no tocamos el filtro: el cambio vino de seleccionar
            // un ítem del dropdown, no de un keystroke del usuario.
            Usuario sel = destinatariosComboBox.getValue();
            String selText = sel != null ? converter.toString(sel) : null;
            if (newV != null && newV.equals(selText)) {
                javafx.application.Platform.runLater(() -> destinatariosFiltrados.setPredicate(u -> true));
                return;
            }
            // Diferir la actualización del predicate con runLater para no
            // interferir con la selección interna del ComboBox cuando este
            // listener se dispara durante un commit.
            javafx.application.Platform.runLater(() -> {
                String filtro = newV == null ? "" : newV.toLowerCase().trim();
                if (filtro.isEmpty()) {
                    destinatariosFiltrados.setPredicate(u -> true);
                } else {
                    destinatariosFiltrados.setPredicate(u -> {
                        String full = (u.getApellidos() + " " + u.getNombres()).toLowerCase();
                        return full.contains(filtro);
                    });
                }
                // Auto-show del dropdown si hay texto y resultados.
                if (!filtro.isEmpty() && !destinatariosFiltrados.isEmpty()
                        && !destinatariosComboBox.isShowing()
                        && destinatariosComboBox.getEditor().isFocused()) {
                    destinatariosComboBox.show();
                }
            });
        });

        // Live preview: cada cambio en el TextArea dispara el debounce que
        // re-renderiza el HTML 200 ms después del último keystroke.
        contenidoTextArea.textProperty().addListener((obs, oldV, newV) -> {
            marcarDirty();
            renderDebounce.setOnFinished(e -> renderPreview());
            renderDebounce.playFromStart();
        });
        // Cualquier cambio en asunto o destinatarios también marca dirty.
        asuntoTextField.textProperty().addListener((obs, oldV, newV) -> marcarDirty());
        destinatariosSeleccionadosList.getItems().addListener(
                (javafx.collections.ListChangeListener<Usuario>) c -> marcarDirty());
    }

    private void marcarDirty() {
        if (!dirty) {
            dirty = true;
            if (cancelarButton != null) cancelarButton.setText("Cancelar");
        }
    }

    private void marcarLimpio() {
        dirty = false;
        if (cancelarButton != null) cancelarButton.setText("Cerrar");
    }

    private void renderPreview() {
        String html = MarkdownRenderer.renderToHtmlDocument(contenidoTextArea.getText());
        vistaPreviaWebView.getEngine().loadContent(html, "text/html");
    }

    /** Llamar después de set* para terminar de inicializar la pantalla. */
    public void postInitialize() {
        cargarDestinatariosValidos();

        if (memoEdicion != null) {
            // Modo editar: precargar datos del borrador.
            asuntoTextField.setText(memoEdicion.getAsunto() != null ? memoEdicion.getAsunto() : "");
            contenidoTextArea.setText(memoEdicion.getContenido() != null ? memoEdicion.getContenido() : "");
            for (MemorandumDestinatario d : memoEdicion.getDestinatarios()) {
                destinatariosTodos.stream()
                        .filter(u -> u.getId().equals(d.getUsuarioId()))
                        .findFirst()
                        .ifPresent(this::agregarDestinatario);
            }
        }
        renderPreview(); // primer render (vacío o precargado)
        // Estado inicial limpio: si el usuario aún no tocó nada, el botón
        // dice "Cerrar". Cualquier cambio posterior lo cambia a "Cancelar".
        marcarLimpio();
        // ESC = onCancelar (con diálogo de confirmación si hay cambios).
        asuntoTextField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ESCAPE),
                        this::onCancelar);
            }
        });
    }

    // ============================================================
    // Destinatarios
    // ============================================================

    private void cargarDestinatariosValidos() {
        try {
            List<Usuario> validos = memorandumService.destinatariosValidosPara(usuarioActual);
            destinatariosTodos.setAll(validos);
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar destinatarios válidos: " + e.getMessage());
        }
    }

    @FXML
    private void onAgregarDestinatario() {
        // 1) ComboBox.getValue() — el último valor confirmado, sobrevive a
        //    actualizaciones del FilteredList mejor que getSelectedItem().
        Usuario seleccionado = destinatariosComboBox.getValue();
        // 2) Si no hay value, intentar la selección.
        if (seleccionado == null) {
            seleccionado = destinatariosComboBox.getSelectionModel().getSelectedItem();
        }
        // 3) Match exacto del texto del editor contra la lista completa.
        if (seleccionado == null) {
            String texto = destinatariosComboBox.getEditor().getText();
            if (texto != null && !texto.isBlank()) {
                final String t = texto.trim();
                seleccionado = destinatariosTodos.stream()
                        .filter(u -> (u.getApellidos() + ", " + u.getNombres()).equalsIgnoreCase(t))
                        .findFirst()
                        .orElse(null);
            }
        }
        // 4) Si el filtro dejó un único candidato visible, lo aceptamos.
        if (seleccionado == null && destinatariosFiltrados.size() == 1) {
            seleccionado = destinatariosFiltrados.get(0);
        }
        if (seleccionado == null) {
            AlertUtils.showInfo("Seleccioná un destinatario del listado o escribí su nombre completo.");
            return;
        }
        agregarDestinatario(seleccionado);
        // Limpiar selección + value + editor + filtro para que el usuario
        // pueda buscar el siguiente destinatario.
        destinatariosComboBox.setValue(null);
        destinatariosComboBox.getSelectionModel().clearSelection();
        destinatariosComboBox.getEditor().clear();
        destinatariosFiltrados.setPredicate(u -> true);
    }

    private void agregarDestinatario(Usuario u) {
        if (destinatariosIds.add(u.getId())) {
            destinatariosSeleccionadosList.getItems().add(u);
        }
    }

    @FXML
    private void onQuitarDestinatario() {
        Usuario sel = destinatariosSeleccionadosList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        destinatariosSeleccionadosList.getItems().remove(sel);
        destinatariosIds.remove(sel.getId());
    }

    @FXML
    private void onAgregarJefaturas() {
        toggleGrupoPorRol(TipoUsuario.JEFATURADESERVICIO, "jefaturas de servicio");
    }

    @FXML
    private void onAgregarDireccion() {
        toggleGrupoPorRol(TipoUsuario.DIRECCION, "usuarios con rol Dirección");
    }

    /**
     * Toggle: si el grupo no está completo entre los seleccionados, agrega
     * los que faltan; si está completo, los quita a todos. Atajo para los
     * dos grupos institucionales más usados.
     */
    private void toggleGrupoPorRol(TipoUsuario rol, String etiquetaGrupo) {
        List<Usuario> candidatos = new ArrayList<>();
        for (Usuario u : destinatariosTodos) {
            if (u.hasRole(rol)) candidatos.add(u);
        }
        if (candidatos.isEmpty()) {
            AlertUtils.showInfo("No hay " + etiquetaGrupo + " disponibles.");
            return;
        }
        boolean todosYaSeleccionados = candidatos.stream()
                .allMatch(c -> destinatariosIds.contains(c.getId()));
        if (todosYaSeleccionados) {
            // Quitar todos los del grupo.
            for (Usuario u : candidatos) {
                if (destinatariosIds.remove(u.getId())) {
                    destinatariosSeleccionadosList.getItems().remove(u);
                }
            }
        } else {
            // Agregar los que faltan.
            for (Usuario u : candidatos) {
                if (destinatariosIds.add(u.getId())) {
                    destinatariosSeleccionadosList.getItems().add(u);
                }
            }
        }
    }

    // ============================================================
    // Toolbar Markdown
    // ============================================================

    @FXML private void onBold() { envolverSeleccion("**", "**", "negrita"); }
    @FXML private void onItalic() { envolverSeleccion("*", "*", "itálica"); }

    @FXML
    private void onHeading() {
        prefijarLineaActual("## ");
    }

    @FXML
    private void onListaNoOrdenada() {
        prefijarLineasSeleccionadas("- ");
    }

    @FXML
    private void onListaOrdenada() {
        prefijarLineasSeleccionadasNumerado();
    }

    private void envolverSeleccion(String pre, String post, String defaultText) {
        IndexRange sel = contenidoTextArea.getSelection();
        String texto = contenidoTextArea.getText();
        int caret = contenidoTextArea.getCaretPosition();
        if (sel.getLength() > 0) {
            String seleccionado = texto.substring(sel.getStart(), sel.getEnd());
            String reemplazo = pre + seleccionado + post;
            contenidoTextArea.replaceText(sel, reemplazo);
            contenidoTextArea.positionCaret(sel.getStart() + reemplazo.length());
        } else {
            String inserto = pre + defaultText + post;
            contenidoTextArea.insertText(caret, inserto);
            // Selecciona el placeholder para que el usuario pueda escribir encima.
            contenidoTextArea.selectRange(caret + pre.length(), caret + pre.length() + defaultText.length());
        }
        contenidoTextArea.requestFocus();
    }

    private void prefijarLineaActual(String prefijo) {
        String texto = contenidoTextArea.getText();
        int caret = contenidoTextArea.getCaretPosition();
        int inicioLinea = texto.lastIndexOf('\n', caret - 1) + 1;
        contenidoTextArea.insertText(inicioLinea, prefijo);
        contenidoTextArea.positionCaret(caret + prefijo.length());
        contenidoTextArea.requestFocus();
    }

    private void prefijarLineasSeleccionadas(String prefijo) {
        IndexRange sel = contenidoTextArea.getSelection();
        if (sel.getLength() == 0) {
            prefijarLineaActual(prefijo);
            return;
        }
        String texto = contenidoTextArea.getText();
        int inicio = texto.lastIndexOf('\n', sel.getStart() - 1) + 1;
        int fin = sel.getEnd();
        String bloque = texto.substring(inicio, fin);
        String[] lineas = bloque.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineas.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(prefijo).append(lineas[i]);
        }
        contenidoTextArea.replaceText(inicio, fin, sb.toString());
        contenidoTextArea.requestFocus();
    }

    private void prefijarLineasSeleccionadasNumerado() {
        IndexRange sel = contenidoTextArea.getSelection();
        String texto = contenidoTextArea.getText();
        int caret = contenidoTextArea.getCaretPosition();

        int inicio, fin;
        if (sel.getLength() == 0) {
            inicio = texto.lastIndexOf('\n', caret - 1) + 1;
            fin = caret;
        } else {
            inicio = texto.lastIndexOf('\n', sel.getStart() - 1) + 1;
            fin = sel.getEnd();
        }
        String bloque = texto.substring(inicio, fin);
        String[] lineas = bloque.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineas.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(lineas[i]);
        }
        contenidoTextArea.replaceText(inicio, fin, sb.toString());
        contenidoTextArea.requestFocus();
    }

    // ============================================================
    // Acciones de guardado / envío
    // ============================================================

    @FXML
    private void onGuardarBorrador() {
        Memorandum memo = construirMemoDesdeFormulario(false);
        if (memo == null) return;
        try {
            Memorandum guardado = persistirBorrador(memo);
            this.memoEdicion = guardado;
            marcarLimpio();
            AlertUtils.showInfo("Borrador guardado.");
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onEnviar() {
        Memorandum memo = construirMemoDesdeFormulario(true);
        if (memo == null) return;
        try {
            Memorandum guardado = persistirBorrador(memo);
            this.memoEdicion = guardado;
            memorandumService.enviar(guardado.getId(), usuarioActual);
            marcarLimpio();
            AlertUtils.showInfo("Memorándum enviado.");
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    /** Decide entre guardarBorrador (modo nuevo) o actualizarBorrador
     *  (modo edición) según si el memo ya existe. */
    private Memorandum persistirBorrador(Memorandum memo) throws ServiceException {
        if (memoEdicion != null && memoEdicion.getId() != null) {
            return memorandumService.actualizarBorrador(memo, usuarioActual);
        }
        return memorandumService.guardarBorrador(memo, usuarioActual);
    }

    @FXML
    private void onCancelar() {
        if (dirty) {
            boolean confirmar = AlertUtils.showConfirm(
                    "Hay cambios sin guardar. ¿Está seguro de cerrar sin guardar?");
            if (!confirmar) return;
        }
        cerrar();
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Lee los campos del formulario y arma un Memorandum válido o muestra
     *  un alert y devuelve null si la validación de UI falla. Cuando
     *  {@code paraEnviar} es {@code true} exige al menos un destinatario;
     *  para guardar borrador se permite la lista vacía. */
    private Memorandum construirMemoDesdeFormulario(boolean paraEnviar) {
        String asunto = asuntoTextField.getText() != null ? asuntoTextField.getText().trim() : "";
        String contenido = contenidoTextArea.getText() != null ? contenidoTextArea.getText().trim() : "";

        if (asunto.isEmpty()) {
            AlertUtils.showWarn("Ingresá un asunto.");
            return null;
        }
        if (asunto.length() > ASUNTO_MAX) {
            AlertUtils.showWarn("El asunto excede el máximo de " + ASUNTO_MAX + " caracteres.");
            return null;
        }
        if (contenido.isEmpty()) {
            AlertUtils.showWarn("Ingresá el contenido del memorándum.");
            return null;
        }
        if (contenido.length() > CONTENIDO_MAX) {
            AlertUtils.showWarn("El contenido excede el máximo de " + CONTENIDO_MAX + " caracteres.");
            return null;
        }
        if (paraEnviar && destinatariosSeleccionadosList.getItems().isEmpty()) {
            AlertUtils.showWarn("Agregá al menos un destinatario para enviar el memorándum.");
            return null;
        }

        Memorandum m = new Memorandum();
        if (memoEdicion != null) {
            m.setId(memoEdicion.getId()); // preserva id del borrador en edición
        }
        m.setAsunto(asunto);
        m.setContenido(contenido);
        List<MemorandumDestinatario> dests = new ArrayList<>();
        for (Usuario u : destinatariosSeleccionadosList.getItems()) {
            dests.add(new MemorandumDestinatario(null, u.getId()));
        }
        m.setDestinatarios(dests);
        return m;
    }

    private void cerrar() {
        Stage stage = (Stage) asuntoTextField.getScene().getWindow();
        stage.close();
    }
}
