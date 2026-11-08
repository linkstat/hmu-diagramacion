package ar.com.hmu.service.asistencia;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.ParteDiario;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.DatabaseConnector;
import ar.com.hmu.repository.ParteDiarioRepository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultar indicadores (CU36): ausentismo por tipo de novedad y cobertura
 * por franja, por servicio y período, sobre los partes cerrados (vistas
 * v_indicador_ausentismo y v_indicador_cobertura). Sólo OP y DH; no
 * identifica agentes.
 */
public class IndicadoresService {

    public static final List<String> TIPOS = List.of("Licencias", "Faltas justificadas",
            "Faltas injustificadas", "Francos compensatorios", "Pases de salida", "Llegadas tarde");

    /** Ausentismo de un servicio en el período: horas por tipo y porcentaje sobre lo programado. */
    public record Ausentismo(String servicio, double horasProgramadas, Map<String, Double> horasPorTipo) {
        public double horasAusencia() {
            return horasPorTipo.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public double porcentaje() {
            return horasProgramadas > 0 ? 100.0 * horasAusencia() / horasProgramadas : 0;
        }
    }

    public record Cobertura(LocalDate fecha, String servicio, String franja, int desde, int hasta, int diagramados,
                            int presentes, int minimo) {
        public boolean bajoMinimo() {
            return presentes < minimo;
        }
    }

    public record Resultado(List<LocalDate> diasCerrados, List<LocalDate> diasAbiertos, List<Ausentismo> ausentismo,
                            List<Cobertura> cobertura) {
    }

    private final DatabaseConnector db;
    private final ParteDiarioRepository partes;

    public IndicadoresService(DatabaseConnector db, ParteDiarioRepository partes) {
        this.db = db;
        this.partes = partes;
    }

    public Resultado calcular(LocalDate desde, LocalDate hasta, UUID servicioId, Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL, TipoUsuario.DIRECCION)) {
            throw new ServiceException("Los indicadores son para la Oficina de Personal y la Dirección.");
        }
        try {
            List<LocalDate> cerrados = partes.fechasConParte(desde, hasta, ParteDiario.Estado.CERRADO);
            List<LocalDate> abiertos = partes.fechasConParte(desde, hasta, ParteDiario.Estado.ABIERTO);
            return new Resultado(cerrados, abiertos, ausentismo(desde, hasta, servicioId), cobertura(desde, hasta, servicioId));
        } catch (SQLException e) {
            throw new ServiceException("Error al calcular los indicadores: " + e.getMessage(), e);
        }
    }

    private List<Ausentismo> ausentismo(LocalDate desde, LocalDate hasta, UUID servicioId) throws SQLException {
        Map<String, double[]> programado = new LinkedHashMap<>();
        Map<String, Map<String, Double>> porTipo = new LinkedHashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT servicio, tipo, sum(minutosProgramados) AS prog, sum(minutosAusencia) AS aus
                     FROM v_indicador_ausentismo
                     WHERE fecha BETWEEN ? AND ? AND (?::uuid IS NULL OR servicioID = ?::uuid)
                     GROUP BY servicio, tipo ORDER BY servicio""")) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            ps.setObject(3, servicioId);
            ps.setObject(4, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String s = rs.getString("servicio");
                    programado.computeIfAbsent(s, k -> new double[1])[0] += rs.getDouble("prog") / 60.0;
                    Map<String, Double> m = porTipo.computeIfAbsent(s, k -> new LinkedHashMap<>());
                    String tipo = rs.getString("tipo");
                    if (tipo != null) m.merge(tipo, rs.getDouble("aus") / 60.0, Double::sum);
                }
            }
        }
        List<Ausentismo> r = new ArrayList<>();
        programado.forEach((s, p) -> r.add(new Ausentismo(s, p[0], porTipo.getOrDefault(s, Map.of()))));
        return r;
    }

    private List<Cobertura> cobertura(LocalDate desde, LocalDate hasta, UUID servicioId) throws SQLException {
        List<Cobertura> r = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT * FROM v_indicador_cobertura
                     WHERE fecha BETWEEN ? AND ? AND (?::uuid IS NULL OR servicioID = ?::uuid)
                     ORDER BY fecha, servicio, desde""")) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            ps.setObject(3, servicioId);
            ps.setObject(4, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.add(new Cobertura(rs.getDate("fecha").toLocalDate(), rs.getString("servicio"),
                            rs.getString("franja"), rs.getInt("desde"), rs.getInt("hasta"), rs.getInt("diagramados"),
                            rs.getInt("presentes"), rs.getInt("minimo")));
                }
            }
        }
        return r;
    }
}
