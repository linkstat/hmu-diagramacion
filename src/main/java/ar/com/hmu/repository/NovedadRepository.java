package ar.com.hmu.repository;

import ar.com.hmu.factory.DiagramaFactory;
import ar.com.hmu.model.EstadoNovedad;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import ar.com.hmu.model.EstadoNovedadAutorizacion;
import ar.com.hmu.model.InstanciaNovedad;
import ar.com.hmu.model.Novedad;
import ar.com.hmu.model.NovedadAdjunto;
import ar.com.hmu.model.NovedadAutorizacion;
import ar.com.hmu.model.OrigenNovedad;
import ar.com.hmu.model.TipoNovedad;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de novedades sobre Novedad, TipoNovedad, NovedadAutorizacion
 * y NovedadAdjunto (E4, Tablas 52 y 53).
 *
 * <p>Las transiciones de estado se hacen en una sola transacción
 * ({@link #enTransaccion}) junto con las filas de autorización, y con
 * concurrencia optimista por {@code version} ({@link #cambiarEstado}).</p>
 */
public class NovedadRepository {

    /** Unidad de trabajo dentro de una transacción. */
    @FunctionalInterface
    public interface Trabajo<T> {
        T ejecutar(Connection connection) throws SQLException;
    }

    private static final String SELECT_NOVEDAD = """
            SELECT n.*, t.codigo, t.nombre AS tipoNombre, t.requiereContraparte, t.afectaSaldoHoras,
                   t.requiereDocumentacion, t.esExterna, t.activo, %s AS tipoCodigoNovedad,
                   %s AS codigoNovedad,
                   ua.apellidos || ', ' || ua.nombres AS agenteNombre,
                   uc.apellidos || ', ' || uc.nombres AS contraparteNombre,
                   us.apellidos || ', ' || us.nombres AS solicitadoPorNombre,
                   s.id AS servicioId, s.nombre AS servicioNombre
            FROM Novedad n
            JOIN TipoNovedad t ON t.id = n.tipoNovedadID
            JOIN Usuario ua ON ua.id = n.agenteID
            LEFT JOIN Usuario uc ON uc.id = n.contraparteAgenteID
            JOIN Usuario us ON us.id = n.solicitadoPorID
            LEFT JOIN Servicio s ON s.id = ua.servicioID
            """.formatted(TraduccionCodigoNovedad.codigoDe("t.codigoNovedadID"),
                    TraduccionCodigoNovedad.codigoDe("n.codigoNovedadID"));

    private final DatabaseConnector databaseConnector;

    public NovedadRepository(DatabaseConnector databaseConnector) {
        this.databaseConnector = databaseConnector;
    }

    // ============================================================
    // Transacciones
    // ============================================================

    public <T> T enTransaccion(Trabajo<T> trabajo) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private <T> T conConexion(Trabajo<T> trabajo) throws SQLException {
        try (Connection connection = databaseConnector.getConnection()) {
            return trabajo.ejecutar(connection);
        }
    }

    // ============================================================
    // Tipos de novedad
    // ============================================================

    public List<TipoNovedad> findTiposActivos() throws SQLException {
        return conConexion(c -> {
            List<TipoNovedad> tipos = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t.*, " + TraduccionCodigoNovedad.codigoDe("t.codigoNovedadID") + " AS codigoNovedad"
                            + " FROM TipoNovedad t WHERE t.activo ORDER BY t.esExterna, t.nombre");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tipos.add(new TipoNovedad(rs.getObject("id", UUID.class), rs.getString("codigo"),
                            rs.getString("nombre"), rs.getBoolean("requiereContraparte"),
                            rs.getInt("afectaSaldoHoras"), rs.getBoolean("requiereDocumentacion"),
                            rs.getBoolean("esExterna"), rs.getBoolean("activo"),
                            rs.getObject("codigoNovedad", Integer.class)));
                }
            }
            return tipos;
        });
    }

    /** Catálogo de códigos numéricos de novedad (tabla de Capital Humano + internos del HMU). */
    public List<ar.com.hmu.model.CodigoNovedad> findCodigos() throws SQLException {
        return conConexion(c -> {
            List<ar.com.hmu.model.CodigoNovedad> codigos = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM CodigoNovedad WHERE activo ORDER BY codigo");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codigos.add(new ar.com.hmu.model.CodigoNovedad(rs.getObject("id", UUID.class), rs.getInt("codigo"),
                            rs.getString("denominacionHmu"), rs.getString("denominacionCapitalHumano"),
                            rs.getBoolean("esLicencia"), rs.getString("sigla")));
                }
            }
            return codigos;
        });
    }

    // ============================================================
    // Lectura
    // ============================================================

    public Novedad findById(UUID id) throws SQLException {
        List<Novedad> r = buscar(" WHERE n.id = ?", List.of(id));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Todas las novedades del hospital (OP y DH). */
    public List<Novedad> findTodas() throws SQLException {
        return buscar("", List.of());
    }

    /** Novedades de los agentes de un servicio (JS). */
    public List<Novedad> findByServicio(UUID servicioId) throws SQLException {
        return buscar(" WHERE ua.servicioID = ?", List.of(servicioId));
    }

    /** Novedades en las que participa el usuario (agente). */
    public List<Novedad> findDeUsuario(UUID usuarioId) throws SQLException {
        return buscar(" WHERE n.agenteID = ? OR n.contraparteAgenteID = ? OR n.solicitadoPorID = ?",
                List.of(usuarioId, usuarioId, usuarioId));
    }

    /**
     * Novedades del agente (como titular o contraparte) que se superponen con
     * el rango, en los estados indicados. Para las validaciones del CU21.
     */
    public List<Novedad> findDelAgenteEnRango(UUID agenteId, LocalDate desde, LocalDate hasta,
                                             Collection<EstadoNovedad> estados) throws SQLException {
        StringBuilder in = new StringBuilder();
        for (EstadoNovedad e : estados) {
            in.append(in.isEmpty() ? "" : ",").append('\'').append(e.name()).append('\'');
        }
        return buscar(" WHERE (n.agenteID = ? OR n.contraparteAgenteID = ?)"
                        + " AND n.fechaDesde <= ? AND n.fechaHasta >= ?"
                        + " AND n.estado::text IN (" + in + ")",
                List.of(agenteId, agenteId, hasta, desde));
    }

    /** Novedades aprobadas, aplicadas o en trámite que tocan la fecha (cotejo, CU31). */
    public List<Novedad> findQueTocanFecha(LocalDate fecha) throws SQLException {
        return buscar(" WHERE ? BETWEEN n.fechaDesde AND n.fechaHasta AND n.estado::text IN ('APROBADA', 'APLICADA',"
                + " 'PENDIENTE_JEFATURA', 'PENDIENTE_OFICINA_PERSONAL', 'PENDIENTE_DIRECCION')", List.of(fecha));
    }

    private List<Novedad> buscar(String where, List<Object> parametros) throws SQLException {
        return conConexion(c -> {
            List<Novedad> novedades = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(SELECT_NOVEDAD + where
                    + " ORDER BY n.fechaDesde DESC, ua.apellidos")) {
                for (int i = 0; i < parametros.size(); i++) {
                    Object p = parametros.get(i);
                    ps.setObject(i + 1, p instanceof LocalDate d ? Date.valueOf(d) : p);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        novedades.add(mapear(rs));
                    }
                }
            }
            return novedades;
        });
    }

    private Novedad mapear(ResultSet rs) throws SQLException {
        Novedad n = new Novedad();
        n.setId(rs.getObject("id", UUID.class));
        n.setTipo(new TipoNovedad(rs.getObject("tipoNovedadID", UUID.class), rs.getString("codigo"),
                rs.getString("tipoNombre"), rs.getBoolean("requiereContraparte"),
                rs.getInt("afectaSaldoHoras"), rs.getBoolean("requiereDocumentacion"),
                rs.getBoolean("esExterna"), rs.getBoolean("activo"),
                rs.getObject("tipoCodigoNovedad", Integer.class)));
        n.setCodigoNovedad(rs.getObject("codigoNovedad", Integer.class));
        n.setNovedadOrigenId(rs.getObject("novedadOrigenID", UUID.class));
        n.setAgenteId(rs.getObject("agenteID", UUID.class));
        n.setContraparteAgenteId(rs.getObject("contraparteAgenteID", UUID.class));
        n.setFechaDesde(rs.getDate("fechaDesde").toLocalDate());
        n.setFechaHasta(rs.getDate("fechaHasta").toLocalDate());
        Time hd = rs.getTime("horaDesde");
        n.setHoraDesde(hd != null ? hd.toLocalTime() : null);
        Time hh = rs.getTime("horaHasta");
        n.setHoraHasta(hh != null ? hh.toLocalTime() : null);
        n.setCantHoras(rs.getBigDecimal("cantHoras"));
        n.setMotivo(rs.getString("motivo"));
        n.setJustificacion(rs.getString("justificacion"));
        n.setEstado(EstadoNovedad.valueOf(rs.getString("estado")));
        n.setOrigen(OrigenNovedad.valueOf(rs.getString("origen")));
        n.setSolicitadoPorId(rs.getObject("solicitadoPorID", UUID.class));
        Timestamp fs = rs.getTimestamp("fechaSolicitud");
        n.setFechaSolicitud(fs != null ? fs.toLocalDateTime() : null);
        n.setReferenciaExterna(rs.getString("referenciaExterna"));
        n.setVersion(rs.getInt("version"));
        n.setCreatedAt(rs.getTimestamp("createdAt").toLocalDateTime());
        n.setUpdatedAt(rs.getTimestamp("updatedAt").toLocalDateTime());
        n.setAgenteNombre(rs.getString("agenteNombre"));
        n.setContraparteNombre(rs.getString("contraparteNombre"));
        n.setSolicitadoPorNombre(rs.getString("solicitadoPorNombre"));
        n.setServicioId(rs.getObject("servicioId", UUID.class));
        n.setServicioNombre(rs.getString("servicioNombre"));
        return n;
    }

    public List<NovedadAutorizacion> findAutorizaciones(UUID novedadId) throws SQLException {
        return autorizaciones("WHERE a.novedadID = ?", novedadId);
    }

    /** Instancias de todas las novedades (para resolver las bandejas en una consulta). */
    public java.util.Map<UUID, List<NovedadAutorizacion>> findAutorizacionesPorNovedad() throws SQLException {
        java.util.Map<UUID, List<NovedadAutorizacion>> mapa = new java.util.HashMap<>();
        for (NovedadAutorizacion a : autorizaciones("", null)) {
            mapa.computeIfAbsent(a.getNovedadId(), k -> new ArrayList<>()).add(a);
        }
        return mapa;
    }

    private List<NovedadAutorizacion> autorizaciones(String where, UUID novedadId) throws SQLException {
        return conConexion(c -> {
            List<NovedadAutorizacion> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT a.*, u.apellidos || ', ' || u.nombres AS resueltoPorNombre
                    FROM NovedadAutorizacion a LEFT JOIN Usuario u ON u.id = a.resueltoPorID
                    """ + where + " ORDER BY a.novedadID, a.orden")) {
                if (novedadId != null) {
                    ps.setObject(1, novedadId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        NovedadAutorizacion a = new NovedadAutorizacion(rs.getObject("id", UUID.class),
                                rs.getObject("novedadID", UUID.class), InstanciaNovedad.valueOf(rs.getString("instancia")),
                                rs.getInt("orden"), EstadoNovedadAutorizacion.valueOf(rs.getString("estado")));
                        a.setResueltoPorId(rs.getObject("resueltoPorID", UUID.class));
                        a.setResueltoPorNombre(rs.getString("resueltoPorNombre"));
                        Timestamp f = rs.getTimestamp("fechaResolucion");
                        a.setFechaResolucion(f != null ? f.toLocalDateTime() : null);
                        a.setComentarios(rs.getString("comentarios"));
                        lista.add(a);
                    }
                }
            }
            return lista;
        });
    }

    /** Metadatos de los adjuntos (sin el contenido). */
    public List<NovedadAdjunto> findAdjuntos(UUID novedadId) throws SQLException {
        return conConexion(c -> {
            List<NovedadAdjunto> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, nombreArchivo, tipoMime, octet_length(contenido) AS tamanio, cargadoPorID, fechaCarga
                    FROM NovedadAdjunto WHERE novedadID = ? ORDER BY fechaCarga""")) {
                ps.setObject(1, novedadId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        NovedadAdjunto a = new NovedadAdjunto();
                        a.setId(rs.getObject("id", UUID.class));
                        a.setNovedadId(novedadId);
                        a.setNombreArchivo(rs.getString("nombreArchivo"));
                        a.setTipoMime(rs.getString("tipoMime"));
                        a.setTamanio(rs.getLong("tamanio"));
                        a.setCargadoPorId(rs.getObject("cargadoPorID", UUID.class));
                        a.setFechaCarga(rs.getTimestamp("fechaCarga").toLocalDateTime());
                        lista.add(a);
                    }
                }
            }
            return lista;
        });
    }

    public byte[] leerContenidoAdjunto(UUID adjuntoId) throws SQLException {
        return conConexion(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT contenido FROM NovedadAdjunto WHERE id = ?")) {
                ps.setObject(1, adjuntoId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getBytes(1) : null;
                }
            }
        });
    }

    /** Saldo vigente del banco de horas: horas sin usar de los créditos no vencidos a la fecha. */
    public BigDecimal findSaldoHoras(UUID agenteId, LocalDate fecha) throws SQLException {
        return conConexion(c -> saldoVigente(c, agenteId, fecha));
    }

    private static BigDecimal saldoVigente(Connection c, UUID agenteId, LocalDate fecha) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COALESCE(SUM(horasRestantes), 0) FROM MovimientoHoras
                WHERE agenteID = ? AND tipo = 'CREDITO' AND horasRestantes > 0
                  AND (fechaVencimiento IS NULL OR fechaVencimiento >= ?)""")) {
            ps.setObject(1, agenteId);
            ps.setDate(2, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** Movimientos del banco de horas del agente (pantalla Banco de horas). */
    public List<ar.com.hmu.model.MovimientoHoras> findMovimientos(UUID agenteId) throws SQLException {
        return conConexion(c -> movimientos(c, "WHERE agenteID = ? ORDER BY fecha, CASE tipo WHEN 'CREDITO' THEN 0 "
                + "WHEN 'DEBITO' THEN 1 ELSE 2 END, fechaVencimiento NULLS LAST", agenteId));
    }

    /** Devoluciones (DEV) de un pase de salida, en esos estados. */
    public List<Novedad> findDevolucionesDe(UUID paseId, Collection<EstadoNovedad> estados) throws SQLException {
        StringBuilder in = new StringBuilder();
        for (EstadoNovedad e : estados) {
            in.append(in.isEmpty() ? "" : ",").append('\'').append(e.name()).append('\'');
        }
        return buscar(" WHERE n.novedadOrigenID = ? AND n.estado::text IN (" + in + ")", List.of(paseId));
    }

    /** Horas extras para FC aplicadas de una fecha (se acreditan al cotejar sus marcaciones). */
    public List<Novedad> findHorasExtraAplicadas(LocalDate fecha) throws SQLException {
        return buscar(" WHERE t.codigo = 'HE' AND n.estado = 'APLICADA' AND ? BETWEEN n.fechaDesde AND n.fechaHasta",
                List.of(fecha));
    }

    // ============================================================
    // Banco de horas (dentro de una transacción)
    // ============================================================

    /** Créditos con horas sin usar vigentes a la fecha, el de vencimiento más próximo primero (FIFO). */
    public List<ar.com.hmu.model.MovimientoHoras> creditosVigentes(Connection c, UUID agenteId, LocalDate fecha)
            throws SQLException {
        return movimientos(c, "WHERE agenteID = ? AND tipo = 'CREDITO' AND horasRestantes > 0"
                + " AND (fechaVencimiento IS NULL OR fechaVencimiento >= ?)"
                + " ORDER BY fechaVencimiento NULLS LAST, fecha FOR UPDATE", agenteId, fecha);
    }

    /** Créditos vencidos antes de la fecha que todavía tienen horas (para darlas de baja). */
    public List<ar.com.hmu.model.MovimientoHoras> creditosVencidos(Connection c, LocalDate fecha) throws SQLException {
        return movimientos(c, "WHERE tipo = 'CREDITO' AND horasRestantes > 0 AND fechaVencimiento < ?"
                + " ORDER BY agenteID, fechaVencimiento FOR UPDATE", fecha);
    }

    /** Crédito que acreditó una novedad de horas extras (uno por novedad). */
    public ar.com.hmu.model.MovimientoHoras creditoDeNovedad(Connection c, UUID novedadId) throws SQLException {
        List<ar.com.hmu.model.MovimientoHoras> l = movimientos(c,
                "WHERE novedadID = ? AND tipo = 'CREDITO' ORDER BY fecha LIMIT 1", novedadId);
        return l.isEmpty() ? null : l.get(0);
    }

    public void insertarMovimiento(Connection c, ar.com.hmu.model.MovimientoHoras m, UUID registradoPorId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO MovimientoHoras (id, agenteID, novedadID, fecha, horas, concepto, saldoResultante,
                    registradoPorID, tipo, fechaVencimiento, horasRestantes, creditoID)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::tipo_movimiento_horas, ?, ?, ?)""")) {
            ps.setObject(1, m.id() != null ? m.id() : UUID.randomUUID());
            ps.setObject(2, m.agenteId());
            ps.setObject(3, m.novedadId());
            ps.setDate(4, Date.valueOf(m.fecha()));
            ps.setBigDecimal(5, m.horas());
            ps.setString(6, m.concepto());
            ps.setBigDecimal(7, m.saldoResultante());
            ps.setObject(8, registradoPorId);
            ps.setString(9, m.tipo().name());
            ps.setDate(10, m.fechaVencimiento() != null ? Date.valueOf(m.fechaVencimiento()) : null);
            ps.setBigDecimal(11, m.horasRestantes());
            ps.setObject(12, m.creditoId());
            ps.executeUpdate();
        }
    }

    public void actualizarCredito(Connection c, UUID creditoId, BigDecimal horas, BigDecimal restantes,
                                  String concepto) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE MovimientoHoras SET horas = ?, horasRestantes = ?, concepto = ? WHERE id = ?")) {
            ps.setBigDecimal(1, horas);
            ps.setBigDecimal(2, restantes);
            ps.setString(3, concepto);
            ps.setObject(4, creditoId);
            ps.executeUpdate();
        }
    }

    public void actualizarRestantes(Connection c, UUID creditoId, BigDecimal restantes) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE MovimientoHoras SET horasRestantes = ? WHERE id = ?")) {
            ps.setBigDecimal(1, restantes);
            ps.setObject(2, creditoId);
            ps.executeUpdate();
        }
    }

    /** Agente.saldoHoras = suma de horasRestantes de los créditos vigentes a la fecha. */
    public BigDecimal recalcularSaldo(Connection c, UUID agenteId, LocalDate fecha) throws SQLException {
        BigDecimal saldo = saldoVigente(c, agenteId, fecha);
        try (PreparedStatement ps = c.prepareStatement("UPDATE Agente SET saldoHoras = ? WHERE id = ?")) {
            ps.setBigDecimal(1, saldo);
            ps.setObject(2, agenteId);
            ps.executeUpdate();
        }
        return saldo;
    }

    public BigDecimal saldoVigenteEn(Connection c, UUID agenteId, LocalDate fecha) throws SQLException {
        return saldoVigente(c, agenteId, fecha);
    }

    /** Actualiza las horas de una novedad (horas extras computadas al cotejar). */
    public void actualizarCantHoras(Connection c, UUID novedadId, BigDecimal horas) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE Novedad SET cantHoras = ? WHERE id = ?")) {
            ps.setBigDecimal(1, horas);
            ps.setObject(2, novedadId);
            ps.executeUpdate();
        }
    }

    private static List<ar.com.hmu.model.MovimientoHoras> movimientos(Connection c, String where, Object... params)
            throws SQLException {
        List<ar.com.hmu.model.MovimientoHoras> l = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM MovimientoHoras " + where)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i] instanceof LocalDate d ? Date.valueOf(d) : params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date vence = rs.getDate("fechaVencimiento");
                    l.add(new ar.com.hmu.model.MovimientoHoras(rs.getObject("id", UUID.class),
                            rs.getObject("agenteID", UUID.class), rs.getObject("novedadID", UUID.class),
                            rs.getDate("fecha").toLocalDate(),
                            ar.com.hmu.model.MovimientoHoras.Tipo.valueOf(rs.getString("tipo")),
                            rs.getBigDecimal("horas"), rs.getString("concepto"), rs.getBigDecimal("saldoResultante"),
                            vence != null ? vence.toLocalDate() : null, rs.getBigDecimal("horasRestantes"),
                            rs.getObject("creditoID", UUID.class)));
                }
            }
        }
        return l;
    }

    // ============================================================
    // Escritura (dentro de una transacción)
    // ============================================================

    public void insertar(Connection c, Novedad n) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO Novedad (id, tipoNovedadID, agenteID, contraparteAgenteID, fechaDesde, fechaHasta,
                    horaDesde, horaHasta, cantHoras, motivo, estado, origen, solicitadoPorID, fechaSolicitud,
                    referenciaExterna, codigoNovedadID, novedadOrigenID, justificacion, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::estado_novedad, ?::origen_novedad, ?, ?, ?,
                    %s, ?, ?, 0)""".formatted(TraduccionCodigoNovedad.ID_DEL_CODIGO))) {
            ps.setObject(1, n.getId());
            ps.setObject(2, n.getTipo().id());
            ps.setObject(3, n.getAgenteId());
            ps.setObject(4, n.getContraparteAgenteId());
            ps.setDate(5, Date.valueOf(n.getFechaDesde()));
            ps.setDate(6, Date.valueOf(n.getFechaHasta()));
            ps.setTime(7, n.getHoraDesde() != null ? Time.valueOf(n.getHoraDesde()) : null);
            ps.setTime(8, n.getHoraHasta() != null ? Time.valueOf(n.getHoraHasta()) : null);
            ps.setBigDecimal(9, n.getCantHoras());
            ps.setString(10, n.getMotivo());
            ps.setString(11, n.getEstado().name());
            ps.setString(12, n.getOrigen().name());
            ps.setObject(13, n.getSolicitadoPorId());
            ps.setTimestamp(14, n.getFechaSolicitud() != null ? Timestamp.valueOf(n.getFechaSolicitud()) : null);
            ps.setString(15, n.getReferenciaExterna());
            ps.setObject(16, n.getCodigoNovedad(), java.sql.Types.INTEGER);
            ps.setObject(17, n.getNovedadOrigenId());
            ps.setString(18, n.getJustificacion());
            ps.executeUpdate();
        }
    }

    /**
     * Reemplaza los datos editables de un borrador u observada, con control
     * de versión. @return false si otro usuario la modificó antes.
     */
    public boolean actualizarDatos(Connection c, Novedad n) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE Novedad SET tipoNovedadID = ?, agenteID = ?, contraparteAgenteID = ?, fechaDesde = ?,
                    fechaHasta = ?, horaDesde = ?, horaHasta = ?, cantHoras = ?, motivo = ?, novedadOrigenID = ?,
                    justificacion = ?, version = version + 1, updatedAt = now()
                WHERE id = ? AND version = ?""")) {
            ps.setObject(1, n.getTipo().id());
            ps.setObject(2, n.getAgenteId());
            ps.setObject(3, n.getContraparteAgenteId());
            ps.setDate(4, Date.valueOf(n.getFechaDesde()));
            ps.setDate(5, Date.valueOf(n.getFechaHasta()));
            ps.setTime(6, n.getHoraDesde() != null ? Time.valueOf(n.getHoraDesde()) : null);
            ps.setTime(7, n.getHoraHasta() != null ? Time.valueOf(n.getHoraHasta()) : null);
            ps.setBigDecimal(8, n.getCantHoras());
            ps.setString(9, n.getMotivo());
            ps.setObject(10, n.getNovedadOrigenId());
            ps.setString(11, n.getJustificacion());
            ps.setObject(12, n.getId());
            ps.setInt(13, n.getVersion());
            boolean ok = ps.executeUpdate() == 1;
            if (ok) {
                n.setVersion(n.getVersion() + 1);
            }
            return ok;
        }
    }

    /**
     * Cambia el estado con concurrencia optimista. Si {@code fechaSolicitud}
     * no es null, la registra (envío de la solicitud).
     *
     * @return false si la versión no coincide (otro usuario la modificó).
     */
    public boolean cambiarEstado(Connection c, UUID id, int versionEsperada, EstadoNovedad nuevo,
                                 LocalDateTime fechaSolicitud) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE Novedad SET estado = ?::estado_novedad, version = version + 1, updatedAt = ?,
                    fechaSolicitud = COALESCE(?, fechaSolicitud)
                WHERE id = ? AND version = ?""")) {
            ps.setString(1, nuevo.name());
            // La fila "Aplicada" del historial muestra updatedAt: misma fecha de referencia que el resto.
            ps.setTimestamp(2, Timestamp.valueOf(ar.com.hmu.util.Reloj.ahora()));
            ps.setTimestamp(3, fechaSolicitud != null ? Timestamp.valueOf(fechaSolicitud) : null);
            ps.setObject(4, id);
            ps.setInt(5, versionEsperada);
            return ps.executeUpdate() == 1;
        }
    }

    public void insertarAutorizaciones(Connection c, List<NovedadAutorizacion> autorizaciones) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO NovedadAutorizacion (id, novedadID, instancia, orden, estado)
                VALUES (?, ?, ?::instancia_novedad, ?, ?::estado_novedad_autorizacion)""")) {
            for (NovedadAutorizacion a : autorizaciones) {
                ps.setObject(1, a.getId());
                ps.setObject(2, a.getNovedadId());
                ps.setString(3, a.getInstancia().name());
                ps.setInt(4, a.getOrden());
                ps.setString(5, a.getEstado().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Registra la decisión de una instancia pendiente. @return false si ya no estaba pendiente. */
    public boolean resolverAutorizacion(Connection c, UUID autorizacionId, EstadoNovedadAutorizacion estado,
                                        UUID resueltoPorId, String comentarios) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE NovedadAutorizacion SET estado = ?::estado_novedad_autorizacion, resueltoPorID = ?,
                    fechaResolucion = ?, comentarios = ?
                WHERE id = ? AND estado = 'PENDIENTE'""")) {
            ps.setString(1, estado.name());
            ps.setObject(2, resueltoPorId);
            ps.setTimestamp(3, Timestamp.valueOf(ar.com.hmu.util.Reloj.ahora()));
            ps.setString(4, comentarios);
            ps.setObject(5, autorizacionId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Quita las instancias que quedaron sin resolver cuando el circuito se
     * corta (observación o rechazo): no contienen ninguna decisión, y al
     * reenviar se crea una ronda nueva.
     */
    public void borrarPendientes(Connection c, UUID novedadId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM NovedadAutorizacion WHERE novedadID = ? AND estado = 'PENDIENTE'")) {
            ps.setObject(1, novedadId);
            ps.executeUpdate();
        }
    }

    public int maxOrden(Connection c, UUID novedadId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(orden), 0) FROM NovedadAutorizacion WHERE novedadID = ?")) {
            ps.setObject(1, novedadId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void insertarAdjunto(Connection c, NovedadAdjunto a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO NovedadAdjunto (id, novedadID, nombreArchivo, tipoMime, contenido, cargadoPorID)
                VALUES (?, ?, ?, ?, ?, ?)""")) {
            ps.setObject(1, a.getId() != null ? a.getId() : UUID.randomUUID());
            ps.setObject(2, a.getNovedadId());
            ps.setString(3, a.getNombreArchivo());
            ps.setString(4, a.getTipoMime() != null ? a.getTipoMime() : "application/octet-stream");
            ps.setBytes(5, a.getContenido());
            ps.setObject(6, a.getCargadoPorId());
            ps.executeUpdate();
        }
    }

    // ============================================================
    // Aplicación sobre el diagrama vigente (CU26)
    // ============================================================

    /** Jornada del agente en un diagrama APROBADO para la fecha, o null. */
    public JornadaLaboral jornadaVigente(Connection c, UUID agenteId, LocalDate fecha) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT j.id, j.diagramaID, j.agenteID, j.fecha, j.tipo, j.fechaIngreso, j.fechaEgreso, j.observaciones
                FROM JornadaLaboral j JOIN DiagramaDeServicio d ON d.id = j.diagramaID AND d.estado = 'APROBADO'
                WHERE j.agenteID = ? AND j.fecha = ?""")) {
            ps.setObject(1, agenteId);
            ps.setDate(2, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? DiagramaFactory.createJornada(rs) : null;
            }
        }
    }

    public void actualizarJornada(Connection c, UUID jornadaId, TipoJornada tipo, LocalDateTime ingreso,
                                  LocalDateTime egreso, UUID novedadId, String observaciones) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE JornadaLaboral SET tipo = ?::tipo_jornada, fechaIngreso = ?, fechaEgreso = ?, novedadID = ?,
                    observaciones = ? WHERE id = ?""")) {
            ps.setString(1, tipo.name());
            ps.setTimestamp(2, ingreso != null ? Timestamp.valueOf(ingreso) : null);
            ps.setTimestamp(3, egreso != null ? Timestamp.valueOf(egreso) : null);
            ps.setObject(4, novedadId);
            ps.setString(5, observaciones);
            ps.setObject(6, jornadaId);
            ps.executeUpdate();
        }
    }

    public void borrar(Connection c, UUID novedadId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Novedad WHERE id = ?")) {
            ps.setObject(1, novedadId);
            ps.executeUpdate();
        }
    }
}
