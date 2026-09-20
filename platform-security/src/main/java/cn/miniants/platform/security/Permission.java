package cn.miniants.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限码。多个码用 {@code |} 表示有其一即可。
 * 不要 {@code ignore}，不要按 URL 白名单。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    String value();
}
