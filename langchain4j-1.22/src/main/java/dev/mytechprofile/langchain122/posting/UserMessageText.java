package dev.mytechprofile.langchain122.posting;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * Counts how often a phrase appears in the user-message text of one chat request.
 *
 * <p>Use it to prove that a {@code TextContent} parameter reaches the model once, not twice:
 *
 * <pre>{@code
 * UserMessageText.copiesOf(model.lastRequest(), AtsSample.POSTING);  // 1 on 1.22.0
 * }</pre>
 *
 * System, AI, and tool messages are not counted. Occurrences do not overlap.
 */
public final class UserMessageText {

    private UserMessageText() {}

    /**
     * @param request the request a {@code ChatModel} received
     * @param phrase non-empty text to look for
     * @return non-overlapping occurrences across every user {@link TextContent}
     */
    public static int copiesOf(ChatRequest request, String phrase) {
        if (phrase == null || phrase.isEmpty()) {
            throw new IllegalArgumentException("Phrase to count must not be empty");
        }
        return request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .flatMap(user -> user.contents().stream())
                .filter(TextContent.class::isInstance)
                .mapToInt(text -> occurrences(((TextContent) text).text(), phrase))
                .sum();
    }

    private static int occurrences(String text, String phrase) {
        int copies = 0;
        for (int found = text.indexOf(phrase); found >= 0; found = text.indexOf(phrase, found + phrase.length())) {
            copies++;
        }
        return copies;
    }
}
