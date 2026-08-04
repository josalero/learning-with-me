package dev.mytechprofile.tokenaudit.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Walks a project tree and loads Java source files, skipping build output and tests.
 * Shared by the regex and AST analyzer implementations.
 */
final class SourceScanner {

	private SourceScanner() {
	}

	/**
	 * Lists Java sources under {@code projectPath} in a stable order.
	 *
	 * @param projectPath project root
	 * @return loaded source files
	 */
	static List<SourceFile> javaSources(Path projectPath) {
		try (Stream<Path> paths = Files.walk(projectPath)) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
					.filter(path -> !isGeneratedOrTest(projectPath.relativize(path)))
					.sorted()
					.map(path -> readSource(projectPath, path))
					.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to scan Java sources under " + projectPath, ex);
		}
	}

	private static boolean isGeneratedOrTest(Path relativePath) {
		for (Path part : relativePath) {
			String name = part.toString();
			if ("build".equals(name) || ".gradle".equals(name) || ".git".equals(name)) {
				return true;
			}
		}
		String normalized = relativePath.toString().replace('\\', '/');
		return normalized.contains("/src/test/") || normalized.startsWith("src/test/");
	}

	private static SourceFile readSource(Path projectPath, Path path) {
		try {
			return new SourceFile(projectPath.relativize(path).toString(), Files.readString(path));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read " + path, ex);
		}
	}
}
