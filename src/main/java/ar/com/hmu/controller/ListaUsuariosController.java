package ar.com.hmu.controller;

import ar.com.hmu.model.Cargo;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.service.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import ar.com.hmu.model.Usuario;
import ar.com.hmu.exceptions.ServiceException;

public class ListaUsuariosController {

    @FXML
    private TableView<Usuario> usuariosTableView;
    @FXML
    private TableColumn<Usuario, Long> cuilColumn;
    @FXML
    private TableColumn<Usuario, String> apellidosNombresColumn;
    @FXML
    private TableColumn<Usuario, Integer> cargoNumeroColumn;
    @FXML
    private TableColumn<Usuario, String> servicioNombreColumn;

    // Servicios
    private UsuarioService usuarioService;
    private CargoService cargoService;
    private ServicioService servicioService;
    private DomicilioService domicilioService;
    private RoleService roleService;


    @FXML
    public void initialize() {
        cuilColumn.setCellValueFactory(cellData -> new SimpleLongProperty(cellData.getValue().getCuil()).asObject());
        apellidosNombresColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getApellidosNombres()));
        cargoNumeroColumn.setCellValueFactory(cellData -> {
            Cargo cargo = cellData.getValue().getCargo();
            int numeroCargo = cargo != null ? cargo.getNumero() : 0;
            return new SimpleIntegerProperty(numeroCargo).asObject();
        });
        servicioNombreColumn.setCellValueFactory(cellData -> {
            Servicio servicio = cellData.getValue().getServicio();
            String nombreServicio = servicio != null ? servicio.getNombre() : "";
            return new SimpleStringProperty(nombreServicio);
        });

        cargarUsuarios();
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

    private void cargarUsuarios() {
        try {
            usuariosTableView.getItems().setAll(usuarioService.readAll());
        } catch (ServiceException e) {
            // Manejo de errores
            e.printStackTrace();
        }
    }
}
