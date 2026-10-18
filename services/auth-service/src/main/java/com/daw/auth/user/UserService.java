package com.daw.auth.user;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    // Compared against when the email is unknown, so login takes the same time either way.
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public User register(String email, String rawPassword, String displayName) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyRegisteredException(normalized);
        }
        User user = new User(normalized, passwordEncoder.encode(rawPassword), displayName.strip(), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // A concurrent registration won the race for the unique index.
            throw new EmailAlreadyRegisteredException(normalized);
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        User user = users.findByEmail(normalize(email)).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(rawPassword, hash);
        if (user == null || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
