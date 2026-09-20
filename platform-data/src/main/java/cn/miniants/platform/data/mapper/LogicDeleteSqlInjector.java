package cn.miniants.platform.data.mapper;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * 只给启用了 {@code @TableLogic} 的表补 {@code recoverByIds}。
 *
 * <p>无条件注入会让没有逻辑删除列的表在启动期就炸。
 */
public class LogicDeleteSqlInjector extends DefaultSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(
            Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
        if (tableInfo.getLogicDeleteFieldInfo() != null) {
            methods.add(new RecoverByIds());
        }
        return methods;
    }
}
