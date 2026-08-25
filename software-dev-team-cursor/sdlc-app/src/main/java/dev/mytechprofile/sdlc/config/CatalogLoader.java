package dev.mytechprofile.sdlc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.mytechprofile.sdlc.catalog.CatalogException;
import dev.mytechprofile.sdlc.catalog.CatalogProblems;
import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.catalog.RoleSpec;
import dev.mytechprofile.sdlc.catalog.TeamBlueprint;
import dev.mytechprofile.sdlc.catalog.TeamPolicy;
import dev.mytechprofile.sdlc.domain.RoleKind;
import dev.mytechprofile.sdlc.domain.StakeholderMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads team and project YAML from disk and fails fast on invalid catalogs.
 *
 * <p><strong>When to use:</strong> application startup and tests that need a real catalog.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * CatalogLoader loader = new CatalogLoader(properties);
 * TeamBlueprint team = loader.team("default-scrum-team");
 * ProjectProfile project = loader.project("users-service-java");
 * }</pre>
 */
public final class CatalogLoader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)(?::([^}]*))?}");

    private final SdlcProperties properties;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<String, String> variables;

    /**
     * Creates a loader rooted at {@code properties.home()}.
     *
     * @param properties filesystem layout and default model slugs
     */
    public CatalogLoader(SdlcProperties properties) {
        this.properties = properties;
        this.variables = defaultVariables(properties);
    }

    /**
     * Loads every {@code *.yaml} team file from {@code config/teams}.
     *
     * @return immutable list of validated blueprints
     */
    public List<TeamBlueprint> loadTeams() {
        Path dir = properties.teamsDir();
        if (!Files.isDirectory(dir)) {
            throw new CatalogException("Teams directory is missing: " + dir.toAbsolutePath()
                    + ". Create config/teams and add at least one team YAML.");
        }
        List<TeamBlueprint> teams = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Path file : yamlFiles(dir)) {
            try {
                teams.add(readTeam(file));
            } catch (CatalogException ex) {
                problems.add(ex.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw CatalogProblems.of(
                    "Team catalog has problems. Fix every item, then restart or reload Teams:", problems);
        }
        if (teams.isEmpty()) {
            throw new CatalogException("No team YAML files found in " + dir.toAbsolutePath());
        }
        return List.copyOf(teams);
    }

    /**
     * Loads every {@code *.yaml} project file from {@code config/projects}.
     *
     * @return immutable list of validated profiles
     */
    public List<ProjectProfile> loadProjects() {
        Path dir = properties.projectsDir();
        if (!Files.isDirectory(dir)) {
            throw new CatalogException("Projects directory is missing: " + dir.toAbsolutePath()
                    + ". Create config/projects and add at least one project YAML.");
        }
        List<ProjectProfile> projects = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Path file : yamlFiles(dir)) {
            try {
                projects.add(readProject(file));
            } catch (CatalogException ex) {
                problems.add(ex.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw CatalogProblems.of(
                    "Project catalog has problems. Fix every item, then restart or reload Projects:", problems);
        }
        if (projects.isEmpty()) {
            throw new CatalogException("No project YAML files found in " + dir.toAbsolutePath());
        }
        return List.copyOf(projects);
    }

    /**
     * Returns the team with {@code id}.
     *
     * @param id blueprint id from YAML
     * @return matching team
     * @throws CatalogException when the id is unknown
     */
    public TeamBlueprint team(String id) {
        return loadTeams().stream()
                .filter(team -> team.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new CatalogException("Unknown team '" + id + "'. Check config/teams."));
    }

    /**
     * Returns the project with {@code id}.
     *
     * @param id profile id from YAML
     * @return matching project
     * @throws CatalogException when the id is unknown
     */
    public ProjectProfile project(String id) {
        return loadProjects().stream()
                .filter(project -> project.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new CatalogException("Unknown project '" + id + "'. Check config/projects."));
    }

    /**
     * Parses one team YAML file.
     *
     * @param file path to {@code *.yaml}
     * @return validated blueprint
     */
    @SuppressWarnings("unchecked")
    public TeamBlueprint readTeam(Path file) {
        Map<String, Object> raw = readMap(file);
        String id = requiredString(raw, "id", file);
        Object rolesNode = raw.get("roles");
        if (!(rolesNode instanceof List<?> rolesList) || rolesList.isEmpty()) {
            throw new CatalogException("Team " + id + " must declare a non-empty roles list in " + file);
        }
        List<RoleSpec> roles = new ArrayList<>();
        for (Object item : rolesList) {
            if (!(item instanceof Map<?, ?> roleMap)) {
                throw new CatalogException("Each role in " + file + " must be a mapping");
            }
            roles.add(toRole((Map<String, Object>) roleMap, file));
        }
        Object policyNode = raw.get("policy");
        if (!(policyNode instanceof Map<?, ?> policyMap)) {
            throw new CatalogException("Team " + id + " must declare policy in " + file);
        }
        return new TeamBlueprint(id, roles, toPolicy((Map<String, Object>) policyMap, file));
    }

    /**
     * Parses one project YAML file.
     *
     * @param file path to {@code *.yaml}
     * @return validated profile
     */
    @SuppressWarnings("unchecked")
    public ProjectProfile readProject(Path file) {
        Map<String, Object> raw = readMap(file);
        String id = requiredString(raw, "id", file);
        Path seed = properties.home().resolve(requiredString(raw, "seed", file)).normalize();
        Path repoPath =
                properties.home().resolve(requiredString(raw, "repoPath", file)).normalize();
        Path workspace = properties.workspaceDir().toAbsolutePath().normalize();
        Path absoluteRepo = repoPath.toAbsolutePath().normalize();
        if (!absoluteRepo.startsWith(workspace)) {
            throw new CatalogException("Project " + id + " repoPath must stay under " + workspace + " but was "
                    + absoluteRepo + ". Update config/projects/" + file.getFileName());
        }
        if (!Files.isDirectory(seed)) {
            throw new CatalogException(
                    "Project " + id + " seed directory does not exist: " + seed + ". Check the seed field in " + file);
        }
        String conventionsValue = stringOrNull(raw.get("conventions"));
        Path conventions = conventionsValue == null
                ? null
                : properties.home().resolve(conventionsValue).normalize();
        String branchPrefix = stringOrDefault(raw.get("branchPrefix"), "feature/");
        List<String> globs = stringList(raw.get("sourceGlobs"));
        Map<String, List<String>> commands = toCommands(raw.get("commands"), file);
        Duration timeout = Duration.ofSeconds(longOrDefault(raw.get("timeoutSeconds"), 600));
        String javaHomeValue = stringOrNull(raw.get("javaHome"));
        Path javaHome = javaHomeValue == null || javaHomeValue.isBlank() ? null : Path.of(javaHomeValue);
        return new ProjectProfile(
                id, seed, absoluteRepo, branchPrefix, globs, conventions, commands, timeout, javaHome);
    }

    private RoleSpec toRole(Map<String, Object> raw, Path file) {
        String id = requiredString(raw, "id", file);
        String kindValue = requiredString(raw, "kind", file);
        RoleKind kind;
        try {
            kind = RoleKind.valueOf(kindValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new CatalogException(
                    "Unknown role kind '" + kindValue + "' in " + file + ". Allowed: " + List.of(RoleKind.values()),
                    ex);
        }
        String model = interpolate(requiredString(raw, "model", file));
        String prompt = requiredString(raw, "prompt", file);
        Path promptPath = properties.home().resolve(prompt).normalize();
        if (!Files.isRegularFile(promptPath)) {
            throw new CatalogException("Prompt file for role " + id + " is missing: " + promptPath
                    + ". Create it or fix the prompt field in " + file);
        }
        double temperature = doubleOrDefault(raw.get("temperature"), 0.2);
        return new RoleSpec(id, kind, model, prompt, temperature);
    }

    private static TeamPolicy toPolicy(Map<String, Object> raw, Path file) {
        StakeholderMode mode = StakeholderMode.AGENT;
        String modeValue = stringOrNull(raw.get("stakeholderMode"));
        if (modeValue != null) {
            try {
                mode = StakeholderMode.valueOf(modeValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new CatalogException(
                        "Unknown stakeholderMode '" + modeValue + "' in " + file + ". Use AGENT or HUMAN.", ex);
            }
        }
        return new TeamPolicy(
                intOrDefault(raw.get("maxSpecRework"), 2),
                intOrDefault(raw.get("maxImplementationAttempts"), 3),
                intOrDefault(raw.get("maxReviewCycles"), 3),
                intOrDefault(raw.get("maxQaCycles"), 3),
                intOrDefault(raw.get("maxStakeholderCycles"), 2),
                intOrDefault(raw.get("qaPassThreshold"), 80),
                mode,
                intOrDefault(raw.get("maxDeveloperToolCalls"), 25),
                intOrDefault(raw.get("maxReadOnlyToolCalls"), 10));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> toCommands(Object node, Path file) {
        if (!(node instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new CatalogException("Project commands must be a non-empty mapping of name -> argv in " + file);
        }
        Map<String, List<String>> commands = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (!(value instanceof List<?> argv) || argv.isEmpty()) {
                throw new CatalogException(
                        "Command '" + key + "' in " + file + " must be a non-empty argv array, never a shell string");
            }
            List<String> args = new ArrayList<>();
            for (Object arg : argv) {
                args.add(String.valueOf(arg));
            }
            commands.put(String.valueOf(key), List.copyOf(args));
        });
        return Map.copyOf(commands);
    }

    private Map<String, Object> readMap(Path file) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yaml.readValue(file.toFile(), Map.class);
            if (raw == null) {
                throw new CatalogException("YAML file is empty: " + file);
            }
            return raw;
        } catch (IOException ex) {
            throw new CatalogException("Cannot parse YAML " + file + ". Check YAML syntax.", ex);
        }
    }

    private static List<Path> yamlFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new CatalogException("Cannot list YAML files in " + dir, ex);
        }
    }

    private String interpolate(String raw) {
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String fallback = matcher.group(2);
            String resolved = variables.get(key);
            if (resolved == null || resolved.isBlank()) {
                resolved = System.getenv(key);
            }
            if (resolved == null || resolved.isBlank()) {
                resolved = fallback;
            }
            if (resolved == null) {
                throw new CatalogException("Unresolved placeholder ${" + key + "}. Set the env var or a default.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Map<String, String> defaultVariables(SdlcProperties properties) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("MODEL_FAST", properties.modelFast());
        values.put("MODEL_STRONG", properties.modelStrong());
        return values;
    }

    private static String requiredString(Map<String, Object> raw, String key, Path file) {
        String value = stringOrNull(raw.get(key));
        if (value == null || value.isBlank()) {
            throw new CatalogException("Missing required field '" + key + "' in " + file);
        }
        return value.trim();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String stringOrDefault(Object value, String fallback) {
        String text = stringOrNull(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static List<String> stringList(Object node) {
        if (!(node instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> String.valueOf(item).trim()).toList();
    }

    private static int intOrDefault(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static long longOrDefault(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static double doubleOrDefault(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
