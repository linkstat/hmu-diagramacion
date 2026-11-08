package ar.com.hmu.ui;

import ar.com.hmu.auth.LoginService;
import ar.com.hmu.config.AppConfigReader;
import ar.com.hmu.controller.LoginController;
import ar.com.hmu.controller.MainMenuMosaicoController;
import ar.com.hmu.factory.UsuarioFactory;
import ar.com.hmu.repository.CargoRepository;
import ar.com.hmu.repository.DatabaseConnector;
import ar.com.hmu.repository.DiagramaRepository;
import ar.com.hmu.repository.DomicilioRepository;
import ar.com.hmu.repository.EstadoTramiteRepository;
import ar.com.hmu.repository.FeriadoRepository;
import ar.com.hmu.repository.HorarioRepository;
import ar.com.hmu.repository.MemorandumRepository;
import ar.com.hmu.repository.NotificacionRepository;
import ar.com.hmu.repository.NovedadRepository;
import ar.com.hmu.repository.ReglaValidacionRepository;
import ar.com.hmu.repository.RoleRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.CargoService;
import ar.com.hmu.service.DomicilioService;
import ar.com.hmu.service.FeriadoService;
import ar.com.hmu.service.MemorandumService;
import ar.com.hmu.service.ReglaValidacionService;
import ar.com.hmu.service.RoleService;
import ar.com.hmu.service.ServicioService;
import ar.com.hmu.service.UsuarioService;
import ar.com.hmu.service.diagramacion.DiagramaService;
import ar.com.hmu.service.notification.EmailNotificationService;
import ar.com.hmu.service.notification.NotificacionService;
import ar.com.hmu.service.notification.NotificationService;
import ar.com.hmu.service.novedades.NovedadService;

/**
 * Armado de repositorios y servicios de la aplicación (antes repetido en el
 * arranque y en el re-login tras cerrar sesión). Cada inicio de sesión usa
 * una instancia nueva.
 */
public final class ContextoAplicacion {

    public final AppConfigReader appConfigReader;
    public final DatabaseConnector databaseConnector;
    public final RoleService roleService;
    public final UsuarioRepository usuarioRepository;
    public final ServicioRepository servicioRepository;
    public final EstadoTramiteRepository estadoTramiteRepository;
    public final UsuarioService usuarioService;
    public final LoginService loginService;
    public final ServicioService servicioService;
    public final CargoService cargoService;
    public final DomicilioService domicilioService;
    public final NotificationService notificationService;
    public final MemorandumService memorandumService;
    public final DiagramaService diagramaService;
    public final FeriadoService feriadoService;
    public final NotificacionService notificacionService;
    public final NovedadService novedadService;
    public final ReglaValidacionService reglaValidacionService;
    public final ar.com.hmu.service.diagramacion.ModalidadHorariaService modalidadHorariaService;
    public final ar.com.hmu.service.asistencia.ParteDiarioService parteDiarioService;
    public final ar.com.hmu.service.asistencia.IndicadoresService indicadoresService;
    public final ar.com.hmu.service.ImportacionPadronService importacionPadronService;
    public final ar.com.hmu.service.AsignacionRolService asignacionRolService;

    private ContextoAplicacion(NotificationService correo) {
        appConfigReader = new AppConfigReader();
        databaseConnector = new DatabaseConnector(appConfigReader);

        RoleRepository roleRepository = new RoleRepository(databaseConnector);
        servicioRepository = new ServicioRepository(databaseConnector);
        CargoRepository cargoRepository = new CargoRepository(databaseConnector);
        DomicilioRepository domicilioRepository = new DomicilioRepository(databaseConnector);
        MemorandumRepository memorandumRepository = new MemorandumRepository(databaseConnector);
        estadoTramiteRepository = new EstadoTramiteRepository(databaseConnector);
        DiagramaRepository diagramaRepository = new DiagramaRepository(databaseConnector);
        HorarioRepository horarioRepository = new HorarioRepository(databaseConnector);
        FeriadoRepository feriadoRepository = new FeriadoRepository(databaseConnector);

        roleService = new RoleService(roleRepository);
        usuarioRepository = new UsuarioRepository(databaseConnector, new UsuarioFactory(roleService));
        usuarioService = new UsuarioService(usuarioRepository, servicioRepository, cargoRepository,
                domicilioRepository, roleService);
        loginService = new LoginService(usuarioService);
        servicioService = new ServicioService(servicioRepository, usuarioRepository);
        cargoService = new CargoService(cargoRepository);
        domicilioService = new DomicilioService(domicilioRepository);

        // Correo: si la sección smtp del config.yaml está vacía, es no-op.
        notificationService = correo != null ? correo : new EmailNotificationService(appConfigReader.getSmtpConfig());

        memorandumService = new MemorandumService(memorandumRepository, estadoTramiteRepository,
                servicioRepository, usuarioRepository, notificationService);
        diagramaService = new DiagramaService(diagramaRepository, horarioRepository, servicioRepository,
                usuarioRepository, feriadoRepository, notificationService);
        feriadoService = new FeriadoService(feriadoRepository);
        notificacionService = new NotificacionService(new NotificacionRepository(databaseConnector),
                usuarioRepository, notificationService);
        novedadService = new NovedadService(new NovedadRepository(databaseConnector), diagramaRepository,
                usuarioRepository, servicioRepository, notificacionService);
        reglaValidacionService = new ReglaValidacionService(new ReglaValidacionRepository(databaseConnector));
        modalidadHorariaService = new ar.com.hmu.service.diagramacion.ModalidadHorariaService(
                horarioRepository, diagramaRepository);
        parteDiarioService = new ar.com.hmu.service.asistencia.ParteDiarioService(
                new ar.com.hmu.repository.ParteDiarioRepository(databaseConnector),
                new ar.com.hmu.repository.MarcacionRepository(databaseConnector),
                new NovedadRepository(databaseConnector), servicioRepository, notificacionService);
        indicadoresService = new ar.com.hmu.service.asistencia.IndicadoresService(databaseConnector,
                new ar.com.hmu.repository.ParteDiarioRepository(databaseConnector));
        importacionPadronService = new ar.com.hmu.service.ImportacionPadronService(databaseConnector,
                servicioRepository, modalidadHorariaService);
        asignacionRolService = new ar.com.hmu.service.AsignacionRolService(roleRepository, servicioRepository);
        diagramaService.setReglas(reglaValidacionService);
        novedadService.setDiagramaService(diagramaService);
        // Banco de horas: feriados y reglas para el cómputo de horas extras; acreditación al aprobarlas.
        parteDiarioService.setFeriadoRepository(feriadoRepository);
        parteDiarioService.setReglas(diagramaService::reglasVigentes);
        novedadService.setAcreditadorHorasExtra(parteDiarioService::acreditarHorasExtraDeNovedad);
    }

    public static ContextoAplicacion crear() {
        return new ContextoAplicacion(null);
    }

    /** Con otro canal de correo (el modo de capturas usa uno que no envía nada). */
    public static ContextoAplicacion crear(NotificationService correo) {
        return new ContextoAplicacion(correo);
    }

    public void inyectar(LoginController c) {
        c.setContexto(this);
        c.setLoginService(loginService);
        c.setDatabaseConnector(databaseConnector);
        c.setRolService(roleService);
        c.setUsuarioService(usuarioService);
        c.setCargoService(cargoService);
        c.setServicioService(servicioService);
        c.setDomicilioService(domicilioService);
        c.setMemorandumService(memorandumService);
        c.setDiagramaService(diagramaService);
        c.setFeriadoService(feriadoService);
        c.setNovedadService(novedadService);
        c.setNotificacionService(notificacionService);
        c.setReglaValidacionService(reglaValidacionService);
        c.setEstadoTramiteRepository(estadoTramiteRepository);
        c.setUsuarioRepository(usuarioRepository);
    }

    public void inyectar(MainMenuMosaicoController c) {
        c.setServices(usuarioService, cargoService, servicioService, domicilioService, roleService);
        c.setMemorandumService(memorandumService);
        c.setDiagramaService(diagramaService);
        c.setFeriadoService(feriadoService);
        c.setNovedadService(novedadService);
        c.setNotificacionService(notificacionService);
        c.setReglaValidacionService(reglaValidacionService);
        c.setModalidadHorariaService(modalidadHorariaService);
        c.setParteDiarioService(parteDiarioService);
        c.setIndicadoresService(indicadoresService);
        c.setImportacionPadronService(importacionPadronService);
        c.setAsignacionRolService(asignacionRolService);
        c.setEstadoTramiteRepository(estadoTramiteRepository);
        c.setUsuarioRepository(usuarioRepository);
        c.setDatabaseConnector(databaseConnector);
    }
}
