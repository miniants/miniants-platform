package cn.miniants.platform.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * 在 MyBatis-Plus 的 {@code BaseMapper} 上补一个「撤销逻辑删除」。
 *
 * @see LogicDeleteSqlInjector 只给启用了 {@code @TableLogic} 的表注入实现
 */
public interface CrudMapper<T> extends BaseMapper<T> {

    /**
     * 把已逻辑删除的行改回未删除。表没启用 {@code @TableLogic} 时不会有实现，
     * 调用会在启动期就报找不到语句。
     */
    int recoverByIds(@Param("ids") Collection<?> ids);
}
