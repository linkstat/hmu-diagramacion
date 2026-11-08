package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.ReglaValidacion;
import ar.com.hmu.model.Severidad;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.ReglaValidacionRepository;
import ar.com.hmu.service.diagramacion.CatalogoReglas;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parametrizar reglas de validación (CU08): la OP modifica los parámetros,
 * la severidad o la activación de una regla con la fecha desde la que rigen;
 * el sistema valida tipo y rango (4a), cierra la versión anterior y registra
 * la nueva (la auditoría la registran los disparadores). El motor de
 * validación lee el catálogo vigente con {@link #catalogo}.
 */
public class ReglaValidacionService {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReglaValidacionRepository repository;

    public ReglaValidacionService(ReglaValidacionRepository repository) {
        this.repository = repository;
    }

    public List<ReglaValidacion> vigentes(LocalDate fecha) throws ServiceException {
        try {
            return repository.findVigentes(fecha);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer las reglas de validación", e);
        }
    }

    public List<ReglaValidacion> versiones(String codigo) throws ServiceException {
        try {
            return repository.findVersiones(codigo);
        } catch (SQLException e) {
            throw new ServiceException("Error al leer las versiones de la regla", e);
        }
    }

    /** Catálogo vigente a la fecha; ante un error de lectura, los valores predeterminados. */
    public CatalogoReglas catalogo(LocalDate fecha) {
        try {
            return CatalogoReglas.de(repository.findVigentes(fecha));
        } catch (SQLException e) {
            System.err.println("ReglaValidacionService: se usan las reglas predeterminadas: " + e.getMessage());
            return CatalogoReglas.PREDETERMINADO;
        }
    }

    /**
     * CU08 3a: aviso cuando la nueva versión rige dentro del período de
     * diagramas ya aprobados (que conservan la versión con la que se validaron).
     */
    public Optional<String> advertenciaVigencia(LocalDate desde) {
        try {
            int n = repository.contarDiagramasAprobadosDesde(desde);
            if (n > 0) {
                boolean uno = n == 1;
                return Optional.of("Hay " + n + (uno ? " diagrama aprobado que incluye" : " diagramas aprobados que incluyen")
                        + " el " + desde.format(DDMMYYYY) + ": " + (uno ? "conserva" : "conservan")
                        + " la versión de la regla con la que " + (uno ? "fue validado" : "fueron validados")
                        + ". La nueva versión se aplica a los diagramas y "
                        + "novedades que se validen desde ahora.");
            }
        } catch (SQLException ignored) {
            // Sin aviso.
        }
        return Optional.empty();
    }

    /** CU08 pasos 3 a 5. */
    public ReglaValidacion registrarNuevaVersion(ReglaValidacion actual, Map<String, String> parametros,
                                                 Severidad severidad, boolean activa, LocalDate desde,
                                                 Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal parametriza las reglas de validación.");
        }
        if (desde == null) {
            throw new ServiceException("Indique la fecha desde la que rige la nueva versión.");
        }
        if (!desde.isAfter(actual.getVigenteDesde())) {
            throw new ServiceException("La nueva versión debe regir después del "
                    + actual.getVigenteDesde().format(DDMMYYYY) + " (inicio de la versión vigente).");
        }
        Map<String, Double> valores = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : parametros.entrySet()) {
            valores.put(e.getKey(), validarParametro(e.getKey(), e.getValue()));
        }
        ReglaValidacion nueva = new ReglaValidacion();
        nueva.setCodigo(actual.getCodigo());
        nueva.setNombre(actual.getNombre());
        nueva.setDescripcion(actual.getDescripcion());
        nueva.setAmbito(actual.getAmbito());
        nueva.setModalidad(actual.getModalidad());
        nueva.setFundamentoNormativo(actual.getFundamentoNormativo());
        nueva.setSeveridad(severidad);
        nueva.setParametros(valores);
        nueva.setVigenteDesde(desde);
        nueva.setActiva(activa);
        try {
            repository.registrarVersion(nueva);
            return nueva;
        } catch (SQLException e) {
            throw new ServiceException("Error al registrar la nueva versión de la regla: " + e.getMessage(), e);
        }
    }

    /** CU08 4a: tipo y rango de cada parámetro según su nombre. */
    static double validarParametro(String clave, String texto) throws ServiceException {
        double v;
        try {
            v = Double.parseDouble(texto.trim().replace(',', '.'));
        } catch (NumberFormatException | NullPointerException e) {
            throw new ServiceException("El parámetro «" + clave + "» debe ser un número.");
        }
        double maximo = clave.startsWith("tope") ? 31 : clave.startsWith("porcentaje") ? 100
                : clave.endsWith("Desde") ? 23 : 48;
        double minimo = clave.equals("horasMinimas") || clave.equals("horasMaximas") ? 1 : 0;
        if (v < minimo || v > maximo || v != Math.rint(v)) {
            throw new ServiceException("El parámetro «" + clave + "» debe ser un entero entre "
                    + (int) minimo + " y " + (int) maximo + ".");
        }
        return v;
    }
}
