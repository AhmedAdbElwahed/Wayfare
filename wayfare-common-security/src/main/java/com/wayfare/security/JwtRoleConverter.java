package com.wayfare.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Maps the auth-service {@code role} claim (RIDER / DRIVER / ADMIN) to a
 * {@code ROLE_*} authority. A valid signature only proves a token is authentic,
 * not which kind of account holds it — every service shares one issuer — so
 * each resource server authorizes on this authority.
 *
 * <p>A plain factory rather than an auto-configuration so a {@code @WebMvcTest}
 * that imports a service's {@code SecurityConfig} still gets it.
 */
public final class JwtRoleConverter {

    private JwtRoleConverter() {
    }

    public static JwtAuthenticationConverter create() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null
                    ? List.<GrantedAuthority>of()
                    : List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
