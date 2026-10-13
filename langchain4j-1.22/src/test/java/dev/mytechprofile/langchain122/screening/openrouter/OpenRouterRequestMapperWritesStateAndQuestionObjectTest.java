package dev.mytechprofile.langchain122.screening.openrouter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.service.decision.DecisionServices;
import dev.mytechprofile.langchain122.sample.AtsSample;
import dev.mytechprofile.langchain122.screening.ApplicationScreen;
import dev.mytechprofile.langchain122.screening.NoteBasedScreeningModel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenRouterRequestMapperWritesStateAndQuestionObjectTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final OpenRouterRequestMapper mapper = new OpenRouterRequestMapper();

    @Test
    void portfolioScreenshotBecomesADataUrlAndFitBecomesAScore() throws Exception {
        AtomicReference<DecisionRequest> captured = new AtomicReference<>();
        NoteBasedScreeningModel offline = new NoteBasedScreeningModel();
        DecisionModel capture = request -> {
            captured.set(request);
            return offline.doDecide(request);
        };
        DecisionServices.create(ApplicationScreen.class, capture)
                .screen(ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"), AtsSample.RECRUITER_NOTE);

        JsonNode body = JSON.readTree(mapper.toJson(captured.get(), OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS));

        assertEquals("openai/gpt-6-luna-decisions", body.path("model").asText());
        assertTrue(body.path("state").isArray());
        assertTrue(body.path("questions").isObject());
        assertFalse(body.path("questions").isArray());
        assertEquals("noul", body.path("questions").path("advance").path("type").asText());
        assertEquals("score", body.path("questions").path("fit").path("type").asText());
        assertTrue(body.path("questions").path("fit").path("criteria").toString()
                .contains("Meets some must-haves and misses one"));
        String serialized = body.toString();
        assertTrue(serialized.contains("data:image/png;base64," + AtsSample.PORTFOLIO_PNG));
        assertTrue(serialized.contains("Kubernetes is missing"));
    }

    @Test
    void textOnlyInputStaysANamedStateObject() throws Exception {
        DecisionRequest request = DecisionRequest.builder()
                .input(Map.of("recruiterNote", AtsSample.RECRUITER_NOTE))
                .question("advance", YesNoQuestion.of("Advance?"))
                .build();

        JsonNode body = JSON.readTree(mapper.toJson(request, OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS));

        assertTrue(body.path("state").isObject());
        assertEquals(AtsSample.RECRUITER_NOTE, body.path("state").path("recruiterNote").asText());
    }

    @Test
    void remotePortfolioUrlIsRejectedBeforeAnyRequestIsBuilt() {
        DecisionRequest request = DecisionRequest.builder()
                .input(Map.of("portfolio", ImageContent.from("https://example.com/portfolio.png")))
                .question("advance", YesNoQuestion.of("Advance?"))
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toJson(request, OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS));

        assertTrue(failure.getMessage().contains("does not fetch remote image URLs"));
    }
}
