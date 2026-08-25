package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.catalog.CatalogConflictException;
import dev.mytechprofile.sdlc.catalog.CatalogException;
import dev.mytechprofile.sdlc.port.CommandNotAllowedException;
import dev.mytechprofile.sdlc.port.PathJailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps catalog and validation failures to RFC 9457 problem details.
 *
 * <p><strong>When to use:</strong> automatic for every {@code /api/v1} controller.
 *
 * <p><strong>Example:</strong> unknown team id becomes {@code 400} with the CatalogException
 * message.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Handles unknown ids and invalid YAML.
     *
     * @param ex catalog failure
     * @return 400 problem
     */
    @ExceptionHandler(CatalogException.class)
    public ProblemDetail catalog(CatalogException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Catalog error", ex.getMessage());
    }

    /**
     * Handles create when the id already exists.
     *
     * @param ex conflict
     * @return 409 problem
     */
    @ExceptionHandler(CatalogConflictException.class)
    public ProblemDetail conflict(CatalogConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Already exists", ex.getMessage());
    }

    /**
     * Handles missing runs and bad path ids.
     *
     * @param ex illegal argument
     * @return 400 problem
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    /**
     * Handles lifecycle mismatches such as approve when not waiting.
     *
     * @param ex illegal state
     * @return 409 problem
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail conflictState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid run state", ex.getMessage());
    }

    /**
     * Handles path jail escapes.
     *
     * @param ex jail failure
     * @return 400 problem
     */
    @ExceptionHandler(PathJailException.class)
    public ProblemDetail jail(PathJailException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Path not allowed", ex.getMessage());
    }

    /**
     * Handles argv that is not in the project allowlist.
     *
     * @param ex command failure
     * @return 400 problem
     */
    @ExceptionHandler(CommandNotAllowedException.class)
    public ProblemDetail command(CommandNotAllowedException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Command not allowed", ex.getMessage());
    }

    /**
     * Handles bean-validation failures on request bodies.
     *
     * @param ex validation failure
     * @return 400 problem
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request body is invalid");
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
