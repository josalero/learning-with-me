package dev.mytechprofile.aigateway.recruiting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.aigateway.domain.CandidateStatus;

class CandidateStateMachineTest {

    private final CandidateStateMachine machine = new CandidateStateMachine();

    @Test
    void allowsTheForwardAndRejectEdges() {
        assertThat(machine.allows(CandidateStatus.APPLIED, CandidateStatus.SCREENING)).isTrue();
        assertThat(machine.allows(CandidateStatus.SCREENING, CandidateStatus.INTERVIEW)).isTrue();
        assertThat(machine.allows(CandidateStatus.INTERVIEW, CandidateStatus.OFFER)).isTrue();
        assertThat(machine.allows(CandidateStatus.OFFER, CandidateStatus.HIRED)).isTrue();
        assertThat(machine.allows(CandidateStatus.SCREENING, CandidateStatus.REJECTED)).isTrue();
    }

    @Test
    void rejectsASkipFromScreeningToHired() {
        assertThat(machine.allows(CandidateStatus.SCREENING, CandidateStatus.HIRED)).isFalse();
        assertThat(machine.allows(CandidateStatus.HIRED, CandidateStatus.REJECTED)).isFalse();
    }
}
