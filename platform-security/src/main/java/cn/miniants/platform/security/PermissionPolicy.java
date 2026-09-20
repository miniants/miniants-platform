package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;
import java.util.Arrays;

/**
 * 应用权限模型适配点。平台只负责执行判定，不感知应用包名、业务注解或历史 scope。
 */
public interface PermissionPolicy {

    record Requirement(boolean ignore, String code) {
    }

    Requirement resolve(HandlerMethod handlerMethod, String httpMethod);

    default boolean skip(HandlerMethod handlerMethod, HttpServletRequest request, Actor actor) {
        return false;
    }

    default boolean allowUnclassified(HandlerMethod handlerMethod, Actor actor) {
        return false;
    }

    default String[] requiredScopes(Requirement requirement) {
        if (requirement == null || requirement.ignore() || requirement.code() == null
                || requirement.code().isBlank()) {
            return new String[0];
        }
        return Arrays.stream(requirement.code().split("\\|"))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    default String firstMatchingScope(Set<String> granted, String[] required) {
        if (granted == null || required == null) {
            return null;
        }
        for (String code : required) {
            if (code != null && granted.contains(code.trim())) {
                return code.trim();
            }
        }
        return null;
    }
}
