package dev.mytechprofile.aigateway.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest of an API key. The raw key is never stored.
 */
public final class ApiKeyHasher {

    private ApiKeyHasher() {}

    /** Hex SHA-256 of the raw key; this is what {@code api_key.key_hash} stores. */
    public static String sha256(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    /** Constant-time comparison, so timing does not reveal how much of a hash matched. */
    public static boolean matches(String storedHash, String computedHash) {
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8), computedHash.getBytes(StandardCharsets.UTF_8));
    }
}
