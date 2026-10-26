package dev.mytechprofile.statefulagent.business;

import java.util.Map;

public final class StageTransitions {

    private static final Map<HiringStage, HiringStage> NEXT = Map.of(
            HiringStage.APPLIED, HiringStage.RECRUITER_SCREEN,
            HiringStage.RECRUITER_SCREEN, HiringStage.TECH_SCREEN,
            HiringStage.TECH_SCREEN, HiringStage.ONSITE,
            HiringStage.ONSITE, HiringStage.OFFER);

    private StageTransitions() {
    }

    public static void requireAdvance(HiringStage from, HiringStage to) {
        if (from.terminal()) {
            throw new HiringRuleException("TERMINAL_STAGE", "The application is already closed.");
        }
        if (NEXT.get(from) != to) {
            throw new HiringRuleException("ILLEGAL_TRANSITION", "The application can only move to the next stage.");
        }
    }

    public static InterviewRound gatingRound(HiringStage to) {
        return switch (to) {
            case ONSITE -> InterviewRound.TECH_SCREEN;
            case OFFER -> InterviewRound.ONSITE;
            default -> null;
        };
    }
}
