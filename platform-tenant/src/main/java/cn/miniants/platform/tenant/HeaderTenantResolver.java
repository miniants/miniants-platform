package cn.miniants.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public class HeaderTenantResolver implements TenantResolver {

    public static final String HEADER = "X-Platform-Tenant";

    private final String headerName;

    public HeaderTenantResolver(String headerName) {
        this.headerName = headerName == null || headerName.isBlank() ? HEADER : headerName;
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
