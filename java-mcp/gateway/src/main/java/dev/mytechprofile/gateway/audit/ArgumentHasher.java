package dev.mytechprofile.gateway.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds salted SHA-256 hashes of canonical tool arguments for audit correlation.
 */
@Component
public class ArgumentHasher {

    private final ObjectMapper mapper;
    private final String salt;

    public ArgumentHasher(
            ObjectMapper mapper,
            @Value("${gateway.observability.argument-hash-salt:gateway-poc-local-salt}") String salt) {
        this.mapper = mapper;
        this.salt = salt;
    }

    /**
     * Hashes arguments without storing them.
     *
     * @param arguments enriched tool arguments
     * @return hex SHA-256 digest
     */
    public String hash(Map<String, Object> arguments) {
        try {
            Map<String, Object> canonical = new TreeMap<>(arguments == null ? Map.of() : arguments);
            String json = mapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalStateException("cannot canonicalize arguments", ex);
        }
    }
}
