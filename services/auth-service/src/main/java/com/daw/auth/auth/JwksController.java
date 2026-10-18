package com.daw.auth.auth;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwksController {

    private final JWKSet publicJwkSet;

    JwksController(JWKSet publicJwkSet) {
        this.publicJwkSet = publicJwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }
}
