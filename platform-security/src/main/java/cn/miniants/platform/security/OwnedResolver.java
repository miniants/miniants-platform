package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 本人数据归属解析 SPI。项目按业务语义实现，并在 {@link Authenticated#resolver()} 上点名 Bean 类型；
 * 解析出的业务主体（如学生档案、本人登记记录）经 {@link OwnedContext} 供业务读取。
 * 内核不感知任何项目身份类型，{@link Resolution.Ok#subject()} 为 {@link Object}。
 */
public interface OwnedResolver {

    /** 解析当前主体是否拥有该资源。 */
    Resolution resolve(OwnedRequest request);

    /**
     * ok 审计粒度后缀，如 {@code "student"} → {@code owned-ok:student}；留空 → {@code owned-ok}。
     * 供日志分析区分解析形态，不参与判定。
     */
    default String okSuffix() {
        return "";
    }

    /**
     * 登录-only 标记解析器：{@link Authenticated#resolver()} 的缺省值，不查归属，
     * 由 {@link PermissionInterceptor} 按原「仅登录」语义把关。
     */
    final class None implements OwnedResolver {
        @Override
        public Resolution resolve(OwnedRequest request) {
            return new Resolution.Ok(request.user());
        }
    }

    /** 一次归属解析的入参。{@code resourceKey} 为注解 {@code param} 命中的请求参数 / 路径变量，未声明为 null。 */
    record OwnedRequest(String resourceKey, CurrentUser user, HttpServletRequest request) {
    }

    /** 一次归属解析的结果。三种实现：{@link Ok} / {@link NotOwner} / {@link Unresolved}。 */
    interface Resolution {

        /** 校验通过；subject 为业务主体，enforce 与 shadow 下均放行并绑定 {@link OwnedContext}。 */
        record Ok(Object subject) implements Resolution {
        }

        /** 资源不属于当前主体。enforce 下 403「只能操作本人数据」。 */
        record NotOwner(String detail) implements Resolution {
        }

        /** 无法解析主体或资源键缺失。enforce 下 401「无法确认本人身份」（已登录）；匿名仍「未登录」。 */
        record Unresolved(String detail) implements Resolution {
        }
    }
}
