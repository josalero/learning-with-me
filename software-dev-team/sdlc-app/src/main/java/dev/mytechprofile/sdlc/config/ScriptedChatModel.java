package dev.mytechprofile.sdlc.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;

/**
 * Offline chat model that returns canned JSON for each SDLC role.
 *
 * <p><strong>When to use:</strong> {@code SDLC_OFFLINE=true} and unit tests. It walks the pipeline
 * without OpenRouter. It does <em>not</em> implement the demo feature in the seed repo.
 *
 * <p><strong>Example:</strong> {@code ChatModelFactory} returns this instance when {@code
 * sdlc.offline} is true.
 */
public final class ScriptedChatModel implements ChatModel {

    /** Shared instance used by the offline factory and tests. */
    public static final ScriptedChatModel INSTANCE = new ScriptedChatModel();

    private static final String FEATURE_BRIEF =
            """
            {
              "title": "Unknown user 404 and blank-name rejection",
              "problem": "Unknown ids do not return RFC 9457 404; create accepts blank names",
              "userStories": ["As an API client I get a problem detail when the user does not exist"],
              "acceptanceCriteria": [
                {"id":"AC-1","given":"the user id is unknown","when":"GET /api/users/{id}","then":"the API returns 404 with an RFC 9457 problem detail"},
                {"id":"AC-2","given":"the name is blank","when":"POST /api/users","then":"the API returns 400 and stores nothing"}
              ],
              "outOfScope": ["authentication"],
              "priority": "must"
            }
            """;

    private static final String AI_SPEC =
            """
            {
              "summary": "Return RFC 9457 404 for unknown users and reject blank names on create",
              "filesToChange": ["UserController.java", "UserControllerTest.java"],
              "apiContract": "GET unknown id -> 404 problem+json; POST blank name -> 400",
              "dataModel": "User id, name, email",
              "testPlan": ["unknownIdReturns404ProblemDetail", "blankNameRejectedOnCreate"],
              "risks": ["Do not log names or emails"],
              "traceability": [
                {"acceptanceCriterionId":"AC-1","plannedTest":"unknownIdReturns404ProblemDetail"},
                {"acceptanceCriterionId":"AC-2","plannedTest":"blankNameRejectedOnCreate"}
              ]
            }
            """;

    private static final String CHANGE_SUMMARY =
            """
            {
              "filesTouched": [],
              "rationale": "Offline demo model does not edit the seed. Run with OPENROUTER_API_KEY to implement.",
              "notes": "Build gate still runs the seed tests."
            }
            """;

    private static final String REVIEW_VERDICT =
            """
            {
              "decision": "APPROVE",
              "findings": [],
              "blockingCount": 0
            }
            """;

    private static final String QA_VERDICT =
            """
            {
              "decision": "PASS",
              "score": 100,
              "results": [
                {"acceptanceCriterionId":"AC-1","status":"PASS","evidence":"offline demo"},
                {"acceptanceCriterionId":"AC-2","status":"PASS","evidence":"offline demo"}
              ],
              "missingTests": []
            }
            """;

    private static final String STAKEHOLDER =
            """
            {
              "decision": "APPROVED",
              "reasons": ["Offline demo pipeline completed"],
              "followUps": []
            }
            """;

    private ScriptedChatModel() {}

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String haystack = concatenate(chatRequest.messages());
        String json = select(haystack);
        return ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
    }

    private static String concatenate(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : messages) {
            text.append(message).append('\n');
        }
        return text.toString();
    }

    private static String select(String haystack) {
        String lower = haystack.toLowerCase();
        if (lower.contains("return json for changesummary")) {
            return CHANGE_SUMMARY;
        }
        if (lower.contains("return json for featurebrief")) {
            return FEATURE_BRIEF;
        }
        if (lower.contains("return json for aispec")) {
            return AI_SPEC;
        }
        if (lower.contains("return json for reviewverdict")) {
            return REVIEW_VERDICT;
        }
        if (lower.contains("return json for qaverdict")) {
            return QA_VERDICT;
        }
        if (lower.contains("return json for stakeholderdecision")) {
            return STAKEHOLDER;
        }
        return CHANGE_SUMMARY;
    }
}
