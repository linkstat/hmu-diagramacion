package ar.com.hmu.service;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.Servicio;
import ar.com.hmu.model.Sexo;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.model.Vinculacion;
import ar.com.hmu.repository.DatabaseConnector;
import ar.com.hmu.repository.ServicioRepository;
import ar.com.hmu.service.diagramacion.ModalidadHorariaService;
import ar.com.hmu.service.diagramacion.ModalidadHorariaService.Modalidad;
import ar.com.hmu.service.diagramacion.ModalidadHorariaService.Parametro;
import ar.com.hmu.util.Reloj;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Importar listado de agentes (CU05): la OP carga el padrón inicial desde un
 * CSV; el sistema verifica las columnas (2a), valida cada fila
 * (obligatorios, formato del CUIL, servicio y modalidad existentes,
 * duplicados), muestra válidas y rechazadas con su motivo (4) y, al
 * confirmar, registra las válidas en una única transacción con la cuenta de
 * agente, contraseña inicial y cambio obligatorio (6).
 *
 * <p>Columnas (separador ";"): {@code cuil;apellidos;nombres;sexo;mail;servicio;modalidad;vinculacion}.
 * {@code vinculacion} es opcional (planta permanente por defecto).</p>
 */
public class ImportacionPadronService {

    public static final List<String> COLUMNAS = List.of("cuil", "apellidos", "nombres", "sexo", "mail", "servicio",
            "modalidad", "vinculacion");
    private static final Set<String> OBLIGATORIAS = Set.of("cuil", "apellidos", "nombres", "sexo", "mail", "servicio",
            "modalidad");
    private static final Set<String> PREFIJOS_CUIL = Set.of("20", "23", "24", "27");

    /** Una fila del archivo con su resultado. */
    public record Fila(int numero, long cuil, String apellidos, String nombres, Sexo sexo, String mail,
                       Servicio servicio, Modalidad modalidad, Vinculacion vinculacion, String motivoRechazo) {
        public boolean valida() {
            return motivoRechazo == null;
        }
    }

    public record Analisis(String archivo, List<Fila> filas) {
        public List<Fila> validas() {
            return filas.stream().filter(Fila::valida).toList();
        }

        public long rechazadas() {
            return filas.stream().filter(f -> !f.valida()).count();
        }
    }

    private final DatabaseConnector db;
    private final ServicioRepository servicioRepository;
    private final ModalidadHorariaService modalidades;

    public ImportacionPadronService(DatabaseConnector db, ServicioRepository servicioRepository,
                                    ModalidadHorariaService modalidades) {
        this.db = db;
        this.servicioRepository = servicioRepository;
        this.modalidades = modalidades;
    }

    /** CU05 pasos 2 a 4: lee y valida, sin registrar nada. */
    public Analisis analizar(String archivo, byte[] contenido) throws ServiceException {
        String[] lineas = new String(contenido, StandardCharsets.UTF_8).replace("﻿", "").split("\\r?\\n");
        if (lineas.length == 0 || lineas[0].isBlank()) {
            throw new ServiceException("El archivo está vacío.");
        }
        List<String> encabezado = new ArrayList<>();
        for (String c : lineas[0].split(";")) encabezado.add(normalizar(c));
        List<String> faltan = OBLIGATORIAS.stream().filter(c -> !encabezado.contains(c)).sorted().toList();
        if (!faltan.isEmpty()) {
            throw new ServiceException("Faltan columnas esperadas: " + String.join(", ", faltan)
                    + ".\nColumnas del archivo: " + String.join(";", COLUMNAS) + ".");
        }
        try {
            Map<String, Servicio> servicios = new HashMap<>();
            for (Servicio s : servicioRepository.readAll()) servicios.put(normalizar(s.getNombre()), s);
            Set<Long> cuilsExistentes = new HashSet<>();
            Set<String> mailsExistentes = new HashSet<>();
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT cuil, lower(mail) FROM Usuario");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cuilsExistentes.add(rs.getLong(1));
                    mailsExistentes.add(rs.getString(2));
                }
            }
            Set<Long> cuilsArchivo = new HashSet<>();
            Set<String> mailsArchivo = new HashSet<>();
            List<Fila> filas = new ArrayList<>();
            for (int i = 1; i < lineas.length; i++) {
                if (lineas[i].isBlank()) continue;
                String[] v = lineas[i].split(";", -1);
                Map<String, String> campo = new LinkedHashMap<>();
                for (int k = 0; k < encabezado.size(); k++) campo.put(encabezado.get(k), k < v.length ? v[k].trim() : "");
                filas.add(validar(i + 1, campo, servicios, cuilsExistentes, mailsExistentes, cuilsArchivo, mailsArchivo));
            }
            return new Analisis(archivo, filas);
        } catch (SQLException e) {
            throw new ServiceException("Error al validar el archivo: " + e.getMessage(), e);
        }
    }

    private Fila validar(int numero, Map<String, String> c, Map<String, Servicio> servicios, Set<Long> cuilsBd,
                         Set<String> mailsBd, Set<Long> cuilsArchivo, Set<String> mailsArchivo) {
        String apellidos = c.get("apellidos");
        String nombres = c.get("nombres");
        String mail = c.get("mail").toLowerCase(Locale.ROOT);
        String digitos = c.get("cuil").replaceAll("\\D", "");
        long cuil = digitos.isEmpty() || digitos.length() > 18 ? 0 : Long.parseLong(digitos);
        Servicio servicio = servicios.get(normalizar(c.get("servicio")));
        Modalidad modalidad = modalidad(c.get("modalidad"));
        Sexo sexo = sexo(c.get("sexo"));
        Vinculacion vinc = vinculacion(c.getOrDefault("vinculacion", ""));
        String motivo = null;
        for (String o : OBLIGATORIAS) {
            if (c.get(o) == null || c.get(o).isBlank()) {
                motivo = "Falta el dato obligatorio «" + o + "».";
                break;
            }
        }
        if (motivo == null && (digitos.length() != 11 || !PREFIJOS_CUIL.contains(digitos.substring(0, 2)))) {
            motivo = "CUIL con formato inválido: " + c.get("cuil") + ".";
        } else if (motivo == null && cuilsBd.contains(cuil)) {
            motivo = "El CUIL ya está en el padrón.";
        } else if (motivo == null && !cuilsArchivo.add(cuil)) {
            motivo = "CUIL repetido en el archivo.";
        } else if (motivo == null && !mail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            motivo = "Correo con formato inválido.";
        } else if (motivo == null && (mailsBd.contains(mail) || !mailsArchivo.add(mail))) {
            motivo = "El correo ya está registrado.";
        } else if (motivo == null && sexo == null) {
            motivo = "Sexo inválido (FEMENINO, MASCULINO u OTRO).";
        } else if (motivo == null && servicio == null) {
            motivo = "El servicio «" + c.get("servicio") + "» no existe.";
        } else if (motivo == null && modalidad == null) {
            motivo = "La modalidad «" + c.get("modalidad") + "» no está en el catálogo.";
        } else if (motivo == null && vinc == null) {
            motivo = "Vinculación inválida.";
        }
        return new Fila(numero, cuil, apellidos, nombres, sexo, mail, servicio, modalidad,
                vinc != null ? vinc : Vinculacion.PLANTA_PERMANENTE, motivo);
    }

    /**
     * CU05 paso 6: registra las filas válidas en una única transacción (cuenta
     * de agente, contraseña inicial y cambio obligatorio) y luego asigna la
     * modalidad horaria de cada agente.
     *
     * @return cantidad de agentes registrados.
     */
    public int confirmar(Analisis analisis, Usuario actor) throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal importa el padrón.");
        }
        List<Fila> validas = analisis.validas();
        if (validas.isEmpty()) {
            throw new ServiceException("No hay filas válidas para importar.");
        }
        Map<UUID, Fila> creados = new LinkedHashMap<>();
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID rolAgente;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM Rol WHERE nombre = 'Empleado'");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rolAgente = rs.getObject(1, UUID.class);
                }
                for (Fila f : validas) {
                    Usuario u = new Usuario();
                    u.setCuil(f.cuil());
                    u.setDefaultPassword();
                    UUID id = UUID.randomUUID();
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO Usuario (id, fechaAlta, estado, cuil, apellidos, nombres, sexo, mail, passwordHash,
                                servicioID, cambioPasswordRequerido)
                            VALUES (?, ?, TRUE, ?, ?, ?, ?, ?, ?, ?, TRUE)""")) {
                        ps.setObject(1, id);
                        ps.setDate(2, Date.valueOf(Reloj.hoy()));
                        ps.setLong(3, f.cuil());
                        ps.setString(4, f.apellidos());
                        ps.setString(5, f.nombres());
                        ps.setObject(6, f.sexo().name(), Types.OTHER);
                        ps.setString(7, f.mail());
                        ps.setString(8, u.getEncryptedPassword());
                        ps.setObject(9, f.servicio().getId());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO Agente (id, vinculacion) VALUES (?, ?::vinculacion)")) {
                        ps.setObject(1, id);
                        ps.setString(2, f.vinculacion().name());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO AsignacionRol (usuarioID, rolID, fechaDesde, otorgadoPorID) VALUES (?, ?, ?, ?)")) {
                        ps.setObject(1, id);
                        ps.setObject(2, rolAgente);
                        ps.setDate(3, Date.valueOf(Reloj.hoy()));
                        ps.setObject(4, actor.getId());
                        ps.executeUpdate();
                    }
                    creados.put(id, f);
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ServiceException("No se importó ningún agente: " + e.getMessage(), e);
        }
        // Modalidad de cada agente (CU04 incluido), con sus parámetros predeterminados.
        for (Map.Entry<UUID, Fila> e : creados.entrySet()) {
            Usuario agente = new Usuario();
            agente.setId(e.getKey());
            Map<String, Integer> valores = new LinkedHashMap<>();
            for (Parametro p : e.getValue().modalidad().parametros()) valores.put(p.clave(), p.valor());
            modalidades.asignar(agente, e.getValue().modalidad(), valores, Reloj.hoy(), null, null, actor);
        }
        return creados.size();
    }

    private static Modalidad modalidad(String texto) {
        String t = normalizar(texto);
        for (Modalidad m : Modalidad.values()) {
            if (normalizar(m.toString()).equals(t) || normalizar(m.name()).equals(t)) return m;
        }
        return null;
    }

    private static Sexo sexo(String texto) {
        String t = normalizar(texto);
        return switch (t) {
            case "femenino", "f" -> Sexo.FEMENINO;
            case "masculino", "m" -> Sexo.MASCULINO;
            case "otro", "x" -> Sexo.OTRO;
            default -> null;
        };
    }

    private static Vinculacion vinculacion(String texto) {
        String t = normalizar(texto);
        if (t.isEmpty()) return Vinculacion.PLANTA_PERMANENTE;
        for (Vinculacion v : Vinculacion.values()) {
            if (normalizar(v.name()).equals(t.replace(' ', '_')) || normalizar(v.toString()).equals(t)) return v;
        }
        return null;
    }

    /** Minúsculas sin acentos para comparar nombres. */
    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
