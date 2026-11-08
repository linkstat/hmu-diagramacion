package ar.com.hmu.controller;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.model.Agrupacion;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.service.ServicioService;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.util.AlertUtils;

import java.util.List;
import java.util.UUID;

public class AbmServicioController {

    @FXML
    private TableView<Servicio> serviciosTableView;
    @FXML
    private TableColumn<Servicio, String> nombreColumn;
    @FXML
    private TableColumn<Servicio, String> agrupacionColumn;
    @FXML
    private TextField nombreTextField;
    @FXML
    private ComboBox<Agrupacion> agrupacionComboBox;
    @FXML
    private ComboBox<Usuario> encargadoComboBox;
    private ObservableList<Agrupacion> agrupacionList = FXCollections.observableArrayList(Agrupacion.values());

    private ServicioService servicioService;
    private Usuario usuarioActual;

    public void setServices(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    /**
     * Inyecta el usuario logueado. Necesario para decidir si el combobox de
     * encargado debe estar habilitado o de sólo lectura para el servicio
     * seleccionado.
     */
    public void setUsuarioActual(Usuario usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    @FXML
    private TableColumn<Servicio, String> dotacionColumn;

    @FXML
    private javafx.scene.control.TextField dotacionTextField;

    @FXML
    public void initialize() {
        dotacionColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getDotacionMinima() != null ? String.valueOf(cd.getValue().getDotacionMinima()) : "—"));
        // Configurar la columna de nombre de servicio
        nombreColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getNombre()));
        // Configurar la columna de agrupación
        agrupacionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAgrupacion().toString()));
        // Manejar la selección en la tabla
        serviciosTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> mostrarDetalleServicio(newValue));
        // Inicializar ComboBox
        agrupacionComboBox.setItems(agrupacionList);
        // ComboBox de encargado: cómo presentar cada Usuario
        encargadoComboBox.setConverter(new StringConverter<Usuario>() {
            @Override
            public String toString(Usuario u) {
                if (u == null) return "(sin encargado)";
                return u.getApellidos() + ", " + u.getNombres();
            }

            @Override
            public Usuario fromString(String s) {
                return null;
            }
        });
        encargadoComboBox.setDisable(true);
        cargarServicios();
    }

    private void cargarServicios() {
        try {
            serviciosTableView.getItems().setAll(servicioService.readAll());
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los servicios: " + e.getMessage());
        }
    }

    private void mostrarDetalleServicio(Servicio servicio) {
        if (servicio != null) {
            nombreTextField.setText(servicio.getNombre());
            agrupacionComboBox.getSelectionModel().select(servicio.getAgrupacion());
            dotacionTextField.setText(servicio.getDotacionMinima() != null ? String.valueOf(servicio.getDotacionMinima()) : "");
            cargarEncargadoComboBox(servicio);
        } else {
            nombreTextField.clear();
            agrupacionComboBox.getSelectionModel().clearSelection();
            dotacionTextField.clear();
            encargadoComboBox.getItems().clear();
            encargadoComboBox.setDisable(true);
        }
    }

    /** Dotación mínima ingresada (CU07): vacío = sin control; entero de 0 a 50. */
    private boolean leerDotacion(Servicio s) {
        String t = dotacionTextField.getText() == null ? "" : dotacionTextField.getText().trim();
        if (t.isEmpty()) {
            s.setDotacionMinima(null);
            return true;
        }
        try {
            int v = Integer.parseInt(t);
            if (v < 0 || v > 50) throw new NumberFormatException();
            s.setDotacionMinima(v);
            return true;
        } catch (NumberFormatException e) {
            AlertUtils.showWarn("La dotación mínima debe ser un número entero entre 0 y 50.");
            return false;
        }
    }

    /**
     * Carga los usuarios del servicio en el combobox y selecciona al
     * encargado actual. Habilita o deshabilita el combo según los permisos
     * del usuario logueado: Oficina de Personal y Dirección pueden cambiar
     * el encargado de cualquier servicio; un Jefe sólo de su propio servicio.
     */
    private void cargarEncargadoComboBox(Servicio servicio) {
        try {
            List<Usuario> usuarios = servicioService.findUsuariosByServicio(servicio.getId());
            encargadoComboBox.getItems().setAll(usuarios);

            // Selección actual
            UUID encargadoActualId = servicio.getEncargadoUsuarioId();
            if (encargadoActualId != null) {
                usuarios.stream()
                        .filter(u -> encargadoActualId.equals(u.getId()))
                        .findFirst()
                        .ifPresent(u -> encargadoComboBox.getSelectionModel().select(u));
            } else {
                encargadoComboBox.getSelectionModel().clearSelection();
            }

            encargadoComboBox.setDisable(!puedeCambiarEncargado(servicio));
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al cargar los usuarios del servicio: " + e.getMessage());
            encargadoComboBox.getItems().clear();
            encargadoComboBox.setDisable(true);
        }
    }

    /**
     * Reglas: OP y Dirección pueden cambiar cualquier encargado. Un jefe de
     * servicio sólo el de su propio servicio. Otros roles no.
     */
    private boolean puedeCambiarEncargado(Servicio servicio) {
        if (usuarioActual == null) return false;
        if (usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION)) {
            return true;
        }
        if (usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO)) {
            UUID servicioDelJefe = usuarioActual.getServicioId();
            return servicioDelJefe != null && servicioDelJefe.equals(servicio.getId());
        }
        return false;
    }

    @FXML
    private void onAgregarServicio() {
        String nombre = nombreTextField.getText().trim();
        Agrupacion agrupacion = (Agrupacion) agrupacionComboBox.getSelectionModel().getSelectedItem();
        if (nombre.isEmpty() || agrupacion == null) {
            AlertUtils.showWarn("Se deben especificar nombre de servicio y agrupación.");
            return;
        }

        Servicio servicio = new Servicio();
        servicio.setId(UUID.randomUUID());
        servicio.setNombre(nombre);
        servicio.setAgrupacion(agrupacion);
        if (!leerDotacion(servicio)) return;

        try {
            servicioService.create(servicio);
            cargarServicios();
            nombreTextField.clear();
            agrupacionComboBox.getSelectionModel().clearSelection();
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al agregar el servicio: " + e.getMessage());
        }
    }

    @FXML
    private void onModificarServicio() {
        Servicio seleccionado = serviciosTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtils.showWarn("Debe seleccionar un servicio para modificar.");
            return;
        }

        String nombre = nombreTextField.getText().trim();
        Agrupacion agrupacion = (Agrupacion) agrupacionComboBox.getSelectionModel().getSelectedItem();
        if (nombre.isEmpty() || agrupacion == null) {
            AlertUtils.showWarn("El nombre de servicio no puede estar vacío. Además, debe seleccionar una agrupación");
            return;
        }

        seleccionado.setNombre(nombre);
        seleccionado.setAgrupacion(agrupacion);
        if (!leerDotacion(seleccionado)) return;

        // Encargado: solo se persiste si el usuario tiene permisos sobre este
        // servicio. La UI ya deshabilita el combo cuando no, pero re-validamos
        // por defensa en profundidad.
        if (puedeCambiarEncargado(seleccionado)) {
            Usuario encargadoSeleccionado = encargadoComboBox.getSelectionModel().getSelectedItem();
            seleccionado.setEncargadoUsuarioId(encargadoSeleccionado != null ? encargadoSeleccionado.getId() : null);
        }

        try {
            servicioService.update(seleccionado);
            cargarServicios();
            nombreTextField.clear();
            agrupacionComboBox.getSelectionModel().clearSelection();
            encargadoComboBox.getItems().clear();
            encargadoComboBox.setDisable(true);
        } catch (ServiceException e) {
            AlertUtils.showErr("Error al modificar el servicio: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminarServicio() {
        Servicio seleccionado = serviciosTableView.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            AlertUtils.showWarn("Debe seleccionar un servicio para eliminar.");
            return;
        }

        boolean confirmar = AlertUtils.showConfirm("¿Está seguro de eliminar el servicio seleccionado?");
        if (confirmar) {
            try {
                servicioService.delete(seleccionado);
                cargarServicios();
            } catch (ServiceException e) {
                AlertUtils.showErr("Error al eliminar el servicio: " + e.getMessage());
            }
        }
    }
}
