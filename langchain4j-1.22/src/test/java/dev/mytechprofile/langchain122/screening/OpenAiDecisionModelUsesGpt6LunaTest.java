package dev.mytechprofile.langchain122.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.model.openai.OpenAiDecisionModel;
import dev.langchain4j.model.openai.OpenAiDecisionModelName;
import org.junit.jupiter.api.Test;

class OpenAiDecisionModelUsesGpt6LunaTest {

    @Test
    void builderNamesThePublicBetaModelWithoutCallingTheApi() {
        OpenAiDecisionModel model = OpenAiDecisionModel.builder()
                .apiKey("test-key")
                .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
                .build();

        assertEquals("gpt-6-luna", model.modelName());
    }
}
