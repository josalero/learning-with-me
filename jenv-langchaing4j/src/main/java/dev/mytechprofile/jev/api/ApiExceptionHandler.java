package dev.mytechprofile.jev.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.mytechprofile.jev.decide.InvalidResumeException;
import dev.mytechprofile.jev.decide.JevClientException;

/**
 * Maps an invalid resume to 400 and a failed Jev call to 502.
 * Bodies stay free of resume text. Other exceptions are not turned into 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Turns a rejected resume into HTTP 400.
     *
     * <p>Example: posting both {@code resume} and {@code path} yields
     * {@code {"message":"Send either resume text or a path, not both"}}.
     *
     * @param ex client-facing reason; the message is returned as-is
     * @return 400 and {@link ErrorResponse}
     */
    @ExceptionHandler(InvalidResumeException.class)
    public ResponseEntity<ErrorResponse> badRequest(InvalidResumeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Turns a missing or malformed JSON body into HTTP 400.
     *
     * <p>Posting a truncated JSON body returns
     * {@code {"message":"Request body must be JSON with resume or path"}}.
     * The parser detail is not returned.
     *
     * @param ex unread body; its message is not copied into the response
     * @return 400 and a fixed {@link ErrorResponse}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Request body must be JSON with resume or path"));
    }

    /**
     * Turns a failed decision call into HTTP 502.
     *
     * <p>Timeouts, connection failures, and invalid JSON all return
     * {@code {"message":"Seniority decision failed"}}. The log line records the
     * exception class and the safe message, not the resume or the upstream body.
     *
     * @param ex failure whose message is safe to log
     * @return 502 and a generic {@link ErrorResponse}
     */
    @ExceptionHandler(JevClientException.class)
    public ResponseEntity<ErrorResponse> upstream(JevClientException ex) {
        log.warn("Seniority decision failed ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("Seniority decision failed"));
    }
}
