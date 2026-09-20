package cn.miniants.platform.data.audit;

import java.util.Optional;

/**
 * 当前写入人。不要依赖 Kisso {@code UserSession}；业务仓自己从登录上下文取。
 */
@FunctionalInterface
public interface AuditorSupplier {

    Optional<Auditor> current();
}
