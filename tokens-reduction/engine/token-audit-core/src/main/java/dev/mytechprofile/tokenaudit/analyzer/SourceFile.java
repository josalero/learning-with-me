package dev.mytechprofile.tokenaudit.analyzer;

/**
 * A Java source file loaded for analysis.
 *
 * @param relativePath path relative to the scanned project root
 * @param text full file contents
 */
record SourceFile(String relativePath, String text) {
}
