package com.match.security;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordCodecTest {
    @Test
    public void verifiesBcryptAndLegacyPasswords() {
        PasswordCodec codec = new PasswordCodec();
        String encoded = codec.encode("secret1");

        assertTrue(codec.isEncoded(encoded));
        assertTrue(codec.matches("secret1", encoded));
        assertFalse(codec.matches("wrong", encoded));
        assertTrue(codec.matches("legacy", "legacy"));
    }
}
