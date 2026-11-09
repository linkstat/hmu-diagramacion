package ar.com.hmu.controller;

import java.io.IOException;

import ar.com.hmu.service.*;
import ar.com.hmu.util.*;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.text.Text;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.*;

import static ar.com.hmu.util.SessionUtils.handleLogout;

/**
 * Controlador para gestionar el comportamiento del menú principal en forma de mosaico.
 * <p>
 * Esta clase gestiona la interacción del usuario con el menú principal de la aplicación,
 * incluyendo el manejo de mosaicos, opciones de menú, estado del servidor y la sesión del usuario.
 */
public class MainMenuMosaicoController {

    // Elementos del Menú
    @FXML
    private MenuBar menuBar;
    @FXML
    private Menu empleadoMenu;
    @FXML
    private Menu jefaturaDeServicioMenu;
    @FXML
    private Menu oficinaDePersonalMenu;
    @FXML
    private Menu direccionMenu;
    @FXML
    private MenuItem changeProfileImageMenuItem;
    @FXML
    private MenuItem changePasswordMenuItem;
    @FXML
    private MenuItem logoutMenuItem;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private MenuItem listadoEmpleadosMenuItem;
    @FXML
    private MenuItem listadoServiciosMenuItem;
    @FXML
    private MenuItem abmEmpleadosMenuItem;
    @FXML
    private MenuItem abmServiciosMenuItem;
    @FXML
    private MenuItem abmCargosMenuItem;
    @FXML
    private MenuItem feriadosOPMenuItem;
    @FXML
    private MenuItem feriadosDireccionMenuItem;
    @FXML
    private MenuItem licenciasDeUsoMenuItem;
    @FXML
    private MenuItem acercaDeMenuItem;

    // Elementos del Encabezado
    @FXML
    private ImageView agentProfileImage;
    @FXML
    private Text agentFullNameText;
    @FXML
    private Text agentServiceText;
    @FXML
    private Text agentCargoText;

    // Elementos del Mosaico
    @FXML
    private Text currentConnDateTimeText;
    @FXML
    private Text currentConnHostnameText;
    @FXML
    private Text currentConnIPAddressText;
    @FXML
    private VBox aprobacionSolicitudesVBox;
    @FXML
    private VBox notasMemosVBox;
    @FXML
    private VBox partesDiariosVBox;
    @FXML
    private VBox consultaDiagramasDeServicioVBox;
    @FXML
    private VBox diagramacionDeServicioVBox;
    @FXML
    private VBox miDiagramaVBox;
    @FXML
    private VBox controlMarcacionesVBox;
    @FXML
    private VBox pasesDeSalidaVBox;
    @FXML
    private VBox omisionesIngresEgresoVBox;
    @FXML
    private VBox faltasJustificadasVBox;
    @FXML
    private VBox faltasInjustificadasVBox;
    @FXML
    private VBox faltasRazonFuerzaMayorVBox;
    @FXML
    private VBox solicitudHorasExtraFCVBox;
    @FXML
    private VBox francosCompensatoriosVBox;
    @FXML
    private VBox reportesVBox;
    @FXML
    private javafx.scene.text.Text reportesText;
    @FXML
    private javafx.scene.text.Text francosText;
    @FXML
    private MenuItem notasMemosMenuItem;
    @FXML
    private MenuItem pasesSalidaMenuItem;
    @FXML
    private MenuItem omisionMenuItem;
    @FXML
    private Menu inasistenciasMenu;
    @FXML
    private Menu francosMenu;
    @FXML
    private MenuItem reportesJSMenuItem;
    @FXML
    private MenuItem reportesOPMenuItem;
    @FXML
    private MenuItem reportesDHMenuItem;
    @FXML
    private MenuItem listadoMisAgentesMenuItem;
    @FXML
    private MenuItem listadoAgentesDHMenuItem;
    @FXML
    private VBox listadoDeEmpleadosVBox;
    @FXML
    private VBox listadoDeServiciosVBox;
    @FXML
    private VBox abmEmpleadosVBox;
    @FXML
    private VBox abmServiciosVBox;

    // Botón de cierre de sesión
    @FXML
    private Button logoutButton;

    @FXML
    private Button avisosButton;

    @FXML
    private MenuItem reglasOPMenuItem;

    @FXML
    private MenuItem reglasDireccionMenuItem;

    @FXML
    private MenuItem modalidadOPMenuItem;

    @FXML
    private MenuItem importarPadronMenuItem;

    @FXML
    private MenuItem rolesMenuItem;

    @FXML
    private Label novedadesCountLabel;

    @FXML
    private Text prevConnDateTimeText;

    @FXML
    private Text prevConnHostText;

    // Elementos de la barra de estado
    @FXML
    private ImageView serverStatusIcon;
    @FXML
    private Label serverStatusLabel;
    @FXML
    private Label memosCountLabel;

    private MainMenuMosaicoService mainMenuMosaicoService;  // Servicio para gestionar la lógica del menú principal
    private DatabaseConnector databaseConnector;  // Necesario para la verificación del estado del servidor.

    private ServerStatusUtils serverStatusUtils;

    // Repositorios
    private DomicilioRepository domicilioRepository;
    private CargoRepository cargoRepository;
    private ServicioRepository servicioRepository;
    private UsuarioRepository usuarioRepository;
    private RoleRepository roleRepository;

    // Servicios
    private UsuarioService usuarioService;
    private CargoService cargoService;
    private ServicioService servicioService;
    private DomicilioService domicilioService;
    private RoleService roleService;
    private MemorandumService memorandumService;
    private ar.com.hmu.service.diagramacion.DiagramaService diagramaService;
    private FeriadoService feriadoService;
    private ar.com.hmu.service.novedades.NovedadService novedadService;
    private ar.com.hmu.service.notification.NotificacionService notificacionService;
    private ReglaValidacionService reglaValidacionService;
    private ar.com.hmu.service.diagramacion.ModalidadHorariaService modalidadHorariaService;
    private ar.com.hmu.service.asistencia.ParteDiarioService parteDiarioService;
    private ar.com.hmu.service.asistencia.IndicadoresService indicadoresService;
    private ImportacionPadronService importacionPadronService;
    private AsignacionRolService asignacionRolService;
    private EstadoTramiteRepository estadoTramiteRepository;

    private Usuario usuarioActual;

    // Referencia a la ventana principal
    private static Stage primaryStage;  // Necesario para guardar las propiedades de ventana (asi lo llamo desde LoginController)

    // Nueva instancia para manejar preferencias
    private PreferencesManager preferencesManager;

    // Almacenamiento de las propiedades de la ventana (tamaño y posición)
    private static final String WINDOW_WIDTH_KEY = "window.width";
    private static final String WINDOW_HEIGHT_KEY = "window.height";
    private static final String WINDOW_X_KEY = "window.x";
    private static final String WINDOW_Y_KEY = "window.y";


    public void setDatabaseConnector(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    public void setServices(UsuarioService usuarioService, CargoService cargoService, ServicioService servicioService, DomicilioService domicilioService, RoleService roleService) {
        this.usuarioService = usuarioService;
        this.cargoService = cargoService;
        this.servicioService = servicioService;
        this.domicilioService = domicilioService;
        this.roleService = roleService;
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

    public void setReglaValidacionService(ReglaValidacionService reglaValidacionService) {
        this.reglaValidacionService = reglaValidacionService;
    }

    public void setModalidadHorariaService(ar.com.hmu.service.diagramacion.ModalidadHorariaService s) {
        this.modalidadHorariaService = s;
    }

    public void setParteDiarioService(ar.com.hmu.service.asistencia.ParteDiarioService s) {
        this.parteDiarioService = s;
    }

    public void setIndicadoresService(ar.com.hmu.service.asistencia.IndicadoresService s) {
        this.indicadoresService = s;
    }

    public void setImportacionPadronService(ImportacionPadronService s) {
        this.importacionPadronService = s;
    }

    public void setAsignacionRolService(AsignacionRolService s) {
        this.asignacionRolService = s;
    }

    public void setEstadoTramiteRepository(EstadoTramiteRepository estadoTramiteRepository) {
        this.estadoTramiteRepository = estadoTramiteRepository;
    }

    public void setUsuarioRepository(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }


    /**
     * Inicializa los componentes después de que el archivo FXML ha sido cargado.
     */
    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    /**
     * Establece el usuario actual y actualiza la interfaz gráfica con sus datos.
     *
     * @param usuario El usuario que ha iniciado sesión.
     * @param stage               El Stage principal de la aplicación.
     */
    public void postInitialize(Usuario usuario, Stage stage) {
        this.usuarioActual = usuario;
        // Identifica al usuario ante los disparadores de auditoría (RNF03).
        ContextoAuditoria.iniciar(usuario.getId());
        this.primaryStage = stage;  // Guardo la referencia al Stage

        this.mainMenuMosaicoService = new MainMenuMosaicoService(usuario);

        try {
            usuarioService.loadAdditionalUserData(usuarioActual);
        } catch (ServiceException e) {
            AlertUtils.showErr(e.getMessage());
        }

        // Inicializar PreferencesManager con el CUIL del usuario
        String userId = String.valueOf(usuarioActual.getCuil());
        this.preferencesManager = new PreferencesManager(userId);

        // Configurar el evento de cierre de la ventana
        this.primaryStage.setOnCloseRequest(event -> saveWindowPreferences());

        // Actualizar la información del agente
        agentFullNameText.setText(mainMenuMosaicoService.getUsuarioFullName());
        agentServiceText.setText(mainMenuMosaicoService.getServicioNombre());
        agentCargoText.setText(mainMenuMosaicoService.getCargoUsuario());
        agentProfileImage.setImage(mainMenuMosaicoService.getProfileImage());

        // Actualizar la información de conexión
        currentConnDateTimeText.setText(mainMenuMosaicoService.getCurrentConnDateTime());
        currentConnHostnameText.setText(mainMenuMosaicoService.getCurrentConnHostname());
        currentConnIPAddressText.setText(mainMenuMosaicoService.getCurrentConnIPAddress());

        // Conexión anterior del usuario (se recuerda por CUIL en las preferencias locales).
        String anterior = preferencesManager.get("ultimaConexion", "");
        String hostAnterior = preferencesManager.get("ultimoHost", "");
        if (ar.com.hmu.ui.ModoCapturas.activo()) {
            anterior = Reloj.ahora().minusDays(1).withHour(7).withMinute(42).format(java.time.format.DateTimeFormatter
                    .ofPattern("EEE, dd MMM yyyy HH:mm:ss"));
            hostAnterior = mainMenuMosaicoService.getCurrentConnHostname() + " ("
                    + mainMenuMosaicoService.getCurrentConnIPAddress() + ")";
        } else {
            preferencesManager.put("ultimaConexion", mainMenuMosaicoService.getCurrentConnDateTime());
            preferencesManager.put("ultimoHost", mainMenuMosaicoService.getCurrentConnHostname() + " ("
                    + mainMenuMosaicoService.getCurrentConnIPAddress() + ")");
        }
        prevConnDateTimeText.setText(anterior.isEmpty() ? "primer ingreso" : anterior);
        prevConnHostText.setText(hostAnterior);

        //Configurar visibilidad
        configurarVisibilidadMenus();

        // Actualizar el estado del servidor y comenzar el chequeo periódico
        if (databaseConnector == null) {
            throw new IllegalStateException("MainMenuMosaicoController: DatabaseConnector no está configurado. No se puede verificar el estado del servidor.");
        }
        serverStatusUtils = new ServerStatusUtils(databaseConnector, serverStatusLabel, serverStatusIcon);
        serverStatusUtils.updateServerStatusUI();
        serverStatusUtils.startPeriodicServerCheck();

        // Configurar el evento de cierre de la ventana
        this.primaryStage.setOnCloseRequest(event -> saveWindowPreferences());

        // Cargar preferencias de la ventana en este punto
        loadWindowPreferences();

        // Alertas de diagramación (RFS09): recordatorio al login. Se corre
        // diferido (runLater) para no bloquear el armado de la ventana.
        if (!ar.com.hmu.ui.ModoCapturas.activo()) {
            javafx.application.Platform.runLater(this::verificarAlertasDiagramacion);
        }
        javafx.application.Platform.runLater(this::refrescarContadorNovedades);
        if (!ar.com.hmu.ui.ModoCapturas.activo()) {
            // Cierre de sesión por inactividad (RNF01).
            ControlInactividad.iniciar(primaryStage, () -> {
                AlertUtils.showInfo("La sesión se cerró tras " + ControlInactividad.minutosLimite()
                        + " minutos de inactividad.");
                handleLogoutAction();
            });
        }
        if (notificacionService != null && !ar.com.hmu.ui.ModoCapturas.activo()) {
            Thread reintento = new Thread(notificacionService::reintentarPendientes, "reintento-avisos");
            reintento.setDaemon(true);
            reintento.start();
        }
    }

    /**
     * Abre la pantalla de novedades (P03). Desde los mosaicos del agente
     * ({@code tipoInicial} = código de TipoNovedad) entra con el perfil de
     * agente y abre directamente la solicitud de ese tipo; {@code ""} abre la
     * bandeja del agente; {@code null} entra con el perfil de mayor alcance.
     */
    private void abrirNovedades(String tipoInicial) {
        if (novedadService == null) {
            AlertUtils.showWarn("Módulo de novedades no disponible.");
            return;
        }
        try {
            NovedadesController c = crearPantallaNovedades();
            Stage stage = c.getStage();
            if (tipoInicial != null) {
                c.usarPerfil(ar.com.hmu.service.novedades.PerfilNovedades.AGENTE);
            }
            stage.setOnHidden(e -> refrescarContadorNovedades());
            stage.show();
            if (tipoInicial != null && !tipoInicial.isEmpty()) {
                SolicitarNovedadController s = c.abrirSolicitud(null);
                if (s != null) {
                    s.completar(tipoInicial, usuarioActual.getId(), null, null, null, null, null, null);
                }
            }
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir las novedades: " + e.getMessage());
        }
    }

    /** Arma la ventana de novedades sin mostrarla (también la usa el modo de capturas). */
    public NovedadesController crearPantallaNovedades() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/novedades.fxml"));
        Parent root = loader.load();
        NovedadesController controller = loader.getController();
        controller.setNovedadService(novedadService);
        controller.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Novedades :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        controller.setStage(stage);
        controller.postInitialize();
        return controller;
    }

    /** Pantalla de modalidad horaria (CU04), sin mostrar (también la usa el modo de capturas). */
    public java.util.Map.Entry<Stage, ModalidadHorariaController> crearPantallaModalidad() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modalidadHoraria.fxml"));
        Parent root = loader.load();
        ModalidadHorariaController c = loader.getController();
        c.setService(modalidadHorariaService);
        c.setUsuarioRepository(usuarioRepository);
        c.setUsuarioActual(usuarioActual);
        java.util.Map<java.util.UUID, String> nombres = new java.util.HashMap<>();
        try {
            for (var sv : servicioService.readAll()) {
                nombres.put(sv.getId(), sv.getNombre());
            }
        } catch (Exception ignored) {
            // La lista muestra "sin servicio".
        }
        c.setServicios(nombres);
        Stage stage = new Stage();
        stage.setTitle("Modalidad horaria :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        c.postInitialize();
        return java.util.Map.entry(stage, c);
    }

    /** Roles con vigencia (CU06), sin mostrar (también la usa el modo de capturas). */
    public java.util.Map.Entry<Stage, RolesVigenciaController> crearPantallaRoles() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/rolesVigencia.fxml"));
        Parent root = loader.load();
        RolesVigenciaController c = loader.getController();
        c.setService(asignacionRolService);
        c.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Roles con vigencia :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        java.util.List<Usuario> usuarios;
        java.util.List<ar.com.hmu.model.Servicio> servicios;
        try {
            usuarios = usuarioRepository.readAll();
            servicios = servicioService.readAll();
        } catch (Exception e) {
            usuarios = java.util.List.of();
            servicios = java.util.List.of();
        }
        c.postInitialize(usuarios, servicios);
        return java.util.Map.entry(stage, c);
    }

    /** Importación del padrón (CU05), sin mostrar (también la usa el modo de capturas). */
    public java.util.Map.Entry<Stage, ImportarPadronController> crearPantallaImportarPadron() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/importarPadron.fxml"));
        Parent root = loader.load();
        ImportarPadronController c = loader.getController();
        c.setService(importacionPadronService);
        c.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Importar listado de agentes :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        c.postInitialize();
        return java.util.Map.entry(stage, c);
    }

    private void abrirBancoHoras() {
        try {
            crearPantallaBancoHoras().getKey().show();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir el banco de horas: " + e.getMessage());
        }
    }

    /** Banco de horas del agente, sin mostrar (también lo usa el modo de capturas). */
    public java.util.Map.Entry<Stage, BancoHorasController> crearPantallaBancoHoras() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/bancoHoras.fxml"));
        Parent root = loader.load();
        BancoHorasController c = loader.getController();
        c.setService(novedadService);
        c.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Banco de horas :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        c.postInitialize();
        return java.util.Map.entry(stage, c);
    }

    private void abrirIndicadores() {
        if (indicadoresService == null
                || !usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION)) {
            AlertUtils.showInfo("Los indicadores son para la Oficina de Personal y la Dirección.");
            return;
        }
        try {
            java.time.LocalDate hoy = Reloj.hoy();
            crearPantallaIndicadores(hoy.withDayOfMonth(1), hoy.withDayOfMonth(hoy.lengthOfMonth())).getKey().show();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir los indicadores: " + e.getMessage());
        }
    }

    /** Pantalla de indicadores (CU36), sin mostrar (también la usa el modo de capturas). */
    public java.util.Map.Entry<Stage, IndicadoresController> crearPantallaIndicadores(java.time.LocalDate desde,
                                                                                     java.time.LocalDate hasta) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/indicadores.fxml"));
        Parent root = loader.load();
        IndicadoresController c = loader.getController();
        c.setService(indicadoresService);
        c.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Indicadores :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        java.util.List<ar.com.hmu.model.Servicio> servicios;
        try {
            servicios = servicioService.readAll();
        } catch (ServiceException e) {
            servicios = java.util.List.of();
        }
        c.postInitialize(servicios, desde, hasta);
        return java.util.Map.entry(stage, c);
    }

    private void abrirParteDiario() {
        if (parteDiarioService == null || !usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            AlertUtils.showInfo("El parte diario lo gestiona la Oficina de Personal.");
            return;
        }
        try {
            crearPantallaParte(Reloj.hoy().minusDays(1)).getKey().show();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir el parte diario: " + e.getMessage());
        }
    }

    /** Pantalla del parte diario (CU30–CU35), sin mostrar (también la usa el modo de capturas). */
    public java.util.Map.Entry<Stage, ParteDiarioController> crearPantallaParte(java.time.LocalDate fecha) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/parteDiario.fxml"));
        Parent root = loader.load();
        ParteDiarioController c = loader.getController();
        c.setService(parteDiarioService);
        c.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Parte diario :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        c.postInitialize(fecha);
        return java.util.Map.entry(stage, c);
    }

    private void abrirReglas() {
        if (reglaValidacionService == null) {
            AlertUtils.showWarn("Módulo de reglas no disponible.");
            return;
        }
        try {
            crearPantallaReglas().getKey().show();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir las reglas de validación: " + e.getMessage());
        }
    }

    /** Arma la ventana de reglas sin mostrarla (también la usa el modo de capturas). */
    public java.util.Map.Entry<Stage, ReglasValidacionController> crearPantallaReglas() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/reglasValidacion.fxml"));
        Parent root = loader.load();
        ReglasValidacionController c = loader.getController();
        c.setService(reglaValidacionService);
        c.setUsuarioActual(usuarioActual);
        Stage stage = new Stage();
        stage.setTitle("Reglas de validación :: " + AppInfo.PRG_LONG_TITLE);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.setStage(stage);
        c.postInitialize(Reloj.hoy());
        return java.util.Map.entry(stage, c);
    }

    private void abrirAvisos() {
        if (notificacionService == null) {
            return;
        }
        Stage stage = AvisosDialog.crear(notificacionService, usuarioActual, primaryStage);
        stage.setOnHidden(e -> {
            try {
                notificacionService.marcarLeidas(usuarioActual);
            } catch (java.sql.SQLException ignored) {
                // Quedan como no leídos.
            }
            refrescarContadorNovedades();
        });
        stage.show();
    }

    /** Badge del mosaico de novedades y del botón de avisos. */
    public void refrescarContadorNovedades() {
        if (notificacionService == null || usuarioActual == null) {
            return;
        }
        int avisos = notificacionService.contarNoLeidas(usuarioActual);
        avisosButton.setText(avisos > 0 ? "Avisos (" + avisos + ")" : "Avisos");
        int pendientes = 0;
        if (novedadService != null) {
            for (var p : ar.com.hmu.service.novedades.PerfilNovedades.de(usuarioActual)) {
                if (p == ar.com.hmu.service.novedades.PerfilNovedades.AGENTE) continue;
                try {
                    pendientes += (int) novedadService.bandeja(usuarioActual, p).stream()
                            .filter(n -> novedadService.puedeResolver(n, usuarioActual, p)).count();
                } catch (ServiceException ignored) {
                    // Sin badge.
                }
            }
        }
        novedadesCountLabel.setText(pendientes > 0 ? "(" + pendientes + " por resolver)" : "");
    }

    /**
     * RFS09 — recordatorio de presentación de diagramas, chequeado al login:
     * a la jefatura le avisa si el diagrama del MES SIGUIENTE de su servicio
     * no está presentado (sin crear / borrador sin enviar / observado sin
     * corregir); a la Oficina de Personal le muestra el resumen de servicios
     * incumplidos (sólo servicios con empleados activos).
     *
     * <p>Pase 1: alerta in-app al login, sin umbral de día (la fecha límite
     * real de presentación del HMU no está modelada — cuando se conozca,
     * agregar umbral/configuración). Alertas por email requerirían un
     * proceso servidor (la app es de escritorio) — deuda documentada.</p>
     */
    private void verificarAlertasDiagramacion() {
        if (diagramaService == null || usuarioActual == null) {
            return;
        }
        java.time.YearMonth mesSiguiente = java.time.YearMonth.from(Reloj.hoy()).plusMonths(1);
        String nombreMes = mesSiguiente.format(java.time.format.DateTimeFormatter
                .ofPattern("MMMM yyyy", java.util.Locale.of("es", "AR")));
        StringBuilder aviso = new StringBuilder();

        try {
            if (usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO)
                    && usuarioActual.getServicioId() != null) {
                switch (diagramaService.estadoPresentacion(usuarioActual.getServicioId(), mesSiguiente)) {
                    case SIN_DIAGRAMA -> aviso.append("Recordatorio: aún no se creó el diagrama de ")
                            .append(nombreMes).append(" de tu servicio.");
                    case BORRADOR_SIN_ENVIAR -> aviso.append("Recordatorio: el diagrama de ")
                            .append(nombreMes).append(" de tu servicio está en borrador. ")
                            .append("Recordá enviarlo a aprobación.");
                    case OBSERVADO_SIN_CORREGIR -> aviso.append("El diagrama de ").append(nombreMes)
                            .append(" de tu servicio fue observado por la Oficina de Personal. ")
                            .append("Corregilo y reenvialo.");
                    case PRESENTADO -> { /* sin alerta */ }
                }
            }

            if (usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
                var alertas = diagramaService.serviciosSinPresentar(mesSiguiente);
                if (!alertas.isEmpty()) {
                    if (aviso.length() > 0) {
                        aviso.append("\n\n");
                    }
                    aviso.append("Servicios que aún no presentaron el diagrama de ")
                            .append(nombreMes).append(" (").append(alertas.size()).append("):\n");
                    aviso.append(alertas.stream()
                            .map(a -> a.servicio().getNombre())
                            .collect(java.util.stream.Collectors.joining(", ")));
                }
            }
        } catch (ServiceException e) {
            // Las alertas son un recordatorio: nunca deben romper el login.
            System.err.println("Alertas de diagramación no disponibles: " + e.getMessage());
            return;
        }

        enviarRecordatoriosPorCorreo(mesSiguiente, nombreMes);

        if (aviso.length() > 0) {
            AlertUtils.showWarn(aviso.toString());
        }
    }

    /**
     * CU19 (RF14): recordatorio por correo a las jefaturas que no presentaron el
     * diagrama del mes siguiente, con tope de uno por día por jefatura. Lo
     * dispara el ingreso de la propia JS o el de la OP (la aplicación es de
     * escritorio: no hay un proceso servidor que lo programe).
     */
    private void enviarRecordatoriosPorCorreo(java.time.YearMonth mes, String nombreMes) {
        if (notificacionService == null || diagramaService == null) return;
        Usuario actual = usuarioActual;
        Thread t = new Thread(() -> {
            try {
                java.util.Set<java.util.UUID> destinatarios = new java.util.LinkedHashSet<>();
                if (actual.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
                    for (var a : diagramaService.serviciosSinPresentar(mes)) {
                        if (a.servicio().getEncargadoUsuarioId() != null) {
                            destinatarios.add(a.servicio().getEncargadoUsuarioId());
                        }
                    }
                } else if (actual.hasRole(TipoUsuario.JEFATURADESERVICIO) && actual.getServicioId() != null
                        && diagramaService.estadoPresentacion(actual.getServicioId(), mes)
                        != ar.com.hmu.service.diagramacion.EstadoPresentacion.PRESENTADO) {
                    destinatarios.add(actual.getId());
                }
                for (java.util.UUID js : destinatarios) {
                    notificacionService.recordarUnaVezPorDia(js, "RECORDATORIO_DIAGRAMA",
                            "Recordatorio: diagrama de " + nombreMes + " sin presentar",
                            "El diagrama de " + nombreMes + " de su servicio todavía no fue presentado a la "
                                    + "Oficina de Personal. Ingrese a «Diagramación de mi Servicio» para completarlo "
                                    + "y enviarlo a aprobación.", "Servicio", null);
                }
            } catch (Exception e) {
                System.err.println("Recordatorios de diagramación no enviados: " + e.getMessage());
            }
        }, "recordatorios-diagrama");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Método que detiene el verificador de estado del servidor.
     * La idea, es llamar a este método cuando se vaya a eliminar el controlador
     */
    public void cleanup() {
        if (serverStatusUtils != null) {
            serverStatusUtils.stop();
        }
    }

    /**
     * Método que configura el tamaño de la ventana al momento de cerrarse.
     */
    public void saveWindowPreferences() {
        preferencesManager.put(WINDOW_WIDTH_KEY, String.valueOf(primaryStage.getWidth()));
        preferencesManager.put(WINDOW_HEIGHT_KEY, String.valueOf(primaryStage.getHeight()));
        preferencesManager.put(WINDOW_X_KEY, String.valueOf(primaryStage.getX()));
        preferencesManager.put(WINDOW_Y_KEY, String.valueOf(primaryStage.getY()));
    }


    /**
     * Método que carga las preferencias de la ventana (dimensiones y posición).
     */
    private void loadWindowPreferences() {
        try {
            double width = Double.parseDouble(preferencesManager.get(WINDOW_WIDTH_KEY, "800"));
            double height = Double.parseDouble(preferencesManager.get(WINDOW_HEIGHT_KEY, "600"));
            double x = Double.parseDouble(preferencesManager.get(WINDOW_X_KEY, "100"));
            double y = Double.parseDouble(preferencesManager.get(WINDOW_Y_KEY, "100"));

            primaryStage.setWidth(width);
            primaryStage.setHeight(height);
            primaryStage.setX(x);
            primaryStage.setY(y);
        } catch (NumberFormatException e) {
            e.printStackTrace(); // Error en la carga de preferencias, se usan valores predeterminados
            // Valores predeterminados ya están establecidos en el método get de PreferencesManager
        }
    }


    /**
     * Configura la visibilidad de elementos según los roles del usuario
     */
    private void configurarVisibilidadMenus() {
        // Inicializar todos los menús como no visibles
        empleadoMenu.setVisible(false);
        jefaturaDeServicioMenu.setVisible(false);
        oficinaDePersonalMenu.setVisible(false);
        direccionMenu.setVisible(false);

        // Configurar visibilidad de menús según roles
        empleadoMenu.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        jefaturaDeServicioMenu.setVisible(usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO));
        oficinaDePersonalMenu.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));
        direccionMenu.setVisible(usuarioActual.hasRole(TipoUsuario.DIRECCION));

        // Configurar visibilidad de mosaicos (VBox) según roles
        aprobacionSolicitudesVBox.setVisible(usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO, TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        aprobacionSolicitudesVBox.setManaged(usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO, TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        notasMemosVBox.setVisible(usuarioActual.hasAnyRoleData());
        notasMemosVBox.setManaged(usuarioActual.hasAnyRoleData());
        // Cargar contador de no leídos al entrar al menú principal.
        refrescarContadorMemos();
        partesDiariosVBox.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));
        partesDiariosVBox.setManaged(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));
        consultaDiagramasDeServicioVBox.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        consultaDiagramasDeServicioVBox.setManaged(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        diagramacionDeServicioVBox.setVisible(usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO));
        diagramacionDeServicioVBox.setManaged(usuarioActual.hasRole(TipoUsuario.JEFATURADESERVICIO));
        miDiagramaVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        miDiagramaVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        controlMarcacionesVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        controlMarcacionesVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        pasesDeSalidaVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        pasesDeSalidaVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        omisionesIngresEgresoVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        omisionesIngresEgresoVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        faltasJustificadasVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        faltasJustificadasVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        faltasInjustificadasVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        faltasInjustificadasVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        faltasRazonFuerzaMayorVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        faltasRazonFuerzaMayorVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        solicitudHorasExtraFCVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        solicitudHorasExtraFCVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        francosCompensatoriosVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        francosCompensatoriosVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        reportesVBox.setVisible(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        reportesVBox.setManaged(usuarioActual.hasRole(TipoUsuario.EMPLEADO));
        listadoDeEmpleadosVBox.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        listadoDeEmpleadosVBox.setManaged(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        listadoDeServiciosVBox.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        listadoDeServiciosVBox.setManaged(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION));
        abmEmpleadosVBox.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));
        abmEmpleadosVBox.setManaged(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));
        abmServiciosVBox.setVisible(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));
        abmServiciosVBox.setManaged(usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL));

        if (AppInfo.perfilTfg()) {
            aplicarPerfilTfg();
        }
    }

    /**
     * Perfil del TFG ({@link AppInfo#perfilTfg()}): oculta memorándums (fuera del
     * alcance del E4) y los módulos heredados que ahora son tipos de novedad (el
     * agente los solicita desde "Novedades"); el "Módulo de Reportes" queda como
     * acceso a los indicadores (CU36) de OP y DH.
     */
    private void aplicarPerfilTfg() {
        for (VBox v : new VBox[] { notasMemosVBox, pasesDeSalidaVBox, faltasJustificadasVBox,
                faltasInjustificadasVBox, faltasRazonFuerzaMayorVBox, solicitudHorasExtraFCVBox,
                listadoDeEmpleadosVBox, omisionesIngresEgresoVBox }) {
            v.setVisible(false);
            v.setManaged(false);
        }
        for (MenuItem m : new MenuItem[] { notasMemosMenuItem, pasesSalidaMenuItem, omisionMenuItem, inasistenciasMenu,
                francosMenu, reportesJSMenuItem, listadoMisAgentesMenuItem, listadoEmpleadosMenuItem,
                listadoAgentesDHMenuItem }) {
            m.setVisible(false);
        }
        // Sin los mosaicos heredados, el agente solicita sus novedades desde "Novedades".
        aprobacionSolicitudesVBox.setVisible(usuarioActual.hasAnyRoleData());
        aprobacionSolicitudesVBox.setManaged(usuarioActual.hasAnyRoleData());
        // El mosaico de francos compensatorios pasa a ser el banco de horas del agente.
        francosCompensatoriosVBox.setVisible(usuarioActual.hasAnyRoleData());
        francosCompensatoriosVBox.setManaged(usuarioActual.hasAnyRoleData());
        francosText.setText("Banco de horas");
        francosCompensatoriosVBox.setOnMouseClicked(e -> abrirBancoHoras());
        boolean indicadores = usuarioActual.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION);
        reportesVBox.setVisible(indicadores);
        reportesVBox.setManaged(indicadores);
        reportesText.setText("Indicadores");
        reportesOPMenuItem.setText("Indicadores…");
        reportesDHMenuItem.setText("Indicadores…");
        reportesOPMenuItem.setOnAction(e -> abrirIndicadores());
        reportesDHMenuItem.setOnAction(e -> abrirIndicadores());
    }


    /**
     * Configura los manejadores de eventos para los componentes del menú y mosaicos.
     */
    private void setupEventHandlers() {
        // Configura la funcionalidad del menú: Archivo -> Modificar contraseña
        changePasswordMenuItem.setOnAction(event -> handleChangePassword());

        // Configura la funcionalidad del menú: Archivo -> Modificar imagen de perfil
        changeProfileImageMenuItem.setOnAction(event -> showModuleUnderConstructionAlert());

        // Configura la funcionalidad del menú: Archivo -> Cerrar sesión
        logoutMenuItem.setOnAction(event -> handleLogout((Stage) menuBar.getScene().getWindow()));

        // Configura la funcionalidad del menú: Archivo -> Salir
        exitMenuItem.setOnAction(event -> System.exit(0));

        // Configura la funcionalidad del menú: Oficina de Personal -> Listado de Empleados
        listadoEmpleadosMenuItem.setOnAction(this::handleListadoDeEmpleados);

        // Configura la funcionalidad del menú: Oficina de Personal -> Listado de Servicios
        listadoServiciosMenuItem.setOnAction(this::handleListadoDeServicios);

        // Configura la funcionalidad del menú: Oficina de Personal -> ABM de Empleados
        abmEmpleadosMenuItem.setOnAction(this::handleAbmEmpleados);

        // Configura la funcionalidad del menú: Oficina de Personal -> ABM de Servicios
        abmServiciosMenuItem.setOnAction(this::handleAbmServicios);

        // Configura la funcionalidad del menú: Oficina de Personal -> ABM de Cargos
        abmCargosMenuItem.setOnAction(this::handleAbmCargos);

        // Menú: Oficina de Personal -> Feriados… / Dirección -> Feriados…
        feriadosOPMenuItem.setOnAction(this::handleFeriados);
        feriadosDireccionMenuItem.setOnAction(this::handleFeriados);

        // Menú: Oficina de Personal / Dirección -> Reglas de validación… (CU08)
        reglasOPMenuItem.setOnAction(e -> abrirReglas());
        reglasDireccionMenuItem.setOnAction(e -> abrirReglas());
        // Menú: Oficina de Personal -> Importar listado de agentes… (CU05)
        importarPadronMenuItem.setOnAction(e -> {
            try {
                crearPantallaImportarPadron().getKey().show();
            } catch (IOException ex) {
                AlertUtils.showErr("Error al abrir la importación del padrón: " + ex.getMessage());
            }
        });
        // Menú: Oficina de Personal -> Roles con vigencia… (CU06)
        rolesMenuItem.setOnAction(e -> {
            try {
                crearPantallaRoles().getKey().show();
            } catch (IOException ex) {
                AlertUtils.showErr("Error al abrir los roles: " + ex.getMessage());
            }
        });
        // Menú: Oficina de Personal -> Modalidad horaria de agentes… (CU04)
        modalidadOPMenuItem.setOnAction(e -> {
            try {
                crearPantallaModalidad().getKey().show();
            } catch (IOException ex) {
                AlertUtils.showErr("Error al abrir la modalidad horaria: " + ex.getMessage());
            }
        });

        // Configura la funcionalidad del menú: Ayuda -> Licencias de uso
        licenciasDeUsoMenuItem.setOnAction(event -> handleLicenciasDeUso());

        // Configura la funcionalidad del menú: Ayuda -> Acerca de
        acercaDeMenuItem.setText("Acerca de " + AppInfo.nombreAcercaDe());
        acercaDeMenuItem.setOnAction(event -> handleAcercaDe());

        // Configura los mosaicos para mostrar una alerta de "Módulo en construcción"
        aprobacionSolicitudesVBox.setOnMouseClicked(event -> abrirNovedades(null));
        notasMemosVBox.setOnMouseClicked(this::handleNotasMemos);
        partesDiariosVBox.setOnMouseClicked(event -> abrirParteDiario());
        consultaDiagramasDeServicioVBox.setOnMouseClicked(this::handleConsultaDiagramas);
        diagramacionDeServicioVBox.setOnMouseClicked(this::handleDiagramacionServicio);
        miDiagramaVBox.setOnMouseClicked(this::handleMiDiagrama);
        controlMarcacionesVBox.setOnMouseClicked(event -> showModuleUnderConstructionAlert());
        pasesDeSalidaVBox.setOnMouseClicked(event -> abrirNovedades("PSP"));
        omisionesIngresEgresoVBox.setOnMouseClicked(event -> abrirNovedades("OM"));
        faltasJustificadasVBox.setOnMouseClicked(event -> abrirNovedades("FJ"));
        faltasInjustificadasVBox.setOnMouseClicked(event -> abrirNovedades(""));
        faltasRazonFuerzaMayorVBox.setOnMouseClicked(event -> abrirNovedades("FJ"));
        solicitudHorasExtraFCVBox.setOnMouseClicked(event -> abrirNovedades("HE"));
        francosCompensatoriosVBox.setOnMouseClicked(event -> abrirNovedades("FC"));
        reportesVBox.setOnMouseClicked(event -> abrirIndicadores());
        listadoDeEmpleadosVBox.setOnMouseClicked(this::handleListadoDeEmpleados);
        listadoDeServiciosVBox.setOnMouseClicked(this::handleListadoDeServicios);
        abmEmpleadosVBox.setOnMouseClicked(this::handleAbmEmpleados);
        abmServiciosVBox.setOnMouseClicked(this::handleAbmServicios);

        // Configura la funcionalidad del botón "Cerrar sesión"
        logoutButton.setOnAction(event -> handleLogoutAction());

        // Avisos en la aplicación (CU27)
        avisosButton.setOnAction(event -> abrirAvisos());

    }


    private void handleLogoutAction() {
        ControlInactividad.detener();
        //Guardar propiedades de ventana
        saveWindowPreferences();

        // Limpieza de recursos
        cleanup();

        // Cerrar la sesión
        handleLogout((Stage) logoutButton.getScene().getWindow());
    }


    /**
     * Maneja la opción "Modificar Contraseña" abriendo una nueva ventana para cambiar la contraseña.
     */
    @FXML
    private void handleChangePassword() {
        PasswordDialogUtils.showChangePasswordDialog(usuarioActual, usuarioService,
                (message) -> {}, // Callback vacío para continuar después del cambio de contraseña exitoso
                () -> SessionUtils.handleLogout((Stage) logoutButton.getScene().getWindow()) // Callback para cerrar la sesión si se cancela
        );

    }

    /**
     * Muestra una alerta indicando que el módulo está en construcción.
     */
    @FXML
    private void showModuleUnderConstructionAlert() {
        AlertUtils.showInfo("Estamos trabajando para Usted\nMódulo en construcción");
    }


    /**
     * Método para el alta, baja, modificación de usuarios (agentes).
     * Este método carga la ventana de ABM de usuarios y la muestra al usuario.
     */
    @FXML
    private void handleAbmEmpleados(Event event) {
        try {
            // Usa los servicios ya inicializados
            UsuarioService usuarioService = this.usuarioService;
            CargoService cargoService = this.cargoService;
            ServicioService servicioService = this.servicioService;
            DomicilioService domicilioService = this.domicilioService;
            RoleService roleService = this.roleService;

            // Configurar la fábrica de controladores
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/abmUsuario.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == AbmUsuarioController.class) {
                    AbmUsuarioController controller = new AbmUsuarioController();
                    controller.setServices(usuarioService, cargoService, servicioService, domicilioService, roleService);
                    controller.setAbrirRoles(cuil -> {
                        try {
                            var roles = crearPantallaRoles();
                            if (cuil != 0) roles.getValue().seleccionar(cuil);
                            roles.getKey().show();
                        } catch (IOException ex) {
                            AlertUtils.showErr("Error al abrir los roles: " + ex.getMessage());
                        }
                    });
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
            Parent abmUsuariosRoot = loader.load();

            // Crear una nueva escena y un nuevo Stage (ventana)
            Stage stage = new Stage();  // Alternativa, que es lo mismo:  stage.setScene(new Scene(root))
            Scene scene = new Scene(abmUsuariosRoot);
            stage.setScene(scene);
            stage.setTitle("Alta, Baja y Modificación de Agentes" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.initModality(Modality.WINDOW_MODAL);
            //stage.initOwner(((Node) event.getSource()).getScene().getWindow()); // Con esto, establecemos la ventana actual como propietaria, evitando múltiples instancias. Supuestamente, es mejor que lo que hacía antes:  stage.initOwner(abmEmpleadosVBox.getScene().getWindow());
            //stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.initOwner(abmEmpleadosVBox.getScene().getWindow());
            // Mostrar la nueva ventana
            stage.show();  // También se sugiere el uso de:  stage.showAndWait(), lo que tal vez por ahí estaría bueno en conjunto con:  stage.initModality(Modality.WINDOW_MODAL)
        } catch (IOException e) {
            // Mostrar un error si no se puede cargar la vista
            AlertUtils.showErr("Error al cargar la pantalla de ABM de Usuarios: " + e.getMessage());
            e.printStackTrace(); // Imprimir la excepción para más detalles en la consola
        }
    }


    @FXML
    private void handleAbmCargos(Event event) {
        // Usa los servicios ya inicializados
        CargoService cargoService = this.cargoService;

        try {
            // Configurar la fábrica de controladores
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/abmCargo.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == AbmCargoController.class) {
                    AbmCargoController controller = new AbmCargoController();
                    controller.setServices(cargoService);
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
            stage.setTitle("Gestión de Cargos" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            //stage.initOwner(((Node) event.getSource()).getScene().getWindow());
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al cargar la ventana de gestión de cargos: " + e.getMessage());
        }
    }

    @FXML
    private void handleListadoDeEmpleados(Event event) {
        try {
            // Usa los servicios ya inicializados
            UsuarioService usuarioService = this.usuarioService;
            CargoService cargoService = this.cargoService;
            ServicioService servicioService = this.servicioService;
            DomicilioService domicilioService = this.domicilioService;
            RoleService roleService = this.roleService;

            // Configurar la fábrica de controladores
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/listaUsuarios.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == ListaUsuariosController.class) {
                    ListaUsuariosController controller = new ListaUsuariosController();
                    controller.setServices(usuarioService, cargoService, servicioService, domicilioService, roleService);
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

            ListaUsuariosController controller = loader.getController();
            controller.setServices(usuarioService, cargoService, servicioService, domicilioService, roleService);

            Stage stage = new Stage();
            stage.setTitle("Listado de Empleados" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setWidth(590.0);
            //stage.initOwner(((Node) event.getSource()).getScene().getWindow());
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            // Manejo de errores
        }
    }

    @FXML
    private void handleListadoDeServicios(Event event) {
        try {
            // Usa los servicios ya inicializados
            UsuarioService usuarioService = this.usuarioService;
            CargoService cargoService = this.cargoService;
            ServicioService servicioService = this.servicioService;
            DomicilioService domicilioService = this.domicilioService;
            RoleService roleService = this.roleService;

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/listaServicios.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == ListaServiciosController.class) {
                    ListaServiciosController controller = new ListaServiciosController();
                    controller.setServices(usuarioService, cargoService, servicioService, domicilioService, roleService);
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

            ListaServiciosController controller = loader.getController();
            controller.setServicioService(servicioService);

            Stage stage = new Stage();
            stage.setTitle("Lista de Servicios" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setWidth(500.0);
            //stage.initOwner(((Node) event.getSource()).getScene().getWindow());
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAbmServicios(Event event) {
        // Usa los servicios ya inicializados
        ServicioService servicioService = this.servicioService;

        try {
            // Configurar la fábrica de controladores
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/abmServicio.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == AbmServicioController.class) {
                    AbmServicioController controller = new AbmServicioController();
                    controller.setServices(servicioService);
                    controller.setUsuarioActual(usuarioActual);
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
            //stage.initOwner(((Node) event.getSource()).getScene().getWindow());
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al cargar la ventana de gestión de servicios: " + e.getMessage());
        }
    }


    /**
     * Abre la bandeja de memorándums (Recibidos / Enviados / Pendientes
     * de autorizar). Inyecta los services y el usuario actual al
     * controller. Tras cerrar la ventana, refresca el contador del
     * mosaico por si cambió el conteo de no leídos.
     */
    @FXML
    private void handleNotasMemos(Event event) {
        if (memorandumService == null) {
            AlertUtils.showWarn("Módulo de memorándums no disponible.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/bandejaMemorandums.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == BandejaMemorandumsController.class) {
                    BandejaMemorandumsController c = new BandejaMemorandumsController();
                    c.setMemorandumService(memorandumService);
                    c.setUsuarioRepository(usuarioRepository);
                    c.setEstadoTramiteRepository(estadoTramiteRepository);
                    c.setUsuarioActual(usuarioActual);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            BandejaMemorandumsController controller = loader.getController();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle("Memorándums :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();

            refrescarContadorMemos();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir los memorándums: " + e.getMessage());
        }
    }

    /**
     * Abre la pantalla de diagramación de servicio (RFS02): grilla
     * empleado × día del diagrama mensual, con edición, validación y
     * envío a aprobación. Inyecta los services y el usuario actual.
     */
    @FXML
    private void handleDiagramacionServicio(Event event) {
        if (diagramaService == null) {
            AlertUtils.showWarn("Módulo de diagramación no disponible.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/diagramacionServicio.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == DiagramacionServicioController.class) {
                    DiagramacionServicioController c = new DiagramacionServicioController();
                    c.setDiagramaService(diagramaService);
                    c.setServicioService(servicioService);
                    c.setUsuarioActual(usuarioActual);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            DiagramacionServicioController controller = loader.getController();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle("Diagramación de servicio :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir la diagramación de servicio: " + e.getMessage());
        }
    }

    /**
     * Abre "Mi Diagrama" (empleado): la pantalla de diagramación en modo
     * sólo consulta — únicamente diagramas APROBADOS de su servicio, y la
     * grilla completa o sólo su fila según el permiso que administre su
     * jefatura (veDiagramaCompleto).
     */
    @FXML
    private void handleMiDiagrama(Event event) {
        if (diagramaService == null) {
            AlertUtils.showWarn("Módulo de diagramación no disponible.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/diagramacionServicio.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == DiagramacionServicioController.class) {
                    DiagramacionServicioController c = new DiagramacionServicioController();
                    c.setDiagramaService(diagramaService);
                    c.setServicioService(servicioService);
                    c.setUsuarioActual(usuarioActual);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            DiagramacionServicioController controller = loader.getController();
            controller.activarModoEmpleado();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle("Mi Diagrama :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir Mi Diagrama: " + e.getMessage());
        }
    }

    /**
     * Abre la pantalla de Feriados: OP hace la carga anual y propone
     * cambios; la Dirección autoriza o rechaza las propuestas pendientes.
     */
    @FXML
    private void handleFeriados(Event event) {
        if (feriadoService == null) {
            AlertUtils.showWarn("Módulo de feriados no disponible.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/feriados.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == FeriadosController.class) {
                    FeriadosController c = new FeriadosController();
                    c.setFeriadoService(feriadoService);
                    c.setUsuarioActual(usuarioActual);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            FeriadosController controller = loader.getController();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle("Feriados :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir los feriados: " + e.getMessage());
        }
    }

    /**
     * Abre la bandeja "Consulta de Diagramas de Servicios" (OP/Dirección):
     * lista con filtros por estado y servicio; desde ahí se abre cada
     * diagrama y OP dispone de Aprobar / Observar.
     */
    @FXML
    private void handleConsultaDiagramas(Event event) {
        if (diagramaService == null) {
            AlertUtils.showWarn("Módulo de diagramación no disponible.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/bandejaDiagramas.fxml"));
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == BandejaDiagramasController.class) {
                    BandejaDiagramasController c = new BandejaDiagramasController();
                    c.setDiagramaService(diagramaService);
                    c.setServicioService(servicioService);
                    c.setUsuarioRepository(usuarioRepository);
                    c.setUsuarioActual(usuarioActual);
                    return c;
                }
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            BandejaDiagramasController controller = loader.getController();
            controller.postInitialize();

            Stage stage = new Stage();
            stage.setTitle("Consulta de Diagramas :: " + AppInfo.PRG_LONG_TITLE);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(MainMenuMosaicoController.getPrimaryStage());
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
            stage.showAndWait();
        } catch (IOException e) {
            AlertUtils.showErr("Error al abrir la consulta de diagramas: " + e.getMessage());
        }
    }

    /**
     * Actualiza el badge "Memos (N)" en el mosaico al login y al cerrar
     * la bandeja. Si no hay no leídos, deja el label vacío.
     */
    public void refrescarContadorMemos() {
        if (memorandumService == null || usuarioActual == null || memosCountLabel == null) {
            return;
        }
        try {
            int n = memorandumService.contarNoLeidos(usuarioActual);
            memosCountLabel.setText(n > 0 ? "(" + n + " sin leer)" : "");
        } catch (ServiceException e) {
            memosCountLabel.setText("");
        }
    }

    /**
     * Muestra la ventana de "Licencias de uso".
     */
    @FXML
    private void handleLicenciasDeUso() {
        try {
            // Cargar el FXML de la ventana "Licencias de uso"
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/licenciasDeUso.fxml"));
            Parent root = loader.load();

            // Crear una nueva ventana (Stage)
            Stage stage = new Stage();
            stage.setTitle("Licencias de uso" + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.initModality(Modality.APPLICATION_MODAL); // Bloquear la ventana principal hasta que se cierre esta
            stage.setResizable(false);
            // Establecer el ícono de la aplicación
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));

            Scene scene = new Scene(root);
            stage.setScene(scene);

            // Mostrar la ventana
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            AlertUtils.showErr("Error al cargar la ventana 'Licencias de uso':\n" + e.getMessage());
        }
    }

    /**
     * Muestra la ventana de "Acerca de...".
     */
    @FXML
    private void handleAcercaDe() {
        try {
            // Cargar el FXML de la ventana "Acerca de"
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/about.fxml"));
            Parent root = loader.load();

            // Crear una nueva ventana (Stage)
            Stage stage = new Stage();
            stage.setTitle("Acerca de " + AppInfo.nombreAcercaDe() + " :: " + AppInfo.PRG_LONG_TITLE);
            stage.initModality(Modality.APPLICATION_MODAL); // Bloquear la ventana principal hasta que se cierre esta
            stage.setResizable(false);
            // Establecer el ícono de la aplicación
            stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));

            Scene scene = new Scene(root);
            stage.setScene(scene);

            // Mostrar la ventana
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            AlertUtils.showErr("Error al cargar la ventana 'Acerca de':\n" + e.getMessage());
        }
    }

}
