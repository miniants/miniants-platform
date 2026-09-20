package cn.miniants.platform.tenant;

import cn.miniants.platform.tenant.schema.SchemaRoutingDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaRoutingDataSourceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void restoresOriginalSchemaBeforeClosingPhysicalConnection() throws SQLException {
        Connection physical = mock(Connection.class);
        SchemaRoutingDataSource dataSource = dataSource(physical);
        when(physical.getSchema()).thenReturn("public");
        TenantContext.bind("alpha");

        dataSource.getConnection().close();

        InOrder order = inOrder(physical);
        order.verify(physical).getSchema();
        order.verify(physical).setSchema("alpha");
        order.verify(physical).setSchema("public");
        order.verify(physical).close();
    }

    @Test
    void fallsBackToCatalogAndRestoresItWhenSchemaIsUnsupported() throws SQLException {
        Connection physical = mock(Connection.class);
        SchemaRoutingDataSource dataSource = dataSource(physical);
        when(physical.getSchema()).thenReturn("public");
        when(physical.getCatalog()).thenReturn("default");
        doThrow(new SQLException("schema unsupported")).when(physical).setSchema("alpha");
        TenantContext.bind("alpha");

        dataSource.getConnection().close();

        InOrder order = inOrder(physical);
        order.verify(physical).setSchema("alpha");
        order.verify(physical).getCatalog();
        order.verify(physical).setCatalog("alpha");
        order.verify(physical).setCatalog("default");
        order.verify(physical).close();
    }

    @Test
    void closesPhysicalConnectionWhenBothRoutingStrategiesFail() throws SQLException {
        Connection physical = mock(Connection.class);
        SchemaRoutingDataSource dataSource = dataSource(physical);
        SQLException schemaFailure = new SQLException("schema failed");
        SQLException catalogFailure = new SQLException("catalog failed");
        SQLException closeFailure = new SQLException("close failed");
        when(physical.getSchema()).thenReturn("public");
        when(physical.getCatalog()).thenReturn("default");
        doThrow(schemaFailure).when(physical).setSchema("alpha");
        doThrow(catalogFailure).when(physical).setCatalog("alpha");
        doThrow(closeFailure).when(physical).close();
        TenantContext.bind("alpha");

        assertThatThrownBy(dataSource::getConnection)
                .isSameAs(catalogFailure)
                .satisfies(ex -> assertThat(ex.getSuppressed()).containsExactly(schemaFailure, closeFailure));
    }

    @Test
    void reportsResetFailureAndStillClosesPhysicalConnection() throws SQLException {
        Connection physical = mock(Connection.class);
        SchemaRoutingDataSource dataSource = dataSource(physical);
        SQLException resetFailure = new SQLException("reset failed");
        SQLException closeFailure = new SQLException("close failed");
        when(physical.getSchema()).thenReturn("public");
        doThrow(resetFailure).when(physical).setSchema("public");
        doThrow(closeFailure).when(physical).close();
        TenantContext.bind("alpha");
        Connection routed = dataSource.getConnection();

        assertThatThrownBy(routed::close)
                .isSameAs(resetFailure)
                .satisfies(ex -> assertThat(ex.getSuppressed()).containsExactly(closeFailure));
    }

    private static SchemaRoutingDataSource dataSource(Connection connection) throws SQLException {
        DataSource target = mock(DataSource.class);
        when(target.getConnection()).thenReturn(connection);
        return new SchemaRoutingDataSource(target);
    }
}
