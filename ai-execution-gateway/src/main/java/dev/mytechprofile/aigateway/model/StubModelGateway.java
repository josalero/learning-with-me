package dev.mytechprofile.aigateway.model;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic scripts keyed by candidate id. Each turn reports a fixed token count.
 */
@Component
@Profile({"stub", "test"})
public class StubModelGateway implements ModelGateway {

    public static final String MODEL_ID = "stub-model";
    public static final int INPUT_TOKENS = 600;
    public static final int OUTPUT_TOKENS = 150;

    private final JsonMapper json;

    public StubModelGateway(JsonMapper json) {
        this.json = json;
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public int estimatedInputTokens(List<ConversationMessage> messages) {
        return INPUT_TOKENS;
    }

    @Override
    public int maxOutputTokens() {
        return OUTPUT_TOKENS;
    }

    @Override
    public ModelTurn complete(String candidateId, List<ConversationMessage> messages) {
        int turn = assistantTurns(messages) + 1;
        return switch (candidateId) {
            case "C-101" -> c101(turn, messages);
            case "C-102" -> c102(turn);
            case "C-103" -> tool("search_evidence", "c103-" + turn, args("C-103", "query", "q" + turn));
            default -> new ModelTurn(List.of(), INPUT_TOKENS, OUTPUT_TOKENS);
        };
    }

    private ModelTurn c101(int turn, List<ConversationMessage> messages) {
        return switch (turn) {
            case 1 -> tool("get_candidate_summary", "c101-1", args("C-101"));
            case 2 -> tool("search_evidence", "c101-2", args("C-101", "query", "skills"));
            case 3 -> tool(
                    "draft_recommendation",
                    "c101-3",
                    """
                    {"candidateId":"C-101","recommendation":"ADVANCE","rationale":"Evidence supports the requisition.","citedEvidenceIds":["E-1","E-2"]}
                    """);
            case 4 -> tool(
                    "request_status_transition",
                    "c101-4",
                    "{\"candidateId\":\"C-101\",\"from\":\"SCREENING\",\"to\":\"INTERVIEW\",\"draftId\":\""
                            + draftId(messages)
                            + "\"}");
            default -> new ModelTurn(List.of(), INPUT_TOKENS, OUTPUT_TOKENS);
        };
    }

    private ModelTurn c102(int turn) {
        return switch (turn) {
            case 1 -> tool("search_evidence", "c102-1", args("C-102", "query", "resume"));
            case 2 -> tool("execute_status_transition", "c102-2", "{\"candidateId\":\"C-102\",\"to\":\"HIRED\"}");
            case 3 -> tool(
                    "request_status_transition",
                    "c102-3",
                    "{\"candidateId\":\"C-102\",\"from\":\"SCREENING\",\"to\":\"HIRED\",\"draftId\":\"00000000-0000-0000-0000-000000000000\"}");
            default -> new ModelTurn(List.of(), INPUT_TOKENS, OUTPUT_TOKENS);
        };
    }

    private String draftId(List<ConversationMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessage message = messages.get(i);
            if (message.role() == ConversationMessage.Role.TOOL) {
                JsonNode node = json.readTree(message.content());
                if (node.hasNonNull("draftId")) {
                    return node.path("draftId").asString();
                }
            }
        }
        throw new IllegalStateException("draft id missing from tool result");
    }

    private ModelTurn tool(String name, String id, String arguments) {
        return new ModelTurn(List.of(new ProposedToolCall(id, name, arguments.trim())), INPUT_TOKENS, OUTPUT_TOKENS);
    }

    private static String args(String candidateId) {
        return "{\"candidateId\":\"" + candidateId + "\"}";
    }

    private static String args(String candidateId, String extraName, String extraValue) {
        return "{\"candidateId\":\"" + candidateId + "\",\"" + extraName + "\":\"" + extraValue + "\"}";
    }

    private static int assistantTurns(List<ConversationMessage> messages) {
        int count = 0;
        for (ConversationMessage message : messages) {
            if (message.role() == ConversationMessage.Role.ASSISTANT) {
                count++;
            }
        }
        return count;
    }
}
