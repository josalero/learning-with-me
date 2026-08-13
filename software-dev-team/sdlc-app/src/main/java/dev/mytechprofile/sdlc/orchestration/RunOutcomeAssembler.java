package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ArtifactFile;
import dev.mytechprofile.sdlc.domain.BuildResult;
import dev.mytechprofile.sdlc.domain.ChangeSummary;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.QaVerdict;
import dev.mytechprofile.sdlc.domain.ReviewVerdict;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.RunStatus;
import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import dev.mytechprofile.sdlc.domain.StateKeys;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import dev.mytechprofile.sdlc.port.RunStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link RunOutcome} snapshots and writes numbered artifacts.
 *
 * <p><strong>When to use:</strong> after the pipeline returns, and when pausing for human
 * approval.
 *
 * <p><strong>Example:</strong> {@code assembler.persist(runId, outcome)}.
 */
public final class RunOutcomeAssembler {

    private static final Logger log = LoggerFactory.getLogger(RunOutcomeAssembler.class);

    private final ArtifactStore artifacts;
    private final RunStore runs;

    /**
     * Creates an assembler bound to run storage.
     *
     * @param artifacts file store
     * @param runs in-memory index
     */
    public RunOutcomeAssembler(ArtifactStore artifacts, RunStore runs) {
        this.artifacts = artifacts;
        this.runs = runs;
    }

    /**
     * Reads current products from the scope and attaches steps.
     *
     * @param context run handles
     * @param scope finished or paused scope
     * @param steps recorded steps
     * @return snapshot without a terminal status
     */
    public RunOutcome snapshot(RunContext context, AgenticScope scope, List<StepEvent> steps) {
        FeatureBrief brief = RunStateFactory.read(
                scope,
                StateKeys.FEATURE_BRIEF,
                FeatureBrief.fromRequest(context.command().featureRequest()));
        AiSpec spec = RunStateFactory.read(scope, StateKeys.AI_SPEC, AiSpec.empty());
        ChangeSummary changes = RunStateFactory.read(scope, StateKeys.CHANGE_SUMMARY, ChangeSummary.none());
        BuildResult build = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
        ReviewVerdict review = RunStateFactory.read(scope, StateKeys.REVIEW_VERDICT, ReviewVerdict.none());
        QaVerdict qa = RunStateFactory.read(scope, StateKeys.QA_VERDICT, QaVerdict.none());
        StakeholderDecision stakeholder =
                RunStateFactory.read(scope, StateKeys.STAKEHOLDER_DECISION, StakeholderDecision.none());
        return current(context.runId())
                .withProducts(context.branch(), brief, spec, changes, build, review, qa, stakeholder)
                .withSteps(steps);
    }

    /**
     * Marks the snapshot completed or escalated from stakeholder + build.
     *
     * @param context run handles
     * @param scope finished scope
     * @param steps recorded steps
     * @return terminal outcome
     */
    public RunOutcome assemble(RunContext context, AgenticScope scope, List<StepEvent> steps) {
        RunOutcome snapshot = snapshot(context, scope, steps);
        StakeholderDecision stakeholder = snapshot.stakeholderDecision();
        BuildResult build = snapshot.buildResult();
        RunStatus status = stakeholder.isApproved() && build.success() ? RunStatus.COMPLETED : RunStatus.ESCALATED;
        return snapshot.withStatus(status).finishedNow();
    }

    /**
     * Writes every {@link ArtifactFile} JSON product from the outcome.
     *
     * @param runId run identifier
     * @param outcome finished outcome
     */
    public void persist(String runId, RunOutcome outcome) {
        artifacts.writeJson(runId, ArtifactFile.FEATURE_BRIEF.fileName(), outcome.featureBrief());
        artifacts.writeJson(runId, ArtifactFile.AI_SPEC.fileName(), outcome.aiSpec());
        artifacts.writeJson(runId, ArtifactFile.CHANGE_SUMMARY.fileName(), outcome.changeSummary());
        artifacts.writeJson(runId, ArtifactFile.BUILD_RESULT.fileName(), outcome.buildResult());
        artifacts.writeJson(runId, ArtifactFile.REVIEW_VERDICT.fileName(), outcome.reviewVerdict());
        artifacts.writeJson(runId, ArtifactFile.QA_VERDICT.fileName(), outcome.qaVerdict());
        artifacts.writeJson(runId, ArtifactFile.STAKEHOLDER_DECISION.fileName(), outcome.stakeholderDecision());
        artifacts.writeOutcome(outcome);
        log.debug("Wrote artifacts for run {}", runId);
    }

    private RunOutcome current(String runId) {
        return runs.find(runId).orElseThrow(() -> new IllegalStateException("Unknown run " + runId));
    }
}
