package dev.mytechprofile.aigateway.recruiting;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.domain.CandidateStatus;

/**
 * Allowed candidate status moves. Anything else is refused.
 */
@Component
public class CandidateStateMachine {

    private static final Map<CandidateStatus, Set<CandidateStatus>> ALLOWED = Map.of(
            CandidateStatus.APPLIED, EnumSet.of(CandidateStatus.SCREENING, CandidateStatus.REJECTED),
            CandidateStatus.SCREENING, EnumSet.of(CandidateStatus.INTERVIEW, CandidateStatus.REJECTED),
            CandidateStatus.INTERVIEW, EnumSet.of(CandidateStatus.OFFER, CandidateStatus.REJECTED),
            CandidateStatus.OFFER, EnumSet.of(CandidateStatus.HIRED, CandidateStatus.REJECTED));

    public boolean allows(CandidateStatus from, CandidateStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
