package dev.mytechprofile.jev.api;

/**
 * Either paste resume text or point at a {@code .txt} file under the working directory.
 *
 * <p>Exactly one field must be non-blank. Example:
 * {@code {"path":"src/main/resources/resumes/senior.txt"}}.
 *
 * @param resume pasted plain text; null when {@code path} is set
 * @param path path relative to the working directory; null when {@code resume} is set
 */
public record SeniorityRequest(String resume, String path) {
}
