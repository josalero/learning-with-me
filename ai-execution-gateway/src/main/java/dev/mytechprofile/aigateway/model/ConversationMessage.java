package dev.mytechprofile.aigateway.model;

/**
 * In-memory turn. Contents are not written to the audit ledger.
 */
public record ConversationMessage(Role role, String content, String toolName, String toolCallId) {

    public enum Role {
        USER,
        ASSISTANT,
        TOOL
    }

    public static ConversationMessage user(String content) {
        return new ConversationMessage(Role.USER, content, null, null);
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage(Role.ASSISTANT, content, null, null);
    }

    public static ConversationMessage assistantTool(String toolCallId, String toolName, String argumentsJson) {
        return new ConversationMessage(Role.ASSISTANT, argumentsJson, toolName, toolCallId);
    }

    public static ConversationMessage toolResult(String toolCallId, String toolName, String resultJson) {
        return new ConversationMessage(Role.TOOL, resultJson, toolName, toolCallId);
    }
}
