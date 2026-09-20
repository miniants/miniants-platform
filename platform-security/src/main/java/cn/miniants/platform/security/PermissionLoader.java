package cn.miniants.platform.security;

import java.util.Collection;
import java.util.Set;

/**
 * 按用户 / 角色展开权限码。JWT 保持瘦票，不把码写进 token。
 * 不要在这里绑采用方 Redis 键名。
 */
public interface PermissionLoader {

    Set<String> load(Long userId, Collection<Long> roleIds);
}
