package com.daw.auth.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties, Environment environment) {
        KeyPair keyPair = loadOrGenerate(properties, environment);
        return toRsaKey(keyPair);
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /** Public half only; served at /.well-known/jwks.json for other services. */
    @Bean
    JWKSet publicJwkSet(RSAKey jwtSigningKey) {
        return new JWKSet(jwtSigningKey.toPublicJWK());
    }

    static RSAKey toRsaKey(KeyPair keyPair) {
        try {
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not build JWK from key pair", e);
        }
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }

    private static KeyPair loadOrGenerate(JwtProperties properties, Environment environment) {
        if (properties.publicKey() != null && properties.privateKey() != null) {
            return new KeyPair(readPublicKey(properties.publicKey()), readPrivateKey(properties.privateKey()));
        }
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            log.warn("No JWT key pair configured; generating a throwaway key. Tokens will not survive a restart.");
            return generateKeyPair();
        }
        throw new IllegalStateException("daw.jwt.public-key and daw.jwt.private-key must be configured");
    }

    private static RSAPublicKey readPublicKey(Resource resource) {
        try {
            byte[] der = pemToDer(resource, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid JWT public key: " + resource, e);
        }
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            byte[] der = pemToDer(resource, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid JWT private key (expected PKCS#8 PEM): " + resource, e);
        }
    }

    private static byte[] pemToDer(Resource resource, String type) {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            String base64 = pem
                    .replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read JWT key: " + resource, e);
        }
    }
}
