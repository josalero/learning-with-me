package dev.mytechprofile.langchain122.screening.openrouter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.service.decision.DecisionServices;
import dev.mytechprofile.langchain122.sample.AtsSample;
import dev.mytechprofile.langchain122.screening.ApplicationScreen;
import dev.mytechprofile.langchain122.screening.FitLevel;
import dev.mytechprofile.langchain122.screening.ScreenResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenRouterDecisionModelReadsLocalDecisionsResponseTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void partialScoreAdvancesTheApplication() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        URI endpoint = serve(200, """
                {
                  "model": "openai/gpt-6-luna-decisions",
                  "answers": {
                    "advance": { "type": "noul", "noul": 0.86 },
                    "fit": {
                      "type": "score",
                      "score": 1.0,
                      "probabilities": { "0": 0.10, "1": 0.80, "2": 0.10 },
                      "confidence": 0.80
                    }
                  },
                  "usage": { "input_tokens": 120, "output_tokens": 40 }
                }
                """, authorization);
        OpenRouterDecisionModel model = new OpenRouterDecisionModel("test-key", "openai/gpt-6-luna-decisions", endpoint);
        AtomicReference<DecisionResponse> seen = new AtomicReference<>();
        DecisionModel remembering = request -> {
            DecisionResponse response = model.doDecide(request);
            seen.set(response);
            return response;
        };

        ScreenResult result = DecisionServices.create(ApplicationScreen.class, remembering)
                .screen(ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"), AtsSample.RECRUITER_NOTE);

        assertEquals("Bearer test-key", authorization.get());
        assertTrue(result.advance());
        assertEquals(FitLevel.PARTIAL, result.fit().mostLikely());
        assertEquals(120, seen.get().tokenUsage().inputTokenCount());
        assertEquals(40, seen.get().tokenUsage().outputTokenCount());
    }

    @Test
    void httpStatusIsReportedWithoutTheResponseBody() throws Exception {
        URI endpoint = serve(401, "secret-note", new AtomicReference<>());
        ApplicationScreen screen = DecisionServices.create(
                ApplicationScreen.class,
                new OpenRouterDecisionModel("test-key", "openai/gpt-6-luna-decisions", endpoint));

        OpenRouterDecisionException failure = assertThrows(
                OpenRouterDecisionException.class,
                () -> screen.screen(ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"), AtsSample.RECRUITER_NOTE));

        assertEquals("OpenRouter decisions HTTP 401", failure.getMessage());
        assertFalse(failure.getMessage().contains("secret-note"));
    }

    @Test
    void blankKeyIsRejectedBeforeAnyRequest() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new OpenRouterDecisionModel(" ", OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS, null));

        assertTrue(failure.getMessage().contains("OPENROUTER_API_KEY"));
    }

    private URI serve(int status, String body, AtomicReference<String> authorization) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/alpha/decisions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/alpha/decisions");
    }
}
