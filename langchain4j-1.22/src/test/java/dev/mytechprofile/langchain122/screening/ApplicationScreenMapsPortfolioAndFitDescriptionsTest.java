package dev.mytechprofile.langchain122.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.decision.DecisionServices;
import dev.mytechprofile.langchain122.sample.AtsSample;
import org.junit.jupiter.api.Test;

class ApplicationScreenMapsPortfolioAndFitDescriptionsTest {

    @Test
    void partialNoteWithAPortfolioScreenshotAdvancesAndKeepsThePartialDescription() {
        NoteBasedScreeningModel model = new NoteBasedScreeningModel();
        ApplicationScreen screen = DecisionServices.create(ApplicationScreen.class, model);

        ScreenResult result = screen.screen(
                ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"), AtsSample.RECRUITER_NOTE);

        assertTrue(result.advance());
        assertEquals(FitLevel.PARTIAL, result.fit().mostLikely());
        assertTrue(model.lastScaleDescriptions().contains("Meets some must-haves and misses one"));
    }
}
