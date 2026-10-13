package dev.mytechprofile.langchain122.screening.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Writes a LangChain4j {@link DecisionRequest} as an OpenRouter decisions body.
 *
 * <p>OpenRouter wants {@code state} plus a {@code questions} object keyed by name. A yes/no
 * question is {@code noul}, a scale is {@code score} with criteria lowest first, and a choice is
 * {@code choice}. An image makes {@code state} an array, because OpenRouter reads images only as
 * top-level array items. Text-only input stays an object of named fields.
 *
 * <pre>{@code
 * String json = new OpenRouterRequestMapper().toJson(request, "openai/gpt-6-luna-decisions");
 * // {"model":"openai/gpt-6-luna-decisions","state":["recruiterNote: ...",{"type":"image_url",...}],
 * //  "questions":{"advance":{"type":"noul",...},"fit":{"type":"score",...}}}
 * }</pre>
 *
 * A remote image URL, a missing mime type, or an unknown question type throws
 * {@link IllegalArgumentException} before any HTTP call.
 */
final class OpenRouterRequestMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param request named input and questions from {@code DecisionServices}
     * @param modelName OpenRouter catalog id placed in {@code model}
     * @return UTF-8 JSON for {@code POST /api/alpha/decisions}
     */
    String toJson(DecisionRequest request, String modelName) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", modelName);
        body.set("state", state(request.input()));
        ObjectNode questions = body.putObject("questions");
        request.questions().forEach((name, question) -> questions.set(name, question(question)));
        try {
            return JSON.writeValueAsString(body);
        } catch (IOException failure) {
            throw new OpenRouterDecisionException("Could not write the OpenRouter decisions body", failure);
        }
    }

    private static JsonNode state(Object input) {
        if (!(input instanceof Map<?, ?> namedInput)) {
            throw new IllegalArgumentException("Expected a named input map from DecisionServices, got " + input);
        }
        return namedInput.values().stream().anyMatch(ImageContent.class::isInstance)
                ? stateArray(namedInput)
                : stateObject(namedInput);
    }

    private static ObjectNode stateObject(Map<?, ?> namedInput) {
        ObjectNode state = JSON.createObjectNode();
        namedInput.forEach((name, value) -> state.put(String.valueOf(name), String.valueOf(value)));
        return state;
    }

    private static ArrayNode stateArray(Map<?, ?> namedInput) {
        ArrayNode state = JSON.createArrayNode();
        namedInput.forEach((name, value) -> {
            if (value instanceof ImageContent image) {
                state.add(imagePart(image.image()));
            } else {
                state.add(name + ": " + value);
            }
        });
        return state;
    }

    private static ObjectNode imagePart(Image image) {
        if (!hasText(image.base64Data())) {
            throw new IllegalArgumentException(image.url() != null
                    ? "OpenRouter decisions does not fetch remote image URLs. Pass base64 image bytes."
                    : "OpenRouter decisions needs base64 image bytes.");
        }
        if (!hasText(image.mimeType())) {
            throw new IllegalArgumentException(
                    "OpenRouter decisions needs an image mime type of image/png, image/jpeg, or image/webp.");
        }
        ObjectNode part = JSON.createObjectNode();
        part.put("type", "image_url");
        part.putObject("image_url").put("url", "data:" + image.mimeType() + ";base64," + image.base64Data());
        return part;
    }

    private static ObjectNode question(Question question) {
        ObjectNode node = JSON.createObjectNode();
        node.put("instructions", question.text());
        switch (question) {
            case YesNoQuestion yesNo -> yesNo(node, yesNo);
            case ScaleQuestion scale -> score(node, scale);
            case ChoiceQuestion choice -> choice(node, choice);
            default -> throw new IllegalArgumentException(
                    "OpenRouter decisions has no mapping for " + question.getClass().getSimpleName());
        }
        return node;
    }

    private static void yesNo(ObjectNode node, YesNoQuestion question) {
        node.put("type", "noul");
        if (hasText(question.yesWhen()) && hasText(question.noWhen())) {
            ObjectNode criteria = node.putObject("criteria");
            criteria.put("true", question.yesWhen());
            criteria.put("false", question.noWhen());
        }
    }

    private static void score(ObjectNode node, ScaleQuestion question) {
        node.put("type", "score");
        ArrayNode criteria = node.putArray("criteria");
        scoreCriteria(question).forEach(criteria::add);
    }

    private static void choice(ObjectNode node, ChoiceQuestion question) {
        node.put("type", "choice");
        ObjectNode criteria = node.putObject("criteria");
        question.options().forEach(criteria::put);
    }

    private static List<String> scoreCriteria(ScaleQuestion question) {
        List<String> descriptions = question.levelDescriptions();
        boolean described = descriptions.size() == question.levels().size()
                && descriptions.stream().anyMatch(OpenRouterRequestMapper::hasText);
        if (!described) {
            return question.levels();
        }
        return descriptions.stream().map(description -> description == null ? "" : description).toList();
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
