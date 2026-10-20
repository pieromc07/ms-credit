package com.nttdata.mscredit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nttdata.mscredit.util.TestRsaKeys;

class SecurityConfigTest {

    private static final String ISSUER = "ms-customer";

    @TempDir
    Path tempDir;

    @Test
    void decodesTokenSignedWithConfiguredKeyAndIssuer() throws Exception {
        KeyPair keys = TestRsaKeys.generate();
        ReactiveJwtDecoder decoder = decoder(keys);

        Jwt jwt = decoder.decode(sign(keys, ISSUER)).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("cust-1");
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        KeyPair keys = TestRsaKeys.generate();
        ReactiveJwtDecoder decoder = decoder(keys);
        String token = sign(keys, "someone-else");

        assertThrows(JwtException.class, () -> decoder.decode(token).block());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        ReactiveJwtDecoder decoder = decoder(TestRsaKeys.generate());
        String token = sign(TestRsaKeys.generate(), ISSUER);

        assertThrows(JwtException.class, () -> decoder.decode(token).block());
    }

    private ReactiveJwtDecoder decoder(KeyPair keys) throws Exception {
        Path publicKeyFile = tempDir.resolve("jwt-public-key.pem");
        Files.writeString(publicKeyFile, TestRsaKeys.publicPem(keys.getPublic()));
        JwtProperties properties = new JwtProperties(ISSUER, publicKeyFile.toUri().toString());
        return new SecurityConfig().jwtDecoder(properties);
    }

    private static String sign(KeyPair keys, String issuer) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("cust-1")
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }
}
