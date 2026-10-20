package com.nttdata.mscredit.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/**
 * Accepts only tokens issued by the expected issuer. Reads the claim as a string because
 * {@link Jwt#getIssuer()} parses it as a URL, and ms-customer uses a plain name.
 */
public class IssuerClaimValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedIssuer;

    public IssuerClaimValidator(String expectedIssuer) {
        this.expectedIssuer = expectedIssuer;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (expectedIssuer.equals(token.getClaimAsString(JwtClaimNames.ISS))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Unexpected token issuer", null));
    }
}
