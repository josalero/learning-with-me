package dev.mytechprofile.statefulagent.platform;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.mytechprofile.statefulagent.business.HiringRuleException;

/**
 * Maps domain failures to problem details. The type URI is
 * {@code https://stateful-agent.local/problems/{code}}.
 *
 * <p><strong>Example:</strong> Alice allowing a high-risk offer returns 403
 * with type {@code .../problems/forbidden}. Carol reading {@code APP-1} returns
 * 404 with detail {@code Application was not found.}
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** {@code APPLICATION_NOT_FOUND} is 404. Other hiring rules, such as {@code OFFER_OUT_OF_BAND} escaping the tool, are 409. */
    @ExceptionHandler(HiringRuleException.class)
    ResponseEntity<ProblemDetail> hiringRule(HiringRuleException exception) {
        if ("APPLICATION_NOT_FOUND".equals(exception.code())) {
            return problem(HttpStatus.NOT_FOUND, "not-found", "Application was not found.");
        }
        return problem(HttpStatus.CONFLICT, "hiring-rule", exception.getMessage());
    }

    /** A missing session, a foreign session, or an offer that has not been created. */
    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(SessionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "not-found", exception.getMessage());
    }

    /** A second message while an approval is pending, or a decision with a new idempotency key. */
    @ExceptionHandler(ApprovalConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ApprovalConflictException exception) {
        return problem(HttpStatus.CONFLICT, "conflict", exception.getMessage());
    }

    /** The caller can see the approval but lacks the approver role for a high-risk decision. */
    @ExceptionHandler(ApprovalForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ApprovalForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage());
    }

    /** The next turn would replay another provider's opaque parts and the mode is {@code REJECT}. Body includes {@code issues}. */
    @ExceptionHandler(ProviderIncompatibleException.class)
    ResponseEntity<ProblemDetail> incompatible(ProviderIncompatibleException exception) {
        ProblemDetail detail = problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, "provider-incompatible", exception.getMessage());
        detail.setProperty("issues", exception.issues());
        log.info("HTTP 422: this session has parts another provider cannot replay.");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(detail);
    }

    /** The session asks for OpenRouter or DeepSeek and that client was not created. */
    @ExceptionHandler(ProviderUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(ProviderUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "provider-unavailable", exception.getMessage());
    }

    /** Bean validation failed. {@code errors} lists {@code field: message}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        ProblemDetail detail = problemDetail(HttpStatus.BAD_REQUEST, "invalid-request", "The request is invalid.");
        detail.setProperty("errors", errors);
        log.info("HTTP 400: the request is invalid ({} field {}).", errors.size(), errors.size() == 1 ? "error" : "errors");
        return ResponseEntity.badRequest().body(detail);
    }

    /** A malformed argument, including a blank {@code Idempotency-Key}. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String message) {
        log.info("HTTP {}: {}", status.value(), message);
        return ResponseEntity.status(status).body(problemDetail(status, type, message));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String type, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create("https://stateful-agent.local/problems/" + type));
        return detail;
    }
}
