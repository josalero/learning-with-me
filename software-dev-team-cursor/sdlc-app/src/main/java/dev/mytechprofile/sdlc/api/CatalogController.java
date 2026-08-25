package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.catalog.TeamBlueprint;
import dev.mytechprofile.sdlc.config.CatalogLoader;
import dev.mytechprofile.sdlc.config.CatalogWriter;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.config.WorkspaceSeeder;
import dev.mytechprofile.sdlc.domain.ArtifactFile;
import dev.mytechprofile.sdlc.domain.RoleKind;
import dev.mytechprofile.sdlc.domain.StateKeys;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalog HTTP API for teams, projects, prompts, seeds, and workspace seeding.
 *
 * <p><strong>When to use:</strong> the dashboard Teams and Projects tabs, and the Feature seed
 * button.
 *
 * <p><strong>Example:</strong> {@code GET /api/v1/teams} returns every YAML blueprint.
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogLoader loader;
    private final CatalogWriter writer;
    private final WorkspaceSeeder seeder;
    private final SdlcProperties properties;

    /**
     * Creates the catalog API.
     *
     * @param loader YAML reader
     * @param writer YAML writer
     * @param seeder workspace copy
     * @param properties home paths
     */
    public CatalogController(
            CatalogLoader loader, CatalogWriter writer, WorkspaceSeeder seeder, SdlcProperties properties) {
        this.loader = loader;
        this.writer = writer;
        this.seeder = seeder;
        this.properties = properties;
    }

    /**
     * Lists loaded teams.
     *
     * @return team blueprints
     */
    @GetMapping("/teams")
    public List<TeamBlueprint> teams() {
        return loader.loadTeams();
    }

    /**
     * Creates a team YAML file.
     *
     * @param request team document
     * @return created team
     */
    @PostMapping("/teams")
    public ResponseEntity<TeamBlueprint> createTeam(@Valid @RequestBody TeamWriteRequest request) {
        TeamBlueprint team = writer.saveTeam(toTeamDocument(request), false);
        return ResponseEntity.status(HttpStatus.CREATED).body(team);
    }

    /**
     * Replaces an existing team YAML file.
     *
     * @param id path id, must match the body
     * @param request team document
     * @return updated team
     */
    @PutMapping("/teams/{id}")
    public TeamBlueprint updateTeam(@PathVariable String id, @Valid @RequestBody TeamWriteRequest request) {
        if (!id.equals(request.id())) {
            throw new IllegalArgumentException("Path id '" + id + "' must match body id '" + request.id() + "'");
        }
        return writer.saveTeam(toTeamDocument(request), true);
    }

    /**
     * Lists loaded projects with paths relative to home.
     *
     * @return project views
     */
    @GetMapping("/projects")
    public List<ProjectView> projects() {
        return loader.loadProjects().stream()
                .map(project -> ProjectView.from(project, properties.home()))
                .toList();
    }

    /**
     * Creates a project YAML file.
     *
     * @param request project document
     * @return created project
     */
    @PostMapping("/projects")
    public ResponseEntity<ProjectView> createProject(@Valid @RequestBody ProjectWriteRequest request) {
        ProjectProfile profile = writer.saveProject(toProjectDocument(request), false);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectView.from(profile, properties.home()));
    }

    /**
     * Replaces an existing project YAML file.
     *
     * @param id path id, must match the body
     * @param request project document
     * @return updated project
     */
    @PutMapping("/projects/{id}")
    public ProjectView updateProject(@PathVariable String id, @Valid @RequestBody ProjectWriteRequest request) {
        if (!id.equals(request.id())) {
            throw new IllegalArgumentException("Path id '" + id + "' must match body id '" + request.id() + "'");
        }
        ProjectProfile profile = writer.saveProject(toProjectDocument(request), true);
        return ProjectView.from(profile, properties.home());
    }

    /**
     * Lists prompt files under {@code prompts/}.
     *
     * @return relative paths
     */
    @GetMapping("/prompts")
    public List<String> prompts() {
        return writer.listPrompts();
    }

    /**
     * Lists seed directory ids.
     *
     * @return seed ids
     */
    @GetMapping("/seeds")
    public List<String> seeds() {
        return writer.listSeeds();
    }

    /**
     * Lists convention files.
     *
     * @return relative paths
     */
    @GetMapping("/conventions")
    public List<String> conventions() {
        return writer.listConventions();
    }

    /**
     * Lists closed-set role kinds the Teams tab may assign.
     *
     * @return enum names
     */
    @GetMapping("/role-kinds")
    public List<String> roleKinds() {
        return Arrays.stream(RoleKind.values()).map(Enum::name).toList();
    }

    /**
     * Lists numbered artifact files written under {@code runs/<id>/}.
     *
     * @return file name, state key, and question
     */
    @GetMapping("/artifact-files")
    public List<ArtifactFileView> artifactFiles() {
        return Arrays.stream(ArtifactFile.values()).map(ArtifactFileView::from).toList();
    }

    /**
     * Lists agentic-scope keys for documentation and the dashboard.
     *
     * @return key names
     */
    @GetMapping("/state-keys")
    public List<String> stateKeys() {
        return List.of(
                StateKeys.FEATURE_REQUEST,
                StateKeys.FEATURE_BRIEF,
                StateKeys.AI_SPEC,
                StateKeys.CHANGE_SUMMARY,
                StateKeys.BUILD_RESULT,
                StateKeys.BUILD_FEEDBACK,
                StateKeys.BUILD_OUTPUT,
                StateKeys.REVIEW_VERDICT,
                StateKeys.REVIEW_FEEDBACK,
                StateKeys.QA_VERDICT,
                StateKeys.QA_FEEDBACK,
                StateKeys.STAKEHOLDER_DECISION,
                StateKeys.STAKEHOLDER_FOLLOW_UPS,
                StateKeys.FILE_TREE,
                StateKeys.CONVENTIONS,
                StateKeys.GIT_DIFF,
                StateKeys.RUN_ID,
                StateKeys.BRANCH);
    }

    /**
     * Copies a seed into {@code workspace/} and initializes git.
     *
     * @param request project id
     * @return 204 when seeded
     */
    @PostMapping("/workspace/seed")
    public ResponseEntity<Void> seed(@Valid @RequestBody SeedRequest request) {
        seeder.ensureWorkspace(loader.project(request.projectId()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Wipes the workspace copy and recopies the seed.
     *
     * @param request project id
     * @return 204 when reset
     */
    @PostMapping("/workspace/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody SeedRequest request) {
        seeder.resetWorkspace(loader.project(request.projectId()));
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> toTeamDocument(TeamWriteRequest request) {
        List<Map<String, Object>> roles = new ArrayList<>();
        for (TeamWriteRequest.RoleWriteRequest role : request.roles()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", role.id());
            row.put("kind", role.kind());
            row.put("model", role.model());
            row.put("prompt", role.prompt());
            row.put("temperature", role.temperature() == null ? 0.2 : role.temperature());
            roles.add(row);
        }
        TeamWriteRequest.PolicyWriteRequest policy = request.policy();
        Map<String, Object> policyMap = new LinkedHashMap<>();
        policyMap.put("maxSpecRework", defaultInt(policy.maxSpecRework(), 2));
        policyMap.put("maxImplementationAttempts", defaultInt(policy.maxImplementationAttempts(), 3));
        policyMap.put("maxReviewCycles", defaultInt(policy.maxReviewCycles(), 3));
        policyMap.put("maxQaCycles", defaultInt(policy.maxQaCycles(), 3));
        policyMap.put("maxStakeholderCycles", defaultInt(policy.maxStakeholderCycles(), 2));
        policyMap.put("qaPassThreshold", defaultInt(policy.qaPassThreshold(), 80));
        policyMap.put(
                "stakeholderMode",
                policy.stakeholderMode() == null || policy.stakeholderMode().isBlank()
                        ? "AGENT"
                        : policy.stakeholderMode());
        policyMap.put("maxDeveloperToolCalls", defaultInt(policy.maxDeveloperToolCalls(), 25));
        policyMap.put("maxReadOnlyToolCalls", defaultInt(policy.maxReadOnlyToolCalls(), 10));
        return CatalogWriter.teamDocument(request.id(), roles, policyMap);
    }

    private static Map<String, Object> toProjectDocument(ProjectWriteRequest request) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", request.id());
        document.put("seed", request.seed());
        document.put("repoPath", request.repoPath());
        document.put("branchPrefix", request.branchPrefix() == null ? "feature/" : request.branchPrefix());
        document.put("sourceGlobs", request.sourceGlobs() == null ? List.of() : request.sourceGlobs());
        if (request.conventions() != null && !request.conventions().isBlank()) {
            document.put("conventions", request.conventions());
        }
        document.put("commands", request.commands());
        document.put("timeoutSeconds", request.timeoutSeconds() == null ? 600L : request.timeoutSeconds());
        if (request.javaHome() != null && !request.javaHome().isBlank()) {
            document.put("javaHome", request.javaHome());
        }
        return document;
    }

    private static int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
