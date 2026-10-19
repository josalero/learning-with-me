package dev.mytechprofile.aigateway.gateway.model;

/**
 * One entry of the in-memory conversation sent to the model. Contents are never written to the audit ledger.
 *
 * <p>A turn with a tool call adds two entries: the call the model made, then the result the gateway returned.
 * {@snippet :
 * messages.add(ConversationMessage.user("Evaluate candidate C-101 for requisition R-7"));
 * messages.add(ConversationMessage.assistantTool("call-1", "get_candidate_summary", "{\"candidateId\":\"C-101\"}"));
 * messages.add(ConversationMessage.toolResult("call-1", "get_candidate_summary", "{\"status\":\"SCREENING\"}"));
 * }
 */
public record ConversationMessage(Role role, String content, String toolName, String toolCallId) {

    /** Who wrote the entry. */
    public enum Role {
        USER,
        ASSISTANT,
        TOOL
    }

    /** The task prompt. */
    public static ConversationMessage user(String content) {
        return new ConversationMessage(Role.USER, content, null, null);
    }

    /** Plain model text, with no tool call. */
    public static ConversationMessage assistant(String content) {
        return new ConversationMessage(Role.ASSISTANT, content, null, null);
    }

    /** A tool call the model proposed; {@code content} holds its arguments. */
    public static ConversationMessage assistantTool(String toolCallId, String toolName, String argumentsJson) {
        return new ConversationMessage(Role.ASSISTANT, argumentsJson, toolName, toolCallId);
    }

    /** The gateway's answer to a call, either the tool output or {@code {"denied":true,"reason":"..."}}. */
    public static ConversationMessage toolResult(String toolCallId, String toolName, String resultJson) {
        return new ConversationMessage(Role.TOOL, resultJson, toolName, toolCallId);
    }
}
