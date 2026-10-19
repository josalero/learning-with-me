package dev.mytechprofile.aigateway.shared.api;

import org.springframework.http.HttpStatus;

/**
 * An expected failure with the HTTP status to return. The message must not contain request data or PII.
 *
 * {@snippet :
 * throw new GatewayException(HttpStatus.NOT_FOUND, "Candidate was not found");
 * }
 */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;

    public GatewayException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    /** HTTP status the API returns for this failure. */
    public HttpStatus status() {
        return status;
    }
}
