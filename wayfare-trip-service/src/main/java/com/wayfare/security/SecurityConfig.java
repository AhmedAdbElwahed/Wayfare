package com.wayfare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT resource server, same shape as the rider service. Role rules mirror who may
 * issue which command: riders request/confirm, drivers run the ride, and either
 * may cancel or read their own trip (ownership is checked in the service/controller).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/trips/*/arriving", "/trips/*/start", "/trips/*/complete")
                        .hasRole("DRIVER")
                        .requestMatchers("/trips/**").hasAnyRole("RIDER", "DRIVER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverter.create())));
        return http.build();
    }
}
