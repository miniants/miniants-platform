package cn.miniants.platform.security;

import org.springframework.web.method.HandlerMethod;

/**
 * 只认方法 / 类上的 {@link Permission}。应用要兼容历史 scope 时再覆盖 {@link #firstMatchingScope}。
 */
public class AnnotationPermissionPolicy implements PermissionPolicy {

    @Override
    public Requirement resolve(HandlerMethod handlerMethod, String httpMethod) {
        Permission method = handlerMethod.getMethodAnnotation(Permission.class);
        if (method != null) {
            return new Requirement(false, method.value());
        }
        Permission type = handlerMethod.getBeanType().getAnnotation(Permission.class);
        return type == null ? null : new Requirement(false, type.value());
    }
}
