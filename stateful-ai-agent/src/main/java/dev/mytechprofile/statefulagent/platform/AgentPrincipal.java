package dev.mytechprofile.statefulagent.platform;

import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public record AgentPrincipal(String username, String tenantId, boolean approver) {

    public static AgentPrincipal current(AgentProperties properties) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("An authenticated user is required");
        }
        AgentProperties.UserAccount account = properties.requireUser(authentication.getName());
        return new AgentPrincipal(account.getUsername(), account.getTenant(), hasRole(authentication.getAuthorities(), "ROLE_APPROVER"));
    }

    public static boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    private static boolean hasRole(Collection<? extends GrantedAuthority> authorities, String role) {
        return authorities.stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
