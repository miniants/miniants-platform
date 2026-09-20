package cn.miniants.platform.tenant.schema;

import cn.miniants.platform.tenant.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 独立 schema：取连接后切 schema；MySQL 失败则切 catalog。
 */
public class SchemaRoutingDataSource extends DelegatingDataSource {

    public SchemaRoutingDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return apply(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return apply(super.getConnection(username, password));
    }

    private static Connection apply(Connection connection) throws SQLException {
        String tenant = TenantContext.current().orElse(null);
        if (tenant == null) {
            return connection;
        }
        SQLException schemaFailure;
        try {
            String originalSchema = connection.getSchema();
            connection.setSchema(tenant);
            return resetting(connection, () -> connection.setSchema(originalSchema));
        } catch (SQLException ex) {
            schemaFailure = ex;
        }
        try {
            String originalCatalog = connection.getCatalog();
            connection.setCatalog(tenant);
            return resetting(connection, () -> connection.setCatalog(originalCatalog));
        } catch (SQLException catalogFailure) {
            catalogFailure.addSuppressed(schemaFailure);
            closeAfterApplyFailure(connection, catalogFailure);
            throw catalogFailure;
        }
    }

    private static Connection resetting(Connection delegate, SqlReset reset) {
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                        if (closed.compareAndSet(false, true)) {
                            resetAndClose(delegate, reset);
                        }
                        return null;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    private static void resetAndClose(Connection connection, SqlReset reset) throws SQLException {
        SQLException resetFailure = null;
        try {
            reset.run();
        } catch (SQLException ex) {
            resetFailure = ex;
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            if (resetFailure == null) {
                throw closeFailure;
            }
            resetFailure.addSuppressed(closeFailure);
        }
        if (resetFailure != null) {
            throw resetFailure;
        }
    }

    private static void closeAfterApplyFailure(Connection connection, SQLException failure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    @FunctionalInterface
    private interface SqlReset {
        void run() throws SQLException;
    }
}
