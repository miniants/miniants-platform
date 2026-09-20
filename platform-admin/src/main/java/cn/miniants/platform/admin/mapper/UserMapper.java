package cn.miniants.platform.admin.mapper;

import cn.miniants.platform.admin.entity.User;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            <script>
            SELECT u.*
            FROM sys_user u
            LEFT JOIN sys_user_profile p ON p.user_id = u.id
            ${ew.customSqlSegment}
            </script>
            """)
    IPage<User> selectAdminPage(
            Page<User> page, @Param(Constants.WRAPPER) Wrapper<User> wrapper);
}
