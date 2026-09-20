package cn.miniants.platform.data.scope;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;

public class DataScopeInnerInterceptor implements InnerInterceptor {

    // InnerInterceptor 声明的就是原始类型，加泛型会变成重载而不是覆写
    @SuppressWarnings("rawtypes")
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        DataScope scope = DataScope.find(parameter);
        if (scope == null || !scope.isApply()) {
            return;
        }
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        String original = mpBoundSql.sql();
        String rewritten = DataScopeSql.rewrite(original, scope);
        if (!original.equals(rewritten)) {
            mpBoundSql.sql(rewritten);
        }
    }
}
