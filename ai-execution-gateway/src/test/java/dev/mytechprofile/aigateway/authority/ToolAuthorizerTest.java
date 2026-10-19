package dev.mytechprofile.aigateway.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.aigateway.domain.CandidateStatus;
import dev.mytechprofile.aigateway.domain.PrincipalKind;
import dev.mytechprofile.aigateway.domain.ReasonCode;
import dev.mytechprofile.aigateway.persistence.CandidateEntity;
import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.persistence.DelegationGrantEntity;
import dev.mytechprofile.aigateway.persistence.ToolPolicyRepository;
import dev.mytechprofile.aigateway.recruiting.CandidateStateMachine;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;
import tools.jackson.databind.json.JsonMapper;

class ToolAuthorizerTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final ToolPolicyRepository policies = mock(ToolPolicyRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final ToolAuthorizer authorizer = new ToolAuthorizer(
            policies,
            candidates,
            new CandidateStateMachine(),
            JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final GatewayPrincipal alice = new GatewayPrincipal(
            "recruiter-alice",
            PrincipalKind.USER,
            "acme",
            Set.of("candidates:read", "evidence:read", "transition:request", "transition:approve"));

    @BeforeEach
    void candidateExists() {
        CandidateEntity candidate = mock(CandidateEntity.class);
        when(candidate.getStatus()).thenReturn(CandidateStatus.SCREENING);
        when(candidates.findByIdAndTenantId("C-101", "acme")).thenReturn(Optional.of(candidate));
        when(policies.existsByAgentIdAndToolName("screening-agent", "get_candidate_summary")).thenReturn(true);
        when(policies.existsByAgentIdAndToolName("screening-agent", "search_evidence")).thenReturn(true);
        when(policies.existsByAgentIdAndToolName("screening-agent", "request_status_transition")).thenReturn(true);
    }

    @Test
    void permitsASummaryReadInsideTheGrant() {
        assertThat(decide(grant(null), alice, "get_candidate_summary", "{\"candidateId\":\"C-101\"}"))
                .isEqualTo(ReasonCode.PERMITTED);
    }

    @Test
    void missingGrantIsDelegationMissing() {
        assertThat(decide(null, alice, "get_candidate_summary", "{\"candidateId\":\"C-101\"}"))
                .isEqualTo(ReasonCode.DELEGATION_MISSING);
    }

    @Test
    void grantForAnotherPrincipalIsDelegationMissing() {
        DelegationGrantEntity grant = grant(null);
        grant.setPrincipalId("someone-else");
        assertThat(decide(grant, alice, "get_candidate_summary", "{\"candidateId\":\"C-101\"}"))
                .isEqualTo(ReasonCode.DELEGATION_MISSING);
    }

    @Test
    void expiredGrantIsDelegationMissing() {
        assertThat(decide(grant(NOW.minusSeconds(1)), alice, "get_candidate_summary", "{\"candidateId\":\"C-101\"}"))
                .isEqualTo(ReasonCode.DELEGATION_MISSING);
    }

    @Test
    void toolOutsideThePolicyIsNotGranted() {
        assertThat(decide(grant(null), alice, "execute_status_transition", "{\"candidateId\":\"C-101\",\"to\":\"HIRED\"}"))
                .isEqualTo(ReasonCode.TOOL_NOT_GRANTED);
    }

    @Test
    void missingScopeIsScopeMissing() {
        GatewayPrincipal limited = new GatewayPrincipal(
                "recruiter-alice", PrincipalKind.USER, "acme", Set.of("candidates:read"));
        assertThat(decide(grant(null), limited, "search_evidence", "{\"candidateId\":\"C-101\",\"query\":\"skills\"}"))
                .isEqualTo(ReasonCode.SCOPE_MISSING);
    }

    @Test
    void malformedJsonIsInvalidArguments() {
        assertThat(decide(grant(null), alice, "get_candidate_summary", "{"))
                .isEqualTo(ReasonCode.INVALID_ARGUMENTS);
    }

    @Test
    void unknownStatusIsInvalidArguments() {
        String args = "{\"candidateId\":\"C-101\",\"from\":\"SCREENING\",\"to\":\"NOPE\",\"draftId\":\"00000000-0000-0000-0000-000000000000\"}";
        assertThat(decide(grant(null), alice, "request_status_transition", args))
                .isEqualTo(ReasonCode.INVALID_ARGUMENTS);
    }

    @Test
    void unknownCandidateIsNotFound() {
        assertThat(decide(grant(null), alice, "get_candidate_summary", "{\"candidateId\":\"C-404\"}"))
                .isEqualTo(ReasonCode.CANDIDATE_NOT_FOUND);
    }

    @Test
    void disallowedEdgeIsInvalidTransition() {
        String args = "{\"candidateId\":\"C-101\",\"from\":\"SCREENING\",\"to\":\"HIRED\",\"draftId\":\"00000000-0000-0000-0000-000000000000\"}";
        assertThat(decide(grant(null), alice, "request_status_transition", args))
                .isEqualTo(ReasonCode.INVALID_TRANSITION);
    }

    @Test
    void fromStatusThatDoesNotMatchTheCandidateIsInvalidTransition() {
        String args = "{\"candidateId\":\"C-101\",\"from\":\"APPLIED\",\"to\":\"SCREENING\",\"draftId\":\"00000000-0000-0000-0000-000000000000\"}";
        assertThat(decide(grant(null), alice, "request_status_transition", args))
                .isEqualTo(ReasonCode.INVALID_TRANSITION);
    }

    private ReasonCode decide(DelegationGrantEntity grant, GatewayPrincipal principal, String tool, String args) {
        return authorizer.authorize(grant, principal, "screening-agent", tool, args).reason();
    }

    private static DelegationGrantEntity grant(Instant expiresAt) {
        DelegationGrantEntity grant = new DelegationGrantEntity();
        grant.setTenantId("acme");
        grant.setPrincipalId("recruiter-alice");
        grant.setAgentId("screening-agent");
        grant.setExpiresAt(expiresAt);
        return grant;
    }
}
