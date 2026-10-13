package dev.mytechprofile.langchain122.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import dev.mytechprofile.langchain122.sample.AtsSample;
import org.junit.jupiter.api.Test;

class PostingTextIsSentOnceTest {

    @Test
    void postingTextContentIsAttachedToTheUserMessageOnce() {
        RecordingChatModel model = new RecordingChatModel();
        PostingSkills skills = AiServices.create(PostingSkills.class, model);

        assertEquals(AtsSample.EXTRACTED_SKILLS, skills.extract(TextContent.from(AtsSample.POSTING)));

        assertEquals(1, UserMessageText.copiesOf(model.lastRequest(), AtsSample.POSTING));
    }

    @Test
    void counterSeesEveryNonOverlappingCopyInUserText() {
        ChatRequest duplicated = ChatRequest.builder()
                .messages(UserMessage.from(AtsSample.POSTING + "\n" + AtsSample.POSTING))
                .build();

        assertEquals(2, UserMessageText.copiesOf(duplicated, AtsSample.POSTING));
    }
}
