package dev.mytechprofile.langchain122.screening.openrouter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.mytechprofile.langchain122.sample.AtsSample;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenRouterResponseMapperReadsAnswersTest {

    private final OpenRouterResponseMapper mapper = new OpenRouterResponseMapper();

    @Test
    void partialScoreAndNoulMapOntoTheQuestionsThatWereSent() {
        DecisionResponse response = mapper.fromJson(screenRequest(), """
                {
                  "model": "openai/gpt-6-luna-decisions",
                  "answers": {
                    "advance": { "type": "noul", "noul": 0.86 },
                    "fit": { "type": "score", "score": 1.0,
                             "probabilities": { "0": 0.10, "1": 0.80, "2": 0.10 }, "confidence": 0.80 }
                  },
                  "usage": { "input_tokens": 120, "output_tokens": 40 }
                }
                """);

        assertEquals(0.86, response.yesNo("advance").probability(), 1e-9);
        ScaleAnswer fit = response.scale("fit");
        assertEquals(1.0, fit.mean(), 1e-9);
        assertEquals(List.of(0.10, 0.80, 0.10), fit.probabilities());
        assertEquals("openai/gpt-6-luna-decisions", response.modelName());
        assertEquals(120, response.tokenUsage().inputTokenCount());
    }

    @Test
    void missingScaleProbabilityIsRejected() {
        OpenRouterDecisionException failure = assertThrows(OpenRouterDecisionException.class, () -> mapper.fromJson(
                screenRequest(), """
                        {"answers": {
                          "advance": { "type": "noul", "noul": 0.86 },
                          "fit": { "type": "score", "score": 1.0, "probabilities": { "0": 0.1, "2": 0.1 } }}}
                        """));

        assertTrue(failure.getMessage().contains("missing probability 1"));
    }

    @Test
    void wrongAnswerTypeNamesTheQuestionWithoutEchoingTheBody() {
        OpenRouterDecisionException failure = assertThrows(OpenRouterDecisionException.class, () -> mapper.fromJson(
                screenRequest(), """
                        {"answers": {
                          "advance": { "type": "choice", "choice": "secret-note" },
                          "fit": { "type": "score", "score": 1.0, "probabilities": { "0": 0.1, "1": 0.8, "2": 0.1 } }}}
                        """));

        assertEquals("OpenRouter answer advance has type choice, expected noul", failure.getMessage());
        assertFalse(failure.getMessage().contains("secret-note"));
    }

    private static DecisionRequest screenRequest() {
        return DecisionRequest.builder()
                .input(Map.of("recruiterNote", AtsSample.RECRUITER_NOTE))
                .question("advance", YesNoQuestion.of("Advance?"))
                .question("fit", ScaleQuestion.builder()
                        .text("Fit?")
                        .level("NO_MATCH", "Misses a must-have")
                        .level("PARTIAL", "Meets some must-haves and misses one")
                        .level("STRONG", "Meets the must-haves")
                        .build())
                .build();
    }
}
