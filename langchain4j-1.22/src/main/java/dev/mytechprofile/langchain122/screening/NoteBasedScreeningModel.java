package dev.mytechprofile.langchain122.screening;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline stand-in for {@code OpenAiDecisionModel}. It reads the recruiter note and checks that the portfolio arrived
 * as an image.
 *
 * <pre>{@code
 * ApplicationScreen screen = DecisionServices.create(ApplicationScreen.class, new NoteBasedScreeningModel());
 * screen.screen(portfolio, "Partial match: ... Kubernetes is missing.");  // advance=true, fit=PARTIAL
 * }</pre>
 *
 * A request without an {@link ImageContent}, or with a question other than yes/no or scale, is rejected.
 */
public final class NoteBasedScreeningModel implements DecisionModel {

    private static final Map<FitLevel, List<Double>> PROBABILITIES = new EnumMap<>(Map.of(
            FitLevel.NO_MATCH, List.of(0.90, 0.08, 0.02),
            FitLevel.PARTIAL, List.of(0.10, 0.80, 0.10),
            FitLevel.STRONG, List.of(0.05, 0.10, 0.85)));

    private List<String> lastScaleDescriptions = List.of();

    /**
     * Descriptions from the last fit question, in enum order. Empty before the first call.
     */
    public List<String> lastScaleDescriptions() {
        return lastScaleDescriptions;
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        Map<?, ?> input = inputMap(request.input());
        if (input.values().stream().noneMatch(ImageContent.class::isInstance)) {
            throw new IllegalArgumentException(
                    "The decision request has no ImageContent. The portfolio parameter should be an image.");
        }
        FitLevel fit = fitFrom(String.valueOf(input.get("recruiterNote")));
        DecisionResponse.Builder response = DecisionResponse.builder();
        request.questions().forEach((name, question) -> response.answer(name, answer(question, fit)));
        return response.build();
    }

    private DecisionAnswer answer(Question question, FitLevel fit) {
        return switch (question) {
            case YesNoQuestion ignored -> YesNoAnswer.of(fit == FitLevel.NO_MATCH ? 0.08 : 0.86);
            case ScaleQuestion scale -> {
                lastScaleDescriptions = List.copyOf(scale.levelDescriptions());
                yield scaleAnswer(fit);
            }
            default -> throw new IllegalArgumentException(
                    "The offline screen answers yes/no and scale questions, not "
                            + question.getClass().getSimpleName());
        };
    }

    private static FitLevel fitFrom(String recruiterNote) {
        String note = recruiterNote.toLowerCase(Locale.ROOT);
        if (note.contains("partial") || note.contains("missing")) {
            return FitLevel.PARTIAL;
        }
        if (note.contains("strong") || note.contains("meets every")) {
            return FitLevel.STRONG;
        }
        return FitLevel.NO_MATCH;
    }

    private static ScaleAnswer scaleAnswer(FitLevel fit) {
        List<Double> probabilities = PROBABILITIES.get(fit);
        return ScaleAnswer.builder()
                .mean((double) fit.ordinal())
                .probabilities(probabilities)
                .confidence(probabilities.get(fit.ordinal()))
                .build();
    }

    private static Map<?, ?> inputMap(Object input) {
        if (input instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("Expected a named input map from DecisionServices, got " + input);
    }
}
