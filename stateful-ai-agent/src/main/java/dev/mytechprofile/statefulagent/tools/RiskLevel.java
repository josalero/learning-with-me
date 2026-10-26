package dev.mytechprofile.statefulagent.tools;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public boolean atLeast(RiskLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
