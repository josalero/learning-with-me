package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.mytechprofile.sdlc.adapter.FileWorkspace;
import dev.mytechprofile.sdlc.catalog.CatalogException;
import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.catalog.TeamBlueprint;
import dev.mytechprofile.sdlc.config.CatalogLoader;
import dev.mytechprofile.sdlc.config.PromptLoader;
import dev.mytechprofile.sdlc.config.RepoContextAssembler;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.config.WorkspaceSeeder;
import dev.mytechprofile.sdlc.domain.RoleKind;
import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.StateKeys;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import dev.mytechprofile.sdlc.port.CommandRunner;
import dev.mytechprofile.sdlc.port.RunStore;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one feature through the configured team against a real workspace and build.
 *
 * <p><strong>When to use:</strong> {@code RunService} starts a virtual thread that calls
 * {@link #run(String, RunCommand, Consumer)}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * RunOutcome outcome = orchestrator.run(runId, command, event -> sse.send(event));
 * }</pre>
 */
public final class SdlcOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SdlcOrchestrator.class);

    private final CatalogLoader catalogs;
    private final WorkspaceSeeder seeder;
    private final VersionControlPort git;
    private final ArtifactStore artifacts;
    private final RunStore runs;
    private final RunStateFactory state;
    private final RunOutcomeAssembler outcomes;
    private final SdlcPipelineFactory pipelineFactory;

    /**
     * Creates an orchestrator with ports and catalog access.
     *
     * @param catalogs team and project YAML
     * @param models per-role chat models
     * @param prompts system prompts
     * @param seeder copies seeds into workspace
     * @param assembler file tree and conventions
     * @param commandRunner allowlisted build/test
     * @param git local git
     * @param artifacts run files
     * @param runs in-memory run records
     * @param approvalGate human stakeholder
     * @param properties timeouts and home
     */
    public SdlcOrchestrator(
            CatalogLoader catalogs,
            RoleModelProvider models,
            PromptLoader prompts,
            WorkspaceSeeder seeder,
            RepoContextAssembler assembler,
            CommandRunner commandRunner,
            VersionControlPort git,
            ArtifactStore artifacts,
            RunStore runs,
            HumanApprovalGate approvalGate,
            SdlcProperties properties) {
        this.catalogs = catalogs;
        this.seeder = seeder;
        this.git = git;
        this.artifacts = artifacts;
        this.runs = runs;
        this.state = new RunStateFactory(assembler, git);
        this.outcomes = new RunOutcomeAssembler(artifacts, runs);
        AgentFactory agents = new AgentFactory(models, prompts);
        this.pipelineFactory = new SdlcPipelineFactory(
                agents, state, outcomes, commandRunner, git, artifacts, runs, approvalGate, properties);
    }

    /**
     * Executes the SDLC pipeline for {@code command}.
     *
     * @param runId run identifier
     * @param command team, project, feature text
     * @param events live step listener
     * @return completed, escalated, or failed outcome
     */
    public RunOutcome run(String runId, RunCommand command, Consumer<StepEvent> events) {
        TeamBlueprint team = catalogs.team(command.teamId());
        if (!team.has(RoleKind.DEVELOPER)) {
            throw new CatalogException(
                    "Team " + team.id() + " has no DEVELOPER role. Add one in config/teams or the Teams tab.");
        }
        ProjectProfile project = catalogs.project(command.projectId());
        seeder.ensureWorkspace(project);
        WorkspacePort workspace = new FileWorkspace(project.repoPath());
        String branch = project.branchPrefix() + "run-" + runId.substring(0, Math.min(8, runId.length()));
        git.createBranch(workspace.root(), branch);

        StepCollectingListener listener = new StepCollectingListener();
        listener.addListener(event -> {
            artifacts.appendStep(runId, event);
            events.accept(event);
        });

        RunContext context = new RunContext(runId, command, team, project, workspace, branch, listener, events);
        UntypedAgent pipeline = pipelineFactory.build(context);

        Map<String, Object> inputs = state.initialState(command, project, workspace);
        inputs.put(StateKeys.RUN_ID, runId);
        inputs.put(StateKeys.BRANCH, branch);

        try {
            ResultWithAgenticScope<?> result = pipeline.invokeWithAgenticScope(inputs);
            RunOutcome outcome = outcomes.assemble(context, result.agenticScope(), listener.steps());
            outcomes.persist(runId, outcome);
            runs.save(outcome);
            artifacts.writeOutcome(outcome);
            return outcome;
        } catch (RuntimeException ex) {
            log.error("SDLC run {} failed: {}", runId, PipelineFailures.userMessage(ex), ex);
            RunOutcome failed = current(runId).failed(PipelineFailures.userMessage(ex));
            failed = failed.withSteps(listener.steps());
            runs.save(failed);
            artifacts.writeOutcome(failed);
            return failed;
        }
    }

    private RunOutcome current(String runId) {
        return runs.find(runId).orElseThrow(() -> new IllegalStateException("Unknown run " + runId));
    }
}
