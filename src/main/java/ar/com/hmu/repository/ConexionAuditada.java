package ar.com.hmu.repository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/**
 * Envoltorio de {@link Connection} que identifica al usuario de la aplicación
 * ante los disparadores de auditoría (E4, Tabla 58: {@code usuarioID} se toma
 * de la variable de sesión {@code app.usuario_id}).
 *
 * <p>Antes de la primera escritura de cada transacción ejecuta
 * {@code set_config('app.usuario_id', <usuario>, true)}: el tercer argumento
 * la hace local a la transacción, así que nunca queda "pegada" a la conexión.
 * Las escrituras en modo autocommit se envuelven en una transacción propia
 * (set_config + sentencia + commit) para que el disparador vea el usuario.</p>
 *
 * <p>Los repositorios no cambian: siguen usando {@code getConnection()}.</p>
 */
final class ConexionAuditada implements InvocationHandler {

    private final Connection real;
    private boolean autoCommit;
    private boolean configuradaEnTransaccion;

    private ConexionAuditada(Connection real) throws SQLException {
        this.real = real;
        this.autoCommit = real.getAutoCommit();
    }

    static Connection envolver(Connection real) throws SQLException {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, new ConexionAuditada(real));
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        switch (m.getName()) {
            case "setAutoCommit" -> {
                autoCommit = (Boolean) args[0];
                configuradaEnTransaccion = false;
            }
            case "commit", "rollback" -> configuradaEnTransaccion = false;
            case "unwrap" -> {
                if (((Class<?>) args[0]).isInstance(real)) return real;
            }
            default -> { }
        }
        Object r = llamar(real, m, args);
        if (r instanceof Statement st && !(m.getName().equals("unwrap"))) {
            String sql = (args != null && args.length > 0 && args[0] instanceof String s) ? s : null;
            Class<?> iface = r instanceof java.sql.CallableStatement ? java.sql.CallableStatement.class
                    : r instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{iface},
                    new SentenciaAuditada(st, sql));
        }
        return r;
    }

    /** Envoltorio de la sentencia: prepara la transacción antes de una escritura. */
    private final class SentenciaAuditada implements InvocationHandler {

        private final Statement real;
        private final String sqlPreparado;

        SentenciaAuditada(Statement real, String sqlPreparado) {
            this.real = real;
            this.sqlPreparado = sqlPreparado;
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            if (!m.getName().startsWith("execute")) {
                return llamar(real, m, args);
            }
            String sql = (args != null && args.length > 0 && args[0] instanceof String s) ? s : sqlPreparado;
            UUID usuario = ContextoAuditoria.usuarioActual();
            if (usuario == null || !esEscritura(sql)) {
                return llamar(real, m, args);
            }
            if (!autoCommit) {
                configurar(usuario);
                return llamar(real, m, args);
            }
            // Autocommit: la escritura se hace en una transacción propia.
            ConexionAuditada.this.real.setAutoCommit(false);
            try {
                configurar(usuario);
                Object r = llamar(real, m, args);
                ConexionAuditada.this.real.commit();
                return r;
            } catch (Throwable t) {
                ConexionAuditada.this.real.rollback();
                throw t;
            } finally {
                ConexionAuditada.this.real.setAutoCommit(true);
                configuradaEnTransaccion = false;
            }
        }
    }

    private void configurar(UUID usuario) throws SQLException {
        if (configuradaEnTransaccion) {
            return;
        }
        try (PreparedStatement ps = real.prepareStatement("SELECT set_config('app.usuario_id', ?, true)")) {
            ps.setString(1, usuario.toString());
            ps.execute();
        }
        configuradaEnTransaccion = true;
    }

    /** Todo lo que no es una consulta pura se considera escritura (INSERT, UPDATE, DELETE, WITH...). */
    static boolean esEscritura(String sql) {
        if (sql == null) {
            return true;    // executeBatch de un Statement sin SQL conocido
        }
        String s = sql.stripLeading().toUpperCase(Locale.ROOT);
        return !(s.startsWith("SELECT") || s.startsWith("SHOW") || s.startsWith("VALUES"));
    }

    private static Object llamar(Object destino, Method m, Object[] args) throws Throwable {
        try {
            return m.invoke(destino, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
