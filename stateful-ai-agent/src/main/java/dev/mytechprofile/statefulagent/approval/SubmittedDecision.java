package dev.mytechprofile.statefulagent.approval;

public record SubmittedDecision(String toolCallKey, boolean allowed, String reason) {
}
