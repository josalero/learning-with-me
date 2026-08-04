package dev.mytechprofile.tokenaudit.cli;

import dev.mytechprofile.tokenaudit.Severity;

/**
 * Severity colors for console (ANSI), Markdown, and HTML reports.
 *
 * <p>Console color follows the <a href="https://no-color.org/">NO_COLOR</a> convention
 * and can be forced on with {@code FORCE_COLOR=1}.
 */
final class SeverityPalette {

	private static final String RESET = "\u001B[0m";
	private static final String BOLD = "\u001B[1m";
	private static final String DIM = "\u001B[2m";
	private static final String RED = "\u001B[31m";
	private static final String YELLOW = "\u001B[33m";
	private static final String CYAN = "\u001B[36m";
	private static final String BLUE = "\u001B[34m";
	private static final String GREEN = "\u001B[32m";
	private static final String MAGENTA = "\u001B[35m";

	private SeverityPalette() {
	}

	/**
	 * Whether ANSI colors should be used for console output.
	 *
	 * @return true when color is allowed
	 */
	static boolean ansiEnabled() {
		if (System.getenv("NO_COLOR") != null) {
			return false;
		}
		String force = System.getenv("FORCE_COLOR");
		if (force != null) {
			return !"0".equals(force);
		}
		return System.console() != null;
	}

	/** ANSI-wraps a severity tag such as {@code [HIGH]}. */
	static String ansiTag(Severity severity, boolean color) {
		String tag = "[" + severity.name() + "]";
		if (!color) {
			return tag;
		}
		return ansi(severity) + BOLD + tag + RESET;
	}

	/** ANSI-wraps an origin marker. */
	static String ansiOrigin(String origin, boolean color) {
		if (origin == null || origin.isEmpty()) {
			return "";
		}
		if (!color) {
			return origin;
		}
		return MAGENTA + origin + RESET;
	}

	/** ANSI-wraps the finding id. */
	static String ansiId(String id, boolean color) {
		if (!color) {
			return id;
		}
		return BOLD + id + RESET;
	}

	/** ANSI-wraps a secondary / muted line. */
	static String ansiMuted(String text, boolean color) {
		if (!color) {
			return text;
		}
		return DIM + text + RESET;
	}

	/** ANSI-wraps the recommendation arrow line. */
	static String ansiRecommendation(String text, boolean color) {
		if (!color) {
			return text;
		}
		return GREEN + text + RESET;
	}

	/** Markdown severity cell with emoji (portable across GitHub, editors, etc.). */
	static String markdown(Severity severity) {
		return switch (severity) {
			case HIGH -> "🔴 **HIGH**";
			case MEDIUM -> "🟠 **MEDIUM**";
			case LOW -> "🟡 **LOW**";
			case INFO -> "⚪ **INFO**";
		};
	}

	/** CSS hex for HTML severity pills. */
	static String htmlColor(Severity severity) {
		return switch (severity) {
			case HIGH -> "#b91c1c";
			case MEDIUM -> "#b45309";
			case LOW -> "#a16207";
			case INFO -> "#475569";
		};
	}

	/** Soft background for HTML severity pills. */
	static String htmlBackground(Severity severity) {
		return switch (severity) {
			case HIGH -> "#fee2e2";
			case MEDIUM -> "#ffedd5";
			case LOW -> "#fef9c3";
			case INFO -> "#f1f5f9";
		};
	}

	private static String ansi(Severity severity) {
		return switch (severity) {
			case HIGH -> RED;
			case MEDIUM -> YELLOW;
			case LOW -> CYAN;
			case INFO -> BLUE;
		};
	}
}
