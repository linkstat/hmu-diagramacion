package ar.com.hmu.controller;

import ar.com.hmu.model.Agrupacion;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import ar.com.hmu.model.Cargo;
import ar.com.hmu.service.CargoService;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.util.AlertUtils;

import java.util.UUID;

public class AbmCargoController {

    @FXML
    private TableView<Cargo> cargosTableView;
    @FXML
    private TableColumn<Cargo, String> numeroColumn;
    @FXML
    private TableColumn<Cargo, String> descripcionColumn;
    @FXML
    private TableColumn<Cargo, String> agrupacionColumn;
    @FXML
    private TextField numeroTextField;
    @FXML
    private TextField descripcionTextField;
    @FXML
    private ComboBox<Agrupacion> agrupacionComboBox;
    private ObservableList<Agrupacion> agrupacionList = FXCollections.observableArrayList(Agrupacion.values());

    private CargoService cargoService;

    public void setServices(CargoService cargoService) {
        this.cargoService = cargoService;
    }

    @FXML
    public void initialize() {
        // Configurar la columna de número de cargo
        numeroColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getNumero().toString()));
        // Configurar la columna de descripción
        descripcionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDescripcion()));
        // Configurar la columna de agrupación
        agrupacionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAgrupacion().toString()));
        // Manejar la selección en la tabla
        cargosTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> mostrarDetalleCargo(newValue));
        // Inicializar ComboBox
        agrupacionComboBox.setItems(agrupacionList);

        cargarCargos();
    }

    private void cargarCargos() {
        try {
            cargosTableView.getItems().setAll(cargoService.readAll());
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los cargos: " + e.getMessage());
        }
    }

    private void mostrarDetalleCargo(Cargo cargo) {
        if (cargo != null) {
            numeroTextField.setText(cargo.getNumero().toString());
            descripcionTextField.setText(cargo.getDescripcion());
            agrupacionComboBox.getSelectionModel().select(cargo.getAgrupacion());
        } else {
            numeroTextField.clear();
            descripcionTextField.clear();
            agrupacionComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void onAgregarCargo() throws ServiceException {
        String numero = numeroTextField.getText().trim();
        String descripcion = descripcionTextField.getText().trim();
        Agrupacion agrupacion = (Agrupacion) agrupacionComboBox.getSelectionModel().getSelectedItem();
        if (numero.isEmpty() || descripcion.isEmpty() || agrupacion == null) {
            AlertUtils.showWarn("Debe introducir un numero de cargo, una descripción y seleccionar una agrupación.");
            return;
        }

        String descripcionByNumero = cargoService.findDescripcionByNumero(Integer.parseInt(numero));
        if (descripcionByNumero == null) {
            Cargo cargo = new Cargo();
            cargo.setId(UUID.randomUUID());
            cargo.setNumero(Integer.parseInt(numero));
            cargo.setDescripcion(descripcion);
            cargo.setAgrupacion(agrupacion);

            try {
                cargoService.create(cargo);
                cargarCargos();
                descripcionTextField.clear();
                agrupacionComboBox.getSelectionModel().clearSelection();
            } catch (ServiceException e) {
                AlertUtils.showErr("Error al agregar el cargo: " + e.getMessage());
            }
        } else {
            AlertUtils.showWarn("El número de cargo introducido ya existe: " + descripcionByNumero);
        }
        
    }

    @FXML
    private void onModificarCargo() {
        Cargo seleccionado = cargosTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtils.showWarn("Debe seleccionar un cargo para modificar.");
            return;
        }

        String numero = numeroTextField.getText().trim();
        String descripcion = descripcionTextField.getText().trim();
        Agrupacion agrupacion = (Agrupacion) agrupacionComboBox.getSelectionModel().getSelectedItem();
        if (numero.isEmpty() || descripcion.isEmpty() || agrupacion == null) {
            AlertUtils.showWarn("Se debe especificar un número de cargo, una descripción y seleccionar una agrupación.");
            return;
        }

        seleccionado.setNumero(Integer.parseInt(numero));
        seleccionado.setDescripcion(descripcion);
        seleccionado.setAgrupacion(agrupacion);

        try {
            cargoService.update(seleccionado);
            cargarCargos();
            descripcionTextField.clear();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al modificar el cargo: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminarCargo() {
        Cargo seleccionado = cargosTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtils.showWarn("Debe seleccionar un cargo para eliminar.");
            return;
        }

        boolean confirmar = AlertUtils.showConfirm("¿Está seguro de eliminar el cargo seleccionado?");
        if (confirmar) {
            try {
                cargoService.delete(seleccionado);
                cargarCargos();
            } catch (ServiceException e) {
                AlertUtils.showErr("Error al eliminar el cargo: " + e.getMessage());
            }
        }
    }
}
