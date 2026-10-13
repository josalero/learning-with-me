package dev.mytechprofile.langchain122.screening.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads an OpenRouter decisions body back into LangChain4j answers.
 *
 * <p>Each answer is matched to the question that was sent, by name. {@code noul} becomes a
 * {@link YesNoAnswer}, {@code score} a {@link ScaleAnswer} whose mean is the 0-based level, and
 * {@code choice} a {@link ChoiceAnswer}. For application {@code app-1042}:
 *
 * <pre>{@code
 * {"answers": {
 *   "advance": {"type": "noul", "noul": 0.86},
 *   "fit": {"type": "score", "score": 1.0, "probabilities": {"0": 0.1, "1": 0.8, "2": 0.1}}}}
 * // advance = yes, fit = FitLevel.PARTIAL
 * }</pre>
 *
 * A missing answer, a wrong {@code type}, or a missing scale probability throws
 * {@link OpenRouterDecisionException}. Messages name the question and never include the body.
 */
final class OpenRouterResponseMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param request the questions that were sent, used to check each answer's type and scale length
     * @param body the response JSON, which is not logged
     * @return answers keyed by the same names, plus model id and token counts when present
     */
    DecisionResponse fromJson(DecisionRequest request, String body) {
        JsonNode root = parse(body);
        JsonNode answers = root.path("answers");
        if (!answers.isObject()) {
            throw new OpenRouterDecisionException(
                    "OpenRouter decisions answers must be an object keyed by question name");
        }
        DecisionResponse.Builder response = DecisionResponse.builder();
        if (root.path("model").isTextual()) {
            response.modelName(root.path("model").asText());
        }
        tokenUsage(root.path("usage")).ifPresent(response::tokenUsage);
        request.questions().forEach((name, question) -> response.answer(name, answer(name, question, answers.get(name))));
        return response.build();
    }

    private static DecisionAnswer answer(String name, Question question, JsonNode answer) {
        if (answer == null || !answer.isObject()) {
            throw new OpenRouterDecisionException("OpenRouter decisions response has no answer for " + name);
        }
        return switch (question) {
            case YesNoQuestion ignored -> yesNo(name, answer);
            case ScaleQuestion scale -> scale(name, scale, answer);
            case ChoiceQuestion choice -> choice(name, choice, answer);
            default -> throw new IllegalArgumentException(
                    "OpenRouter decisions has no mapping for " + question.getClass().getSimpleName());
        };
    }

    private static YesNoAnswer yesNo(String name, JsonNode answer) {
        requireType(name, answer, "noul");
        return YesNoAnswer.of(requiredNumber(name, answer, "noul"));
    }

    private static ScaleAnswer scale(String name, ScaleQuestion question, JsonNode answer) {
        requireType(name, answer, "score");
        return ScaleAnswer.builder()
                .mean(requiredNumber(name, answer, "score"))
                .probabilities(scaleProbabilities(name, question.levels().size(), answer.path("probabilities")))
                .confidence(optionalNumber(answer, "confidence"))
                .build();
    }

    private static ChoiceAnswer choice(String name, ChoiceQuestion question, JsonNode answer) {
        requireType(name, answer, "choice");
        if (!answer.path("choice").isTextual()) {
            throw new OpenRouterDecisionException("OpenRouter choice answer for " + name + " has no choice");
        }
        return ChoiceAnswer.builder()
                .value(answer.get("choice").asText())
                .options(question.options().keySet())
                .probabilities(choiceProbabilities(name, answer.path("probabilities")))
                .confidence(optionalNumber(answer, "confidence"))
                .build();
    }

    private static List<Double> scaleProbabilities(String name, int levels, JsonNode probabilities) {
        requireObject(name, probabilities);
        List<Double> values = new ArrayList<>(levels);
        for (int level = 0; level < levels; level++) {
            JsonNode value = probabilities.get(Integer.toString(level));
            if (value == null || !value.isNumber()) {
                throw new OpenRouterDecisionException(
                        "OpenRouter score answer for " + name + " is missing probability " + level);
            }
            values.add(value.doubleValue());
        }
        return List.copyOf(values);
    }

    private static Map<String, Double> choiceProbabilities(String name, JsonNode probabilities) {
        requireObject(name, probabilities);
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : probabilities.properties()) {
            if (!entry.getValue().isNumber()) {
                throw new OpenRouterDecisionException(
                        "OpenRouter choice answer for " + name + " has a non-numeric probability");
            }
            values.put(entry.getKey(), entry.getValue().doubleValue());
        }
        return Collections.unmodifiableMap(values);
    }

    private static void requireType(String name, JsonNode answer, String expected) {
        String actual = answer.path("type").asText("");
        if (!expected.equals(actual)) {
            throw new OpenRouterDecisionException(
                    "OpenRouter answer " + name + " has type " + actual + ", expected " + expected);
        }
    }

    private static void requireObject(String name, JsonNode probabilities) {
        if (!probabilities.isObject()) {
            throw new OpenRouterDecisionException("OpenRouter answer for " + name + " has no probabilities object");
        }
    }

    private static double requiredNumber(String name, JsonNode answer, String field) {
        JsonNode value = answer.get(field);
        if (value == null || !value.isNumber()) {
            throw new OpenRouterDecisionException("OpenRouter answer for " + name + " has no numeric " + field);
        }
        return value.doubleValue();
    }

    private static Double optionalNumber(JsonNode answer, String field) {
        JsonNode value = answer.get(field);
        return value != null && value.isNumber() ? value.doubleValue() : null;
    }

    private static Optional<TokenUsage> tokenUsage(JsonNode usage) {
        JsonNode input = usage.get("input_tokens");
        JsonNode output = usage.get("output_tokens");
        if (input == null || !input.isNumber() || output == null || !output.isNumber()) {
            return Optional.empty();
        }
        return Optional.of(new TokenUsage(input.intValue(), output.intValue()));
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException failure) {
            throw new OpenRouterDecisionException("OpenRouter decisions returned invalid JSON", failure);
        }
    }
}
