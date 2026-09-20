package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 当前主体。由 Filter 验票后 {@link #bind(HttpServletRequest, CurrentUser)}，不读 Kisso Session。
 */
public final class CurrentUser {

    public static final String REQUEST_ATTR = CurrentUser.class.getName();

    private final Long userId;
    private final String username;
    private final String name;
    private final String clientId;
    private final List<Long> roleIds;
    private final Set<String> permissions;
    private final Set<String> scopes;
    private final boolean sysAdmin;

    private CurrentUser(Builder builder) {
        this.userId = builder.userId;
        this.username = builder.username;
        this.name = builder.name;
        this.clientId = builder.clientId;
        this.roleIds = builder.roleIds == null ? List.of() : List.copyOf(builder.roleIds);
        this.permissions = builder.permissions == null ? Set.of() : Set.copyOf(builder.permissions);
        this.scopes = builder.scopes == null ? Set.of() : Set.copyOf(builder.scopes);
        this.sysAdmin = builder.sysAdmin;
    }

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String name() {
        return name;
    }

    public String clientId() {
        return clientId;
    }

    public List<Long> roleIds() {
        return roleIds;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public boolean sysAdmin() {
        return sysAdmin;
    }

    public Actor actor() {
        if (userId != null || hasText(username)) {
            return Actor.USER;
        }
        if (hasText(clientId)) {
            return Actor.CLIENT;
        }
        return Actor.ANON;
    }

    public static CurrentUser find() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : from(request);
    }

    public static CurrentUser from(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object bound = request.getAttribute(REQUEST_ATTR);
        return bound instanceof CurrentUser user ? user : null;
    }

    public static CurrentUser require() {
        CurrentUser user = find();
        if (user == null) {
            throw new AuthDeniedException(SecurityCodes.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    public static void bind(HttpServletRequest request, CurrentUser user) {
        if (request != null) {
            request.setAttribute(REQUEST_ATTR, user);
        }
    }

    public static void clear(HttpServletRequest request) {
        if (request != null) {
            request.removeAttribute(REQUEST_ATTR);
        }
    }

    public Builder toBuilder() {
        return new Builder()
                .userId(userId)
                .username(username)
                .name(name)
                .clientId(clientId)
                .roleIds(roleIds)
                .permissions(permissions)
                .scopes(scopes)
                .sysAdmin(sysAdmin);
    }

    public static Builder user(Long userId, String username) {
        return new Builder().userId(userId).username(username);
    }

    public static Builder client(String clientId) {
        return new Builder().clientId(clientId);
    }

    public static HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class Builder {
        private Long userId;
        private String username;
        private String name;
        private String clientId;
        private Collection<Long> roleIds;
        private Collection<String> permissions;
        private Collection<String> scopes;
        private boolean sysAdmin;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder roleIds(Collection<Long> roleIds) {
            this.roleIds = roleIds;
            return this;
        }

        public Builder permissions(Collection<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder scopes(Collection<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder sysAdmin(boolean sysAdmin) {
            this.sysAdmin = sysAdmin;
            return this;
        }

        public CurrentUser build() {
            return new CurrentUser(this);
        }
    }
}
