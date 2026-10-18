package com.daw.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Exposes the package-private key helpers of {@link JwtKeyConfig} to tests in other packages. */
public final class JwtKeyConfigTestSupport {

    private static final JwtKeyConfig CONFIG = new JwtKeyConfig();

    private JwtKeyConfigTestSupport() {
    }

    public static RSAKey generateKey() {
        return JwtKeyConfig.toRsaKey(JwtKeyConfig.generateKeyPair());
    }

    public static JwtEncoder encoder(RSAKey key) {
        return CONFIG.jwtEncoder(key);
    }

    public static JwtDecoder decoder(RSAKey key, JwtProperties properties) {
        try {
            return CONFIG.jwtDecoder(key, properties);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
