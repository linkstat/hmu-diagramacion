package ar.com.hmu.service.diagramacion;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.DiagramaRepository;
import ar.com.hmu.repository.HorarioRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.notification.NotificationService;
import ar.com.hmu.service.notification.TipoEventoDiagrama;
import ar.com.hmu.util.DiaSemana;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagramaServiceTest {

    @Mock private DiagramaRepository diagramaRepo;
    @Mock private HorarioRepository horarioRepo;
    @Mock private ServicioRepository servicioRepo;
    @Mock private UsuarioRepository usuarioRepo;
    @Mock private ar.com.hmu.repository.FeriadoRepository feriadoRepo;
    @Mock private NotificationService notif;

    private DiagramaService service;

    private final UUID servicioId = UUID.randomUUID();
    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 7, 12);

    @BeforeEach
    void setUp() {
        service = new DiagramaService(diagramaRepo, horarioRepo, servicioRepo,
                usuarioRepo, feriadoRepo, notif);
    }

    // ============================================================
    // crearBorrador
    // ============================================================

    @Test
    void crearBorrador_generaLaGrillaDesdeLosHorarios() throws Exception {
        Usuario jefe = jefeDelServicio();
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        when(diagramaRepo.findSolapados(servicioId, LUNES, DOMINGO)).thenReturn(List.of());
        when(usuarioRepo.findUsuariosByServicio(servicioId)).thenReturn(List.of(empleado));
        when(horarioRepo.findHorarioActualDeEmpleado(empleado.getId())).thenReturn(estandarLaV());

        DiagramaDeServicio d = service.crearBorrador(servicioId, LUNES, DOMINGO, jefe);

        assertThat(d.getEstado()).isEqualTo(EstadoDiagrama.BORRADOR);
        assertThat(d.getCreadoPorId()).isEqualTo(jefe.getId());
        assertThat(d.getJornadas()).hasSize(7); // una por día
        assertThat(d.getJornadas())
                .filteredOn(j -> j.getTipo() == TipoJornada.TURNO_NORMAL).hasSize(5);
        assertThat(d.getJornadas()).allSatisfy(j -> {
            assertThat(j.getDiagramaId()).isEqualTo(d.getId());
            assertThat(j.getEmpleadoId()).isEqualTo(empleado.getId());
        });
        verify(diagramaRepo).create(d);
    }

    @Test
    void crearBorrador_empleadoSinHorario_recibeGrillaTodaFranco() throws Exception {
        Usuario jefe = jefeDelServicio();
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        when(diagramaRepo.findSolapados(servicioId, LUNES, DOMINGO)).thenReturn(List.of());
        when(usuarioRepo.findUsuariosByServicio(servicioId)).thenReturn(List.of(empleado));
        when(horarioRepo.findHorarioActualDeEmpleado(empleado.getId())).thenReturn(null);

        DiagramaDeServicio d = service.crearBorrador(servicioId, LUNES, DOMINGO, jefe);

        assertThat(d.getJornadas()).hasSize(7)
                .allSatisfy(j -> assertThat(j.getTipo()).isEqualTo(TipoJornada.FRANCO));
    }

    @Test
    void crearBorrador_rangoInvertido_lanza() {
        assertThatThrownBy(() -> service.crearBorrador(servicioId, DOMINGO, LUNES, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Rango de fechas inválido");
    }

    @Test
    void crearBorrador_periodoSolapado_lanza() throws Exception {
        DiagramaDeServicio existente = new DiagramaDeServicio();
        existente.setEstado(EstadoDiagrama.APROBADO);
        when(diagramaRepo.findSolapados(servicioId, LUNES, DOMINGO)).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.crearBorrador(servicioId, LUNES, DOMINGO, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("se solapa");
    }

    @Test
    void crearBorrador_jefeDeOtroServicio_lanza() {
        Usuario jefeAjeno = usuarioConRoles(TipoUsuario.JEFATURADESERVICIO);
        jefeAjeno.setServicioId(UUID.randomUUID()); // otro servicio

        assertThatThrownBy(() -> service.crearBorrador(servicioId, LUNES, DOMINGO, jefeAjeno))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("permisos");
    }

    @Test
    void crearBorrador_empleadoSimple_lanza() {
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        empleado.setServicioId(servicioId); // aunque sea del servicio, no es jefe

        assertThatThrownBy(() -> service.crearBorrador(servicioId, LUNES, DOMINGO, empleado))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("permisos");
    }

    // ============================================================
    // guardarJornadas
    // ============================================================

    @Test
    void guardarJornadas_enBorrador_reemplazaYSubeLaVersion() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 3);
        List<JornadaLaboral> jornadas = List.of(franco(LUNES));
        when(diagramaRepo.reemplazarJornadas(d.getId(), jornadas, 3)).thenReturn(true);

        service.guardarJornadas(d, jornadas, jefeDelServicio());

        assertThat(d.getVersion()).isEqualTo(4);
        assertThat(d.getJornadas()).isEqualTo(jornadas);
        assertThat(jornadas.get(0).getDiagramaId()).isEqualTo(d.getId());
    }

    @Test
    void guardarJornadas_enAprobado_lanza() {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.APROBADO, 1);

        assertThatThrownBy(() -> service.guardarJornadas(d, List.of(franco(LUNES)), jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("BORRADOR u OBSERVADO");
    }

    @Test
    void guardarJornadas_conflictoDeVersion_lanza() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 3);
        when(diagramaRepo.reemplazarJornadas(any(), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.guardarJornadas(d, List.of(franco(LUNES)), jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("otra sesión");
    }

    @Test
    void guardarJornadas_jornadaSinEmpleado_lanza() {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 0);
        JornadaLaboral sinEmpleado = franco(LUNES);
        sinEmpleado.setEmpleadoId(null);

        assertThatThrownBy(() -> service.guardarJornadas(d, List.of(sinEmpleado), jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("empleado, fecha y tipo");
    }

    // ============================================================
    // enviarParaAprobacion
    // ============================================================

    @Test
    void enviar_valido_transicionaYNotificaSoloAOficinaDePersonal() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 0);
        when(diagramaRepo.findJornadasByDiagramaId(d.getId())).thenReturn(List.of(franco(LUNES)));
        when(diagramaRepo.actualizarEstado(eq(d.getId()), eq(EstadoDiagrama.PENDIENTE_APROBACION),
                isNull(), isNull(), isNull(), eq(0))).thenReturn(true);

        Usuario op = usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);
        Usuario empleadoComun = usuarioConRoles(TipoUsuario.EMPLEADO);
        when(usuarioRepo.readAll()).thenReturn(List.of(op, empleadoComun));
        when(servicioRepo.readByUUID(servicioId)).thenReturn(servicio("Enfermería"));

        service.enviarParaAprobacion(d, jefeDelServicio());

        assertThat(d.getEstado()).isEqualTo(EstadoDiagrama.PENDIENTE_APROBACION);
        assertThat(d.getVersion()).isEqualTo(1);
        verify(notif).notify(eq(op), eq(TipoEventoDiagrama.DIAGRAMA_PENDIENTE_APROBACION),
                eq(d), eq("Enfermería"), isNull());
        verify(notif, never()).notify(eq(empleadoComun), any(TipoEventoDiagrama.class),
                any(), any(), any());
    }

    @Test
    void enviar_conViolacionesEstructurales_lanzaSinTransicionar() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 0);
        // Turno sin horario: violación estructural.
        JornadaLaboral invalida = new JornadaLaboral();
        invalida.setEmpleadoId(UUID.randomUUID());
        invalida.setFecha(LUNES);
        invalida.setTipo(TipoJornada.TURNO_NORMAL);
        when(diagramaRepo.findJornadasByDiagramaId(d.getId())).thenReturn(List.of(invalida));

        assertThatThrownBy(() -> service.enviarParaAprobacion(d, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("violación");
        verify(diagramaRepo, never()).actualizarEstado(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void enviar_desdeAprobado_lanza() {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.APROBADO, 2);

        assertThatThrownBy(() -> service.enviarParaAprobacion(d, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("BORRADOR u OBSERVADO");
    }

    @Test
    void enviar_trasObservacion_limpiaLaObservacionAnterior() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.OBSERVADO, 5);
        d.setComentariosObservacion("Faltan guardias del fin de semana");
        d.setAprobadoPorId(UUID.randomUUID());
        when(diagramaRepo.findJornadasByDiagramaId(d.getId())).thenReturn(List.of(franco(LUNES)));
        when(diagramaRepo.actualizarEstado(any(), any(), any(), any(), any(), anyInt())).thenReturn(true);
        when(usuarioRepo.readAll()).thenReturn(List.of());

        service.enviarParaAprobacion(d, jefeDelServicio());

        assertThat(d.getEstado()).isEqualTo(EstadoDiagrama.PENDIENTE_APROBACION);
        assertThat(d.getComentariosObservacion()).isNull();
        assertThat(d.getAprobadoPorId()).isNull();
    }

    // ============================================================
    // aprobar / observar
    // ============================================================

    @Test
    void aprobar_porOficinaDePersonal_transicionaYNotificaAlCreador() throws Exception {
        Usuario creador = jefeDelServicio();
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.PENDIENTE_APROBACION, 1);
        d.setCreadoPorId(creador.getId());
        Usuario op = usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);
        when(diagramaRepo.actualizarEstado(eq(d.getId()), eq(EstadoDiagrama.APROBADO),
                eq(op.getId()), any(LocalDateTime.class), isNull(), eq(1))).thenReturn(true);
        when(usuarioRepo.readByUUID(creador.getId())).thenReturn(creador);
        when(servicioRepo.readByUUID(servicioId)).thenReturn(servicio("Enfermería"));

        service.aprobar(d, op);

        assertThat(d.getEstado()).isEqualTo(EstadoDiagrama.APROBADO);
        assertThat(d.getAprobadoPorId()).isEqualTo(op.getId());
        assertThat(d.getFechaAprobacion()).isNotNull();
        verify(notif).notify(eq(creador), eq(TipoEventoDiagrama.DIAGRAMA_APROBADO),
                eq(d), eq("Enfermería"), isNull());
    }

    @Test
    void aprobar_porJefatura_lanza() {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.PENDIENTE_APROBACION, 1);

        assertThatThrownBy(() -> service.aprobar(d, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Oficina de Personal");
    }

    @Test
    void aprobar_desdeBorrador_lanza() {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 0);
        Usuario op = usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);

        assertThatThrownBy(() -> service.aprobar(d, op))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PENDIENTE_APROBACION");
    }

    @Test
    void observar_guardaComentariosYNotificaAlCreador() throws Exception {
        Usuario creador = jefeDelServicio();
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.PENDIENTE_APROBACION, 1);
        d.setCreadoPorId(creador.getId());
        Usuario op = usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);
        when(diagramaRepo.actualizarEstado(eq(d.getId()), eq(EstadoDiagrama.OBSERVADO),
                eq(op.getId()), any(LocalDateTime.class), eq("Falta cobertura nocturna"), eq(1)))
                .thenReturn(true);
        when(usuarioRepo.readByUUID(creador.getId())).thenReturn(creador);
        when(servicioRepo.readByUUID(servicioId)).thenReturn(servicio("UTI"));

        service.observar(d, "Falta cobertura nocturna", op);

        assertThat(d.getEstado()).isEqualTo(EstadoDiagrama.OBSERVADO);
        assertThat(d.getComentariosObservacion()).isEqualTo("Falta cobertura nocturna");
        verify(notif).notify(eq(creador), eq(TipoEventoDiagrama.DIAGRAMA_OBSERVADO),
                eq(d), eq("UTI"), eq("Falta cobertura nocturna"));
    }

    @Test
    void observar_sinComentarios_lanza() {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.PENDIENTE_APROBACION, 1);
        Usuario op = usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);

        assertThatThrownBy(() -> service.observar(d, "  ", op))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("comentarios");
    }

    // ============================================================
    // eliminarBorrador
    // ============================================================

    @Test
    void eliminarBorrador_enBorrador_borra() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.BORRADOR, 0);

        service.eliminarBorrador(d, jefeDelServicio());

        verify(diagramaRepo).delete(d);
    }

    @Test
    void eliminarBorrador_enOtroEstado_lanza() throws Exception {
        DiagramaDeServicio d = diagramaEn(EstadoDiagrama.PENDIENTE_APROBACION, 1);

        assertThatThrownBy(() -> service.eliminarBorrador(d, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("BORRADOR");
        verify(diagramaRepo, never()).delete(any());
    }

    // ============================================================
    // Alertas de presentación (RFS09)
    // ============================================================

    private static final java.time.YearMonth AGOSTO = java.time.YearMonth.of(2026, 8);

    @Test
    void estadoPresentacion_sinDiagramas_esSinDiagrama() throws Exception {
        when(diagramaRepo.findSolapados(servicioId, AGOSTO.atDay(1), AGOSTO.atEndOfMonth()))
                .thenReturn(List.of());

        assertThat(service.estadoPresentacion(servicioId, AGOSTO))
                .isEqualTo(EstadoPresentacion.SIN_DIAGRAMA);
    }

    @Test
    void estadoPresentacion_conBorrador_esBorradorSinEnviar() throws Exception {
        when(diagramaRepo.findSolapados(any(), any(), any()))
                .thenReturn(List.of(diagramaEn(EstadoDiagrama.BORRADOR, 0)));

        assertThat(service.estadoPresentacion(servicioId, AGOSTO))
                .isEqualTo(EstadoPresentacion.BORRADOR_SIN_ENVIAR);
    }

    @Test
    void estadoPresentacion_observadoPesaMasQueBorrador() throws Exception {
        when(diagramaRepo.findSolapados(any(), any(), any()))
                .thenReturn(List.of(diagramaEn(EstadoDiagrama.BORRADOR, 0),
                        diagramaEn(EstadoDiagrama.OBSERVADO, 2)));

        assertThat(service.estadoPresentacion(servicioId, AGOSTO))
                .isEqualTo(EstadoPresentacion.OBSERVADO_SIN_CORREGIR);
    }

    @Test
    void estadoPresentacion_pendienteOAprobado_esPresentado() throws Exception {
        when(diagramaRepo.findSolapados(any(), any(), any()))
                .thenReturn(List.of(diagramaEn(EstadoDiagrama.APROBADO, 3)));

        assertThat(service.estadoPresentacion(servicioId, AGOSTO))
                .isEqualTo(EstadoPresentacion.PRESENTADO);
    }

    @Test
    void serviciosSinPresentar_salteaPresentadosYServiciosSinEmpleados() throws Exception {
        Servicio conDiagrama = servicio("Biomédica");
        Servicio sinDiagrama = new Servicio();
        sinDiagrama.setId(UUID.randomUUID());
        sinDiagrama.setNombre("Personal");
        Servicio vacio = new Servicio();
        vacio.setId(UUID.randomUUID());
        vacio.setNombre("Anestesia"); // sin empleados: no requiere diagrama

        when(servicioRepo.readAll()).thenReturn(List.of(conDiagrama, sinDiagrama, vacio));
        when(usuarioRepo.countUsuariosActivosPorServicio()).thenReturn(java.util.Map.of(
                conDiagrama.getId(), 3, sinDiagrama.getId(), 4));
        when(diagramaRepo.findSolapados(eq(conDiagrama.getId()), any(), any()))
                .thenReturn(List.of(diagramaEn(EstadoDiagrama.APROBADO, 3)));
        when(diagramaRepo.findSolapados(eq(sinDiagrama.getId()), any(), any()))
                .thenReturn(List.of());

        List<AlertaPresentacion> alertas = service.serviciosSinPresentar(AGOSTO);

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).servicio().getNombre()).isEqualTo("Personal");
        assertThat(alertas.get(0).estado()).isEqualTo(EstadoPresentacion.SIN_DIAGRAMA);
    }

    // ============================================================
    // Permiso de vista del diagrama (veDiagramaCompleto)
    // ============================================================

    @Test
    void setPermisoVista_porJefeDeSuServicio_actualiza() throws Exception {
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        empleado.setServicioId(servicioId);
        when(usuarioRepo.readByUUID(empleado.getId())).thenReturn(empleado);

        service.setPermisoVistaCompleta(empleado.getId(), false, jefeDelServicio());

        verify(usuarioRepo).setVeDiagramaCompleto(empleado.getId(), false);
    }

    @Test
    void setPermisoVista_porJefeDeOtroServicio_lanza() throws Exception {
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        empleado.setServicioId(UUID.randomUUID()); // servicio ajeno al jefe
        when(usuarioRepo.readByUUID(empleado.getId())).thenReturn(empleado);

        assertThatThrownBy(() -> service.setPermisoVistaCompleta(empleado.getId(), false, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("permisos");
        verify(usuarioRepo, never()).setVeDiagramaCompleto(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void setPermisoVista_porOficinaDePersonal_actualizaCualquierServicio() throws Exception {
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        empleado.setServicioId(UUID.randomUUID());
        when(usuarioRepo.readByUUID(empleado.getId())).thenReturn(empleado);
        Usuario op = usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);

        service.setPermisoVistaCompleta(empleado.getId(), true, op);

        verify(usuarioRepo).setVeDiagramaCompleto(empleado.getId(), true);
    }

    @Test
    void setPermisoVista_empleadoInexistente_lanza() throws Exception {
        UUID inexistente = UUID.randomUUID();
        when(usuarioRepo.readByUUID(inexistente)).thenReturn(null);

        assertThatThrownBy(() -> service.setPermisoVistaCompleta(inexistente, false, jefeDelServicio()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void puedeVerContexto_delegaEnElRepositorio() throws Exception {
        UUID empleadoId = UUID.randomUUID();
        when(usuarioRepo.findVeDiagramaCompleto(empleadoId)).thenReturn(false);

        assertThat(service.puedeVerContexto(empleadoId)).isFalse();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Usuario jefeDelServicio() {
        Usuario jefe = usuarioConRoles(TipoUsuario.JEFATURADESERVICIO);
        jefe.setServicioId(servicioId);
        return jefe;
    }

    private Usuario usuarioConRoles(TipoUsuario... roles) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setMail("test@hospital.cba.gov.ar");
        // Usuario.hasRole consulta rolesBehavior (instancias de Role), no
        // rolesData: sembramos rolesData y disparamos assignRoleBehaviors
        // (mismo helper que MemorandumServiceTest).
        java.util.Set<ar.com.hmu.model.RoleData> rolesData = new java.util.HashSet<>();
        for (TipoUsuario t : roles) {
            ar.com.hmu.model.RoleData rd = new ar.com.hmu.model.RoleData();
            rd.setId(UUID.randomUUID());
            rd.setNombre(t.getInternalName());
            rolesData.add(rd);
        }
        u.setRolesData(rolesData);
        try {
            u.assignRoleBehaviors();
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private DiagramaDeServicio diagramaEn(EstadoDiagrama estado, int version) {
        DiagramaDeServicio d = new DiagramaDeServicio(UUID.randomUUID(), servicioId, estado,
                LUNES, DOMINGO, UUID.randomUUID());
        d.setVersion(version);
        return d;
    }

    private JornadaLaboral franco(LocalDate fecha) {
        JornadaLaboral j = new JornadaLaboral();
        j.setId(UUID.randomUUID());
        j.setEmpleadoId(UUID.randomUUID());
        j.setFecha(fecha);
        j.setTipo(TipoJornada.FRANCO);
        return j;
    }

    private HorarioEstandar estandarLaV() {
        HorarioEstandar h = new HorarioEstandar();
        h.setDiasLaborables(List.of(DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES,
                DiaSemana.JUEVES, DiaSemana.VIERNES));
        h.setHorasPorDia(7);
        return h;
    }

    private Servicio servicio(String nombre) {
        Servicio s = new Servicio();
        s.setId(servicioId);
        s.setNombre(nombre);
        return s;
    }
}
