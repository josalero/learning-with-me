package dev.mytechprofile.tokenaudit.cli;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Output formats for a written scan report.
 */
public enum ReportFormat {
	/** Plain text, mirroring the console output (no ANSI). */
	TEXT("txt"),
	/** Machine-readable JSON, suitable for CI. */
	JSON("json"),
	/** Markdown report with emoji severity markers. */
	MARKDOWN("md"),
	/** Self-contained HTML report with colored severity pills. */
	HTML("html");

	private final String extension;

	ReportFormat(String extension) {
		this.extension = extension;
	}

	/**
	 * Returns the canonical file extension (without a dot) for this format.
	 *
	 * @return file extension such as {@code json}
	 */
	public String extension() {
		return this.extension;
	}

	/**
	 * Parses an explicit format name such as {@code json}, {@code md}, {@code html}, or {@code text}.
	 *
	 * @param value format name
	 * @return matching format
	 * @throws IllegalArgumentException if the name is unknown
	 */
	public static ReportFormat fromName(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("format must not be blank");
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "text", "txt" -> TEXT;
			case "json" -> JSON;
			case "md", "markdown" -> MARKDOWN;
			case "html", "htm" -> HTML;
			default -> throw new IllegalArgumentException("Unknown report format: " + value);
		};
	}

	/**
	 * Infers a format from a file's extension, defaulting to {@link #TEXT} when unknown.
	 *
	 * @param file the output file
	 * @return the inferred format
	 */
	public static ReportFormat fromFile(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return TEXT;
		}
		return switch (name.substring(dot + 1)) {
			case "json" -> JSON;
			case "md", "markdown" -> MARKDOWN;
			case "html", "htm" -> HTML;
			default -> TEXT;
		};
	}
}
