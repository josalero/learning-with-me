package dev.mytechprofile.statefulagent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.ApprovalForbiddenException;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import tools.jackson.databind.json.JsonMapper;

class ToolAuthorizationServiceTest {

    private final ToolAuthorizationService service = new ToolAuthorizationService(JsonMapper.builder().build());

    private final AgentPrincipal recruiter = new AgentPrincipal("alice", "tenant-a", false);

    private final AgentPrincipal approver = new AgentPrincipal("bob", "tenant-a", true);

    @Test
    void recruiterCannotRequestRejectOrLateStageMoves() {
        assertThat(this.service.mayInvoke(this.recruiter, "getApplication", "{\"applicationId\":\"APP-1\"}")).isTrue();
        assertThat(this.service.mayInvoke(this.recruiter, "rejectApplication",
                "{\"applicationId\":\"APP-1\",\"reason\":\"BAR_NOT_MET\"}")).isFalse();
        assertThat(this.service.mayInvoke(this.recruiter, "advanceApplication",
                "{\"applicationId\":\"APP-1\",\"stage\":\"ONSITE\"}")).isFalse();
        assertThat(this.service.mayInvoke(this.approver, "advanceApplication",
                "{\"applicationId\":\"APP-1\",\"stage\":\"OFFER\"}")).isTrue();
        assertThat(this.service.mayInvoke(this.recruiter, "advanceApplication",
                "{\"applicationId\":\"APP-1\",\"stage\":\"TECH_SCREEN\"}")).isTrue();
    }

    @Test
    void highDecisionRequiresAnApproverAndForeignSessionsLookMissing() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setSessionId("s");
        session.setTenantId("tenant-a");
        session.setOwnerName("alice");
        assertThatThrownBy(() -> this.service.assertMayDecide(this.recruiter, session, RiskLevel.HIGH))
                .isInstanceOf(ApprovalForbiddenException.class);
        this.service.assertMayDecide(this.approver, session, RiskLevel.HIGH);
        AgentPrincipal otherTenant = new AgentPrincipal("carol", "tenant-b", false);
        assertThatThrownBy(() -> this.service.assertMayDecide(otherTenant, session, RiskLevel.LOW))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
