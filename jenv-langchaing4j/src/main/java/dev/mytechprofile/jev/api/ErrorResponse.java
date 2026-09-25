package dev.mytechprofile.jev.api;

/**
 * Error body for rejected input and a failed decision call.
 *
 * <p>Serialized as {@code {"message":"..."}}. The lab page reads {@code message}.
 *
 * @param message short reason; never the resume or an upstream payload
 */
public record ErrorResponse(String message) {
}
