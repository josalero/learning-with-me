package dev.mytechprofile.statefulagent.business;

public enum HiringStage {
    APPLIED,
    RECRUITER_SCREEN,
    TECH_SCREEN,
    ONSITE,
    OFFER,
    HIRED,
    OFFER_DECLINED,
    REJECTED;

    public boolean terminal() {
        return this == HIRED || this == OFFER_DECLINED || this == REJECTED;
    }
}
