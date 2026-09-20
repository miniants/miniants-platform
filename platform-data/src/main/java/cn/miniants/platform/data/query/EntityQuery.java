package cn.miniants.platform.data.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 现网 {@code SqlDbHandelUtil} 的白名单版：JSON 方言不变，列必须来自实体。
 */
public final class EntityQuery {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Class<?> entityType;
    private final Set<String> exclusions;
    private final String tableAlias;
    private final Map<String, Class<?>> extraEntities;
    private final EntityColumnIndex columns;
    private final Map<String, EntityColumnIndex> extraIndexes;

    private EntityQuery(Class<?> entityType, Set<String> exclusions, String tableAlias,
                        Map<String, Class<?>> extraEntities) {
        this.entityType = entityType;
        this.exclusions = Set.copyOf(exclusions);
        this.tableAlias = tableAlias;
        this.extraEntities = Map.copyOf(extraEntities);
        this.columns = new EntityColumnIndex(entityType, this.exclusions, tableAlias);
        Map<String, EntityColumnIndex> extras = new LinkedHashMap<>();
        this.extraEntities.forEach((alias, type) ->
                extras.put(alias, new EntityColumnIndex(type, this.exclusions, alias)));
        this.extraIndexes = Map.copyOf(extras);
    }

    public static EntityQuery of(Class<?> entityType) {
        return new EntityQuery(entityType, Set.of(), null, Map.of());
    }

    public EntityQuery exclude(String... fieldOrColumn) {
        Set<String> next = new LinkedHashSet<>(this.exclusions);
        Collections.addAll(next, fieldOrColumn);
        return new EntityQuery(this.entityType, next, this.tableAlias, this.extraEntities);
    }

    /**
     * 联表 SQL 的主表别名。登记后允许 {@code alias.field}，未加前缀的实体字段也会写成 {@code alias.column}。
     * 未登记的别名（如 {@code sa.id}）仍 400，除非再 {@link #allow(String, Class)}。
     */
    public EntityQuery tableAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return new EntityQuery(this.entityType, this.exclusions, null, this.extraEntities);
        }
        if (!legalAlias(alias)) {
            throw new IllegalArgumentException("表别名不合法：" + alias);
        }
        if (this.extraEntities.containsKey(alias)) {
            throw new IllegalArgumentException("表别名重复：" + alias);
        }
        return new EntityQuery(this.entityType, this.exclusions, alias, this.extraEntities);
    }

    /**
     * 再登记一张联表。允许 {@code alias.field}，列必须来自该实体。不要「带点号就放行」。
     */
    public EntityQuery allow(String alias, Class<?> joinEntity) {
        if (alias == null || alias.isBlank() || !legalAlias(alias)) {
            throw new IllegalArgumentException("表别名不合法：" + alias);
        }
        if (joinEntity == null) {
            throw new IllegalArgumentException("联表实体不能为空");
        }
        if (alias.equals(this.tableAlias) || this.extraEntities.containsKey(alias)) {
            throw new IllegalArgumentException("表别名重复：" + alias);
        }
        Map<String, Class<?>> next = new LinkedHashMap<>(this.extraEntities);
        next.put(alias, joinEntity);
        return new EntityQuery(this.entityType, this.exclusions, this.tableAlias, next);
    }

    private static boolean legalAlias(String alias) {
        char first = alias.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < alias.length(); i++) {
            char ch = alias.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    private String resolveColumn(String jsonKey, String action) {
        if (jsonKey != null) {
            int dot = jsonKey.indexOf('.');
            if (dot >= 0) {
                String alias = jsonKey.substring(0, dot);
                if (tableAlias != null && tableAlias.equals(alias)) {
                    return columns.requireColumn(jsonKey, action);
                }
                EntityColumnIndex extra = extraIndexes.get(alias);
                if (extra == null) {
                    throw new IllegalArgumentException("不允许按该字段" + action + "：" + jsonKey);
                }
                return extra.requireColumn(jsonKey, action);
            }
        }
        return columns.requireColumn(jsonKey, action);
    }

    public QueryWrapper<?> wrapper(String filterJson, String orderJson) {
        QueryWrapper<?> wrapper = parseFilter(filterJson);
        applyOrder(wrapper, orderJson);
        return wrapper;
    }

    private QueryWrapper<?> parseFilter(String filterJson) {
        if (filterJson == null || filterJson.isBlank()) {
            return Wrappers.query();
        }
        Map<String, Object> raw = readMap(decodeJson(filterJson), "filter");
        Filter root = clean(parseGroup(FilterGroup.$and, raw));
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        if (root != null && (hasBeans(root) || hasChildren(root))) {
            applyGroup(wrapper, root);
        }
        return wrapper;
    }

    private Filter parseGroup(FilterGroup group, Map<String, Object> raw) {
        Filter filter = new Filter(group);
        if (raw == null || raw.isEmpty()) {
            return filter;
        }
        raw.forEach((key, value) -> {
            if (!hasValue(value)) {
                return;
            }
            if (isSimple(value)) {
                filter.add(resolveColumn(key, "查询"), FilterOp.$eq, value);
                return;
            }
            if (!(value instanceof Map<?, ?> childRaw)) {
                throw new IllegalArgumentException("filter 字段错误");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) childRaw;
            FilterGroup nested = FilterGroup.of(key);
            if (nested != null) {
                filter.children.add(parseGroup(nested, child));
                return;
            }
            String column = resolveColumn(key, "查询");
            child.forEach((op, childValue) -> {
                FilterOp operational = FilterOp.of(op);
                if (operational == null) {
                    throw new IllegalArgumentException("不支持的操作符：" + op);
                }
                filter.add(column, operational, childValue);
            });
        });
        return filter;
    }

    private Filter clean(Filter filter) {
        if (filter == null) {
            return null;
        }
        if (!filter.beans.isEmpty()) {
            return filter;
        }
        if (filter.children.isEmpty()) {
            return null;
        }
        List<Filter> cleaned = new ArrayList<>(filter.children.size());
        for (Filter child : filter.children) {
            Filter next = clean(child);
            if (next != null) {
                cleaned.add(next);
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.size() == 1) {
            return cleaned.get(0);
        }
        filter.children.clear();
        filter.children.addAll(cleaned);
        return filter;
    }

    private void applyGroup(QueryWrapper<?> wrapper, Filter filter) {
        if (filter.group == FilterGroup.$or) {
            wrapper.or(inner -> applyMembers(inner, filter));
            return;
        }
        wrapper.and(inner -> applyMembers(inner, filter));
    }

    private void applyMembers(QueryWrapper<?> wrapper, Filter filter) {
        filter.beans.forEach(bean -> applyOp(wrapper, bean));
        filter.children.forEach(child -> applyGroup(wrapper, child));
    }

    private void applyOp(QueryWrapper<?> wrapper, FilterBean bean) {
        boolean notEmpty = bean.value() != null && !bean.value().toString().isEmpty();
        switch (bean.op()) {
            case $eq -> wrapper.eq(notEmpty, bean.column(), bean.value());
            case $ne -> wrapper.ne(notEmpty, bean.column(), bean.value());
            case $like -> wrapper.like(notEmpty, bean.column(), bean.value().toString());
            case $in -> {
                if (!(bean.value() instanceof Collection<?> values)) {
                    throw new IllegalArgumentException("filter 字段 " + bean.column() + " 的 $in 值必须是集合");
                }
                wrapper.in(!values.isEmpty(), bean.column(), values);
            }
            case $gt -> wrapper.gt(notEmpty, bean.column(), bean.value());
            case $ge -> wrapper.ge(notEmpty, bean.column(), bean.value());
            case $lt -> wrapper.lt(notEmpty, bean.column(), bean.value());
            case $le -> wrapper.le(notEmpty, bean.column(), bean.value());
            case $isNull -> wrapper.isNull(notEmpty, bean.column());
        }
    }

    private void applyOrder(QueryWrapper<?> wrapper, String orderJson) {
        if (orderJson == null || orderJson.isBlank()) {
            return;
        }
        Map<String, Object> raw = readMap(decodeJson(orderJson), "order");
        raw.forEach((key, value) -> {
            if (value == null || value.toString().isEmpty()) {
                return;
            }
            OrderDir dir = OrderDir.of(value.toString());
            if (dir == null) {
                throw new IllegalArgumentException("order 字段错误！【ASC,DESC】");
            }
            String column = resolveColumn(key, "排序");
            switch (dir) {
                case ASC -> wrapper.orderByAsc(column);
                case DESC -> wrapper.orderByDesc(column);
            }
        });
    }

    /**
     * 容器只会解一层 querystring；前端若先 {@code encodeURI(JSON)} 再交给 qs，
     * 这里会收到 {@code %7B...}。order 原先就多解一次，filter 对齐。
     */
    private static String decodeJson(String json) {
        String text = json.trim();
        if (!text.startsWith("{")) {
            text = URLDecoder.decode(text, StandardCharsets.UTF_8).trim();
        }
        return text;
    }

    private static Map<String, Object> readMap(String json, String field) {
        try {
            Map<String, Object> map = JSON.readValue(json, MAP_TYPE);
            if (map == null) {
                throw new IllegalArgumentException(field + " 字段错误！");
            }
            return map;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(field + " 字段错误！");
        }
    }

    private static boolean isSimple(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static boolean hasBeans(Filter filter) {
        return !filter.beans.isEmpty();
    }

    private static boolean hasChildren(Filter filter) {
        return !filter.children.isEmpty();
    }

    private enum FilterGroup {
        $and,
        $or;

        static FilterGroup of(String key) {
            if ("$and".equals(key)) {
                return $and;
            }
            if ("$or".equals(key)) {
                return $or;
            }
            return null;
        }
    }

    private enum FilterOp {
        $eq,
        $ne,
        $like,
        $in,
        $gt,
        $ge,
        $lt,
        $le,
        $isNull;

        static FilterOp of(String key) {
            for (FilterOp op : values()) {
                if (op.name().equals(key)) {
                    return op;
                }
            }
            return null;
        }
    }

    private enum OrderDir {
        ASC,
        DESC;

        static OrderDir of(String value) {
            if ("ASC".equals(value)) {
                return ASC;
            }
            if ("DESC".equals(value)) {
                return DESC;
            }
            return null;
        }
    }

    private static final class Filter {
        private final FilterGroup group;
        private final List<FilterBean> beans = new LinkedList<>();
        private final List<Filter> children = new LinkedList<>();

        private Filter(FilterGroup group) {
            this.group = group;
        }

        private void add(String column, FilterOp op, Object value) {
            if (value != null && value.toString().isEmpty()) {
                return;
            }
            beans.add(new FilterBean(column, op, value));
        }
    }

    private record FilterBean(String column, FilterOp op, Object value) {
    }
}
