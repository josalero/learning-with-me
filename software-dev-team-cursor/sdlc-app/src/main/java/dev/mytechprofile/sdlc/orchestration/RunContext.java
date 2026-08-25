package dev.mytechprofile.sdlc.orchestration;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.catalog.TeamBlueprint;
import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.util.function.Consumer;

/**
 * Per-run handles shared by pipeline factory, agents, and outcome assembly.
 *
 * <p><strong>When to use:</strong> constructed once in {@link SdlcOrchestrator#run} and passed
 * into {@link SdlcPipelineFactory}.
 *
 * <p><strong>Example:</strong> {@code new RunContext(runId, command, team, project, workspace,
 * branch, listener, events)}.
 */
public record RunContext(
        String runId,
        RunCommand command,
        TeamBlueprint team,
        ProjectProfile project,
        WorkspacePort workspace,
        String branch,
        StepCollectingListener listener,
        Consumer<StepEvent> events) {}
