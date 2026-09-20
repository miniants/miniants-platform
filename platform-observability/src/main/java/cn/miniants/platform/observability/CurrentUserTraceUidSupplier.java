package cn.miniants.platform.observability;

import cn.miniants.platform.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 有登录时写 username，否则 userId / clientId。
 * 优先读 request 属性（JWT Filter 绑在上面），不依赖 RequestContextHolder。
 */
public class CurrentUserTraceUidSupplier implements TraceUidSupplier {

    @Override
    public String current() {
        return resolve(CurrentUser.find());
    }

    @Override
    public String current(HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        return resolve(user != null ? user : CurrentUser.find());
    }

    private static String resolve(CurrentUser user) {
        if (user == null) {
            return TraceIds.ANON_UID;
        }
        if (StringUtils.hasText(user.username())) {
            return user.username();
        }
        if (user.userId() != null) {
            return String.valueOf(user.userId());
        }
        if (StringUtils.hasText(user.clientId())) {
            return user.clientId();
        }
        return TraceIds.ANON_UID;
    }
}
