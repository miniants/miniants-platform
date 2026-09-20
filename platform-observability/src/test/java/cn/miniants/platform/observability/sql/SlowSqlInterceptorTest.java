package cn.miniants.platform.observability.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlowSqlInterceptorTest {

    @Test
    void collapsesWhitespaceSoOneStatementStaysOneLogLine() {
        assertEquals("SELECT 1", SlowSqlInterceptor.truncateSql("  SELECT\n  1  ", 2000));
    }

    @Test
    void truncatesOversizedStatements() {
        assertEquals("SELECT ab...", SlowSqlInterceptor.truncateSql("SELECT abcdef", 9));
    }

    @Test
    void nonPositiveLimitMeansNoTruncation() {
        assertEquals("SELECT abcdef", SlowSqlInterceptor.truncateSql("SELECT abcdef", 0));
    }

    @Test
    void nullStatementBecomesEmptyRatherThanNpe() {
        assertEquals("", SlowSqlInterceptor.truncateSql(null, 2000));
    }
}
