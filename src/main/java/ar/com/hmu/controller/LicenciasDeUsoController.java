package ar.com.hmu.controller;

import java.awt.Desktop;
import java.net.URI;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import ar.com.hmu.util.AppInfo;

public class LicenciasDeUsoController {
    @FXML
    private ImageView logoImageView;

    @FXML
    private Text titleText;

    @FXML
    private Text descriptionText;

    @FXML
    private WebView licenceInfoWebView;

    @FXML
    public void initialize() {
        // Establecer los valores de las etiquetas utilizando las constantes

        titleText.setText("HMU Diagramación");
        titleText.setWrappingWidth(380);
        titleText.setFont(javafx.scene.text.Font.font("Barlow Condensed", 46));

        descriptionText.setText("Este software hace uso de los siguientes elementos externos");
        descriptionText.setFont(Font.font("Barlow Condensed", 18));

        // Opcionalmente, también se podría setear la imagen del logo (pero usamos la que se estableció por defecto en el fxml)
        logoImageView.setImage(new Image(getClass().getResourceAsStream(AppInfo.LOGO_IMAGE)));

        // Establecer el productInfoWebView del Acerca de (y el comportamiento de links)
        WebEngine webEngine = licenceInfoWebView.getEngine();  // Obtiene el WebEngine
        webEngine.loadContent(AppInfo.LICENSES_OF_USE);  // Carga el contenido HTML
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
