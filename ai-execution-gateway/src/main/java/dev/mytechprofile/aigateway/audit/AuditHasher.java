package dev.mytechprofile.aigateway.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.config.GatewayProperties;

/**
 * HMAC-SHA256 of tool arguments and results. Raw content is not stored.
 */
@Component
public class AuditHasher {

    private final byte[] key;

    public AuditHasher(GatewayProperties properties) {
        String hmacKey = properties.audit().hmacKey();
        if (hmacKey == null || hmacKey.isBlank()) {
            throw new IllegalStateException("GATEWAY_AUDIT_HMAC_KEY is required");
        }
        this.key = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public String hmac(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is required", ex);
        }
    }
}
