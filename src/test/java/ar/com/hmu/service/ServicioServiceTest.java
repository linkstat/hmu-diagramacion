package ar.com.hmu.service;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Agrupacion;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link ServicioService}.
 *
 * Foco en la nueva lógica de "encargado actual" del servicio: validación de
 * pertenencia, manejo de nulos, y delegación a los repositories.
 */
@ExtendWith(MockitoExtension.class)
class ServicioServiceTest {

    @Mock
    private ServicioRepository servicioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private ServicioService service;

    @BeforeEach
    void setUp() {
        service = new ServicioService(servicioRepository, usuarioRepository);
    }

    @Test
    void getEncargado_devuelveUUIDActual() throws Exception {
        UUID servicioId = UUID.randomUUID();
        UUID encargadoId = UUID.randomUUID();
        when(servicioRepository.findEncargadoByServicio(servicioId)).thenReturn(encargadoId);

        UUID resultado = service.getEncargado(servicioId);

        assertThat(resultado).isEqualTo(encargadoId);
    }

    @Test
    void getEncargado_servicioSinEncargado_devuelveNull() throws Exception {
        UUID servicioId = UUID.randomUUID();
        when(servicioRepository.findEncargadoByServicio(servicioId)).thenReturn(null);

        UUID resultado = service.getEncargado(servicioId);

        assertThat(resultado).isNull();
    }

    @Test
    void setEncargado_usuarioPerteneceAlServicio_persiste() throws Exception {
        UUID servicioId = UUID.randomUUID();
        UUID encargadoId = UUID.randomUUID();
        Servicio servicio = servicioConId(servicioId);
        Usuario candidato = usuarioConServicio(encargadoId, servicioId);

        when(servicioRepository.readByUUID(servicioId)).thenReturn(servicio);
        when(usuarioRepository.readByUUID(encargadoId)).thenReturn(candidato);

        service.setEncargado(servicioId, encargadoId);

        verify(servicioRepository).updateEncargado(servicioId, encargadoId);
    }

    @Test
    void setEncargado_usuarioNoPerteneceAlServicio_lanzaServiceException() throws Exception {
        UUID servicioId = UUID.randomUUID();
        UUID otroServicioId = UUID.randomUUID();
        UUID candidatoId = UUID.randomUUID();
        Servicio servicio = servicioConId(servicioId);
        Usuario candidato = usuarioConServicio(candidatoId, otroServicioId);

        when(servicioRepository.readByUUID(servicioId)).thenReturn(servicio);
        when(usuarioRepository.readByUUID(candidatoId)).thenReturn(candidato);

        assertThatThrownBy(() -> service.setEncargado(servicioId, candidatoId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no pertenece al servicio");

        verify(servicioRepository, never()).updateEncargado(any(), any());
    }

    @Test
    void setEncargado_usuarioInexistente_lanzaServiceException() throws Exception {
        UUID servicioId = UUID.randomUUID();
        UUID candidatoId = UUID.randomUUID();
        Servicio servicio = servicioConId(servicioId);

        when(servicioRepository.readByUUID(servicioId)).thenReturn(servicio);
        when(usuarioRepository.readByUUID(candidatoId)).thenReturn(null);

        assertThatThrownBy(() -> service.setEncargado(servicioId, candidatoId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no existe");

        verify(servicioRepository, never()).updateEncargado(any(), any());
    }

    @Test
    void setEncargado_servicioInexistente_lanzaServiceException() throws Exception {
        UUID servicioId = UUID.randomUUID();
        UUID candidatoId = UUID.randomUUID();

        when(servicioRepository.readByUUID(servicioId)).thenReturn(null);

        assertThatThrownBy(() -> service.setEncargado(servicioId, candidatoId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("servicio no existe");

        verify(servicioRepository, never()).updateEncargado(any(), any());
    }

    @Test
    void setEncargado_null_desasignaSinValidarPertenencia() throws Exception {
        UUID servicioId = UUID.randomUUID();
        Servicio servicio = servicioConId(servicioId);

        when(servicioRepository.readByUUID(servicioId)).thenReturn(servicio);

        service.setEncargado(servicioId, null);

        verify(servicioRepository).updateEncargado(servicioId, null);
        verify(usuarioRepository, never()).readByUUID(any());
    }

    @Test
    void setEncargado_repositoryFalla_propagacomoServiceException() throws Exception {
        UUID servicioId = UUID.randomUUID();
        when(servicioRepository.readByUUID(servicioId)).thenThrow(new SQLException("BD caída"));

        assertThatThrownBy(() -> service.setEncargado(servicioId, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Error al actualizar el encargado");
    }

    @Test
    void findUsuariosByServicio_delegaAlRepository() throws Exception {
        UUID servicioId = UUID.randomUUID();
        List<Usuario> usuarios = List.of(usuarioConServicio(UUID.randomUUID(), servicioId));
        when(usuarioRepository.findUsuariosByServicio(servicioId)).thenReturn(usuarios);

        List<Usuario> resultado = service.findUsuariosByServicio(servicioId);

        assertThat(resultado).hasSize(1);
        verify(usuarioRepository).findUsuariosByServicio(servicioId);
    }

    // ----- Helpers -----

    private Servicio servicioConId(UUID id) {
        Servicio s = new Servicio();
        s.setId(id);
        s.setNombre("Servicio de prueba");
        s.setAgrupacion(Agrupacion.SERVICIO);
        return s;
    }

    private Usuario usuarioConServicio(UUID id, UUID servicioId) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setServicioId(servicioId);
        return u;
    }
}
