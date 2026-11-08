package ar.com.hmu.controller;

import java.io.IOException;
import java.util.Arrays;

import ar.com.hmu.service.*;
import ar.com.hmu.util.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import ar.com.hmu.auth.LoginService;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.*;

/**
 * Controlador encargado de gestionar la interfaz de usuario de la pantalla de login.
 * <p>
 * Esta clase se encarga de manejar la lógica de presentación del formulario de inicio de sesión,
 * incluyendo la validación de entradas del usuario y la coordinación con {@link LoginService}
 * para la autenticación de las credenciales.
 * Su responsabilidad principal es actualizar la interfaz de usuario en función de los resultados
 * obtenidos del servicio de autenticación, mostrando mensajes adecuados para informar al usuario.
 */
public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordFieldVisible;
    @FXML
    private CheckBox showPasswordCheckBox;
    @FXML
    private CheckBox rememberMeCheckBox;
    @FXML
    private Button loginButton;
    @FXML
    private ImageView serverStatusIcon;
    @FXML
    private Label serverStatusLabel;

    // Añadir una referencia a las preferencias del sistema
    private static final String LAST_USER_CUIL_KEY = "lastUserCuil";
    private PreferencesManager preferencesManager;

    private LoginService loginService;  // LoginService para la autenticación del usuario
    private DatabaseConnector databaseConnector;  // DatabaseConnector para la verificación del estado del servidor de BD
    private RoleService roleService;
    private UsuarioService usuarioService; // objeto para persistencia en la BD
    private ServerStatusUtils serverStatusUtils;
    private CargoService cargoService;
    private ServicioService servicioService;
    private DomicilioService domicilioService;
    private MemorandumService memorandumService;
    private ar.com.hmu.service.diagramacion.DiagramaService diagramaService;
    private FeriadoService feriadoService;
    private ar.com.hmu.service.novedades.NovedadService novedadService;
    private ar.com.hmu.service.notification.NotificacionService notificacionService;
    private ar.com.hmu.service.ReglaValidacionService reglaValidacionService;
    private ar.com.hmu.ui.ContextoAplicacion contexto;
    private EstadoTramiteRepository estadoTramiteRepository;
    private UsuarioRepository usuarioRepository;

    /**
     * Método para establecer el {@link LoginService} que se utilizará para la autenticación.
     *
     * @param loginService el servicio de autenticación que se va a utilizar.
     */
    public void setLoginService(LoginService loginService) {
        this.loginService = loginService;
    }

    public void setUsuarioService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public void setRolService(RoleService roleService) {
        this.roleService = roleService;
    }

    public void setCargoService(CargoService cargoService) {
        this.cargoService = cargoService;
    }

    public void setServicioService(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    public void setDomicilioService(DomicilioService domicilioService) {
        this.domicilioService = domicilioService;
    }

    public void setMemorandumService(MemorandumService memorandumService) {
        this.memorandumService = memorandumService;
    }

    public void setDiagramaService(ar.com.hmu.service.diagramacion.DiagramaService diagramaService) {
        this.diagramaService = diagramaService;
    }

    public void setFeriadoService(FeriadoService feriadoService) {
        this.feriadoService = feriadoService;
    }

    public void setNovedadService(ar.com.hmu.service.novedades.NovedadService novedadService) {
        this.novedadService = novedadService;
    }

    public void setNotificacionService(ar.com.hmu.service.notification.NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    public void setReglaValidacionService(ar.com.hmu.service.ReglaValidacionService reglaValidacionService) {
        this.reglaValidacionService = reglaValidacionService;
    }

    /** Servicios de la sesión: el menú principal recibe el mismo contexto. */
    public void setContexto(ar.com.hmu.ui.ContextoAplicacion contexto) {
        this.contexto = contexto;
    }

    public void setEstadoTramiteRepository(EstadoTramiteRepository estadoTramiteRepository) {
        this.estadoTramiteRepository = estadoTramiteRepository;
    }

    public void setUsuarioRepository(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Inicializa los componentes de la interfaz de usuario al cargar la pantalla de inicio de sesión.
     * <p>
     * Este método es invocado automáticamente por el framework JavaFX después de que se haya cargado
     * el archivo FXML correspondiente. Se encarga de realizar las siguientes tareas:
     * * Verifica el estado de conexión con el server y actualiza la GUI en consecuencia.
     * * Configura el TextBox del CUIL, limitando la entrada solo a números y además, le da un formato
     *   de fácil lectura para humanos (NN-UUVVVWWW-M en vez de NNUUVVVWWWM).
     */
    @FXML
    public void initialize() {

        // Inicializar PreferencesManager
        preferencesManager = new PreferencesManager();

        // Configurar el campo de texto para el CUIL
        CuilUtils.configureCuilField(usernameField);

        // Configurar el checkbox de mostrar/ocultar contraseña
        configureShowPassword();

        // Cargar el último CUIL, si existe
        loadUserCuil();

        // Añadir el evento de presionar "Enter" para usernameField
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleLoginButtonClick();
            }
        });

        // Añadir el evento de presionar "Enter" para passwordField
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleLoginButtonClick();
            }
        });

        // Configurar el estado del botón de iniciar sesión
        configureLoginButton();

    }

    /**
     * Método para establecer el {@link DatabaseConnector} que se utilizará para verificar el estado del servidor.
     *
     * @param databaseConnector el conector de base de datos a utilizar.
     */
    public void setDatabaseConnector(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    /**
     * Método que se debe llamar después de establecer todas las dependencias necesarias.
     * <p>
     * Este método se encarga de realizar todas las inicializaciones de la interfaz de usuario y de las
     * verificaciones necesarias para el estado del servidor.
     */
    public void postInitialize() {
        // Actualizar el estado del servidor y comenzar el chequeo periódico
        if (databaseConnector == null) {
            throw new IllegalStateException("MainMenuMosaicoController: DatabaseConnector no está configurado. No se puede verificar el estado del servidor.");
        }
        serverStatusUtils = new ServerStatusUtils(databaseConnector, serverStatusLabel, serverStatusIcon);
        serverStatusUtils.updateServerStatusUI();
        serverStatusUtils.startPeriodicServerCheck();

    }


    /**
     * Opcionalmente, parar el chequeo de estado del servidor cuando el se elimine el controlador
     */
    public void cleanup() {
        if (serverStatusUtils != null) {
            serverStatusUtils.stop();
        }
    }


    /**
     * Configura el botón de inicio de sesión para habilitarse o deshabilitarse
     * según el contenido de los campos de CUIL y contraseña.
     */
    private void configureLoginButton() {
        // Añadir listeners para habilitar/deshabilitar el botón de iniciar sesión
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> updateLoginButtonState());
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> updateLoginButtonState());

        // Inicializar el estado del botón
        updateLoginButtonState();
    }

    /**
     * Actualiza el estado del botón de inicio de sesión en función de los campos de CUIL y contraseña.
     * El botón solo estará habilitado si ambos campos tienen algún valor.
     */
    private void updateLoginButtonState() {
        String cuil = usernameField.getText();
        String password = passwordField.getText();

        // Habilitar el botón solo si CUIL y contraseña tienen valores
        boolean isCuilFilled = cuil != null && !cuil.trim().isEmpty();
        boolean isPasswordFilled = password != null && !password.trim().isEmpty();

        loginButton.setDisable(!(isCuilFilled && isPasswordFilled));
    }


    /**
     * Carga el último CUIL utilizado por el usuario desde las preferencias del sistema y
     * lo inserta en el campo correspondiente. Si existe un valor previo almacenado,
     * posiciona el foco automáticamente en el campo de contraseña para facilitar el inicio de sesión.
     * <p>
     * Este método se utiliza para mejorar la experiencia del usuario recordando la última cuenta utilizada.
     * Si no existe un valor almacenado, el campo de CUIL quedará vacío y el usuario deberá ingresar
     * sus credenciales manualmente.
     */
    private void loadUserCuil() {
        String lastUserCuil = preferencesManager.get(LAST_USER_CUIL_KEY, "");

        if (!lastUserCuil.isEmpty()) {
            usernameField.setText(lastUserCuil);
            rememberMeCheckBox.setSelected(true); // Dejar marcada la casilla por defecto siempre ue haya un CUIL previamente recordado.
            Platform.runLater(() -> passwordField.requestFocus()); // Posicionar el foco en el campo de contraseña. Platform.runLater() permite ejecutar el código de establecimiento del foco después de que JavaFX termine de realizar la configuración inicial de la pantalla.
        } else {
            rememberMeCheckBox.setSelected(false); // Desmarcar la casilla cuando no haya un CUIL recordado.
        }
    }

    /**
     * Guarda el CUIL del usuario en las preferencias del sistema para que sea recordado en futuros inicios de sesión.
     * <p>
     * Este método almacena el CUIL proporcionado, lo cual agiliza el inicio de sesión del usuario en un futuro,
     * evitando que tenga que ingresarlo nuevamente. Se invoca después de una autenticación exitosa.
     *
     * @param cuil el CUIL del usuario que se debe almacenar para recordar en futuros inicios de sesión.
     */
    private void saveUserCuil(String cuil) {
        if (rememberMeCheckBox.isSelected()) {
            preferencesManager.put(LAST_USER_CUIL_KEY, cuil);
        } else {
            preferencesManager.put(LAST_USER_CUIL_KEY, ""); // Truquito para no tener que implementar un método remove :-/
        }
    }


    /**
     * Configura la funcionalidad de mostrar u ocultar la contraseña ingresada por el usuario.
     * <p>
     * Este método alterna entre un {@link PasswordField} (que como tal, no muestra la contraseña),
     * y un {@link TextField}, que sí muestra la contraseña (en texto plano) cuando la casilla
     * "Mostrar contraseña" está seleccionada.
     * La sincronización entre ambos campos permite que el usuario pueda alternar libremente la
     * visibilidad de la contraseña sin perder la información ya ingresada.
     * <p>
     * El {@link CheckBox} "Mostrar contraseña" controla esta funcionalidad y al ser seleccionado,
     * oculta el campo de {@link PasswordField} y muestra el campo de {@link TextField} para visualizar la contraseña en texto plano.
     * Cuando se deselecciona, se vuelve a ocultar la contraseña.
     */
    private void configureShowPassword() {
        // Ocultamos el TextField visible de contraseña (de forma inicial)
        passwordFieldVisible.setVisible(false);
        passwordFieldVisible.managedProperty().bind(showPasswordCheckBox.selectedProperty());
        passwordField.managedProperty().bind(showPasswordCheckBox.selectedProperty().not());
        passwordFieldVisible.textProperty().bindBidirectional(passwordField.textProperty());

        // Listener para la casilla "Mostrar contraseña"
        showPasswordCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                // Mostrar el campo de texto que tiene la contraseña visible
                passwordFieldVisible.setVisible(true);
                passwordFieldVisible.setText(passwordField.getText());
                passwordField.setVisible(false);
            } else {
                // Volver a ocultar la contraseña y mostrar el PasswordField
                passwordField.setVisible(true);
                passwordField.setText(passwordFieldVisible.getText());
                passwordFieldVisible.setVisible(false);
            }
        });
    }

    /**
     * Gestiona el evento del botón de inicio de sesión.
     * <p>
     * Este método es invocado cuando el usuario hace clic en el botón de "Iniciar Sesión".
     * Valida las credenciales ingresadas por el usuario consultando la base de datos.
     * Si las credenciales son correctas, se muestra un mensaje de éxito; si no lo son, se muestra una advertencia.
     */
    @FXML
    private void handleLoginButtonClick() {
        char[] passwordCharArray = null; // Inicializar a null
        try {
            // Obtener el CUIL y la contraseña ingresados
            String cuil = usernameField.getText().replaceAll("[^\\d]", "");  // Remover guiones para obtener solo números
            // Obtener la contraseña ingresada como un CharSequence
            CharSequence passwordChars = passwordField.getCharacters();
            if (passwordChars == null || passwordChars.isEmpty()) {
                AlertUtils.showErr("El campo de contraseña está vacío. Por favor, ingrese su contraseña.");
                return;
            }
            // Convertir CharSequence a char[] para poder manipular y luego limpiar
            passwordCharArray = new char[passwordChars.length()];
            for (int i = 0; i < passwordChars.length(); i++) {
                passwordCharArray[i] = passwordChars.charAt(i);
            }

            if (loginService == null) {
                throw new IllegalStateException("LoginService no está configurado. No se puede validar la autenticación.");
            }

            LoginService.ResultadoLogin resultado = loginService.autenticar(Long.parseLong(cuil), passwordCharArray);

            if (resultado == LoginService.ResultadoLogin.CORRECTO) {
                saveUserCuil(cuil); // Guardar el CUIL (si correspondiese)
                // Obtener el usuario autenticado para realizar verificaciones adicionales
                Usuario usuario = loginService.getUsuarioByCuil(Long.parseLong(cuil));
                if (usuario == null) {
                    throw new IllegalStateException("No se pudo recuperar el usuario después de la autenticación.");
                }

                // Cambio obligatorio: contraseña por defecto o marca de restablecimiento (CU01 3a)
                if (usuario.isDefaultPassword() || loginService.requiereCambioPassword(usuario.getCuil())) {
                    // Mostrar ventana para que el usuario cambie la contraseña
                    PasswordDialogUtils.showChangePasswordDialog(usuario, usuarioService,
                            (message) -> showMainMenu(usuario), // Callback para continuar al menú principal
                            () -> SessionUtils.handleLogout((Stage) loginButton.getScene().getWindow()) // Callback para cerrar la sesión si se cancela
                    );
                } else {
                    // Realizar limpieza (actualmente, detener la verificación de estado de servidor)
                    cleanup();

                    // Continuar al menú principal
                    showMainMenu(usuario);
                }
                //MANTENIMIENTO: AlertUtils.showInfo("Inicio de sesión exitoso para el CUIL: " + cuil + "\nPero el sistema está en mantenimiento en este momento.\nIntente nuevamente más tarde.");
            } else {
                mostrarErrorDeAcceso(resultado);
            }

        } catch (NumberFormatException e) {
            AlertUtils.showErr("El CUIL ingresado no es válido.\nDebe contener solo números.");
        } catch (IllegalStateException e) {
            AlertUtils.showErr("Error de configuración: " + e.getMessage());
        } catch (Exception e) {
            AlertUtils.showErr("Ocurrió un error inesperado: " + e.getMessage());
        } finally {
            // Limpiar la contraseña después de la validación
            if (passwordCharArray != null) {
                Arrays.fill(passwordCharArray, '\0');  // Limpiar la memoria del char[]
            }
        }
    }

    /**
     * Muestra un diálogo para cambiar la contraseña del usuario utilizando el método centralizado en `PasswordDialogUtils`.
     * <p>
     * Este método delega la lógica de presentación del diálogo y validación de las contraseñas a la clase utilitaria
     * {@link PasswordDialogUtils}. Es utilizado especialmente para forzar al usuario a cambiar la contraseña predeterminada
     * antes de continuar usando el sistema.
     * <p>
     * `PasswordDialogUtils` presenta una ventana emergente que contiene los siguientes elementos:
     * <ul>
     *     <li>Un campo para ingresar la contraseña actual.</li>
     *     <li>Un campo para la nueva contraseña.</li>
     *     <li>Un campo para confirmar la nueva contraseña.</li>
     * </ul>
     * <p>
     * Si el cambio es exitoso, el callback de éxito (`onSuccess`) es llamado para proceder con la lógica posterior
     * (en este caso, redirigir al menú principal). Si hay un error (como la contraseña actual incorrecta o si las nuevas contraseñas
     * no coinciden), se muestra un mensaje de error y se reabre el diálogo.
     * <p>
     * Si el usuario cancela el cambio de contraseña, se llama al callback de cancelación (`onCancel`), el cual cierra la sesión
     * y vuelve a la pantalla de inicio de sesión.
     *
     * @param usuario El usuario autenticado que necesita cambiar su contraseña.
     */
    private void showChangePasswordDialog(Usuario usuario) {
        // Utilizar PasswordDialogUtils para mostrar el diálogo de cambio de contraseña
        PasswordDialogUtils.showChangePasswordDialog(usuario, usuarioService,
                successMessage -> {
                    // Mostrar el mensaje de éxito al cambiar la contraseña
                    AlertUtils.showInfo(successMessage);

                    // Redirigir al menú principal después del cambio de contraseña exitoso
                    showMainMenu(usuario);
                },
                () -> {
                    // Si el usuario cancela el diálogo, cerramos la sesión
                    SessionUtils.handleLogout((Stage) loginButton.getScene().getWindow());
                }
        );
    }

    /**
     * Mensajes del CU01: el error no indica cuál de los dos datos falló (2a, 2c);
     * el bloqueo se informa (2b).
     */
    private void mostrarErrorDeAcceso(LoginService.ResultadoLogin resultado) {
        switch (resultado) {
            case BLOQUEADA_AHORA -> AlertUtils.showErr("La cuenta quedó bloqueada por "
                    + LoginService.MAX_INTENTOS + " intentos fallidos consecutivos.\n"
                    + "Solicite el desbloqueo a la Oficina de Personal.");
            case BLOQUEADA -> AlertUtils.showErr("La cuenta está bloqueada.\n"
                    + "Solicite el desbloqueo a la Oficina de Personal.");
            default -> AlertUtils.showWarn("CUIL o contraseña incorrectos.");
        }
    }

    /**
     * Muestra el menú principal de la aplicación después de un inicio de sesión exitoso.
     * <p>
     * Este método se encarga de cargar la vista del menú principal (mainMenu.fxml), configurar el controlador
     * del menú con el usuario autenticado y las opciones de menú correspondientes, y luego cambiar la escena
     * de la ventana principal de la aplicación para mostrar el menú principal.
     *
     * @param usuario El usuario autenticado que ha iniciado sesión.
     */
    private void showMainMenu(Usuario usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainMenuMosaico.fxml"));
            Parent root = loader.load();

            MainMenuMosaicoController controller = loader.getController();
            if (controller == null) {
                throw new IllegalStateException("El controlador del menú principal no fue inicializado correctamente.");
            }

            // Obtener el Stage actual y pasar el Stage al controlador
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // Pass services to the controller
            if (contexto != null) {
                contexto.inyectar(controller);
            } else {
                controller.setServices(usuarioService, cargoService, servicioService, domicilioService, roleService);
                controller.setMemorandumService(memorandumService);
                controller.setDiagramaService(diagramaService);
                controller.setFeriadoService(feriadoService);
                controller.setNovedadService(novedadService);
                controller.setNotificacionService(notificacionService);
                controller.setReglaValidacionService(reglaValidacionService);
                controller.setEstadoTramiteRepository(estadoTramiteRepository);
                controller.setUsuarioRepository(usuarioRepository);
                controller.setDatabaseConnector(databaseConnector);
            }

            controller.postInitialize(usuario, stage);

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setResizable(true);  // Hacer que el Stage sea redimensionable
            stage.setTitle("Menú Principal" + " :: " + AppInfo.PRG_LONG_TITLE);  // Establecer un título a la ventana


        } catch (IOException e) {
            e.printStackTrace(); // Para imprimir todo el stack trace y facilitar el diagnóstico.
            AlertUtils.showErr("Error al cargar el menú principal:\n" + e.getMessage());
        }
    }

}
