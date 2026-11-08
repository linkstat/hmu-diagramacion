package ar.com.hmu.controller;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.EstadoNovedadAutorizacion;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.NovedadAdjunto;
import ar.com.hmu.model.NovedadAutorizacion;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.TipoNovedad;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.novedades.NovedadService;
import ar.com.hmu.service.novedades.PerfilNovedades;
import ar.com.hmu.service.novedades.ResultadoValidacion;
import ar.com.hmu.service.novedades.ValidacionNovedadException;
import ar.com.hmu.service.novedades.ViolacionNovedad;
import ar.com.hmu.util.AlertUtils;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Solicitar novedad (P03b; CU20 con el CU21 incluido): el agente elige el
 * tipo, el sistema muestra los campos que corresponden, y la solicitud se
 * valida contra el diagrama vigente mientras se completa. Con errores
 * bloqueantes no se puede enviar (CU20 4a). También corrige y reenvía una
 * solicitud observada (CU25).
 */
public class SolicitarNovedadController {

    private static final DateTimeFormatter DDMM_HHMM = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final long MAX_ADJUNTO_BYTES = 5L * 1024 * 1024;

    @FXML private Label tituloLabel;
    @FXML private Label estadoChip;
    @FXML private Label observacionLabel;
    @FXML private ComboBox<TipoNovedad> tipoCombo;
    @FXML private ComboBox<Usuario> agenteCombo;
    @FXML private Label saldoLabel;
    @FXML private Label desdeLabel;
    @FXML private DatePicker desdePicker;
    @FXML private Label jornadaDesdeLabel;
    @FXML private Label contraparteLabel;
    @FXML private ComboBox<Usuario> contraparteCombo;
    /** Devolución de pase de salida: el pase que se devuelve (Novedad.novedadOrigenID). */
    @FXML private ComboBox<ar.com.hmu.service.novedades.NovedadService.PaseADevolver> paseOrigenCombo;
    @FXML private Label horaALabel;
    @FXML private Label hastaLabel;
    @FXML private DatePicker hastaPicker;
    @FXML private Label jornadaHastaLabel;
    @FXML private Label horasLabel;
    @FXML private HBox horasBox;
    @FXML private TextField horaDesdeField;
    @FXML private TextField horaHastaField;
    @FXML private Label cantHorasLabel;
    @FXML private TextArea motivoArea;
    @FXML private Label justificacionLabel;
    @FXML private TextField justificacionField;
    @FXML private Label respaldoLabel;
    @FXML private FlowPane adjuntosPane;
    @FXML private ListView<ViolacionNovedad> validacionList;
    @FXML private Button guardarButton;
    @FXML private Button enviarButton;

    private NovedadService novedadService;
    private Usuario usuarioActual;
    private PerfilNovedades perfil = PerfilNovedades.AGENTE;

    /** Novedad que se corrige (borrador u observada); null = nueva. */
    private Novedad existente;
    private final List<NovedadAdjunto> adjuntosNuevos = new ArrayList<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private boolean cargando;
    private Stage stage;

    public Stage getStage() {
        return stage;
    }

    /** Se llama antes de mostrar la ventana. */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setNovedadService(NovedadService novedadService) {
        this.novedadService = novedadService;
    }

    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    public void setPerfil(PerfilNovedades perfil) {
        this.perfil = perfil;
    }

    /** @param aCorregir novedad en borrador u observada, o null para una nueva. */
    public void postInitialize(Novedad aCorregir) {
        ar.com.hmu.util.FechaUtils.formatear(desdePicker, hastaPicker);
        this.existente = aCorregir;
        cargando = true;
        configurarCombos();
        configurarValidacion();
        if (aCorregir != null) {
            cargar(aCorregir);
        } else {
            agenteCombo.getItems().stream().filter(u -> u.getId().equals(usuarioActual.getId())).findFirst()
                    .ifPresentOrElse(agenteCombo::setValue, () -> agenteCombo.getSelectionModel().selectFirst());
            tipoCombo.getSelectionModel().selectFirst();
        }
        cargando = false;
        ajustarCamposAlTipo();
        validarAhora();
        tipoCombo.sceneProperty().addListener((obs, vieja, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCancelar);
            }
        });
        if (tipoCombo.getScene() != null) {
            tipoCombo.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::onCancelar);
        }
    }

    // ============================================================
    // Configuración
    // ============================================================

    private void configurarCombos() {
        try {
            tipoCombo.getItems().setAll(novedadService.tiposSolicitables());
            agenteCombo.getItems().setAll(novedadService.agentesSeleccionables(usuarioActual, perfil));
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
        StringConverter<Usuario> conv = new StringConverter<>() {
            @Override public String toString(Usuario u) { return u == null ? "" : u.getApellidosNombres(); }
            @Override public Usuario fromString(String s) { return null; }
        };
        agenteCombo.setConverter(conv);
        contraparteCombo.setConverter(conv);
        agenteCombo.setDisable(agenteCombo.getItems().size() <= 1);

        tipoCombo.valueProperty().addListener((o, a, b) -> {
            if (!cargando) {
                ajustarCamposAlTipo();
            }
            programarValidacion();
        });
        agenteCombo.valueProperty().addListener((o, a, b) -> {
            cargarContrapartes();
            cargarPasesADevolver();
            actualizarSaldo();
            programarValidacion();
        });
        desdePicker.valueProperty().addListener((o, a, b) -> {
            boolean cg = tipoCombo.getValue() != null && tipoCombo.getValue().es(TipoNovedad.CAMBIO_GUARDIA);
            if (!cargando && b != null && (hastaPicker.getValue() == null || !hastaVisible()
                    || (!cg && hastaPicker.getValue().isBefore(b)))) {
                hastaPicker.setValue(b);
            }
            programarValidacion();
        });
        contraparteCombo.valueProperty().addListener((o, a, b) -> programarValidacion());
        paseOrigenCombo.valueProperty().addListener((o, a, b) -> {
            if (!cargando && b != null && desdePicker.getValue() == null) {
                desdePicker.setValue(b.pase().getFechaDesde());
            }
            programarValidacion();
        });
        hastaPicker.valueProperty().addListener((o, a, b) -> programarValidacion());
        horaDesdeField.textProperty().addListener((o, a, b) -> programarValidacion());
        horaHastaField.textProperty().addListener((o, a, b) -> programarValidacion());
        motivoArea.textProperty().addListener((o, a, b) -> programarValidacion());
        // Novedad.justificacion es VARCHAR(255).
        justificacionField.setTextFormatter(new TextFormatter<String>(c ->
                c.getControlNewText().length() <= Novedad.JUSTIFICACION_MAX ? c : null));
        justificacionField.textProperty().addListener((o, a, b) -> programarValidacion());
    }

    private void configurarValidacion() {
        debounce.setOnFinished(e -> validarAhora());
        validacionList.setCellFactory(lv -> new ListCell<>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(lv.widthProperty().subtract(20));
                setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }

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
    }

    private void cargarContrapartes() {
        Usuario agente = agenteCombo.getValue();
        UUID previa = contraparteCombo.getValue() != null ? contraparteCombo.getValue().getId() : null;
        try {
            contraparteCombo.getItems().setAll(novedadService.contrapartesPosibles(agente));
        } catch (ServiceException e) {
            contraparteCombo.getItems().clear();
        }
        if (previa != null) {
            contraparteCombo.getItems().stream().filter(u -> u.getId().equals(previa)).findFirst()
                    .ifPresent(contraparteCombo::setValue);
        }
    }

    private void cargarPasesADevolver() {
        Usuario agente = agenteCombo.getValue();
        UUID previo = paseOrigenCombo.getValue() != null ? paseOrigenCombo.getValue().pase().getId() : null;
        try {
            paseOrigenCombo.getItems().setAll(agente != null ? novedadService.pasesADevolver(agente.getId()) : List.of());
        } catch (ServiceException e) {
            paseOrigenCombo.getItems().clear();
        }
        UUID buscado = previo != null ? previo : existente != null ? existente.getNovedadOrigenId() : null;
        if (buscado != null) {
            paseOrigenCombo.getItems().stream().filter(p -> p.pase().getId().equals(buscado)).findFirst()
                    .ifPresent(paseOrigenCombo::setValue);
        }
    }

    private void actualizarSaldo() {
        Usuario agente = agenteCombo.getValue();
        if (agente == null) {
            saldoLabel.setText("");
            return;
        }
        try {
            saldoLabel.setText("Banco de horas (vigente): "
                    + novedadService.saldoHoras(agente.getId()).stripTrailingZeros().toPlainString() + " h");
        } catch (ServiceException e) {
            saldoLabel.setText("");
        }
    }

    /** CU20 paso 2: el sistema muestra los campos que corresponden al tipo elegido. */
    private void ajustarCamposAlTipo() {
        TipoNovedad t = tipoCombo.getValue();
        if (t == null) return;
        boolean cg = t.es(TipoNovedad.CAMBIO_GUARDIA);
        boolean devolucion = t.es(TipoNovedad.DEVOLUCION_PASE);
        boolean sinRetorno = t.es(TipoNovedad.PASE_SIN_RETORNO);
        boolean unDia = cg || t.es(TipoNovedad.CAMBIO_HORARIO) || t.es(TipoNovedad.PASE_SALIDA_OFICIAL)
                || t.es(TipoNovedad.PASE_SALIDA_PARTICULAR) || t.es(TipoNovedad.HORAS_EXTRA)
                || t.es(TipoNovedad.OMISION_MARCACION) || sinRetorno || devolucion;
        // Las horas extras para FC no llevan horario: se computan de las marcaciones de la jornada.
        boolean horas = t.es(TipoNovedad.CAMBIO_HORARIO) || t.es(TipoNovedad.PASE_SALIDA_OFICIAL)
                || t.es(TipoNovedad.PASE_SALIDA_PARTICULAR) || sinRetorno || devolucion;

        desdeLabel.setText(cg ? "Guardia que cede:" : unDia ? "Fecha:" : "Desde:");
        hastaLabel.setText(cg ? "Guardia que recibe:" : "Hasta:");
        visible(contraparteLabel, t.requiereContraparte() || devolucion);
        visible(contraparteCombo, t.requiereContraparte());
        visible(paseOrigenCombo, devolucion);
        contraparteLabel.setText(devolucion ? "Pase a devolver:" : "Intercambia con:");
        if (devolucion) {
            cargarPasesADevolver();
        }
        visible(horaALabel, !sinRetorno);
        visible(horaHastaField, !sinRetorno);
        visible(hastaLabel, cg || !unDia);
        visible(hastaPicker.getParent(), cg || !unDia);
        visible(horasLabel, horas);
        visible(horasBox, horas);
        horasLabel.setText(t.es(TipoNovedad.CAMBIO_HORARIO) ? "Nuevo horario:"
                : sinRetorno ? "Hora de salida:" : devolucion ? "Horario de devolución:" : "Horario:");
        // Horas extras: justificación precargada con la fórmula usual, editable (no reemplaza al motivo).
        visible(justificacionLabel, t.llevaJustificacion());
        visible(justificacionField, t.llevaJustificacion());
        if (t.llevaJustificacion() && justificacionField.getText().isBlank()) {
            justificacionField.setText(Novedad.JUSTIFICACION_USUAL);
        }
        respaldoLabel.setText(t.requiereDocumentacion() ? "Respaldo (obligatorio):" : "Respaldo:");
        saldoLabel.setVisible(t.afectaSaldoHoras() != 0);
        if (!unDia || cg) {
            return;
        }
        hastaPicker.setValue(desdePicker.getValue());
    }

    private boolean hastaVisible() {
        return hastaPicker.getParent().isVisible();
    }

    private static void visible(Node n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }

    private void cargar(Novedad n) {
        tituloLabel.setText("Corregir solicitud");
        estadoChip.setText(n.getEstado().getEtiqueta());
        estadoChip.getStyleClass().setAll("label", "estado-chip", NovedadesController.claseEstado(n.getEstado()));
        estadoChip.setVisible(true);
        tipoCombo.getItems().stream().filter(t -> t.id().equals(n.getTipo().id())).findFirst()
                .ifPresent(tipoCombo::setValue);
        agenteCombo.getItems().stream().filter(u -> u.getId().equals(n.getAgenteId())).findFirst()
                .ifPresent(agenteCombo::setValue);
        cargarContrapartes();
        if (n.getContraparteAgenteId() != null) {
            contraparteCombo.getItems().stream().filter(u -> u.getId().equals(n.getContraparteAgenteId()))
                    .findFirst().ifPresent(contraparteCombo::setValue);
        }
        desdePicker.setValue(n.getFechaDesde());
        hastaPicker.setValue(n.getFechaHasta());
        if (n.getTipo().es(TipoNovedad.CAMBIO_GUARDIA)
                && novedadService.jornadaVigente(n.getAgenteId(), n.getFechaHasta()).filter(JornadaLaboral::tieneHorario).isPresent()
                && novedadService.jornadaVigente(n.getAgenteId(), n.getFechaDesde()).filter(JornadaLaboral::tieneHorario).isEmpty()) {
            desdePicker.setValue(n.getFechaHasta());
            hastaPicker.setValue(n.getFechaDesde());
        }
        horaDesdeField.setText(n.getHoraDesde() != null ? n.getHoraDesde().format(HHMM) : "");
        horaHastaField.setText(n.getHoraHasta() != null ? n.getHoraHasta().format(HHMM) : "");
        motivoArea.setText(n.getMotivo());
        justificacionField.setText(n.getJustificacion() != null ? n.getJustificacion() : "");
        for (NovedadAdjunto a : n.getAdjuntos()) {
            adjuntosPane.getChildren().add(new Label(a.descripcion()));
        }
        tipoCombo.setDisable(true);
        agenteCombo.setDisable(true);
        // Última observación: lo que hay que corregir (CU25).
        n.getAutorizaciones().stream()
                .filter(a -> a.getEstado() == EstadoNovedadAutorizacion.OBSERVADO)
                .max(Comparator.comparingInt(NovedadAutorizacion::getOrden))
                .ifPresent(a -> {
                    observacionLabel.setText("Observada por " + a.getInstancia().getEtiqueta()
                            + (a.getResueltoPorNombre() != null ? " (" + a.getResueltoPorNombre() + ")" : "")
                            + ": " + a.getComentarios());
                    visible(observacionLabel, true);
                });
        enviarButton.setText(n.getEstado() == EstadoNovedad.OBSERVADA ? "Reenviar solicitud" : "Enviar solicitud");
    }

    // ============================================================
    // Validación inmediata (CU21)
    // ============================================================

    private void programarValidacion() {
        if (!cargando) {
            debounce.playFromStart();
        }
    }

    private void validarAhora() {
        mostrarJornadas();
        Novedad n = armar();
        ResultadoValidacion r = novedadService.validar(n, !adjuntosNuevos.isEmpty()
                || (existente != null && !existente.getAdjuntos().isEmpty()));
        actualizarCantHoras(n);
        List<ViolacionNovedad> items = new ArrayList<>(r.violaciones());
        items.sort(Comparator.comparing(v -> !v.esBloqueante()));
        if (items.isEmpty()) {
            items.add(new ViolacionNovedad(null, null, "Sin observaciones: la solicitud puede enviarse."));
        }
        validacionList.getItems().setAll(items);
        enviarButton.setDisable(r.hayBloqueantes());
    }

    private void actualizarCantHoras(Novedad n) {
        if (n.getCantHoras() != null && n.getCantHoras().signum() > 0) {
            cantHorasLabel.setText(n.getCantHoras().stripTrailingZeros().toPlainString() + " h");
        } else {
            cantHorasLabel.setText("");
        }
    }

    /** Muestra la jornada diagramada de cada fecha para orientar al agente. */
    private void mostrarJornadas() {
        Usuario agente = agenteCombo.getValue();
        TipoNovedad t = tipoCombo.getValue();
        jornadaDesdeLabel.setText(agente != null && desdePicker.getValue() != null
                ? describir(novedadService.jornadaVigente(agente.getId(), desdePicker.getValue())) : "");
        Usuario quienEntrega = t != null && t.es(TipoNovedad.CAMBIO_GUARDIA) ? contraparteCombo.getValue() : agente;
        jornadaHastaLabel.setText(quienEntrega != null && hastaPicker.getValue() != null && hastaVisible()
                ? describir(novedadService.jornadaVigente(quienEntrega.getId(), hastaPicker.getValue())) : "");
    }

    private static String describir(Optional<JornadaLaboral> j) {
        if (j.isEmpty()) {
            return "Sin diagrama aprobado";
        }
        JornadaLaboral x = j.get();
        if (!x.tieneHorario()) {
            return x.getTipo() == TipoJornada.FRANCO ? "Franco" : etiquetaTipo(x.getTipo());
        }
        return etiquetaTipo(x.getTipo()) + " " + x.getFechaIngreso().format(DDMM_HHMM) + " → "
                + x.getFechaEgreso().format(DDMM_HHMM);
    }

    private static String etiquetaTipo(TipoJornada t) {
        return switch (t) {
            case TURNO_NORMAL -> "Turno";
            case GUARDIA_ACTIVA -> "Guardia";
            case GUARDIA_PASIVA -> "Guardia pasiva";
            case FRANCO -> "Franco";
            case FRANCO_COMPENSATORIO -> "Franco compensatorio";
            case LICENCIA -> "Licencia";
        };
    }

    /** Arma la novedad con los datos del formulario. */
    private Novedad armar() {
        Novedad n = new Novedad();
        if (existente != null) {
            n.setId(existente.getId());
            n.setVersion(existente.getVersion());
            n.setEstado(existente.getEstado());
            n.setSolicitadoPorId(existente.getSolicitadoPorId());
            n.setAdjuntos(existente.getAdjuntos());
            n.setAutorizaciones(existente.getAutorizaciones());
        }
        TipoNovedad t = tipoCombo.getValue();
        n.setTipo(t);
        n.setAgenteId(agenteCombo.getValue() != null ? agenteCombo.getValue().getId() : null);
        if (agenteCombo.getValue() != null) {
            n.setAgenteNombre(agenteCombo.getValue().getApellidosNombres());
        }
        n.setContraparteAgenteId(t != null && t.requiereContraparte() && contraparteCombo.getValue() != null
                ? contraparteCombo.getValue().getId() : null);
        n.setNovedadOrigenId(t != null && t.es(TipoNovedad.DEVOLUCION_PASE) && paseOrigenCombo.getValue() != null
                ? paseOrigenCombo.getValue().pase().getId() : null);
        n.setFechaDesde(desdePicker.getValue());
        n.setFechaHasta(hastaVisible() ? hastaPicker.getValue() : desdePicker.getValue());
        // Cambio de guardia: la recibida puede ser anterior a la cedida; se guarda el rango ordenado.
        if (t != null && t.es(TipoNovedad.CAMBIO_GUARDIA) && n.getFechaDesde() != null && n.getFechaHasta() != null
                && n.getFechaHasta().isBefore(n.getFechaDesde())) {
            LocalDate x = n.getFechaDesde();
            n.setFechaDesde(n.getFechaHasta());
            n.setFechaHasta(x);
        }
        if (horasBox.isVisible()) {
            n.setHoraDesde(hora(horaDesdeField.getText()));
            n.setHoraHasta(horaHastaField.isVisible() ? hora(horaHastaField.getText()) : null);
        }
        n.setMotivo(motivoArea.getText() != null ? motivoArea.getText().trim() : null);
        n.setJustificacion(t != null && t.llevaJustificacion() ? justificacionField.getText() : null);
        n.normalizarJustificacion();
        return n;
    }

    private static LocalTime hora(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String s = texto.trim();
        if (s.matches("\\d{1,2}")) s = s + ":00";
        if (s.matches("\\d:\\d{2}")) s = "0" + s;
        try {
            return LocalTime.parse(s, HHMM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ============================================================
    // Acciones
    // ============================================================

    @FXML
    private void onAdjuntar() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Adjuntar respaldo");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documentos e imágenes", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        if (f.length() > MAX_ADJUNTO_BYTES) {
            AlertUtils.showWarn("El archivo supera el máximo de 5 MB.");
            return;
        }
        try {
            String mime = Optional.ofNullable(Files.probeContentType(f.toPath())).orElse("application/octet-stream");
            agregarAdjunto(new NovedadAdjunto(f.getName(), mime, Files.readAllBytes(f.toPath())));
        } catch (IOException e) {
            AlertUtils.showErr("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    /** Agrega un respaldo pendiente de guardar (también lo usa el modo de capturas). */
    public void agregarAdjunto(NovedadAdjunto a) {
        adjuntosNuevos.add(a);
        Label etiqueta = new Label(a.descripcion());
        etiqueta.setUserData(a);
        adjuntosPane.getChildren().add(etiqueta);
        programarValidacion();
    }

    /** Descarta los respaldos todavía no guardados (modo de capturas). */
    public void quitarAdjuntosNuevos() {
        adjuntosPane.getChildren().removeIf(n -> adjuntosNuevos.contains(n.getUserData()));
        adjuntosNuevos.clear();
        programarValidacion();
    }

    @FXML
    private void onGuardar() {
        try {
            Novedad n = armar();
            novedadService.guardarBorrador(n, adjuntosNuevos, usuarioActual);
            cerrar();
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onEnviar() {
        try {
            Novedad n = armar();
            ResultadoValidacion r = novedadService.enviar(n, adjuntosNuevos, usuarioActual);
            if (r.requiereSecretariaSalud()) {
                AlertUtils.showInfo("La solicitud se registró. Por superar el tope mensual de faltas justificadas, "
                        + "requiere además la autorización de la Secretaría de Salud.");
            }
            cerrar();
        } catch (ValidacionNovedadException e) {
            validacionList.getItems().setAll(e.getResultado().violaciones());
            enviarButton.setDisable(true);
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }
    }

    @FXML
    private void onCancelar() {
        cerrar();
    }

    private void cerrar() {
        stage.close();
    }

    // ============================================================
    // Modo de capturas
    // ============================================================

    /** Completa el formulario (modo de capturas) y valida de inmediato. */
    public void completar(String codigoTipo, UUID agenteId, LocalDate desde, UUID contraparteId, LocalDate hasta,
                          String horaDesde, String horaHasta, String motivo) {
        cargando = true;
        tipoCombo.getItems().stream().filter(t -> t.es(codigoTipo)).findFirst().ifPresent(tipoCombo::setValue);
        if (agenteId != null) {
            agenteCombo.getItems().stream().filter(u -> u.getId().equals(agenteId)).findFirst()
                    .ifPresent(agenteCombo::setValue);
        }
        cargarContrapartes();
        ajustarCamposAlTipo();
        desdePicker.setValue(desde);
        if (contraparteId != null) {
            contraparteCombo.getItems().stream().filter(u -> u.getId().equals(contraparteId)).findFirst()
                    .ifPresent(contraparteCombo::setValue);
        }
        hastaPicker.setValue(hasta != null ? hasta : desde);
        horaDesdeField.setText(horaDesde != null ? horaDesde : "");
        horaHastaField.setText(horaHasta != null ? horaHasta : "");
        motivoArea.setText(motivo);
        cargando = false;
        validarAhora();
    }
}
