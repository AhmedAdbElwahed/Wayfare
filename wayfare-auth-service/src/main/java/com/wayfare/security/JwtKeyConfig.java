package com.wayfare.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Loads the RSA signing key pair from PEM files supplied at deploy time — a
 * Kubernetes Secret mounted as a volume, a Vault-injected file, etc. — rather
 * than generating one per startup. A persistent, shared key means tokens
 * survive restarts and every replica signs with the same key, so the JWKS a
 * resource server fetches from {@code /auth/.well-known/jwks.json} always
 * matches the signature on the tokens this service issues.
 *
 * <p>Locations are configured via {@code jwt.public-key} / {@code jwt.private-key}
 * and accept any Spring resource location ({@code file:}, {@code classpath:}, …).
 * Generate a 2048-bit PKCS#8 pair with:
 *
 * <pre>
 * openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
 * openssl rsa -pubout -in private.pem -out public.pem
 * </pre>
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey rsaKey(
            @Value("${jwt.public-key}") Resource publicKeyResource,
            @Value("${jwt.private-key}") Resource privateKeyResource) {
        RSAPublicKey publicKey = readPublicKey(publicKeyResource);
        RSAPrivateKey privateKey = readPrivateKey(privateKeyResource);
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    // Deterministic key id derived from the key material itself
                    // (RFC 7638 thumbprint): stable across restarts and replicas,
                    // and it changes only when the key does — which is what lets a
                    // resource server cache the JWKS by kid and rotate cleanly.
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to derive JWK key id from RSA key", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    private RSAPublicKey readPublicKey(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to read JWT public key from " + resource.getDescription(), e);
        }
    }

    private RSAPrivateKey readPrivateKey(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(in);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to read JWT private key from " + resource.getDescription(), e);
        }
    }
}
