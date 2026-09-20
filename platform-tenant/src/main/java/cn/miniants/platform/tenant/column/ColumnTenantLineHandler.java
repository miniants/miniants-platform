package cn.miniants.platform.tenant.column;

import cn.miniants.platform.tenant.TenantContext;
import cn.miniants.platform.tenant.TenantProperties;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class ColumnTenantLineHandler implements TenantLineHandler {

    private final TenantProperties properties;
    private final Set<String> ignoreTables;

    public ColumnTenantLineHandler(TenantProperties properties) {
        this.properties = properties;
        Set<String> ignore = new LinkedHashSet<>();
        for (String table : properties.getIgnoreTables()) {
            if (table != null && !table.isBlank()) {
                ignore.add(normalize(table));
            }
        }
        this.ignoreTables = Set.copyOf(ignore);
    }

    @Override
    public Expression getTenantId() {
        return new StringValue(TenantContext.require());
    }

    @Override
    public String getTenantIdColumn() {
        return properties.getColumn();
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return ignoreTables.contains(normalize(tableName));
    }

    private static String normalize(String tableName) {
        String name = tableName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("`") && name.endsWith("`") && name.length() > 1) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }
}
