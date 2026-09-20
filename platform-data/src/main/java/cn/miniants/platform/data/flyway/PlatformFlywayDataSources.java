package cn.miniants.platform.data.flyway;

import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 有动态数据源且写了 {@code platform.flyway.datasource} 时取出 named 源；
 * 否则原样返回注入的 {@code DataSource}。不编译期依赖 baomidou。
 */
final class PlatformFlywayDataSources {

    private static final String ROUTING =
            "com.baomidou.dynamic.datasource.DynamicRoutingDataSource";

    private PlatformFlywayDataSources() {
    }

    static DataSource resolve(DataSource dataSource, String name) {
        if (dataSource == null) {
            throw new IllegalStateException("platform.flyway 需要 DataSource");
        }
        if (!StringUtils.hasText(name) || !ClassUtils.isPresent(ROUTING, null)) {
            return dataSource;
        }
        Class<?> routing;
        try {
            routing = ClassUtils.forName(ROUTING, null);
        } catch (ClassNotFoundException e) {
            return dataSource;
        }
        if (!routing.isInstance(dataSource)) {
            return dataSource;
        }
        try {
            Method getDataSource = routing.getMethod("getDataSource", String.class);
            DataSource target = (DataSource) getDataSource.invoke(dataSource, name.trim());
            if (target == null) {
                throw new IllegalStateException("platform.flyway 找不到数据源 " + name);
            }
            return target;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("platform.flyway 找不到数据源 " + name, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("platform.flyway 无法解析数据源 " + name, e);
        }
    }
}
