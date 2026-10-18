package com.daw.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.daw.auth.config.JwtKeyConfigTestSupport;
import com.daw.auth.config.JwtProperties;
import com.daw.auth.user.User;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class TokenServiceTest {

    private final RSAKey key = JwtKeyConfigTestSupport.generateKey();
    private final JwtProperties properties = new JwtProperties("daw-auth", Duration.ofMinutes(15), null, null);
    private final Clock clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
    private final TokenService tokens = new TokenService(JwtKeyConfigTestSupport.encoder(key), properties, clock);
    private final JwtDecoder decoder = JwtKeyConfigTestSupport.decoder(key, properties);

    @Test
    void issuesSignedTokenWithUserClaims() {
        User user = new User("ada@example.com", "{bcrypt}hash", "Ada", clock.instant());

        TokenResponse response = tokens.issueAccessToken(user);
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", key.getKeyID());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("daw-auth");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ada@example.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ada");
        assertThat(jwt.getIssuedAt()).isEqualTo(clock.instant());
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }
}
