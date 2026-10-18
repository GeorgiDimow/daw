package com.daw.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;

class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void loadsConfiguredPemKeyPair() throws Exception {
        KeyPair pair = JwtKeyConfig.generateKeyPair();
        JwtProperties properties = new JwtProperties("daw-auth", Duration.ofMinutes(15),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()));

        RSAKey key = config.jwtSigningKey(properties, new MockEnvironment());

        assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
        assertThat(key.isPrivate()).isTrue();
    }

    @Test
    void generatesKeyInLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        RSAKey key = config.jwtSigningKey(new JwtProperties("daw-auth", Duration.ofMinutes(15), null, null), environment);

        assertThat(key.getKeyID()).isNotBlank();
    }

    @Test
    void refusesToStartWithoutKeysOutsideLocalProfiles() {
        JwtProperties properties = new JwtProperties("daw-auth", Duration.ofMinutes(15), null, null);

        assertThatThrownBy(() -> config.jwtSigningKey(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
    }

    @Test
    void publicJwkSetHasNoPrivatePart() {
        RSAKey key = JwtKeyConfigTestSupport.generateKey();

        assertThat(config.publicJwkSet(key).getKeys()).singleElement()
                .satisfies(jwk -> assertThat(jwk.isPrivate()).isFalse());
    }

    private static ByteArrayResource pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
