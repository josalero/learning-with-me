package dev.mytechprofile.gateway.model;

/**
 * Unchecked exception carrying a {@link GatewayError} through the pipeline.
 *
 * <p><strong>When to use:</strong> governance gates (validation, authorization,
 * quota, approval) throw this type; the pipeline converts it to
 * {@link ToolResult#error(GatewayError)}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * throw new GatewayException(new GatewayError.ValidationError("limit", "must be <= 25"));
 * }</pre>
 */
public class GatewayException extends RuntimeException {

    private final GatewayError error;

    /**
     * Creates an exception for the given stable error.
     *
     * @param error non-null gateway error
     */
    public GatewayException(GatewayError error) {
        super(error.message());
        this.error = error;
    }

    /**
     * Returns the stable error payload.
     *
     * @return gateway error
     */
    public GatewayError error() {
        return error;
    }
}
