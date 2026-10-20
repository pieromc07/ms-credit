package com.nttdata.mscredit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

import org.junit.jupiter.api.Test;

class PemKeyLoaderTest {

    @Test
    void loadsPublicKeyFromPem() {
        KeyPair keys = TestRsaKeys.generate();

        RSAPublicKey loaded = PemKeyLoader.loadPublicKey(TestRsaKeys.publicPem(keys.getPublic()));

        assertThat(loaded.getModulus()).isEqualTo(((RSAPublicKey) keys.getPublic()).getModulus());
        assertThat(loaded.getPublicExponent()).isEqualTo(((RSAPublicKey) keys.getPublic()).getPublicExponent());
    }

    @Test
    void rejectsNonBase64Content() {
        assertThrows(IllegalArgumentException.class,
                () -> PemKeyLoader.loadPublicKey("-----BEGIN PUBLIC KEY-----\n!!!\n-----END PUBLIC KEY-----"));
    }

    @Test
    void rejectsBytesThatAreNotAnRsaKey() {
        assertThrows(IllegalArgumentException.class,
                () -> PemKeyLoader.loadPublicKey("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----"));
    }
}
