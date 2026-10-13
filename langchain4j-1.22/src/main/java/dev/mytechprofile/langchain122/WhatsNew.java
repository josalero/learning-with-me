package dev.mytechprofile.langchain122;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.decision.DecisionServices;
import dev.mytechprofile.langchain122.applicationfiles.CancelledToolCall;
import dev.mytechprofile.langchain122.posting.PostingSkills;
import dev.mytechprofile.langchain122.posting.RecordingChatModel;
import dev.mytechprofile.langchain122.posting.UserMessageText;
import dev.mytechprofile.langchain122.provider.ProviderEnvironment;
import dev.mytechprofile.langchain122.sample.AtsSample;
import dev.mytechprofile.langchain122.screening.ApplicationScreen;
import dev.mytechprofile.langchain122.screening.NoteBasedScreeningModel;
import dev.mytechprofile.langchain122.screening.ScreenResult;
import dev.mytechprofile.langchain122.screening.openrouter.OpenRouterApplicationScreen;
import dev.mytechprofile.langchain122.screening.openrouter.OpenRouterDecisionModel;
import java.nio.file.Files;

/**
 * Screens application {@code app-1042} with the three 1.22.0 behaviors that do not need an API key.
 */
public final class WhatsNew {

    private WhatsNew() {}

    public static void main(String[] args) throws Exception {
        screenApplication();
        readPostingOnce();
        cancelApplicationRead();
        describeOpenRouter(ProviderEnvironment.system());
    }

    private static void screenApplication() {
        NoteBasedScreeningModel decisions = new NoteBasedScreeningModel();
        ScreenResult screen = DecisionServices.create(ApplicationScreen.class, decisions)
                .screen(ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"), AtsSample.RECRUITER_NOTE);
        System.out.println("screen " + AtsSample.APPLICATION_ID + ": advance=" + screen.advance()
                + " fit=" + screen.fit().mostLikely()
                + " partialDescriptionSeen="
                + decisions.lastScaleDescriptions().contains("Meets some must-haves and misses one"));
    }

    private static void readPostingOnce() {
        RecordingChatModel model = new RecordingChatModel();
        AiServices.create(PostingSkills.class, model).extract(TextContent.from(AtsSample.POSTING));
        System.out.println("posting copies=" + UserMessageText.copiesOf(model.lastRequest(), AtsSample.POSTING));
    }

    private static void cancelApplicationRead() throws Exception {
        CancelledToolCall.Outcome outcome =
                CancelledToolCall.cancelApplicationRead(Files.createTempDirectory("applications-"));
        System.out.println("read " + outcome.startedNote());
        System.out.println("cancel " + outcome.cancellationNote());
    }

    private static void describeOpenRouter(ProviderEnvironment env) {
        if (OpenRouterApplicationScreen.whenKeyPresent(env).isPresent()) {
            System.out.println("openrouter: key present. This demo still uses the offline screen.");
        } else {
            System.out.println("openrouter: set " + OpenRouterApplicationScreen.API_KEY + " to screen "
                    + AtsSample.APPLICATION_ID + " on " + OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS);
        }
    }
}
