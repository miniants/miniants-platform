package cn.miniants.platform.data.scope;

import java.util.*;

/**
 * 查询数据范围。仅当 Mapper 参数带上本对象才改 SQL。
 * 只接受数字 ID，不接受原始 SQL 片段。院系 / 本人语义留给业务算出 ID 列表。
 */
public final class DataScope {

    private static final DataScope SKIP = new DataScope(false, null, List.of());

    private final boolean apply;
    private final String column;
    private final List<Long> ids;

    private DataScope(boolean apply, String column, List<Long> ids) {
        this.apply = apply;
        this.column = column;
        this.ids = ids;
    }

    public static DataScope skip() {
        return SKIP;
    }

    public static DataScope in(String column, Collection<? extends Number> ids) {
        List<Long> values = new ArrayList<>();
        if (ids != null) {
            for (Number id : ids) {
                if (id != null) {
                    values.add(id.longValue());
                }
            }
        }
        return new DataScope(true, column, List.copyOf(values));
    }

    public static DataScope none(String column) {
        return in(column, List.of());
    }

    public boolean isApply() {
        return apply;
    }

    public String getColumn() {
        return column;
    }

    public List<Long> getIds() {
        return ids;
    }

    public static DataScope find(Object parameter) {
        if (parameter instanceof DataScope dataScope) {
            return dataScope;
        }
        if (parameter instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof DataScope dataScope) {
                    return dataScope;
                }
            }
        }
        return null;
    }

    static List<Long> idsOrEmpty(DataScope scope) {
        return scope == null || scope.ids == null ? Collections.emptyList() : scope.ids;
    }
}
