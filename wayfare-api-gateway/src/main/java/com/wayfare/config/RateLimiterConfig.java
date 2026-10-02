package com.wayfare.config;

import java.net.InetSocketAddress;
import java.security.Principal;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Bucket keys for the Redis {@code RequestRateLimiter}. Routes pick one by
 * name in wayfare-api-gateway.yml ({@code key-resolver: "#{@userKeyResolver}"}).
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Per authenticated account (JWT subject), so one rider's burst can't
     * starve others behind the same NAT. Falls back to the client address when
     * no principal is present.
     *
     * <p>{@code @Primary}: the gateway's own limiter factory autowires a single
     * {@code KeyResolver} as its default and fails to start when it finds two.
     * Routes still choose explicitly via {@code key-resolver}.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> clientAddress(exchange)));
    }

    /**
     * Per client address, for the unauthenticated /auth/login and
     * /auth/register routes where no principal exists yet. Uses the socket
     * address rather than X-Forwarded-For, which a client can spoof to dodge
     * the limit; put a trusted proxy resolver here if a load balancer fronts
     * the gateway.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.fromSupplier(() -> clientAddress(exchange));
    }

    private static String clientAddress(org.springframework.web.server.ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
