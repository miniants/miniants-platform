package cn.miniants.platform.data.query;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 从实体字段推导可查询列。前端 JSON key 必须命中字段名或列名。
 */
public final class EntityColumnIndex {

    private final Map<String, String> keyToColumn;
    private final String tableAlias;

    public EntityColumnIndex(Class<?> entityType, Set<String> extraExclusions) {
        this(entityType, extraExclusions, null);
    }

    public EntityColumnIndex(Class<?> entityType, Set<String> extraExclusions, String tableAlias) {
        this.tableAlias = tableAlias;
        Map<String, String> keys = new LinkedHashMap<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!include(field)) {
                    continue;
                }
                String column = columnName(field);
                if (field.getAnnotation(QueryHidden.class) != null || excluded(field.getName(), column, extraExclusions)) {
                    continue;
                }
                keys.put(field.getName(), column);
                keys.put(column, column);
            }
        }
        this.keyToColumn = Collections.unmodifiableMap(keys);
    }

    public String requireColumn(String jsonKey, String action) {
        String lookupKey = jsonKey;
        if (jsonKey != null) {
            int dot = jsonKey.indexOf('.');
            if (dot >= 0) {
                String alias = jsonKey.substring(0, dot);
                String rest = jsonKey.substring(dot + 1);
                if (tableAlias == null || !tableAlias.equals(alias) || rest.isEmpty() || rest.indexOf('.') >= 0) {
                    throw new IllegalArgumentException("不允许按该字段" + action + "：" + jsonKey);
                }
                lookupKey = rest;
            }
        }
        String column = keyToColumn.get(lookupKey);
        if (column == null) {
            throw new IllegalArgumentException("不允许按该字段" + action + "：" + jsonKey);
        }
        if (tableAlias != null) {
            return tableAlias + "." + column;
        }
        return column;
    }

    public Set<String> columns() {
        return Set.copyOf(keyToColumn.values());
    }

    private static boolean include(Field field) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
            return false;
        }
        if ("serialVersionUID".equals(field.getName())) {
            return false;
        }
        TableField tableField = field.getAnnotation(TableField.class);
        return tableField == null || tableField.exist();
    }

    private static String columnName(Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        if (tableId != null && !tableId.value().isEmpty()) {
            return tableId.value();
        }
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.value().isEmpty()) {
            return tableField.value();
        }
        return StringUtils.camelToUnderline(field.getName());
    }

    private static boolean excluded(String fieldName, String column, Set<String> extraExclusions) {
        if (extraExclusions == null || extraExclusions.isEmpty()) {
            return false;
        }
        return extraExclusions.contains(fieldName) || extraExclusions.contains(column);
    }
}
