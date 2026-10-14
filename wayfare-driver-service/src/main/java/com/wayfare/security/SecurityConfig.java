package com.wayfare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server only: JWTs come from auth-service. The {@code role} claim is
 * mapped to a {@code ROLE_*} authority (see {@link JwtRoleConverter}) because a
 * valid signature says nothing about which kind of account holds the token —
 * riders share the issuer.
 *
 * <p>Admin endpoints need {@code ROLE_ADMIN}, which only auth-service's
 * start-up {@code AdminBootstrap} (ADMIN_EMAIL / ADMIN_PASSWORD) can create.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/drivers/admin/**").hasRole("ADMIN")
                        .requestMatchers("/drivers/**").hasRole("DRIVER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverter.create())));
        return http.build();
    }
}
