package com.fertigate.config;

import com.fertigate.entity.SysPermission;
import com.fertigate.repository.SysPermissionRepository;
import com.fertigate.repository.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final SysPermissionRepository permissionRepository;
    private final SysUserRepository userRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier,
                                       RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        
        if (authentication == null || !authentication.isAuthenticated() 
                || authentication instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UUID userId)) {
            return new AuthorizationDecision(false);
        }

        HttpServletRequest request = context.getRequest();
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            requestUri = requestUri.substring(contextPath.length());
        }

        List<String> userPermissions = userRepository.findPermissionCodesByUserId(userId);

        List<SysPermission> allPermissions = permissionRepository.findAll();
        
        for (SysPermission permission : allPermissions) {
            if (permission.getResourceUrl() == null || permission.getResourceMethod() == null) {
                continue;
            }

            boolean urlMatched = false;
            String[] urlPatterns = permission.getResourceUrl().split(",");
            for (String pattern : urlPatterns) {
                pattern = pattern.trim();
                if (pathMatcher.match(pattern, requestUri)) {
                    urlMatched = true;
                    break;
                }
            }

            if (!urlMatched) {
                continue;
            }

            boolean methodMatched = false;
            String[] methods = permission.getResourceMethod().split(",");
            for (String method : methods) {
                if (method.trim().equalsIgnoreCase(requestMethod) || method.trim().equalsIgnoreCase("ALL")) {
                    methodMatched = true;
                    break;
                }
            }

            if (methodMatched) {
                boolean hasPermission = userPermissions.contains(permission.getPermissionCode());
                if (!hasPermission) {
                    log.warn("User {} denied access to {} {} - required permission: {}", 
                            userId, requestMethod, requestUri, permission.getPermissionCode());
                }
                return new AuthorizationDecision(hasPermission);
            }
        }

        return new AuthorizationDecision(true);
    }
}
