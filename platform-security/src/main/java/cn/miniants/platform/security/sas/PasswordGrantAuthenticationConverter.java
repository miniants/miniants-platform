package cn.miniants.platform.security.sas;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class PasswordGrantAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!PlatformGrantTypes.PASSWORD.getValue().equals(grantType)) {
            return null;
        }
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (clientPrincipal == null || !clientPrincipal.isAuthenticated()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        Set<String> scopes = Set.of();
        String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
        if (StringUtils.hasText(scope)) {
            scopes = new LinkedHashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }
        return new PasswordGrantAuthenticationToken(clientPrincipal, username, password, scopes);
    }
}
