package com.daw.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * JWT settings. The key pair is given as PEM files, e.g.
 * {@code DAW_JWT_PUBLIC_KEY=file:/etc/daw/jwt/public.pem}. Without keys, the
 * {@code local} and {@code test} profiles generate a throwaway pair.
 */
@ConfigurationProperties("daw.jwt")
public record JwtProperties(String issuer, Duration ttl, Resource publicKey, Resource privateKey) {
}
