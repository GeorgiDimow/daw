package com.daw.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, encoder, clock);
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        User user = service.register("  Ada@Example.COM ", "s3cret-pass", " Ada ");

        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Ada");
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("s3cret-pass");
        assertThat(encoder.matches("s3cret-pass", user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void registerRejectsExistingEmail() {
        when(users.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("ada@example.com", "s3cret-pass", "Ada"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void registerTurnsUniqueIndexViolationIntoConflict() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.register("ada@example.com", "s3cret-pass", "Ada"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void authenticateAcceptsCorrectPassword() {
        User stored = new User("ada@example.com", encoder.encode("s3cret-pass"), "Ada", clock.instant());
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(stored));

        assertThat(service.authenticate("ADA@example.com", "s3cret-pass")).isSameAs(stored);
    }

    @Test
    void authenticateRejectsWrongPasswordAndUnknownEmail() {
        User stored = new User("ada@example.com", encoder.encode("s3cret-pass"), "Ada", clock.instant());
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(stored));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ada@example.com", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.authenticate("nobody@example.com", "s3cret-pass"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
