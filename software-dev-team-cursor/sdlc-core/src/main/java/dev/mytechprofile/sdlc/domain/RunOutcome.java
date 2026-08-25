package dev.mytechprofile.sdlc.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Final result of one SDLC run.
 *
 * <p>Sample: a completed run with {@link RunStatus#COMPLETED} after the stakeholder approves.
 */
public record RunOutcome(
        String runId,
        RunStatus status,
        String teamId,
        String projectId,
        String featureRequest,
        String branch,
        FeatureBrief featureBrief,
        AiSpec aiSpec,
        ChangeSummary changeSummary,
        BuildResult buildResult,
        ReviewVerdict reviewVerdict,
        QaVerdict qaVerdict,
        StakeholderDecision stakeholderDecision,
        List<StepEvent> steps,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage) {

    public RunOutcome {
        steps = steps == null ? List.of() : List.copyOf(steps);
        errorMessage = Objects.requireNonNullElse(errorMessage, "");
        branch = Objects.requireNonNullElse(branch, "");
    }

    /**
     * Returns a pending run before the pipeline starts.
     *
     * @param runId generated identifier
     * @param command team, project, and feature text
     * @return outcome with {@link RunStatus#PENDING}
     */
    public static RunOutcome pending(String runId, RunCommand command) {
        Instant now = Instant.now();
        return new RunOutcome(
                runId,
                RunStatus.PENDING,
                command.teamId(),
                command.projectId(),
                command.featureRequest(),
                "",
                FeatureBrief.fromRequest(command.featureRequest()),
                AiSpec.empty(),
                ChangeSummary.none(),
                BuildResult.none(),
                ReviewVerdict.none(),
                QaVerdict.none(),
                StakeholderDecision.none(),
                List.of(),
                now,
                null,
                "");
    }

    /**
     * Returns a copy with pipeline products from the agentic scope.
     *
     * @param nextBranch git branch used for the change
     * @param brief feature brief
     * @param spec AI spec
     * @param changes developer summary
     * @param build build gate
     * @param review PR review
     * @param qa QA verdict
     * @param stakeholder stakeholder decision
     * @return new outcome
     */
    public RunOutcome withProducts(
            String nextBranch,
            FeatureBrief brief,
            AiSpec spec,
            ChangeSummary changes,
            BuildResult build,
            ReviewVerdict review,
            QaVerdict qa,
            StakeholderDecision stakeholder) {
        return new RunOutcome(
                runId,
                status,
                teamId,
                projectId,
                featureRequest,
                nextBranch,
                brief,
                spec,
                changes,
                build,
                review,
                qa,
                stakeholder,
                steps,
                startedAt,
                finishedAt,
                errorMessage);
    }

    /**
     * Returns a copy with a new lifecycle status.
     *
     * @param nextStatus replacement status
     * @return new outcome
     */
    public RunOutcome withStatus(RunStatus nextStatus) {
        return new RunOutcome(
                runId,
                nextStatus,
                teamId,
                projectId,
                featureRequest,
                branch,
                featureBrief,
                aiSpec,
                changeSummary,
                buildResult,
                reviewVerdict,
                qaVerdict,
                stakeholderDecision,
                steps,
                startedAt,
                finishedAt,
                errorMessage);
    }

    /**
     * Returns a copy with recorded steps.
     *
     * @param nextSteps timeline
     * @return new outcome
     */
    public RunOutcome withSteps(List<StepEvent> nextSteps) {
        return new RunOutcome(
                runId,
                status,
                teamId,
                projectId,
                featureRequest,
                branch,
                featureBrief,
                aiSpec,
                changeSummary,
                buildResult,
                reviewVerdict,
                qaVerdict,
                stakeholderDecision,
                nextSteps,
                startedAt,
                finishedAt,
                errorMessage);
    }

    /**
     * Returns a failed outcome with an operator-facing message.
     *
     * @param message what failed and what to check
     * @return new outcome with {@link RunStatus#FAILED}
     */
    public RunOutcome failed(String message) {
        return new RunOutcome(
                runId,
                RunStatus.FAILED,
                teamId,
                projectId,
                featureRequest,
                branch,
                featureBrief,
                aiSpec,
                changeSummary,
                buildResult,
                reviewVerdict,
                qaVerdict,
                stakeholderDecision,
                steps,
                startedAt,
                Instant.now(),
                message);
    }

    /**
     * Returns a copy with {@code finishedAt} set to now.
     *
     * @return new outcome
     */
    public RunOutcome finishedNow() {
        return new RunOutcome(
                runId,
                status,
                teamId,
                projectId,
                featureRequest,
                branch,
                featureBrief,
                aiSpec,
                changeSummary,
                buildResult,
                reviewVerdict,
                qaVerdict,
                stakeholderDecision,
                steps,
                startedAt,
                Instant.now(),
                errorMessage);
    }
}
