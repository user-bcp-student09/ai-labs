package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    @Test
    void createsDistinctSaltedHashesAndVerifiesPassword() {
        String firstHash = PasswordHasher.hash("FictionalPassword123!");
        String secondHash = PasswordHasher.hash("FictionalPassword123!");

        assertTrue(PasswordHasher.isHashed(firstHash));
        assertNotEquals(firstHash, secondHash);
        assertTrue(PasswordHasher.matches("FictionalPassword123!", firstHash));
        assertFalse(PasswordHasher.matches("IncorrectPassword123!", firstHash));
    }

    @Test
    void rejectsMalformedEncodedHashes() {
        String invalidIterations = "pbkdf2$not-a-number$salt$hash";
        String invalidBase64 = "pbkdf2$210000$%%%$hash";
        String invalidLengths = "pbkdf2$210000$c2FsdA$aGFzaA";
        assertFalse(PasswordHasher.matches("password", invalidIterations));
        assertFalse(PasswordHasher.matches("password", invalidBase64));
        assertFalse(PasswordHasher.matches("password", invalidLengths));
        assertFalse(PasswordHasher.isHashed(invalidIterations));
        assertFalse(PasswordHasher.isHashed(invalidBase64));
        assertFalse(PasswordHasher.isHashed(invalidLengths));
    }

    @Test
    void rejectsNullPasswordValues() {
        assertFalse(PasswordHasher.matches(null, "legacy"));
        assertFalse(PasswordHasher.matches("password", null));
        assertFalse(PasswordHasher.isHashed(null));
    }
}
