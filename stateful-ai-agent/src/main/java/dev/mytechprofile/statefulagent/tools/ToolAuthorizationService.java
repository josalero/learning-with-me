package dev.mytechprofile.statefulagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.ApprovalForbiddenException;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ToolAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ToolAuthorizationService.class);

    private final JsonMapper jsonMapper;

    public ToolAuthorizationService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public boolean mayInvoke(AgentPrincipal principal, String toolName, String argumentsJson) {
        if ("rejectApplication".equals(toolName)) {
            return principal.approver();
        }
        if ("advanceApplication".equals(toolName)) {
            String stage = ArgumentFingerprint.canonical("advanceApplication", argumentsJson, this.jsonMapper);
            boolean high = stage.endsWith("|ONSITE") || stage.endsWith("|OFFER");
            return !high || principal.approver();
        }
        return true;
    }

    public void assertMayDecide(AgentPrincipal principal, AgentSessionEntity session, RiskLevel risk) {
        if (!session.getTenantId().equals(principal.tenantId())) {
            log.info("{} cannot decide this approval. The session belongs to another tenant.", principal.username());
            throw new SessionNotFoundException();
        }
        boolean owner = session.getOwnerName().equals(principal.username());
        if (!owner && !principal.approver()) {
            log.info("{} cannot decide this approval. Only the owner or a same-tenant approver can.", principal.username());
            throw new SessionNotFoundException();
        }
        if (risk == RiskLevel.HIGH && !principal.approver()) {
            log.info("{} cannot allow this. High-risk actions need an approver.", principal.username());
            throw new ApprovalForbiddenException("This operation requires an approver in the same tenant.");
        }
    }

    public static boolean approverStage(HiringStage stage) {
        return stage == HiringStage.ONSITE || stage == HiringStage.OFFER;
    }
}
