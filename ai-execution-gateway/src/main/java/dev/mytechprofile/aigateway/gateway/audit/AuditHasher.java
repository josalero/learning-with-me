package dev.mytechprofile.aigateway.gateway.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.shared.config.GatewayProperties;

/**
 * HMAC-SHA256 of tool arguments and results with {@code GATEWAY_AUDIT_HMAC_KEY}. Raw content is not stored.
 *
 * <p>Anyone holding the key can recompute a value and compare, without the ledger keeping candidate data:
 * {@snippet :
 * String expected = hasher.hmac("{\"candidateId\":\"C-101\"}");
 * boolean same = expected.equals(step.getArgsHmac());
 * }
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

    /** HMAC-SHA256 of the value with {@code GATEWAY_AUDIT_HMAC_KEY}, hex-encoded; {@code null} stays {@code null}. */
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
