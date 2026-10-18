package com.daw.auth.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
