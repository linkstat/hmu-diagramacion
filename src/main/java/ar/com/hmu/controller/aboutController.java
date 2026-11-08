package ar.com.hmu.controller;

import java.awt.Desktop;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import ar.com.hmu.util.AppInfo;

public class aboutController {
    @FXML
    private ImageView logoImageView;

    @FXML
    private Label versionLabel;

    @FXML
    private Label buildLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private WebView productInfoWebView;

    @FXML
    private Text titleText;

    @FXML
    private Text subtitleText;

    @FXML
    private Text descriptionText;

    @FXML
    public void initialize() {
        // Los siguientes valores los establecimos por defecto en el fxml, pero luego vinculamos los elementos gráficos para que puedan ser seteados desde el controlador

        titleText.setText(AppInfo.nombreAcercaDe());
        if (AppInfo.perfilTfg()) {
            titleText.setWrappingWidth(330);
            titleText.setFont(Font.font("Barlow Condensed", 46));
        } else {
            titleText.setFont(Font.font("Barlow Condensed", 72));
        }

        subtitleText.setText("Diagramación de Servicios y Gestión Trazable del Ausentismo"
                + " en el Hospital de Urgencias de Córdoba");
        subtitleText.setWrappingWidth(420);
        subtitleText.setFont(Font.font("Barlow Condensed", 20));

        descriptionText.setText("");

        // Opcionalmente, también se podría setear la imagen del logo (pero usamos la que se estableció por defecto en el fxml)
        InputStream imageStream = getClass().getResourceAsStream(AppInfo.LOGO_IMAGE);
        if (imageStream != null) {
            logoImageView.setImage(new Image(imageStream));
        } else {
            System.err.println("Error: No se pudo encontrar la imagen: " + AppInfo.LOGO_IMAGE);
            // Opcional: usar una imagen alternativa o una imagen predeterminada
        }

        // Establecer los valores de las etiquetas utilizando las constantes
        versionLabel.setText("Versión: " + AppInfo.VERSION);
        buildLabel.setVisible(false);
        dateLabel.setText(AppInfo.PRODUCTION_DATE);

        // Establecer el productInfoWebView del Acerca de (y el comportamiento de links)
        WebEngine webEngine = productInfoWebView.getEngine();  // Obtiene el WebEngine
        webEngine.loadContent(AppInfo.PRODUCT_INFO);  // Carga el contenido HTML
        webEngine.setJavaScriptEnabled(true);  // Utilizo JavaScript para deshabilitar el menú contextual dentro del WebView
        // Agregar un listener para cuando el contenido haya cargado completamente
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                // El contenido se ha cargado completamente
                Document doc = webEngine.getDocument();
                NodeList nodeList = doc.getElementsByTagName("a");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    EventTarget eventTarget = (EventTarget) node;
                    eventTarget.addEventListener("click", new EventListener() {
                        @Override
                        public void handleEvent(Event evt) {
                            evt.preventDefault(); // Prevenir la navegación en el WebView
                            String href = ((Element) evt.getCurrentTarget()).getAttribute("href");
                            try {
                                // Abrir el enlace en el navegador predeterminado
                                Desktop.getDesktop().browse(new URI(href));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, false);
                }
            }
        });

    }

}
