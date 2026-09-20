package cn.miniants.platform.data.scope;

import cn.miniants.platform.core.error.PlatformException;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class DataScopeSql {

    private static final Pattern COLUMN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private DataScopeSql() {
    }

    static String rewrite(String sql, DataScope dataScope) {
        if (dataScope == null || !dataScope.isApply() || sql == null || sql.isBlank()) {
            return sql;
        }
        String column = dataScope.getColumn();
        if (column == null || !COLUMN.matcher(column).matches()) {
            throw new PlatformException("数据范围列名不合法");
        }
        List<Long> ids = DataScope.idsOrEmpty(dataScope);
        if (ids.isEmpty()) {
            return "SELECT * FROM (" + sql + ") _ds_t WHERE 1=0";
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "SELECT * FROM (" + sql + ") _ds_t WHERE _ds_t." + column + " IN (" + in + ")";
    }
}
