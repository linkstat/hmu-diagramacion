package ar.com.hmu.controller;

import ar.com.hmu.service.*;
import ar.com.hmu.util.AlertUtils;
import ar.com.hmu.util.AppInfo;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;

import ar.com.hmu.model.Servicio;
import ar.com.hmu.exceptions.ServiceException;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class ListaServiciosController {

    @FXML
    private TableView<Servicio> serviciosTableView;
    @FXML
    private TableColumn<Servicio, String> nombreServicioColumn;
    @FXML
    private TableColumn<Servicio, Integer> cantidadUsuariosColumn;
    @FXML
    private TableColumn<Servicio, String> apellidoJefeColumn;
    @FXML
    private Button abmServicioButton;

    // Servicios
    private UsuarioService usuarioService;
    private CargoService cargoService;
    private ServicioService servicioService;
    private DomicilioService domicilioService;
    private RoleService roleService;

    public void setServicioService(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    @FXML
    public void initialize() {
        nombreServicioColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getNombre()));
        cantidadUsuariosColumn.setCellValueFactory(cellData -> {
            int cantidadUsuarios = obtenerCantidadUsuarios(cellData.getValue());
            return new SimpleIntegerProperty(cantidadUsuarios).asObject();
        });
        apellidoJefeColumn.setCellValueFactory(cellData -> {
            String jefaturaDeServicio = obtenerJefaturaDeServicio(cellData.getValue());
            return new SimpleStringProperty(jefaturaDeServicio);
        });

        cargarServicios();
    }

    /**
     * Método para Inyectar los Servicios
     * @param usuarioService para la gestión de Usuarios
     * @param cargoService para la gestión de Cargos
     * @param servicioService para la gestión de Servicios
     */
    public void setServices(UsuarioService usuarioService, CargoService cargoService, ServicioService servicioService, DomicilioService domicilioService, RoleService roleService) {
        this.usuarioService = usuarioService;
        this.cargoService = cargoService;
        this.servicioService = servicioService;
        this.domicilioService = domicilioService;
        this.roleService = roleService;
    }


    private void cargarServicios() {
        try {
            List<Servicio> servicios = servicioService.readAll();
            serviciosTableView.getItems().setAll(servicios);
        } catch (ServiceException e) {
            // Manejo de errores
            e.printStackTrace();
        }
    }

    private int obtenerCantidadUsuarios(Servicio servicio) {
        try {
            return servicioService.countUsuariosByServicio(servicio.getId());
        } catch (ServiceException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private String obtenerJefaturaDeServicio(Servicio servicio) {
        try {
            return servicioService.findJefeByServicio(servicio.getId());
        } catch (ServiceException e) {
            e.printStackTrace();
            return "";
        }
    }

    @FXML
    private void onAbmServicio(ActionEvent event) {
        // Usa los servicios ya inicializados
        ServicioService servicioService = this.servicioService;

        try {
            // Configurar la fábrica de controladores
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/abmServicio.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == AbmServicioController.class) {
                    AbmServicioController controller = new AbmServicioController();
                    controller.setServices(servicioService);
                    return controller;
                } else {
                    // Manejo predeterminado
                    try {
                        return controllerClass.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            // Carga del FXML después de configurada la Fábrica
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Gestión de Servicios" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(((Node) event.getSource()).getScene().getWindow());
            //stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al cargar la ventana de gestión de servicios: " + e.getMessage());
        }
    }

}
