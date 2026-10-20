package com.nttdata.mscredit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(String issuer, String publicKeyLocation) {
}
