package com.wayfare.controller;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RSAKey rsaKey;


    @GetMapping("/auth/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject()));
    }
}
