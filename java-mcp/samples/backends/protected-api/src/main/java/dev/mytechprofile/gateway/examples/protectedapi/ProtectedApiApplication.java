package dev.mytechprofile.gateway.examples.protectedapi;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class ProtectedApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProtectedApiApplication.class, args);
    }

    @RestController
    static class ProtectedController {

        static final String EXPECTED_BEARER = "demo-access-token";

        @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        Map<String, Object> token(
                @RequestParam("grant_type") String grantType,
                @RequestParam("client_id") String clientId,
                @RequestParam("client_secret") String clientSecret) {
            if (!"client_credentials".equals(grantType)
                    || !"gateway-client".equals(clientId)
                    || !"gateway-secret".equals(clientSecret)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_client");
            }
            return Map.of(
                    "access_token",
                    EXPECTED_BEARER,
                    "token_type",
                    "Bearer",
                    "expires_in",
                    3600);
        }

        @GetMapping("/api/secure/ping")
        Map<String, Object> ping(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
            if (!StringUtils.hasText(authorization) || !authorization.equals("Bearer " + EXPECTED_BEARER)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing or invalid bearer");
            }
            return Map.of("ok", true, "requestId", UUID.randomUUID().toString());
        }

        @GetMapping("/actuator/health")
        ResponseEntity<Map<String, String>> health() {
            return ResponseEntity.ok(Map.of("status", "UP"));
        }
    }
}
