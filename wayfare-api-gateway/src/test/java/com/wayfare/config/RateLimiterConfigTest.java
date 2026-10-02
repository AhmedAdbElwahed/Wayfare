package com.wayfare.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    @Test
    void ipResolverKeysOnSocketAddressNotForwardedHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/login")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 5555))
                .header("X-Forwarded-For", "6.6.6.6"));

        assertThat(config.ipKeyResolver().resolve(exchange).block()).isEqualTo("10.1.2.3");
    }

    @Test
    void userResolverFallsBackToAddressWhenAnonymous() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/riders/me")
                .remoteAddress(new InetSocketAddress("10.9.9.9", 1)));

        assertThat(config.userKeyResolver().resolve(exchange).block()).isEqualTo("10.9.9.9");
    }
}
