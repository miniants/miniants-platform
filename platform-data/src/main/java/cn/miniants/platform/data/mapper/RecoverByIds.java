package cn.miniants.platform.data.mapper;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 注入 {@link CrudMapper#recoverByIds}。
 *
 * <p>主键列与「已删除」判定都从 {@link TableInfo} 取，不写死 {@code id} 和 {@code > 0}：
 * 内核 {@code LogicDeleteEntity} 的 {@code deleted} 是 {@code TIMESTAMP}（未删为 {@code NULL}），
 * 而不少现网表用的是数值列。写死任一种都会让另一种静默匹配不到行。
 */
public class RecoverByIds extends AbstractMethod {

    private static final String NULL = "null";

    public RecoverByIds() {
        super("recoverByIds");
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass,
                                                 Class<?> modelClass,
                                                 TableInfo tableInfo) {
        TableFieldInfo logic = tableInfo.getLogicDeleteFieldInfo();
        if (logic == null) {
            throw new IllegalStateException(tableInfo.getTableName() + " 未启用 @TableLogic");
        }
        String keyColumn = tableInfo.getKeyColumn();
        if (keyColumn == null || keyColumn.isBlank()) {
            throw new IllegalStateException(tableInfo.getTableName() + " 没有主键，无法按 ID 恢复");
        }

        String logicColumn = logic.getColumn();
        String notDeleted = logic.getLogicNotDeleteValue();
        // 用 != 而不是 <>：整段 SQL 要过一遍 XML 解析，`<` 会被当成标签开头
        String deleted = NULL.equalsIgnoreCase(notDeleted)
                ? logicColumn + " IS NOT NULL"
                : logicColumn + " != " + notDeleted;

        String sql = """
                <script>
                UPDATE %s
                SET %s = %s
                WHERE %s
                  AND %s IN
                  <foreach collection="ids" item="id" open="(" separator="," close=")">
                    #{id}
                  </foreach>
                </script>
                """.formatted(tableInfo.getTableName(), logicColumn, notDeleted, deleted, keyColumn);

        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addUpdateMappedStatement(mapperClass, modelClass, this.methodName, sqlSource);
    }
}
