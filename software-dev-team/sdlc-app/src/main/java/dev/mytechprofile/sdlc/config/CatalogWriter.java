package dev.mytechprofile.sdlc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import dev.mytechprofile.sdlc.catalog.CatalogConflictException;
import dev.mytechprofile.sdlc.catalog.CatalogException;
import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.catalog.TeamBlueprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writes team and project YAML under {@code config/} after validating the result.
 *
 * <p><strong>When to use:</strong> HTTP create/update from the dashboard. Agents do not write
 * catalogs.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * writer.saveTeam(request, false);
 * }</pre>
 */
public final class CatalogWriter {

    private static final Pattern ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private final SdlcProperties properties;
    private final CatalogLoader loader;
    private final ObjectMapper yaml;

    /**
     * Creates a writer rooted at {@code properties.home()}.
     *
     * @param properties filesystem layout
     * @param loader used to validate files after write
     */
    public CatalogWriter(SdlcProperties properties, CatalogLoader loader) {
        this.properties = properties;
        this.loader = loader;
        this.yaml = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    }

    /**
     * Writes {@code config/teams/{id}.yaml}.
     *
     * @param document YAML-shaped team map ({@code id}, {@code roles}, {@code policy})
     * @param overwrite when false, existing ids are rejected
     * @return validated blueprint
     */
    public TeamBlueprint saveTeam(Map<String, Object> document, boolean overwrite) {
        String id = requireId(document.get("id"), "team");
        Path dest = properties.teamsDir().resolve(id + ".yaml");
        if (!overwrite && Files.exists(dest)) {
            throw new CatalogConflictException("Team '" + id + "' already exists. Use update or pick a new id.");
        }
        writeValidated(dest, document, file -> loader.readTeam(file));
        return loader.team(id);
    }

    /**
     * Writes {@code config/projects/{id}.yaml}.
     *
     * @param document YAML-shaped project map
     * @param overwrite when false, existing ids are rejected
     * @return validated profile
     */
    public ProjectProfile saveProject(Map<String, Object> document, boolean overwrite) {
        String id = requireId(document.get("id"), "project");
        Path dest = properties.projectsDir().resolve(id + ".yaml");
        if (!overwrite && Files.exists(dest)) {
            throw new CatalogConflictException("Project '" + id + "' already exists. Use update or pick a new id.");
        }
        writeValidated(dest, document, file -> loader.readProject(file));
        return loader.project(id);
    }

    /**
     * Lists prompt markdown files relative to home.
     *
     * @return paths such as {@code prompts/developer.md}
     */
    public List<String> listPrompts() {
        return listFiles(properties.promptsDir(), ".md", "prompts");
    }

    /**
     * Lists seed directory names.
     *
     * @return seed ids
     */
    public List<String> listSeeds() {
        return listDirectories(properties.seedsDir());
    }

    /**
     * Lists convention markdown files relative to home.
     *
     * @return paths such as {@code docs/conventions/java.md}
     */
    public List<String> listConventions() {
        return listFiles(properties.conventionsDir(), ".md", "docs/conventions");
    }

    private void writeValidated(Path dest, Map<String, Object> document, Validator validator) {
        try {
            Files.createDirectories(dest.getParent());
            Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
            yaml.writeValue(tmp.toFile(), document);
            try {
                validator.validate(tmp);
            } catch (RuntimeException ex) {
                Files.deleteIfExists(tmp);
                throw ex;
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new CatalogException("Cannot write catalog file " + dest + ". Check disk permissions.", ex);
        }
    }

    private static String requireId(Object raw, String kind) {
        String id = raw == null ? "" : String.valueOf(raw).trim();
        if (!ID.matcher(id).matches()) {
            throw new CatalogException("Invalid " + kind + " id '" + id
                    + "'. Use letters, digits, hyphen, or underscore, starting with a letter or digit.");
        }
        return id;
    }

    private static List<String> listFiles(Path dir, String suffix, String prefix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(suffix))
                    .sorted()
                    .map(name -> prefix + "/" + name)
                    .toList();
        } catch (IOException ex) {
            throw new CatalogException("Cannot list files in " + dir, ex);
        }
    }

    private static List<String> listDirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new CatalogException("Cannot list directories in " + dir, ex);
        }
    }

    /**
     * Copies a nested map defensively for YAML output.
     *
     * @param source request body
     * @return mutable linked map
     */
    public static Map<String, Object> copyDocument(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            throw new CatalogException("Request body is required");
        }
        return new LinkedHashMap<>(source);
    }

    /**
     * Builds a team document from typed pieces.
     *
     * @param id team id
     * @param roles role maps
     * @param policy policy map
     * @return YAML document
     */
    public static Map<String, Object> teamDocument(
            String id, List<Map<String, Object>> roles, Map<String, Object> policy) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", id);
        document.put("roles", roles == null ? List.of() : new ArrayList<>(roles));
        document.put("policy", policy == null ? Map.of() : new LinkedHashMap<>(policy));
        return document;
    }

    @FunctionalInterface
    private interface Validator {
        void validate(Path file);
    }
}
