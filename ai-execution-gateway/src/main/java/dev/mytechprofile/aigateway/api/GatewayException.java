package dev.mytechprofile.aigateway.api;

import org.springframework.http.HttpStatus;

/**
 * Request failure with a client-safe detail. The detail never includes personal data or secrets.
 */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;

    public GatewayException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
