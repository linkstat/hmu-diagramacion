package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoMemorandumAutorizacion;
import ar.com.hmu.model.EstadoTramite;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.MemorandumAutorizacion;
import ar.com.hmu.model.MemorandumDestinatario;
import ar.com.hmu.model.TipoRolMemoAutorizacion;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.EstadoTramiteRepository;
import ar.com.hmu.repository.MemorandumRepository;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import ar.com.hmu.service.notification.NotificationService;
import ar.com.hmu.service.notification.TipoEventoMemorandum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemorandumServiceTest {

    @Mock private MemorandumRepository memoRepo;
    @Mock private EstadoTramiteRepository estadoRepo;
    @Mock private ServicioRepository servicioRepo;
    @Mock private UsuarioRepository usuarioRepo;
    @Mock private NotificationService notif;

    private MemorandumService service;

    // UUIDs reutilizables para los estados.
    private final UUID idBorrador = UUID.randomUUID();
    private final UUID idEnviado = UUID.randomUUID();
    private final UUID idPendAuth = UUID.randomUUID();
    private final UUID idAutorizado = UUID.randomUUID();
    private final UUID idRechazado = UUID.randomUUID();
    private final UUID idObservado = UUID.randomUUID();
    private final UUID idLeido = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MemorandumService(memoRepo, estadoRepo, servicioRepo, usuarioRepo, notif);
    }

    // ============================================================
    // guardarBorrador
    // ============================================================

    @Test
    void guardarBorrador_persisteEnEstadoBorrador() throws Exception {
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum borrador = memoBasico("Asunto", "Contenido");
        borrador.agregarDestinatario(new MemorandumDestinatario(null, UUID.randomUUID()));

        when(estadoRepo.getId(EstadoTramite.BORRADOR)).thenReturn(idBorrador);

        Memorandum guardado = service.guardarBorrador(borrador, remitente);

        assertThat(guardado.getEstadoTramiteId()).isEqualTo(idBorrador);
        assertThat(guardado.getRemitenteId()).isEqualTo(remitente.getId());
        assertThat(guardado.getId()).isNotNull();
        verify(memoRepo).create(guardado);
    }

    @Test
    void guardarBorrador_sinDestinatarios_seGuardaIgual() throws Exception {
        // Borradores admiten lista vacía: la validación se aplica al enviar.
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum borrador = memoBasico("Asunto", "Contenido");

        when(estadoRepo.getId(EstadoTramite.BORRADOR)).thenReturn(idBorrador);

        Memorandum guardado = service.guardarBorrador(borrador, remitente);

        assertThat(guardado.getEstadoTramiteId()).isEqualTo(idBorrador);
        verify(memoRepo).create(guardado);
    }

    @Test
    void enviar_sinDestinatarios_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idBorrador);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(memoRepo.findDestinatariosByMemoId(memoId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.enviar(memoId, remitente))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("destinatario");
    }

    @Test
    void guardarBorrador_asuntoVacio_lanza() {
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum borrador = memoBasico("", "Contenido");
        borrador.agregarDestinatario(new MemorandumDestinatario(null, UUID.randomUUID()));

        assertThatThrownBy(() -> service.guardarBorrador(borrador, remitente))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("asunto");
    }

    @Test
    void guardarBorrador_contenidoVacio_lanza() {
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum borrador = memoBasico("Asunto", "");
        borrador.agregarDestinatario(new MemorandumDestinatario(null, UUID.randomUUID()));

        assertThatThrownBy(() -> service.guardarBorrador(borrador, remitente))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("contenido");
    }

    @Test
    void guardarBorrador_contenidoExcesivo_lanza() {
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum borrador = memoBasico("Asunto", "x".repeat(8001));
        borrador.agregarDestinatario(new MemorandumDestinatario(null, UUID.randomUUID()));

        assertThatThrownBy(() -> service.guardarBorrador(borrador, remitente))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("máximo");
    }

    // ============================================================
    // enviar
    // ============================================================

    @Test
    void enviar_jefatura_pasaDirectoAEnviado() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario jefe = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.JEFATURADESERVICIO);
        Memorandum memo = memoEnEstado(memoId, jefe.getId(), idBorrador);
        MemorandumDestinatario unDest = new MemorandumDestinatario(memoId, UUID.randomUUID());

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idBorrador)).thenReturn(EstadoTramite.BORRADOR);
        when(estadoRepo.getId(EstadoTramite.ENVIADO)).thenReturn(idEnviado);
        when(memoRepo.findDestinatariosByMemoId(memoId)).thenReturn(List.of(unDest));

        service.enviar(memoId, jefe);

        verify(memoRepo).actualizarEstadoTramite(memoId, idEnviado);
        verify(notif).notify(eq(jefe), eq(TipoEventoMemorandum.MEMO_ENVIADO_CONFIRMACION), any(), any());
    }

    @Test
    void enviar_empleadoAOficinaPersonal_pasaAPendienteAutorizacion() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID servicioRemitenteId = UUID.randomUUID();
        Usuario empleado = usuarioConRoles(TipoUsuario.EMPLEADO);
        empleado.setServicioId(servicioRemitenteId);

        Usuario destinoOP = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.OFICINADEPERSONAL);
        destinoOP.setServicioId(UUID.randomUUID());

        Memorandum memo = memoEnEstado(memoId, empleado.getId(), idBorrador);
        memo.agregarDestinatario(new MemorandumDestinatario(memoId, destinoOP.getId()));

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idBorrador)).thenReturn(EstadoTramite.BORRADOR);
        when(estadoRepo.getId(EstadoTramite.PENDIENTE_DE_AUTORIZACION)).thenReturn(idPendAuth);
        when(memoRepo.findDestinatariosByMemoId(memoId)).thenReturn(memo.getDestinatarios());
        when(servicioRepo.findEncargadoByServicio(servicioRemitenteId)).thenReturn(UUID.randomUUID());
        // Cualquier readByUUID devuelve el destinatarioOP (alcanza para
        // que requiereAutorizacion vea al destinatario fuera de la jefatura
        // propia y para resolver al encargado al notificarlo).
        when(usuarioRepo.readByUUID(any(UUID.class))).thenReturn(destinoOP);

        service.enviar(memoId, empleado);

        verify(memoRepo).actualizarEstadoTramite(memoId, idPendAuth);
        verify(memoRepo).agregarAutorizacion(any(MemorandumAutorizacion.class));
        verify(notif).notify(eq(empleado), eq(TipoEventoMemorandum.MEMO_ENVIADO_CONFIRMACION), any(), any());
    }

    @Test
    void enviar_remitenteIncorrecto_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID otroId = UUID.randomUUID();
        Usuario otro = usuarioConRoles(TipoUsuario.EMPLEADO);
        otro.setId(otroId);

        Memorandum memo = memoEnEstado(memoId, UUID.randomUUID(), idBorrador);
        when(memoRepo.readByUUID(memoId)).thenReturn(memo);

        assertThatThrownBy(() -> service.enviar(memoId, otro))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("remitente");
    }

    // ============================================================
    // autorizar / rechazar / observar
    // ============================================================

    @Test
    void autorizar_porEncargadoCorrecto_transicionaAEnviado() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID servicioId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        remitente.setServicioId(servicioId);
        Usuario encargado = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.JEFATURADESERVICIO);

        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idPendAuth);
        UUID autoId = UUID.randomUUID();
        MemorandumAutorizacion pend = new MemorandumAutorizacion(autoId, memoId,
                TipoRolMemoAutorizacion.JEFATURADESERVICIO,
                EstadoMemorandumAutorizacion.PENDIENTE);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idPendAuth)).thenReturn(EstadoTramite.PENDIENTE_DE_AUTORIZACION);
        when(estadoRepo.getId(EstadoTramite.ENVIADO)).thenReturn(idEnviado);
        when(usuarioRepo.readByUUID(remitente.getId())).thenReturn(remitente);
        when(servicioRepo.findEncargadoByServicio(servicioId)).thenReturn(encargado.getId());
        when(memoRepo.findAutorizacionesByMemoId(memoId)).thenReturn(List.of(pend));
        when(memoRepo.findDestinatariosByMemoId(memoId)).thenReturn(List.of());

        service.autorizar(memoId, encargado);

        verify(memoRepo).resolverAutorizacion(eq(autoId), eq(EstadoMemorandumAutorizacion.AUTORIZADO),
                eq(encargado.getId()), any(), eq(null));
        verify(memoRepo).actualizarEstadoTramite(memoId, idEnviado);
        verify(notif).notify(eq(remitente), eq(TipoEventoMemorandum.MEMO_AUTORIZADO), any(), any());
    }

    @Test
    void autorizar_porUsuarioDistintoAlEncargado_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID servicioId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        remitente.setServicioId(servicioId);
        Usuario otro = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.JEFATURADESERVICIO);

        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idPendAuth);
        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idPendAuth)).thenReturn(EstadoTramite.PENDIENTE_DE_AUTORIZACION);
        when(usuarioRepo.readByUUID(remitente.getId())).thenReturn(remitente);
        when(servicioRepo.findEncargadoByServicio(servicioId)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.autorizar(memoId, otro))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("encargado");
    }

    @Test
    void rechazar_guardaComentariosYTransicionaARechazado() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID servicioId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        remitente.setServicioId(servicioId);
        Usuario encargado = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.JEFATURADESERVICIO);

        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idPendAuth);
        UUID autoId = UUID.randomUUID();
        MemorandumAutorizacion pend = new MemorandumAutorizacion(autoId, memoId,
                TipoRolMemoAutorizacion.JEFATURADESERVICIO,
                EstadoMemorandumAutorizacion.PENDIENTE);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idPendAuth)).thenReturn(EstadoTramite.PENDIENTE_DE_AUTORIZACION);
        when(estadoRepo.getId(EstadoTramite.RECHAZADO)).thenReturn(idRechazado);
        when(usuarioRepo.readByUUID(remitente.getId())).thenReturn(remitente);
        when(servicioRepo.findEncargadoByServicio(servicioId)).thenReturn(encargado.getId());
        when(memoRepo.findAutorizacionesByMemoId(memoId)).thenReturn(List.of(pend));

        service.rechazar(memoId, encargado, "fuera de norma");

        verify(memoRepo).resolverAutorizacion(eq(autoId), eq(EstadoMemorandumAutorizacion.RECHAZADO),
                eq(encargado.getId()), any(), eq("fuera de norma"));
        verify(memoRepo).actualizarEstadoTramite(memoId, idRechazado);

        ArgumentCaptor<String> coment = ArgumentCaptor.forClass(String.class);
        verify(notif).notify(eq(remitente), eq(TipoEventoMemorandum.MEMO_RECHAZADO), any(), coment.capture());
        assertThat(coment.getValue()).isEqualTo("fuera de norma");
    }

    @Test
    void observar_sinComentarios_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario encargado = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.JEFATURADESERVICIO);

        assertThatThrownBy(() -> service.observar(memoId, encargado, ""))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("comentarios");
    }

    @Test
    void observar_transicionaAObservadoYNotifica() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID servicioId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        remitente.setServicioId(servicioId);
        Usuario encargado = usuarioConRoles(TipoUsuario.EMPLEADO, TipoUsuario.JEFATURADESERVICIO);

        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idPendAuth);
        UUID autoId = UUID.randomUUID();
        MemorandumAutorizacion pend = new MemorandumAutorizacion(autoId, memoId,
                TipoRolMemoAutorizacion.JEFATURADESERVICIO,
                EstadoMemorandumAutorizacion.PENDIENTE);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idPendAuth)).thenReturn(EstadoTramite.PENDIENTE_DE_AUTORIZACION);
        when(estadoRepo.getId(EstadoTramite.OBSERVADO)).thenReturn(idObservado);
        when(usuarioRepo.readByUUID(remitente.getId())).thenReturn(remitente);
        when(servicioRepo.findEncargadoByServicio(servicioId)).thenReturn(encargado.getId());
        when(memoRepo.findAutorizacionesByMemoId(memoId)).thenReturn(List.of(pend));

        service.observar(memoId, encargado, "ajustar formato");

        verify(memoRepo).resolverAutorizacion(eq(autoId), eq(EstadoMemorandumAutorizacion.OBSERVADO),
                eq(encargado.getId()), any(), eq("ajustar formato"));
        verify(memoRepo).actualizarEstadoTramite(memoId, idObservado);
        verify(notif).notify(eq(remitente), eq(TipoEventoMemorandum.MEMO_OBSERVADO), any(), eq("ajustar formato"));
    }

    // ============================================================
    // reenviarPostObservacion
    // ============================================================

    @Test
    void reenviarPostObservacion_creaNuevaFilaPendienteYTransiciona() throws Exception {
        UUID memoId = UUID.randomUUID();
        UUID servicioId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        remitente.setServicioId(servicioId);

        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idObservado);
        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idObservado)).thenReturn(EstadoTramite.OBSERVADO);
        when(estadoRepo.getId(EstadoTramite.PENDIENTE_DE_AUTORIZACION)).thenReturn(idPendAuth);
        when(servicioRepo.findEncargadoByServicio(servicioId)).thenReturn(UUID.randomUUID());

        service.reenviarPostObservacion(memoId, remitente);

        verify(memoRepo).agregarAutorizacion(any(MemorandumAutorizacion.class));
        verify(memoRepo).actualizarEstadoTramite(memoId, idPendAuth);
    }

    @Test
    void reenviarPostObservacion_estadoIncorrecto_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idBorrador);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idBorrador)).thenReturn(EstadoTramite.BORRADOR);

        assertThatThrownBy(() -> service.reenviarPostObservacion(memoId, remitente))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("OBSERVADO");
    }

    // ============================================================
    // marcarLeido
    // ============================================================

    @Test
    void marcarLeido_unDestinatario_noTransicionaSiOtrosNoLeyeron() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario destinatario = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, UUID.randomUUID(), idEnviado);

        MemorandumDestinatario yo = new MemorandumDestinatario(memoId, destinatario.getId());
        yo.setFechaRecepcion(LocalDateTime.now());
        MemorandumDestinatario otro = new MemorandumDestinatario(memoId, UUID.randomUUID());

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idEnviado)).thenReturn(EstadoTramite.ENVIADO);
        when(memoRepo.findDestinatariosByMemoId(memoId)).thenReturn(List.of(yo, otro));

        service.marcarLeido(memoId, destinatario);

        verify(memoRepo).marcarLeido(eq(memoId), eq(destinatario.getId()), any());
        verify(memoRepo, never()).actualizarEstadoTramite(eq(memoId), eq(idLeido));
    }

    @Test
    void marcarLeido_todosLosDestinatarios_transicionaALeido() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario destinatario = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, UUID.randomUUID(), idEnviado);

        MemorandumDestinatario yo = new MemorandumDestinatario(memoId, destinatario.getId());
        yo.setFechaRecepcion(LocalDateTime.now());

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idEnviado)).thenReturn(EstadoTramite.ENVIADO);
        when(estadoRepo.getId(EstadoTramite.LEIDO)).thenReturn(idLeido);
        when(memoRepo.findDestinatariosByMemoId(memoId)).thenReturn(List.of(yo));

        service.marcarLeido(memoId, destinatario);

        verify(memoRepo).actualizarEstadoTramite(memoId, idLeido);
    }

    // ============================================================
    // eliminarBorrador
    // ============================================================

    @Test
    void eliminarBorrador_estadoNoBorrador_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idEnviado);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idEnviado)).thenReturn(EstadoTramite.ENVIADO);

        assertThatThrownBy(() -> service.eliminarBorrador(memoId, remitente))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("BORRADOR");

        verify(memoRepo, never()).delete(any());
    }

    @Test
    void eliminarBorrador_remitenteDistinto_lanza() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario otro = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, UUID.randomUUID(), idBorrador);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);

        assertThatThrownBy(() -> service.eliminarBorrador(memoId, otro))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("remitente");
    }

    @Test
    void eliminarBorrador_happyPath_invocaDelete() throws Exception {
        UUID memoId = UUID.randomUUID();
        Usuario remitente = usuarioConRoles(TipoUsuario.EMPLEADO);
        Memorandum memo = memoEnEstado(memoId, remitente.getId(), idBorrador);

        when(memoRepo.readByUUID(memoId)).thenReturn(memo);
        when(estadoRepo.getEstadoTramite(idBorrador)).thenReturn(EstadoTramite.BORRADOR);

        service.eliminarBorrador(memoId, remitente);

        verify(memoRepo).delete(memo);
    }

    // ============================================================
    // bandejas + contador
    // ============================================================

    @Test
    void bandejaEntrada_delegaAlRepo() throws Exception {
        Usuario u = usuarioConRoles(TipoUsuario.EMPLEADO);
        when(memoRepo.findRecibidosPorDestinatario(u.getId())).thenReturn(List.of());

        service.bandejaEntrada(u);

        verify(memoRepo).findRecibidosPorDestinatario(u.getId());
    }

    @Test
    void contarNoLeidos_delegaAlRepo() throws Exception {
        Usuario u = usuarioConRoles(TipoUsuario.EMPLEADO);
        when(memoRepo.countNoLeidos(u.getId())).thenReturn(3);

        int n = service.contarNoLeidos(u);

        assertThat(n).isEqualTo(3);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Usuario usuarioConRoles(TipoUsuario... roles) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setMail("test@hospital.cba.gov.ar");
        // Usuario.hasRole consulta rolesBehavior (instancias de Role), no
        // rolesData. Para que funcione en los tests, primero sembramos
        // rolesData con los TipoUsuario y luego invocamos assignRoleBehaviors
        // para que reflexivamente instancie los Role correspondientes.
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

    private Memorandum memoBasico(String asunto, String contenido) {
        Memorandum m = new Memorandum();
        m.setAsunto(asunto);
        m.setContenido(contenido);
        return m;
    }

    private Memorandum memoEnEstado(UUID id, UUID remitenteId, UUID estadoId) {
        Memorandum m = new Memorandum();
        m.setId(id);
        m.setRemitenteId(remitenteId);
        m.setEstadoTramiteId(estadoId);
        m.setAsunto("Asunto X");
        m.setContenido("Contenido Y");
        return m;
    }
}
