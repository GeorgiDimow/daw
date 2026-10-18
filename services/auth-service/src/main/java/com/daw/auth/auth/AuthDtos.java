package com.daw.auth.auth;

import java.time.Instant;
import java.util.UUID;

import com.daw.auth.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // bcrypt only uses the first 72 bytes of a password
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName) {
}

record LoginRequest(@NotBlank String email, @NotBlank String password) {
}

record UserResponse(UUID id, String email, String displayName, Instant createdAt) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
