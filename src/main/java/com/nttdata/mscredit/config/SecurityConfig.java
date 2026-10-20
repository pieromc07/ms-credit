package com.nttdata.mscredit.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.nttdata.mscredit.security.IssuerClaimValidator;
import com.nttdata.mscredit.util.PemKeyLoader;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/webjars/**"
    };

    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        RSAPublicKey publicKey = PemKeyLoader.loadPublicKey(
                readPem(new DefaultResourceLoader(), properties.publicKeyLocation()));
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new IssuerClaimValidator(properties.issuer())));
        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .build();
    }

    private static String readPem(ResourceLoader loader, String location) {
        try {
            return loader.getResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JWT public key at " + location, exception);
        }
    }
}
