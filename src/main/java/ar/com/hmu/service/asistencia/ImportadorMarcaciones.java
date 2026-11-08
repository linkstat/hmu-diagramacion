package ar.com.hmu.service.asistencia;

import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.MarcacionRepository;
import ar.com.hmu.repository.MarcacionRepository.Marcacion;
import ar.com.hmu.util.Reloj;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Importar marcaciones (CU30): la OP carga el archivo exportado por la
 * aplicación de los relojes; el sistema registra el lote con su hash (no se
 * importa dos veces el mismo archivo), asocia cada marcación al agente por
 * CUIL, conforma las jornadas registradas (ingreso y egreso) y detecta las
 * omisiones. La importación es repetible: las jornadas de los días del
 * archivo se reconforman con todas las marcaciones guardadas.
 *
 * <p>Formato CSV (separador ";"): {@code cuil;fecha_hora;sentido;dispositivo},
 * con fecha_hora {@code dd/MM/yyyy HH:mm} y sentido {@code E} (entrada) o
 * {@code S} (salida). La primera fila puede ser el encabezado.</p>
 */
public class ImportadorMarcaciones {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Un egreso se empareja con el ingreso previo si están a menos de este lapso. */
    private static final Duration MAX_JORNADA = Duration.ofHours(26);

    /** Resumen que se muestra a la OP (CU30 paso 5). */
    public record Resumen(LoteMarcaciones lote, int marcaciones, int agentes, int jornadas, int omisiones,
                          Map<String, Integer> sinAgente, List<String> filasRechazadas) {
    }

    private final MarcacionRepository repo;

    public ImportadorMarcaciones(MarcacionRepository repo) {
        this.repo = repo;
    }

    public Resumen importar(String nombreArchivo, byte[] contenido, Usuario actor) throws ServiceException {
        return procesar(nombreArchivo, contenido, actor, true);
    }

    /** Lee y resume el archivo sin guardar nada (vista previa; modo de capturas). */
    public Resumen analizar(String nombreArchivo, byte[] contenido, Usuario actor) throws ServiceException {
        return procesar(nombreArchivo, contenido, actor, false);
    }

    private Resumen procesar(String nombreArchivo, byte[] contenido, Usuario actor, boolean guardar)
            throws ServiceException {
        String hash = sha256(contenido);
        try {
            if (repo.existeHash(hash)) {
                throw new ServiceException("El archivo «" + nombreArchivo + "» ya fue importado (mismo contenido): "
                        + "no se duplican las marcaciones.");
            }
            Map<Long, UUID> padron = repo.agentesPorCuil();
            List<Marcacion> marcaciones = new ArrayList<>();
            Map<String, Integer> sinAgente = new TreeMap<>();
            List<String> rechazadas = new ArrayList<>();
            String[] lineas = new String(contenido, StandardCharsets.UTF_8).split("\\r?\\n");
            for (int i = 0; i < lineas.length; i++) {
                String linea = lineas[i].strip();
                if (linea.isEmpty() || (i == 0 && linea.toLowerCase().startsWith("cuil"))) continue;
                String[] c = linea.split(";");
                try {
                    long cuil = Long.parseLong(c[0].replaceAll("\\D", ""));
                    LocalDateTime fh = LocalDateTime.parse(c[1].trim(), FORMATO);
                    String sentido = c[2].trim().toUpperCase();
                    if (!sentido.equals("E") && !sentido.equals("S")) throw new IllegalArgumentException("sentido");
                    UUID agente = padron.get(cuil);
                    if (agente == null) {
                        sinAgente.merge(c[0].trim(), 1, Integer::sum);
                        continue;
                    }
                    marcaciones.add(new Marcacion(UUID.randomUUID(), agente, fh, sentido.equals("E"),
                            c.length > 3 ? c[3].trim() : null));
                } catch (RuntimeException e) {
                    rechazadas.add("Fila " + (i + 1) + ": " + linea);
                }
            }
            if (marcaciones.isEmpty()) {
                throw new ServiceException("El archivo no tiene marcaciones de agentes del padrón.");
            }
            LocalDateTime desde = marcaciones.stream().map(Marcacion::fechaHora).min(LocalDateTime::compareTo).get();
            LocalDateTime hasta = marcaciones.stream().map(Marcacion::fechaHora).max(LocalDateTime::compareTo).get();
            LoteMarcaciones lote = new LoteMarcaciones(UUID.randomUUID(), nombreArchivo, hash, Reloj.ahora(),
                    actor.getId(), actor.getApellidosNombres(), marcaciones.size(), desde, hasta);

            Map<UUID, List<Marcacion>> porAgente = new LinkedHashMap<>();
            marcaciones.forEach(m -> porAgente.computeIfAbsent(m.agenteId(), k -> new ArrayList<>()).add(m));
            int jornadas = 0;
            int omisiones = 0;
            if (!guardar) {
                for (Map.Entry<UUID, List<Marcacion>> e : porAgente.entrySet()) {
                    for (JornadaRegistrada j : conformar(e.getKey(), e.getValue())) {
                        jornadas++;
                        if (j.omisionIngreso() || j.omisionEgreso()) omisiones++;
                    }
                }
                return new Resumen(lote, marcaciones.size(), porAgente.size(), jornadas, omisiones, sinAgente, rechazadas);
            }
            try (Connection c = repo.conexion()) {
                c.setAutoCommit(false);
                try {
                    repo.insertarLote(c, lote);
                    repo.insertarMarcaciones(c, lote.id(), marcaciones);
                    LocalDate d1 = desde.toLocalDate().minusDays(1);
                    LocalDate d2 = hasta.toLocalDate();
                    for (UUID agente : porAgente.keySet()) {
                        repo.borrarJornadasRegistradas(c, agente, d1, d2);
                        List<Marcacion> todas = repo.marcacionesDe(c, agente, d1.atStartOfDay(), d2.plusDays(1).atStartOfDay());
                        for (JornadaRegistrada j : conformar(agente, todas)) {
                            repo.insertarJornadaRegistrada(c, j);
                            jornadas++;
                            if (j.omisionIngreso() || j.omisionEgreso()) omisiones++;
                        }
                    }
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                }
            }
            return new Resumen(lote, marcaciones.size(), porAgente.size(), jornadas, omisiones, sinAgente, rechazadas);
        } catch (SQLException e) {
            throw new ServiceException("Error al importar las marcaciones: " + e.getMessage(), e);
        }
    }

    /**
     * Conforma las jornadas registradas: cada ingreso se empareja con el
     * egreso siguiente (dentro de 26 h, admite el cruce de medianoche); un
     * ingreso sin egreso o un egreso sin ingreso quedan como omisión.
     */
    static List<JornadaRegistrada> conformar(UUID agente, List<Marcacion> marcaciones) {
        List<JornadaRegistrada> r = new ArrayList<>();
        Marcacion abierta = null;
        for (Marcacion m : marcaciones.stream().sorted((a, b) -> a.fechaHora().compareTo(b.fechaHora())).toList()) {
            if (m.ingreso()) {
                if (abierta != null) {
                    r.add(new JornadaRegistrada(UUID.randomUUID(), agente, abierta.fechaHora().toLocalDate(),
                            abierta.fechaHora(), null, abierta.id(), null));
                }
                abierta = m;
            } else if (abierta != null && Duration.between(abierta.fechaHora(), m.fechaHora()).compareTo(MAX_JORNADA) <= 0) {
                r.add(new JornadaRegistrada(UUID.randomUUID(), agente, abierta.fechaHora().toLocalDate(),
                        abierta.fechaHora(), m.fechaHora(), abierta.id(), m.id()));
                abierta = null;
            } else {
                r.add(new JornadaRegistrada(UUID.randomUUID(), agente, m.fechaHora().toLocalDate(), null,
                        m.fechaHora(), null, m.id()));
            }
        }
        if (abierta != null) {
            r.add(new JornadaRegistrada(UUID.randomUUID(), agente, abierta.fechaHora().toLocalDate(),
                    abierta.fechaHora(), null, abierta.id(), null));
        }
        return r;
    }

    static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
