package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.mytechprofile.sdlc.catalog.RoleSpec;
import dev.mytechprofile.sdlc.catalog.TeamBlueprint;
import dev.mytechprofile.sdlc.catalog.TeamPolicy;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ArtifactFile;
import dev.mytechprofile.sdlc.domain.BuildResult;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.QaEvidence;
import dev.mytechprofile.sdlc.domain.QaVerdict;
import dev.mytechprofile.sdlc.domain.ReviewEvidence;
import dev.mytechprofile.sdlc.domain.ReviewVerdict;
import dev.mytechprofile.sdlc.domain.RoleKind;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.RunStatus;
import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import dev.mytechprofile.sdlc.domain.StakeholderMode;
import dev.mytechprofile.sdlc.domain.StateKeys;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.domain.TraceabilityItem;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import dev.mytechprofile.sdlc.port.CommandRunner;
import dev.mytechprofile.sdlc.port.RunStore;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Assembles the LangChain4j sequence and bounded rework loops for one team.
 *
 * <p><strong>When to use:</strong> {@link SdlcOrchestrator#run} after the workspace is seeded.
 *
 * <p><strong>Example:</strong> {@code UntypedAgent pipeline = factory.build(context)}.
 */
public final class SdlcPipelineFactory {

    private final AgentFactory agents;
    private final RunStateFactory state;
    private final RunOutcomeAssembler outcomes;
    private final BuildGate buildGate;
    private final VersionControlPort git;
    private final ArtifactStore artifacts;
    private final RunStore runs;
    private final HumanApprovalGate approvalGate;
    private final SdlcProperties properties;

    /**
     * Creates a pipeline factory.
     *
     * @param agents role builders
     * @param state scope keys
     * @param outcomes snapshots for HITL
     * @param commandRunner build gate
     * @param git local git
     * @param artifacts run files
     * @param runs run index
     * @param approvalGate human stakeholder
     * @param properties HITL timeout
     */
    public SdlcPipelineFactory(
            AgentFactory agents,
            RunStateFactory state,
            RunOutcomeAssembler outcomes,
            CommandRunner commandRunner,
            VersionControlPort git,
            ArtifactStore artifacts,
            RunStore runs,
            HumanApprovalGate approvalGate,
            SdlcProperties properties) {
        this.agents = agents;
        this.state = state;
        this.outcomes = outcomes;
        this.buildGate = new BuildGate(commandRunner, git, artifacts);
        this.git = git;
        this.artifacts = artifacts;
        this.runs = runs;
        this.approvalGate = approvalGate;
        this.properties = properties;
    }

    /**
     * Builds the outer stakeholder loop wrapping the inner SDLC cycle.
     *
     * @param context run handles
     * @return invokable pipeline
     */
    public UntypedAgent build(RunContext context) {
        TeamBlueprint team = context.team();
        TeamPolicy policy = team.policy();
        List<Object> cycle = new ArrayList<>();

        if (team.has(RoleKind.PRODUCT_OWNER)) {
            cycle.add(agents.productOwner(team.role(RoleKind.PRODUCT_OWNER).orElseThrow(), context));
            cycle.add(persistAction(context, ArtifactFile.FEATURE_BRIEF));
        } else {
            cycle.add(AgenticServices.agentAction(scope -> scope.writeState(
                    StateKeys.FEATURE_BRIEF,
                    FeatureBrief.fromRequest(context.command().featureRequest()))));
        }

        if (team.has(RoleKind.TECH_LEAD)) {
            cycle.add(specLoop(team.role(RoleKind.TECH_LEAD).orElseThrow(), context, policy));
            cycle.add(persistAction(context, ArtifactFile.AI_SPEC));
        } else {
            cycle.add(AgenticServices.agentAction(scope -> {
                FeatureBrief brief = RunStateFactory.read(scope, StateKeys.FEATURE_BRIEF, FeatureBrief.fromRequest(""));
                scope.writeState(StateKeys.AI_SPEC, syntheticSpec(brief));
            }));
        }

        cycle.add(implementationLoop(team.role(RoleKind.DEVELOPER).orElseThrow(), context, policy));
        cycle.add(persistAction(context, ArtifactFile.CHANGE_SUMMARY));
        cycle.add(persistAction(context, ArtifactFile.BUILD_RESULT));

        if (team.has(RoleKind.PR_REVIEWER)) {
            cycle.add(whenBuildSucceeded(
                    context,
                    "pr-reviewer",
                    reviewLoop(
                            team.role(RoleKind.PR_REVIEWER).orElseThrow(),
                            team.role(RoleKind.DEVELOPER).orElseThrow(),
                            context,
                            policy)));
            cycle.add(persistAction(context, ArtifactFile.REVIEW_VERDICT));
        }

        if (team.has(RoleKind.QA)) {
            cycle.add(whenBuildSucceeded(
                    context,
                    "qa",
                    qaLoop(
                            team.role(RoleKind.QA).orElseThrow(),
                            team.role(RoleKind.DEVELOPER).orElseThrow(),
                            context,
                            policy)));
            cycle.add(persistAction(context, ArtifactFile.QA_VERDICT));
        }

        cycle.add(commitAction(context));

        if (team.has(RoleKind.STAKEHOLDER)) {
            if (policy.stakeholderMode() == StakeholderMode.HUMAN) {
                cycle.add(humanStakeholder(context));
            } else {
                cycle.add(agents.stakeholder(team.role(RoleKind.STAKEHOLDER).orElseThrow(), context));
                cycle.add(reconcileStakeholderAction(context, policy));
            }
            cycle.add(persistAction(context, ArtifactFile.STAKEHOLDER_DECISION));
        } else {
            cycle.add(AgenticServices.agentAction(
                    scope -> scope.writeState(StateKeys.STAKEHOLDER_DECISION, StakeholderDecision.approved())));
        }

        UntypedAgent inner = AgenticServices.sequenceBuilder()
                .name("sdlc-cycle")
                .subAgents(cycle.toArray())
                .build();

        return AgenticServices.loopBuilder()
                .name("stakeholder-cycle")
                .subAgents(inner)
                .maxIterations(policy.maxStakeholderCycles())
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> {
                    Object decision = scope.readState(StateKeys.STAKEHOLDER_DECISION);
                    return decision instanceof StakeholderDecision stakeholder && stakeholder.endsCycle();
                })
                .listener(context.listener())
                .build();
    }

    private UntypedAgent specLoop(RoleSpec role, RunContext context, TeamPolicy policy) {
        return AgenticServices.loopBuilder()
                .name("spec-loop")
                .subAgents(agents.techLead(role, context, policy))
                .beforeCall(scope -> state.refresh(context, scope))
                .maxIterations(policy.maxSpecRework())
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> {
                    FeatureBrief brief =
                            RunStateFactory.read(scope, StateKeys.FEATURE_BRIEF, FeatureBrief.fromRequest(""));
                    AiSpec spec = RunStateFactory.read(scope, StateKeys.AI_SPEC, AiSpec.empty());
                    return spec.covers(brief);
                })
                .build();
    }

    private UntypedAgent implementationLoop(RoleSpec role, RunContext context, TeamPolicy policy) {
        return AgenticServices.loopBuilder()
                .name("implementation-loop")
                .subAgents(agents.developer(role, context, policy), buildGateAction(context))
                .beforeCall(scope -> state.refresh(context, scope))
                .maxIterations(policy.maxImplementationAttempts())
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> {
                    BuildResult build = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
                    if (build.success()) {
                        return true;
                    }
                    return !git.hasChanges(context.workspace().root()) && build.evaluated();
                })
                .build();
    }

    private Object buildGateAction(RunContext context) {
        return AgenticServices.agentAction(scope -> buildGate.evaluate(context, scope));
    }

    private UntypedAgent reviewLoop(
            RoleSpec reviewerRole, RoleSpec developerRole, RunContext context, TeamPolicy policy) {
        Object rework = AgenticServices.sequenceBuilder()
                .name("review-rework")
                .subAgents(agents.developer(developerRole, context, policy), buildGateAction(context))
                .beforeCall(scope -> {
                    Object verdict = scope.readState(StateKeys.REVIEW_VERDICT);
                    scope.writeState(StateKeys.REVIEW_FEEDBACK, String.valueOf(verdict));
                    state.refresh(context, scope);
                })
                .build();
        return verdictLoop(
                "review-loop",
                reviewGrade(reviewerRole, context),
                rework,
                scope -> {
                    Object verdict = scope.readState(StateKeys.REVIEW_VERDICT);
                    return verdict instanceof ReviewVerdict review && review.requestsChanges();
                },
                scope -> {
                    Object verdict = scope.readState(StateKeys.REVIEW_VERDICT);
                    return verdict instanceof ReviewVerdict review && review.approved();
                },
                policy.maxReviewCycles(),
                context);
    }

    private Object reviewGrade(RoleSpec reviewerRole, RunContext context) {
        return AgenticServices.sequenceBuilder()
                .name("review-grade")
                .subAgents(
                        agents.prReviewer(reviewerRole, context),
                        AgenticServices.agentAction(scope -> reconcileReview(context, scope)))
                .build();
    }

    private void reconcileReview(RunContext context, AgenticScope scope) {
        ReviewVerdict llm = RunStateFactory.read(scope, StateKeys.REVIEW_VERDICT, ReviewVerdict.none());
        AiSpec spec = RunStateFactory.read(scope, StateKeys.AI_SPEC, AiSpec.empty());
        BuildResult build = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
        String gitDiff = RunStateFactory.read(scope, StateKeys.GIT_DIFF, "");
        ReviewVerdict reconciled = ReviewEvidence.reconcile(llm, spec, build, gitDiff);
        scope.writeState(StateKeys.REVIEW_VERDICT, reconciled);
        if (!llm.equals(reconciled)) {
            context.events()
                    .accept(new StepEvent(
                            "pr-reviewer",
                            reconciled.approved() ? "approved" : "reconciled",
                            reconciled.decision(),
                            "Dropped empty-diff findings after a green test run",
                            0L));
        }
    }

    private UntypedAgent qaLoop(RoleSpec qaRole, RoleSpec developerRole, RunContext context, TeamPolicy policy) {
        Object rework = AgenticServices.sequenceBuilder()
                .name("qa-rework")
                .subAgents(agents.developer(developerRole, context, policy), buildGateAction(context))
                .beforeCall(scope -> {
                    Object verdict = scope.readState(StateKeys.QA_VERDICT);
                    scope.writeState(StateKeys.QA_FEEDBACK, String.valueOf(verdict));
                    state.refresh(context, scope);
                })
                .build();
        return verdictLoop(
                "qa-loop",
                qaGrade(qaRole, context),
                rework,
                scope -> {
                    QaVerdict verdict = RunStateFactory.read(scope, StateKeys.QA_VERDICT, QaVerdict.none());
                    return verdict.failed(policy.qaPassThreshold());
                },
                scope -> {
                    QaVerdict verdict = RunStateFactory.read(scope, StateKeys.QA_VERDICT, QaVerdict.none());
                    return verdict.passed(policy.qaPassThreshold());
                },
                policy.maxQaCycles(),
                context);
    }

    private Object qaGrade(RoleSpec qaRole, RunContext context) {
        return AgenticServices.sequenceBuilder()
                .name("qa-grade")
                .subAgents(
                        agents.qa(qaRole, context), AgenticServices.agentAction(scope -> reconcileQa(context, scope)))
                .build();
    }

    private void reconcileQa(RunContext context, AgenticScope scope) {
        QaVerdict llm = RunStateFactory.read(scope, StateKeys.QA_VERDICT, QaVerdict.none());
        FeatureBrief brief = RunStateFactory.read(scope, StateKeys.FEATURE_BRIEF, FeatureBrief.fromRequest(""));
        AiSpec spec = RunStateFactory.read(scope, StateKeys.AI_SPEC, AiSpec.empty());
        BuildResult build = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
        QaVerdict reconciled = QaEvidence.reconcile(llm, brief, spec, build);
        scope.writeState(StateKeys.QA_VERDICT, reconciled);
        if (!llm.equals(reconciled)) {
            context.events()
                    .accept(new StepEvent(
                            "qa",
                            "reconciled",
                            reconciled.decision() + " score=" + reconciled.score(),
                            "Aligned QA with executed tests",
                            0L));
        }
    }

    private Object reconcileStakeholderAction(RunContext context, TeamPolicy policy) {
        return AgenticServices.agentAction(scope -> {
            StakeholderDecision raw = RunStateFactory.read(
                    scope, StateKeys.STAKEHOLDER_DECISION, StakeholderDecision.rejected("pending"));
            QaVerdict qa = RunStateFactory.read(scope, StateKeys.QA_VERDICT, QaVerdict.none());
            BuildResult build = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
            AiSpec spec = RunStateFactory.read(scope, StateKeys.AI_SPEC, AiSpec.empty());
            StakeholderDecision decided = StakeholderDecision.reconcile(raw, qa, policy.qaPassThreshold(), build, spec);
            scope.writeState(StateKeys.STAKEHOLDER_DECISION, decided);
            if (!raw.equals(decided)) {
                context.events()
                        .accept(new StepEvent(
                                "stakeholder",
                                decided.isApproved() ? "approved" : "reconciled",
                                decided.decision(),
                                "Dropped no-op follow-ups after a green test run",
                                0L));
            }
        });
    }

    /**
     * Agent → optional rework → bounded loop with an exit predicate.
     *
     * @param name loop name
     * @param agent reviewer or QA
     * @param rework developer + build
     * @param needsRework whether to run rework
     * @param exit whether the loop may stop
     * @param maxCycles policy cap
     * @param context run handles
     * @return loop agent
     */
    UntypedAgent verdictLoop(
            String name,
            Object agent,
            Object rework,
            Predicate<AgenticScope> needsRework,
            Predicate<AgenticScope> exit,
            int maxCycles,
            RunContext context) {
        Object maybeRework = AgenticServices.conditionalBuilder()
                .name(name + "-conditional")
                .subAgents(needsRework::test, rework)
                .build();
        return AgenticServices.loopBuilder()
                .name(name)
                .subAgents(AgenticServices.agentAction(scope -> state.refresh(context, scope)), agent, maybeRework)
                .maxIterations(maxCycles)
                .testExitAtLoopEnd(true)
                .exitCondition(exit::test)
                .build();
    }

    private Object humanStakeholder(RunContext context) {
        return AgenticServices.humanInTheLoopBuilder()
                .description("Human stakeholder approval")
                .outputKey(StateKeys.STAKEHOLDER_DECISION)
                .responseProvider(scope -> {
                    RunOutcome waiting = outcomes.snapshot(
                                    context, scope, context.listener().steps())
                            .withStatus(RunStatus.WAITING_APPROVAL);
                    runs.save(waiting);
                    artifacts.writeOutcome(waiting);
                    context.events()
                            .accept(new StepEvent(
                                    "stakeholder", "waiting", "human approval required", "WAITING_APPROVAL", 0L));
                    return approvalGate.await(context.runId(), properties.humanApprovalTimeout());
                })
                .build();
    }

    /**
     * Review and QA expect a green test run. Invoking them on a compile failure wastes the
     * model and often crashes the loop with an {@code UntypedAgent} wrapper.
     */
    private Object whenBuildSucceeded(RunContext context, String role, Object agent) {
        Object note = AgenticServices.agentAction(scope -> {
            if (!buildSucceeded(scope)) {
                context.events()
                        .accept(new StepEvent(
                                role, "skipped", "build failed", "Waiting for a green test run before " + role, 0L));
            }
        });
        Object run = AgenticServices.conditionalBuilder()
                .name(role + "-if-green")
                .subAgents(SdlcPipelineFactory::buildSucceeded, agent)
                .build();
        return AgenticServices.sequenceBuilder()
                .name(role + "-gate")
                .subAgents(note, run)
                .build();
    }

    private static boolean buildSucceeded(AgenticScope scope) {
        return RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none())
                .success();
    }

    private Object commitAction(RunContext context) {
        return AgenticServices.agentAction(scope -> {
            String diff = git.workingTreeDiff(context.workspace().root());
            artifacts.writeText(context.runId(), ArtifactFile.PULL_REQUEST.fileName(), diff);
            scope.writeState(StateKeys.GIT_DIFF, RunStateFactory.truncateDiff(diff));
            FeatureBrief brief = RunStateFactory.read(scope, StateKeys.FEATURE_BRIEF, FeatureBrief.fromRequest(""));
            String title = brief.title().isBlank() ? context.command().featureRequest() : brief.title();
            BuildResult build = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
            if (build.success() && git.hasChanges(context.workspace().root())) {
                git.commitAll(context.workspace().root(), "feat: " + title);
            }
        });
    }

    private Object persistAction(RunContext context, ArtifactFile file) {
        return AgenticServices.agentAction(scope -> {
            Object value = scope.readState(file.stateKey());
            if (value != null && file != ArtifactFile.PULL_REQUEST) {
                artifacts.writeJson(context.runId(), file.fileName(), value);
            }
            RunOutcome live = outcomes.snapshot(
                            context, scope, context.listener().steps())
                    .withStatus(RunStatus.RUNNING);
            runs.save(live);
            artifacts.writeOutcome(live);
        });
    }

    private static AiSpec syntheticSpec(FeatureBrief brief) {
        List<TraceabilityItem> items = brief.acceptanceCriteria().stream()
                .map(criterion -> new TraceabilityItem(criterion.id(), "covers-" + criterion.id()))
                .toList();
        return new AiSpec(
                "Implement the feature brief",
                List.of(),
                brief.problem(),
                "",
                items.stream().map(TraceabilityItem::plannedTest).toList(),
                List.of(),
                items);
    }
}
