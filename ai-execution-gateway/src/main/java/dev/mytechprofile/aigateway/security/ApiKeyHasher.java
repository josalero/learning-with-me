package dev.mytechprofile.aigateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest of an API key. The raw key is never stored.
 */
public final class ApiKeyHasher {

    private ApiKeyHasher() {}

    public static String sha256(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    public static boolean matches(String storedHash, String computedHash) {
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8), computedHash.getBytes(StandardCharsets.UTF_8));
    }
}
