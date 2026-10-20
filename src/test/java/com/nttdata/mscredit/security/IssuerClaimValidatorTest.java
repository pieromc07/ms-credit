package com.nttdata.mscredit.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class IssuerClaimValidatorTest {

    private final IssuerClaimValidator validator = new IssuerClaimValidator("ms-customer");

    @Test
    void acceptsTokenFromExpectedIssuer() {
        assertThat(validator.validate(token("ms-customer")).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenFromOtherIssuer() {
        assertThat(validator.validate(token("someone-else")).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutIssuer() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("cust-1")
                .issuedAt(Instant.now())
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private static Jwt token(String issuer) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .subject("cust-1")
                .issuedAt(Instant.now())
                .build();
    }
}
