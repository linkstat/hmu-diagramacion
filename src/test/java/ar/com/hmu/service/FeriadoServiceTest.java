package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.EstadoFeriado;
import ar.com.hmu.model.Feriado;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.FeriadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeriadoServiceTest {

    @Mock private FeriadoRepository repo;

    private FeriadoService service;

    @BeforeEach
    void setUp() {
        service = new FeriadoService(repo);
    }

    // ============================================================
    // Carga anual
    // ============================================================

    @Test
    void cargarAnio_conAnioVacio_creaTodosVigentes() throws Exception {
        when(repo.countActivosEnAnio(2026)).thenReturn(0);
        Map<LocalDate, String> feriados = new LinkedHashMap<>();
        feriados.put(LocalDate.of(2026, 7, 9), "Día de la Independencia");
        feriados.put(LocalDate.of(2026, 12, 25), "Navidad");

        service.cargarAnio(2026, feriados, op());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Feriado>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).createBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allSatisfy(f -> {
                    assertThat(f.getEstado()).isEqualTo(EstadoFeriado.VIGENTE);
                    assertThat(f.getId()).isNotNull();
                });
    }

    @Test
    void cargarAnio_conAnioYaCargado_lanza() throws Exception {
        when(repo.countActivosEnAnio(2026)).thenReturn(12);

        assertThatThrownBy(() -> service.cargarAnio(2026,
                Map.of(LocalDate.of(2026, 7, 9), "Independencia"), op()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ya tiene feriados");
        verify(repo, never()).createBatch(any());
    }

    @Test
    void cargarAnio_conFechaDeOtroAnio_lanza() {
        assertThatThrownBy(() -> service.cargarAnio(2026,
                Map.of(LocalDate.of(2027, 1, 1), "Año Nuevo"), op()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no pertenece al año");
    }

    @Test
    void cargarAnio_porNoOP_lanza() {
        assertThatThrownBy(() -> service.cargarAnio(2026,
                Map.of(LocalDate.of(2026, 7, 9), "Independencia"),
                usuarioConRoles(TipoUsuario.JEFATURADESERVICIO)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Oficina de Personal");
    }

    // ============================================================
    // Propuestas de OP
    // ============================================================

    @Test
    void proponerAlta_quedaPendiente() throws Exception {
        Feriado f = service.proponerAlta(LocalDate.of(2026, 10, 12),
                "Feriado administrativo", op());

        assertThat(f.getEstado()).isEqualTo(EstadoFeriado.ALTA_PENDIENTE);
        verify(repo).create(f);
    }

    @Test
    void proponerBaja_deVigente_quedaBajaPendiente() throws Exception {
        Feriado vigente = feriadoEn(EstadoFeriado.VIGENTE);
        when(repo.readByUUID(vigente.getId())).thenReturn(vigente);

        service.proponerBaja(vigente.getId(), op());

        verify(repo).actualizarEstado(eq(vigente.getId()),
                eq(EstadoFeriado.BAJA_PENDIENTE), isNull(), isNull());
    }

    @Test
    void proponerBaja_deNoVigente_lanza() throws Exception {
        Feriado pendiente = feriadoEn(EstadoFeriado.ALTA_PENDIENTE);
        when(repo.readByUUID(pendiente.getId())).thenReturn(pendiente);

        assertThatThrownBy(() -> service.proponerBaja(pendiente.getId(), op()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("VIGENTE");
    }

    // ============================================================
    // Resolución de la Dirección
    // ============================================================

    @Test
    void autorizar_altaPendiente_pasaAVigente() throws Exception {
        Feriado f = feriadoEn(EstadoFeriado.ALTA_PENDIENTE);
        when(repo.readByUUID(f.getId())).thenReturn(f);
        Usuario direccion = usuarioConRoles(TipoUsuario.DIRECCION);

        service.autorizar(f.getId(), direccion);

        verify(repo).actualizarEstado(eq(f.getId()), eq(EstadoFeriado.VIGENTE),
                eq(direccion.getId()), any(LocalDateTime.class));
    }

    @Test
    void autorizar_bajaPendiente_pasaAAnulado() throws Exception {
        Feriado f = feriadoEn(EstadoFeriado.BAJA_PENDIENTE);
        when(repo.readByUUID(f.getId())).thenReturn(f);

        service.autorizar(f.getId(), usuarioConRoles(TipoUsuario.DIRECCION));

        verify(repo).actualizarEstado(eq(f.getId()), eq(EstadoFeriado.ANULADO),
                any(), any(LocalDateTime.class));
    }

    @Test
    void rechazar_altaPendiente_pasaARechazado() throws Exception {
        Feriado f = feriadoEn(EstadoFeriado.ALTA_PENDIENTE);
        when(repo.readByUUID(f.getId())).thenReturn(f);

        service.rechazar(f.getId(), usuarioConRoles(TipoUsuario.DIRECCION));

        verify(repo).actualizarEstado(eq(f.getId()), eq(EstadoFeriado.RECHAZADO),
                any(), any(LocalDateTime.class));
    }

    @Test
    void rechazar_bajaPendiente_vuelveAVigente() throws Exception {
        Feriado f = feriadoEn(EstadoFeriado.BAJA_PENDIENTE);
        when(repo.readByUUID(f.getId())).thenReturn(f);

        service.rechazar(f.getId(), usuarioConRoles(TipoUsuario.DIRECCION));

        verify(repo).actualizarEstado(eq(f.getId()), eq(EstadoFeriado.VIGENTE),
                any(), any(LocalDateTime.class));
    }

    @Test
    void autorizar_porOP_lanza() {
        assertThatThrownBy(() -> service.autorizar(UUID.randomUUID(), op()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Dirección");
    }

    @Test
    void autorizar_sinPropuestaPendiente_lanza() throws Exception {
        Feriado vigente = feriadoEn(EstadoFeriado.VIGENTE);
        when(repo.readByUUID(vigente.getId())).thenReturn(vigente);

        assertThatThrownBy(() -> service.autorizar(vigente.getId(),
                usuarioConRoles(TipoUsuario.DIRECCION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("propuesta pendiente");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Usuario op() {
        return usuarioConRoles(TipoUsuario.OFICINADEPERSONAL);
    }

    private Usuario usuarioConRoles(TipoUsuario... roles) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
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

    private Feriado feriadoEn(EstadoFeriado estado) {
        return new Feriado(UUID.randomUUID(), LocalDate.of(2026, 10, 12),
                "Feriado de prueba", estado, UUID.randomUUID());
    }
}
