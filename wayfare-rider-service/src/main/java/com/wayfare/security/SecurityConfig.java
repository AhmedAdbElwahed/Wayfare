package com.wayfare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Until this bean existed, the service leaned on Spring Security's
 * autoconfigured default — with the resource server starter on the classpath
 * that means *every* request needs a valid JWT, which is right for
 * `/riders/**` but leaves no way to let anything through. The health endpoint
 * is the first thing that needs to be public: Compose (and, later, a
 * Kubernetes probe) curls it with no token, and an invisible default that
 * answers 401 would keep the container permanently unhealthy.
 *
 * JWT validation itself is unchanged, but it now has to be declared
 * explicitly — a custom chain switches off the resource server's own
 * autoconfigured chain, so dropping `oauth2ResourceServer` here would leave
 * `/riders/**` unauthenticated rather than JWT-protected.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // /health/liveness and /health/readiness live under this
                        // prefix too, hence the wildcard rather than an exact match.
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**").permitAll()
                        // A valid signature only proves the token is authentic, not
                        // that the account is a rider: drivers share this issuer.
                        .requestMatchers("/riders/**").hasRole("RIDER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Maps the auth-service {@code role} claim (RIDER / DRIVER) to a {@code ROLE_*} authority. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
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
