package ar.com.hmu.controller;

import ar.com.hmu.model.Notificacion;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.notification.NotificacionService;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.AppInfo;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/**
 * Avisos en la aplicación (P03d; CU27): los cambios de estado de las
 * novedades en las que el usuario participa o que debe resolver. Los no
 * leídos van en negrita; al cerrar se marcan como leídos.
 */
public final class AvisosDialog {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private AvisosDialog() {
    }

    /** Construye la ventana (sin mostrarla, para el modo de capturas). */
    public static Stage crear(NotificacionService servicio, Usuario usuario, Window owner) {
        TableView<Notificacion> tabla = new TableView<>();
        TableColumn<Notificacion, String> fecha = new TableColumn<>("FECHA");
        fecha.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFechaCreacion().format(FECHA_HORA)));
        fecha.setPrefWidth(130);
        TableColumn<Notificacion, String> asunto = new TableColumn<>("AVISO");
        asunto.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAsunto()));
        asunto.setPrefWidth(520);
        TableColumn<Notificacion, String> correo = new TableColumn<>("CORREO");
        correo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().isEnviadaEmail() ? "Enviado" : "Pendiente"));
        correo.setPrefWidth(90);
        tabla.getColumns().addAll(fecha, asunto, correo);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No tiene avisos."));
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Notificacion n, boolean empty) {
                super.updateItem(n, empty);
                getStyleClass().remove("no-leida");
                if (!empty && n != null && !n.estaLeida()) {
                    getStyleClass().add("no-leida");
                }
            }
        });

        TextArea cuerpo = new TextArea();
        cuerpo.setEditable(false);
        cuerpo.setWrapText(true);
        cuerpo.setPrefRowCount(5);
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, b) ->
                cuerpo.setText(b != null && b.getCuerpo() != null ? b.getCuerpo() : ""));

        try {
            tabla.getItems().setAll(servicio.recientes(usuario));
        } catch (SQLException e) {
            AlertUtils.showErr("No se pudieron leer los avisos: " + e.getMessage());
        }
        long noLeidos = tabla.getItems().stream().filter(n -> !n.estaLeida()).count();

        Label titulo = new Label("Avisos");
        titulo.getStyleClass().add("titulo-pantalla");
        Label sub = new Label(noLeidos == 0 ? "Sin avisos nuevos" : noLeidos + (noLeidos == 1 ? " aviso nuevo" : " avisos nuevos"));
        sub.getStyleClass().add("subtitulo-perfil");
        HBox top = new HBox(10, titulo, sub);
        top.setPadding(new Insets(10, 10, 8, 10));
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox centro = new VBox(8, tabla, cuerpo);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        centro.setPadding(new Insets(0, 10, 0, 10));

        Stage stage = new Stage();
        Button cerrar = new Button("Cerrar");
        cerrar.setCancelButton(true);
        cerrar.setOnAction(e -> stage.close());
        Label ayuda = new Label("Los avisos también se envían al correo institucional.");
        ayuda.getStyleClass().add("ayuda");
        HBox pie = new HBox(10, ayuda, new HBox(), cerrar);
        HBox.setHgrow(pie.getChildren().get(1), Priority.ALWAYS);
        pie.setPadding(new Insets(8, 10, 10, 10));
        pie.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        BorderPane root = new BorderPane(centro, top, null, pie, null);
        root.getStylesheets().add(AvisosDialog.class.getResource("/css/novedades.css").toExternalForm());
        Scene scene = new Scene(root, 860, 520);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), stage::close);
        stage.setScene(scene);
        stage.setTitle("Avisos :: " + AppInfo.PRG_LONG_TITLE);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.getIcons().add(new Image(AvisosDialog.class.getResourceAsStream(AppInfo.ICON_IMAGE)));
        stage.setOnHidden(e -> {
            try {
                servicio.marcarLeidas(usuario);
            } catch (SQLException ex) {
                // Quedan como no leídos; no afecta la operación.
            }
        });
        tabla.getSelectionModel().selectFirst();
        return stage;
    }
}
