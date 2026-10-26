package dev.mytechprofile.statefulagent.approval;

import org.springframework.ai.session.EventType;

public final class AgentEvents {

    public static final EventType APPROVAL_REQUESTED = EventType.of("AGENT.APPROVAL_REQUESTED");
    public static final EventType APPROVAL_DECIDED = EventType.of("AGENT.APPROVAL_DECIDED");
    public static final EventType TOOL_EXECUTED = EventType.of("AGENT.TOOL_EXECUTED");
    public static final EventType TOOL_FAILED = EventType.of("AGENT.TOOL_FAILED");
    public static final EventType RECOVERY = EventType.of("AGENT.RECOVERY");
    public static final EventType EVALUATION = EventType.of("AGENT.EVALUATION");

    private AgentEvents() {
    }
}
