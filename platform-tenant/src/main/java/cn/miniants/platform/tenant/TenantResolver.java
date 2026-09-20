package cn.miniants.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface TenantResolver {

    Optional<String> resolve(HttpServletRequest request);
}
