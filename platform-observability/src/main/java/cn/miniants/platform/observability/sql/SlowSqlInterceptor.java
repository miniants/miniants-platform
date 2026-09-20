package cn.miniants.platform.observability.sql;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * 只对超过阈值的 SQL 打 ERROR。全量明细仍由 dev 下的 p6spy 负责。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "update", args = {
                MappedStatement.class, Object.class
        })
})
public class SlowSqlInterceptor implements Interceptor {

    public static final String LOGGER_NAME = "pl.slow-sql";

    private static final Logger SLOW = LoggerFactory.getLogger(LOGGER_NAME);

    private final SlowSqlProperties properties;

    public SlowSqlInterceptor(SlowSqlProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }
        long startNs = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            if (elapsedMs >= properties.getThresholdMs()) {
                MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
                BoundSql boundSql = statement.getBoundSql(invocation.getArgs()[1]);
                SLOW.error("{}ms | id={} | {}",
                        elapsedMs,
                        statement.getId(),
                        truncateSql(boundSql.getSql(), properties.getMaxSqlLength()));
            }
        }
    }

    public static String truncateSql(String sql, int maxLen) {
        if (sql == null) {
            return "";
        }
        String single = sql.replaceAll("\\s+", " ").trim();
        if (maxLen <= 0 || single.length() <= maxLen) {
            return single;
        }
        return single.substring(0, maxLen) + "...";
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 阈值走 Spring 配置，不用 MyBatis 的 properties
    }
}
