package com.bcp.workshop.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_PREFIX = "pbkdf2$";
    private static final int ITERATION_COUNT = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = KEY_LENGTH_BITS / Byte.SIZE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {}

    static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derivedKey = deriveKey(password, salt, ITERATION_COUNT);
        return FORMAT_PREFIX + ITERATION_COUNT + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derivedKey);
    }

    static boolean matches(String password, String storedPassword) {
        if (password == null || storedPassword == null) {
            return false;
        }
        PasswordHash passwordHash = parseHash(storedPassword);
        if (passwordHash == null) {
            return MessageDigest.isEqual(
                    password.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8));
        }

        byte[] actualHash = deriveKey(
                password,
                passwordHash.salt(),
                passwordHash.iterations());
        return MessageDigest.isEqual(passwordHash.derivedKey(), actualHash);
    }

    static boolean isHashed(String storedPassword) {
        return parseHash(storedPassword) != null;
    }

    private static PasswordHash parseHash(String storedPassword) {
        if (storedPassword == null) {
            return null;
        }

        String[] parts = storedPassword.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return null;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] derivedKey = Base64.getDecoder().decode(parts[3]);
            if (iterations != ITERATION_COUNT
                    || salt.length != SALT_LENGTH_BYTES
                    || derivedKey.length != HASH_LENGTH_BYTES) {
                return null;
            }
            return new PasswordHash(iterations, salt, derivedKey);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static byte[] deriveKey(String password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations,
                KEY_LENGTH_BITS);
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
            return keyFactory.generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            keySpec.clearPassword();
        }
    }

    private record PasswordHash(int iterations, byte[] salt, byte[] derivedKey) {}
}
