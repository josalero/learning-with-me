package dev.mytechprofile.sdlc.adapter;

import dev.mytechprofile.sdlc.port.PathJailException;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * File workspace jailed to a repository root. Symlinks that escape the root are rejected.
 *
 * <p>Build output and VCS metadata are invisible: agents should reason about sources, not about
 * {@code build/reports} or {@code node_modules}.
 *
 * <p>Sample: {@code new FileWorkspace(repo).writeFile("src/User.java", "...")}.
 */
public final class FileWorkspace implements WorkspacePort {

    static final int MAX_FILE_CHARS = 80_000;
    static final int MAX_LISTING_ENTRIES = 400;
    static final Set<String> GENERATED_DIRECTORIES = GeneratedTrees.DIRECTORY_NAMES;

    private final Path root;

    public FileWorkspace(Path root) {
        try {
            this.root = Files.createDirectories(root).toRealPath();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create workspace root: " + root, ex);
        }
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public List<String> listFiles(String glob) {
        String pattern = glob == null || glob.isBlank() ? "**/*" : glob;
        List<String> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    boolean generated = !dir.equals(root)
                            && GENERATED_DIRECTORIES.contains(dir.getFileName().toString());
                    return generated ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file) && insideJail(file)) {
                        matches.add(relativize(file));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list files in " + root, ex);
        }
        return matches.stream()
                .filter(relative -> globMatches(pattern, relative))
                .sorted()
                .limit(MAX_LISTING_ENTRIES)
                .toList();
    }

    @Override
    public String readFile(String relativePath) {
        Path target = resolveInsideJail(relativePath);
        String relative = relativize(target);
        if (isGenerated(relative)) {
            throw new IllegalArgumentException("Generated output is not readable: " + relative
                    + ". Read sources instead, for example src/main/**. Build logs come back from the build command.");
        }
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }
        try {
            String text = Files.readString(target, StandardCharsets.UTF_8);
            if (text.length() > MAX_FILE_CHARS) {
                throw new IllegalArgumentException("File exceeds " + MAX_FILE_CHARS + " characters: " + relative
                        + ". Read a single source file instead.");
            }
            return text;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + relativePath, ex);
        }
    }

    private String relativize(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    static boolean isGenerated(String relativePath) {
        for (String segment : relativePath.split("/")) {
            if (GENERATED_DIRECTORIES.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeFile(String relativePath, String content) {
        Path target = resolveInsideJail(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            if (!insideJail(target)) {
                Files.deleteIfExists(target);
                throw new PathJailException("Write escaped workspace jail: " + relativePath);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write " + relativePath, ex);
        }
    }

    @Override
    public void deleteFile(String relativePath) {
        Path target = resolveInsideJail(relativePath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete " + relativePath, ex);
        }
    }

    Path resolveInsideJail(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new PathJailException("Path is required");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new PathJailException("Absolute paths are not allowed: " + relativePath);
        }
        Path candidate = root.resolve(normalized).normalize();
        if (!candidate.startsWith(root)) {
            throw new PathJailException("Path escapes workspace jail: " + relativePath);
        }
        try {
            if (Files.exists(candidate)) {
                Path real = candidate.toRealPath();
                if (!real.startsWith(root)) {
                    throw new PathJailException("Symlink escapes workspace jail: " + relativePath);
                }
                return real;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to resolve " + relativePath, ex);
        }
        return candidate;
    }

    private boolean insideJail(Path path) {
        try {
            Path real = path.toRealPath();
            return real.startsWith(root);
        } catch (IOException ex) {
            return path.normalize().startsWith(root);
        }
    }

    static boolean globMatches(String glob, String path) {
        String regex = globToRegex(glob);
        return path.matches(regex) || Path.of(path).getFileName().toString().matches(regex);
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        regex.append("(?i)");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                    i++;
                    regex.append("/?");
                }
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.toString();
    }
}
