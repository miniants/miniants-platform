package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.entity.Role;
import cn.miniants.platform.admin.entity.Resource;
import cn.miniants.platform.admin.mapper.RoleMapper;
import cn.miniants.platform.admin.mapper.ResourceMapper;
import cn.miniants.platform.core.error.PlatformException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.List;
import java.util.Objects;

public final class AdminReferenceValidator {

    private AdminReferenceValidator() {
    }

    public static void requireRoles(RoleMapper roleMapper, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> unique = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (unique.isEmpty()) {
            return;
        }
        Long count = roleMapper.selectCount(Wrappers.<Role>lambdaQuery().in(Role::getId, unique));
        if (count == null || count != unique.size()) {
            throw new PlatformException("存在无效的角色 ID");
        }
    }

    public static void requireResources(ResourceMapper resourceMapper, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        List<Long> unique = resourceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (unique.isEmpty()) {
            return;
        }
        Long count = resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().in(Resource::getId, unique));
        if (count == null || count != unique.size()) {
            throw new PlatformException("存在无效的资源 ID");
        }
    }
}
