package dev.mytechprofile.aigateway.recruiting.candidate;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Allowed candidate status moves: one step forward, or to {@code REJECTED}. Anything else is refused.
 *
 * {@snippet :
 * stateMachine.allows(SCREENING, INTERVIEW);   // true
 * stateMachine.allows(SCREENING, HIRED);       // false: scenario 2's injected request
 * }
 */
@Component
public class CandidateStateMachine {

    private static final Map<CandidateStatus, Set<CandidateStatus>> ALLOWED = Map.of(
            CandidateStatus.APPLIED, EnumSet.of(CandidateStatus.SCREENING, CandidateStatus.REJECTED),
            CandidateStatus.SCREENING, EnumSet.of(CandidateStatus.INTERVIEW, CandidateStatus.REJECTED),
            CandidateStatus.INTERVIEW, EnumSet.of(CandidateStatus.OFFER, CandidateStatus.REJECTED),
            CandidateStatus.OFFER, EnumSet.of(CandidateStatus.HIRED, CandidateStatus.REJECTED));

    /** Whether {@code from -> to} is one of the allowed forward edges or a rejection. */
    public boolean allows(CandidateStatus from, CandidateStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
