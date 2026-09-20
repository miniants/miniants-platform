package cn.miniants.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 身份档：已登录（用户或客户端）即可；指定 {@link #resolver()} 后为「登录 + 本人数据归属」。
 * <p>
 * 归属模式（resolver 非 {@link OwnedResolver.None}）：
 * 仅用户 token 可过；匿名在 shadow / enforce 下都 401；客户端与解析失败在 enforce 下拒绝。
 * 解析逻辑由项目实现 {@link OwnedResolver}，业务侧经 {@link OwnedContext} 读取已校验主体。
 * 与 {@link Permission} 互斥（同一方法只标一档）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Authenticated {

    /**
     * 本人归属解析器 Bean 类型。缺省 {@link OwnedResolver.None} = 仅要求已登录（用户或客户端即可，不看码）。
     * 指定后启动时校验该 Bean 存在。
     */
    Class<? extends OwnedResolver> resolver() default OwnedResolver.None.class;

    /**
     * 归属 key 的请求参数 / 路径变量名；仅 resolver 模式下有意义。
     * 留空 = 主体即本人（只解析并绑定主体）；非空 = 校验该 key 属于当前用户。
     */
    String param() default "";
}
